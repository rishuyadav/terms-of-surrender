package dev.termsofsurrender.privacy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.termsofsurrender.analysis.Analysis;
import dev.termsofsurrender.analysis.ModelAnalysisEngine;
import dev.termsofsurrender.api.ApiException;
import dev.termsofsurrender.documents.Document;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

/** Shares the existing admission guard so checks and reservations are atomic across replicas. */
@Repository
public class AiDemoQuota {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final AiDemoProperties properties;
    private final ObjectMapper mapper;

    public AiDemoQuota(
            JdbcTemplate jdbc,
            TransactionTemplate transactions,
            AiDemoProperties properties,
            ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.properties = properties;
        this.mapper = mapper;
    }

    public record Decision(Analysis.Mode mode, String reason) {}

    public record Previous(UUID analysisId, String responseJson) {}

    public record Question(String requestHash, String code, String message, String responseJson) {}

    public void lock() {
        jdbc.queryForObject("SELECT id FROM queue_guard WHERE id = 1 FOR UPDATE", Integer.class);
    }

    public void consumeNonce(Optional<VisitorIdentity.Verified> identity) {
        identity.ifPresent(
                visitor -> {
                    Integer count =
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM ai_demo_nonces WHERE nonce = ?",
                                    Integer.class,
                                    visitor.nonce());
                    if (count != null && count > 0) {
                        throw new ApiException(
                                HttpStatus.CONFLICT,
                                "REQUEST_REPLAYED",
                                "Please retry the request.");
                    }
                    jdbc.update(
                            "INSERT INTO ai_demo_nonces (nonce, expires_at) VALUES (?, ?)",
                            visitor.nonce(),
                            Timestamp.from(visitor.expiresAt()));
                });
    }

    public Optional<Previous> previous(
            String owner, String key, String operation, String payloadHash) {
        if (key == null) {
            return Optional.empty();
        }
        return jdbc
                .query(
                        "SELECT analysis_id, payload_hash, response_json FROM ai_demo_requests WHERE request_hash = ?",
                        (row, index) -> {
                            if (!payloadHash.equals(row.getString("payload_hash"))) {
                                throw new ApiException(
                                        HttpStatus.CONFLICT,
                                        "REQUEST_KEY_REUSED",
                                        "This request identifier has already been used for different input.");
                            }
                            return new Previous(
                                    UUID.fromString(row.getString("analysis_id")),
                                    row.getString("response_json"));
                        },
                        requestHash(owner, key, operation))
                .stream()
                .findFirst();
    }

    public Decision analysis(
            boolean modelEnabled, Optional<VisitorIdentity.Verified> identity, Document document) {
        if (!modelEnabled) {
            return new Decision(Analysis.Mode.RULE_BASED_PREVIEW, null);
        }
        if (!properties.enabled()) {
            return new Decision(Analysis.Mode.MODEL_ASSISTED, null);
        }
        if (identity.isEmpty()) {
            return new Decision(Analysis.Mode.RULE_BASED_PREVIEW, "VISITOR_UNVERIFIED");
        }
        if (document.text().length() > properties.maxCharacters()
                || !ModelAnalysisEngine.withinRequestBudget(document)) {
            return new Decision(Analysis.Mode.RULE_BASED_PREVIEW, "DOCUMENT_TOO_LARGE");
        }
        return reserve(identity.get().visitorHash(), "analyses_used", properties.analysesPerIp())
                ? new Decision(Analysis.Mode.MODEL_ASSISTED, null)
                : new Decision(Analysis.Mode.RULE_BASED_PREVIEW, "FREE_ANALYSES_EXHAUSTED");
    }

    public void recordAnalysis(
            String owner,
            String key,
            String payloadHash,
            UUID id,
            Optional<VisitorIdentity.Verified> identity,
            Decision decision) {
        boolean reserved = properties.enabled() && decision.mode() == Analysis.Mode.MODEL_ASSISTED;
        insertRequest(
                owner, key, "ANALYSIS", payloadHash, id, identity, reserved ? "RESERVED" : "FREE");
    }

    public Question question(
            String owner,
            String key,
            String payloadHash,
            UUID id,
            Optional<VisitorIdentity.Verified> identity,
            boolean modelReport) {
        return transactions.execute(
                transaction -> {
                    lock();
                    consumeNonce(identity);
                    Optional<Previous> previous = previous(owner, key, "QUESTION", payloadHash);
                    if (previous.isPresent()) {
                        String cached = previous.get().responseJson();
                        if (cached == null) {
                            throw new ApiException(
                                    HttpStatus.CONFLICT,
                                    "QUESTION_ALREADY_SUBMITTED",
                                    "This question has already been submitted. Please wait for its response.");
                        }
                        return new Question(null, null, null, cached);
                    }
                    if (properties.enabled() && identity.isEmpty()) {
                        return new Question(
                                null,
                                "VISITOR_UNVERIFIED",
                                "AI questions are unavailable for this request. Please use the application.",
                                null);
                    }
                    // Report exhaustion before rejecting preview-only Q&A.
                    if (properties.enabled()
                            && used(identity.orElseThrow().visitorHash(), "questions_used")
                                    >= properties.questionsPerIp()) {
                        return new Question(
                                null,
                                "FREE_QUESTIONS_EXHAUSTED",
                                "Free AI questions exhausted. You can still review this report and its cited clauses.",
                                null);
                    }
                    if (!modelReport) {
                        return new Question(
                                null,
                                "QA_REQUIRES_AI_REPORT",
                                "AI questions require an AI-generated report. You can still review this keyword preview and its cited clauses.",
                                null);
                    }
                    if (properties.enabled()) {
                        Integer inFlight =
                                jdbc.queryForObject(
                                        "SELECT COUNT(*) FROM ai_demo_requests WHERE visitor_hash = ? AND operation = 'QUESTION' AND state = 'STARTED' AND response_json IS NULL AND created_at > ?",
                                        Integer.class,
                                        identity.orElseThrow().visitorHash(),
                                        Timestamp.from(Instant.now().minusSeconds(35)));
                        if (inFlight != null && inFlight > 0) {
                            throw new ApiException(
                                    HttpStatus.TOO_MANY_REQUESTS,
                                    "QUESTION_IN_PROGRESS",
                                    "A question is already being answered. Please try again shortly.");
                        }
                    }
                    if (properties.enabled()
                            && !reserve(
                                    identity.orElseThrow().visitorHash(),
                                    "questions_used",
                                    properties.questionsPerIp())) {
                        throw new IllegalStateException(
                                "Question reservation changed under admission lock");
                    }
                    String hash =
                            insertRequest(
                                    owner, key, "QUESTION", payloadHash, id, identity, "STARTED");
                    return new Question(hash, null, null, null);
                });
    }

    public void saveAnswer(String requestHash, Map<String, Object> response) {
        try {
            jdbc.update(
                    "UPDATE ai_demo_requests SET response_json = ?, state = 'ANSWERED' WHERE request_hash = ?",
                    mapper.writeValueAsString(response),
                    requestHash);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not persist question response", exception);
        }
    }

    public Map<String, Object> cachedAnswer(String json) {
        try {
            return mapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not read question response", exception);
        }
    }

    /** Start paid work or release an unused reservation. */
    public boolean start(UUID id, boolean paid) {
        return transactions.execute(
                transaction -> {
                    lock();
                    Integer count =
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM analyses WHERE id = ? AND status = 'PROCESSING' AND expires_at > ? AND lease_until > ?",
                                    Integer.class,
                                    id.toString(),
                                    Timestamp.from(Instant.now()),
                                    Timestamp.from(Instant.now()));
                    if (count == null || count == 0) {
                        return false;
                    }
                    if (!paid) {
                        releaseUnstarted(id);
                        return true;
                    }
                    jdbc.update(
                            "UPDATE ai_demo_requests SET state = 'STARTED' WHERE analysis_id = ? AND operation = 'ANALYSIS' AND state = 'RESERVED'",
                            id.toString());
                    return true;
                });
    }

    /** Must be called under the admission lock, before deleting/expiring queued work. */
    public void releaseUnstarted(UUID id) {
        var visitors =
                jdbc.queryForList(
                        "SELECT visitor_hash FROM ai_demo_requests WHERE analysis_id = ? AND state = 'RESERVED'",
                        String.class,
                        id.toString());
        for (String visitor : visitors) {
            jdbc.update(
                    "UPDATE ai_demo_visitors SET analyses_used = analyses_used - 1 WHERE visitor_hash = ? AND analyses_used > 0",
                    visitor);
        }
        jdbc.update(
                "UPDATE ai_demo_requests SET state = 'RELEASED' WHERE analysis_id = ? AND state = 'RESERVED'",
                id.toString());
    }

    private int used(String visitor, String column) {
        return jdbc
                .query(
                        "SELECT " + column + " FROM ai_demo_visitors WHERE visitor_hash = ?",
                        (row, index) -> row.getInt(1),
                        visitor)
                .stream()
                .findFirst()
                .orElse(0);
    }

    private boolean reserve(String visitor, String column, int limit) {
        Integer count =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM ai_demo_visitors WHERE visitor_hash = ?",
                        Integer.class,
                        visitor);
        if (count == null || count == 0) {
            jdbc.update("INSERT INTO ai_demo_visitors (visitor_hash) VALUES (?)", visitor);
        }
        return jdbc.update(
                        "UPDATE ai_demo_visitors SET "
                                + column
                                + " = "
                                + column
                                + " + 1 WHERE visitor_hash = ? AND "
                                + column
                                + " < ?",
                        visitor,
                        limit)
                == 1;
    }

    private String insertRequest(
            String owner,
            String key,
            String operation,
            String payloadHash,
            UUID id,
            Optional<VisitorIdentity.Verified> identity,
            String state) {
        String hash =
                requestHash(owner, key == null ? UUID.randomUUID().toString() : key, operation);
        jdbc.update(
                "INSERT INTO ai_demo_requests (request_hash, visitor_hash, operation, payload_hash, analysis_id, state, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                hash,
                identity.map(VisitorIdentity.Verified::visitorHash).orElse(null),
                operation,
                payloadHash,
                id.toString(),
                state,
                Timestamp.from(Instant.now()));
        return hash;
    }

    private String requestHash(String owner, String key, String operation) {
        return Sessions.hash(owner + "\n" + operation + "\n" + key);
    }
}
