package dev.termsofsurrender.jobs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.termsofsurrender.AppProperties;
import dev.termsofsurrender.analysis.Analysis;
import dev.termsofsurrender.api.ApiException;
import dev.termsofsurrender.privacy.AiDemoQuota;
import dev.termsofsurrender.privacy.VisitorIdentity;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class AnalysisRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final AppProperties properties;
    private final TransactionTemplate transactions;
    private final AiDemoQuota quota;

    public AnalysisRepository(
            JdbcTemplate jdbc,
            ObjectMapper mapper,
            AppProperties properties,
            TransactionTemplate transactions,
            AiDemoQuota quota) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.properties = properties;
        this.transactions = transactions;
        this.quota = quota;
    }

    public Analysis.View create(String sessionHash, Analysis.Input input) {
        return create(sessionHash, input, Optional.empty(), null);
    }

    public Analysis.View create(
            String sessionHash,
            Analysis.Input input,
            Optional<VisitorIdentity.Verified> identity,
            String requestKey) {
        return transactions.execute(
                transaction -> {
                    quota.lock();
                    quota.consumeNonce(identity);
                    String payloadHash = dev.termsofsurrender.privacy.Sessions.hash(json(input));
                    var previous = quota.previous(sessionHash, requestKey, "ANALYSIS", payloadHash);
                    if (previous.isPresent()) {
                        return find(previous.get().analysisId(), sessionHash)
                                .orElseThrow(ApiException::notFound);
                    }
                    Instant now = Instant.now();
                    int active =
                            count(
                                    "SELECT COUNT(*) FROM analyses WHERE session_hash = ? AND status IN ('QUEUED', 'PROCESSING') AND expires_at > ?",
                                    sessionHash,
                                    Timestamp.from(now));
                    int recent =
                            count(
                                    "SELECT COUNT(*) FROM analysis_admissions WHERE session_hash = ? AND admitted_at > ?",
                                    sessionHash,
                                    Timestamp.from(now.minus(1, ChronoUnit.HOURS)));
                    int pending =
                            count(
                                    "SELECT COUNT(*) FROM analyses WHERE status IN ('QUEUED', 'PROCESSING') AND expires_at > ?",
                                    Timestamp.from(now));
                    if (active >= properties.maxActiveJobsPerSession()
                            || recent >= properties.maxJobsPerSessionPerHour()) {
                        throw new ApiException(
                                HttpStatus.TOO_MANY_REQUESTS,
                                "SESSION_LIMIT",
                                "Your session has reached its analysis limit. Please try again later.");
                    }
                    if (pending >= properties.maxPendingJobs()) {
                        throw new ApiException(
                                HttpStatus.SERVICE_UNAVAILABLE,
                                "QUEUE_FULL",
                                "Analysis is busy right now. Please try again shortly.");
                    }
                    UUID id = UUID.randomUUID();
                    var decision =
                            quota.analysis(
                                    properties.model().enabled(), identity, input.document());
                    quota.recordAnalysis(
                            sessionHash, requestKey, payloadHash, id, identity, decision);
                    jdbc.update(
                            "INSERT INTO analysis_admissions (id, session_hash, admitted_at) VALUES (?, ?, ?)",
                            id.toString(),
                            sessionHash,
                            Timestamp.from(now));
                    jdbc.update(
                            "INSERT INTO analyses (id, session_hash, status, stage, input_json, created_at, expires_at, selected_mode, preview_reason, qa_available) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                            id.toString(),
                            sessionHash,
                            "QUEUED",
                            "Waiting for analysis",
                            json(input),
                            Timestamp.from(now),
                            Timestamp.from(now.plus(properties.retentionHours(), ChronoUnit.HOURS)),
                            decision.mode().name(),
                            decision.reason(),
                            properties.model().enabled());
                    return find(id, sessionHash).orElseThrow();
                });
    }

    public Optional<Analysis.View> find(UUID id, String sessionHash) {
        return jdbc
                .query(
                        "SELECT * FROM analyses WHERE id = ? AND session_hash = ? AND expires_at > ?",
                        this::view,
                        id.toString(),
                        sessionHash,
                        Timestamp.from(Instant.now()))
                .stream()
                .findFirst();
    }

    public Optional<Job> claim() {
        List<String> candidates =
                jdbc.queryForList(
                        "SELECT id FROM analyses WHERE status = 'QUEUED' AND expires_at > ? ORDER BY created_at LIMIT 8",
                        String.class,
                        Timestamp.from(Instant.now()));
        for (String id : candidates) {
            int changed =
                    jdbc.update(
                            "UPDATE analyses SET status = 'PROCESSING', stage = 'Reading terms and checking source evidence', lease_until = ?, version = version + 1 WHERE id = ? AND status = 'QUEUED' AND expires_at > ?",
                            Timestamp.from(Instant.now().plus(10, ChronoUnit.MINUTES)),
                            id,
                            Timestamp.from(Instant.now()));
            if (changed == 1) {
                return jdbc
                        .query(
                                "SELECT input_json, selected_mode FROM analyses WHERE id = ? AND status = 'PROCESSING'",
                                (row, index) ->
                                        new Job(
                                                UUID.fromString(id),
                                                read(row.getString(1), Analysis.Input.class),
                                                Analysis.Mode.valueOf(row.getString(2))),
                                id)
                        .stream()
                        .findFirst();
            }
        }
        return Optional.empty();
    }

    public void updateStage(UUID id, String stage) {
        jdbc.update(
                "UPDATE analyses SET stage = ?, version = version + 1 WHERE id = ? AND status = 'PROCESSING'",
                stage,
                id.toString());
    }

    public void complete(UUID id, Analysis.Report report) {
        Instant now = Instant.now();
        jdbc.update(
                "UPDATE analyses SET status = 'COMPLETED', stage = 'Analysis complete', report_json = ?, progress_json = NULL, lease_until = NULL, version = version + 1 WHERE id = ? AND status = 'PROCESSING' AND expires_at > ? AND lease_until > ?",
                json(report),
                id.toString(),
                Timestamp.from(now),
                Timestamp.from(now));
    }

    public void fail(UUID id) {
        jdbc.update(
                "UPDATE analyses SET status = 'FAILED', stage = 'Analysis could not be completed', error_code = 'ANALYSIS_FAILED', lease_until = NULL, version = version + 1 WHERE id = ? AND status = 'PROCESSING'",
                id.toString());
    }

    public void completePreview(UUID id, Analysis.Report report, String reason) {
        transactions.executeWithoutResult(
                transaction -> {
                    jdbc.update(
                            "UPDATE analyses SET selected_mode = 'RULE_BASED_PREVIEW', preview_reason = ?, progress_json = NULL WHERE id = ? AND status = 'PROCESSING'",
                            reason,
                            id.toString());
                    complete(id, report);
                });
    }

    public void updateProgress(UUID id, Analysis.Progress progress) {
        Instant now = Instant.now();
        String stage =
                progress.completedGroups() == progress.totalGroups()
                        ? "Preparing your complete report"
                        : "Reviewing text group "
                                + (progress.completedGroups() + 1)
                                + " of "
                                + progress.totalGroups();
        jdbc.update(
                "UPDATE analyses SET progress_json = ?, stage = ?, version = version + 1 WHERE id = ? AND status = 'PROCESSING' AND expires_at > ? AND lease_until > ?",
                json(progress),
                stage,
                id.toString(),
                Timestamp.from(now),
                Timestamp.from(now));
    }

    public boolean delete(UUID id, String sessionHash) {
        return transactions.execute(
                transaction -> {
                    quota.lock();
                    if (find(id, sessionHash).isEmpty()) {
                        return false;
                    }
                    quota.releaseUnstarted(id);
                    jdbc.update(
                            "DELETE FROM ai_demo_requests WHERE analysis_id = ?", id.toString());
                    return jdbc.update(
                                    "DELETE FROM analyses WHERE id = ? AND session_hash = ?",
                                    id.toString(),
                                    sessionHash)
                            > 0;
                });
    }

    public void cleanup() {
        transactions.executeWithoutResult(
                transaction -> {
                    quota.lock();
                    Instant now = Instant.now();
                    var expired =
                            jdbc.queryForList(
                                    "SELECT id FROM analyses WHERE expires_at <= ? OR (status = 'PROCESSING' AND lease_until < ?)",
                                    String.class,
                                    Timestamp.from(now),
                                    Timestamp.from(now));
                    expired.forEach(id -> quota.releaseUnstarted(UUID.fromString(id)));
                    jdbc.update(
                            "DELETE FROM ai_demo_requests WHERE analysis_id IN (SELECT id FROM analyses WHERE expires_at <= ?)",
                            Timestamp.from(now));
                    jdbc.update(
                            "DELETE FROM ai_demo_requests WHERE created_at <= ?",
                            Timestamp.from(
                                    now.minus(properties.retentionHours(), ChronoUnit.HOURS)));
                    jdbc.update(
                            "DELETE FROM ai_demo_nonces WHERE expires_at < ?", Timestamp.from(now));
                    jdbc.update("DELETE FROM analyses WHERE expires_at <= ?", Timestamp.from(now));
                    jdbc.update(
                            "DELETE FROM analysis_admissions WHERE admitted_at <= ?",
                            Timestamp.from(now.minus(1, ChronoUnit.HOURS)));
                    jdbc.update(
                            "UPDATE analyses SET status = 'FAILED', stage = 'Analysis timed out; please submit again', error_code = 'WORKER_TIMEOUT', lease_until = NULL, version = version + 1 WHERE status = 'PROCESSING' AND lease_until < ?",
                            Timestamp.from(now));
                });
    }

    private int count(String sql, Object... args) {
        Integer result = jdbc.queryForObject(sql, Integer.class, args);
        return result == null ? 0 : result;
    }

    private Analysis.View view(ResultSet row, int index) throws SQLException {
        Analysis.Input input = read(row.getString("input_json"), Analysis.Input.class);
        String report = row.getString("report_json");
        String progress = row.getString("progress_json");
        return new Analysis.View(
                UUID.fromString(row.getString("id")),
                Analysis.Status.valueOf(row.getString("status")),
                row.getString("stage"),
                input.document(),
                input.context(),
                report == null ? null : read(report, Analysis.Report.class),
                row.getString("error_code"),
                row.getTimestamp("created_at").toInstant(),
                row.getTimestamp("expires_at").toInstant(),
                row.getInt("version"),
                progress == null ? null : read(progress, Analysis.Progress.class),
                Analysis.Mode.valueOf(row.getString("selected_mode")),
                row.getString("preview_reason"),
                row.getBoolean("qa_available"));
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize persisted analysis");
        }
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return mapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not deserialize persisted analysis");
        }
    }

    public record Job(UUID id, Analysis.Input input, Analysis.Mode mode) {}
}
