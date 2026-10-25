package dev.termsofsurrender.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.termsofsurrender.AppProperties;
import dev.termsofsurrender.documents.Document;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Document-grounded Q&A. Answers come only from the supplied contract text; the model is instructed
 * to abstain rather than guess. Only available when the model is configured.
 */
public class ContractQa {
    private static final int MAX_QUESTION_CHARS = 300;
    private static final int MAX_CONTEXT_CHARS = 12_000;
    private static final int MAX_RESPONSE_BYTES = 65_536;

    private static final String QA_INSTRUCTIONS =
            """
            You are a document-grounded assistant. Your only source is the contract text provided.
            Answer the user's question using ONLY what the document says.
            Always cite the exact passage that supports your answer using the blockId field.
            If the document does not contain enough information to answer, say so clearly — do not guess or infer from general knowledge.
            Do not give legal advice. Do not state what the law requires. Say what the contract says.
            If multiple clauses are relevant, cite the most directly applicable one and mention any exceptions found.
            Keep the answer concise — 2-5 sentences unless the clause is complex.
            Return JSON only: {"answer": "plain-English answer grounded in the document text",
            "citation": {"blockId": "the block ID of the most relevant passage", "quote": "exact contiguous passage that supports the answer"},
            "abstain": false,
            "abstainReason": null}
            If you cannot find relevant text, return: {"answer": null, "citation": null, "abstain": true, "abstainReason": "short reason"}
            """;

    public record Answer(
            String answer, String blockId, String quote, boolean abstain, String abstainReason) {}

    private final AppProperties.Model config;
    private final ObjectMapper mapper;
    private final EvidenceValidator validator;
    private final HttpClient client;

    public ContractQa(
            AppProperties.Model config, ObjectMapper mapper, EvidenceValidator validator) {
        this(
                config,
                mapper,
                validator,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(8))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build());
    }

    ContractQa(
            AppProperties.Model config,
            ObjectMapper mapper,
            EvidenceValidator validator,
            HttpClient client) {
        this.config = config;
        this.mapper = mapper;
        this.validator = validator;
        this.client = client;
    }

    public boolean isAvailable() {
        return config.enabled();
    }

    /**
     * Answer a question grounded in the supplied document. Returns empty when the model is not
     * configured.
     */
    public Optional<Answer> ask(Document document, String question) {
        if (!config.enabled()) {
            return Optional.empty();
        }
        if (question == null || question.isBlank()) {
            return Optional.empty();
        }
        String sanitized = question.strip();
        if (sanitized.length() > MAX_QUESTION_CHARS) {
            sanitized = sanitized.substring(0, MAX_QUESTION_CHARS);
        }

        // Select the most relevant blocks by scoring each block's text overlap with the question.
        List<Document.Block> relevant = selectRelevantBlocks(document.blocks(), sanitized);

        Set<String> retrievedIds =
                relevant.stream()
                        .map(Document.Block::id)
                        .collect(java.util.stream.Collectors.toSet());

        JsonNode response = callModel(document, relevant, sanitized, retrievedIds);
        return Optional.of(parseAnswer(document, response, retrievedIds));
    }

    private List<Document.Block> selectRelevantBlocks(
            List<Document.Block> blocks, String question) {
        // Score each block by the number of question words it contains.
        String[] words = question.toLowerCase(java.util.Locale.ROOT).split("[^a-z0-9]+");
        List<String> keywords = new ArrayList<>();
        for (String w : words) {
            if (w.length() >= 4) {
                keywords.add(w);
            }
        }

        record Scored(Document.Block block, int score) {}
        List<Scored> scored = new ArrayList<>();
        for (Document.Block block : blocks) {
            String lower = block.text().toLowerCase(java.util.Locale.ROOT);
            int score = 0;
            for (String kw : keywords) {
                if (lower.contains(kw)) {
                    score++;
                }
            }
            scored.add(new Scored(block, score));
        }
        scored.sort((a, b) -> Integer.compare(b.score(), a.score()));

        // Take the top-scoring blocks up to the context character budget.
        List<Document.Block> selected = new ArrayList<>();
        int totalChars = 0;
        for (Scored s : scored) {
            if (totalChars + s.block().text().length() > MAX_CONTEXT_CHARS) {
                break;
            }
            selected.add(s.block());
            totalChars += s.block().text().length();
            if (selected.size() >= 20) {
                break;
            }
        }
        // Sort back to original document order by block ID.
        selected.sort(java.util.Comparator.comparingInt(b -> blocks.indexOf(b)));
        return selected;
    }

    private JsonNode callModel(
            Document document,
            List<Document.Block> blocks,
            String question,
            Set<String> retrievedIds) {
        CompletableFuture<HttpResponse<byte[]>> pending = null;
        try {
            Map<String, Object> userPayload = new HashMap<>();
            userPayload.put("question", question);
            userPayload.put("documentTitle", document.title());
            userPayload.put("blocks", blocks);

            String body =
                    mapper.writeValueAsString(
                            Map.of(
                                    "model",
                                    config.name(),
                                    "max_completion_tokens",
                                    800,
                                    "response_format",
                                    Map.of("type", "json_object"),
                                    "messages",
                                    List.of(
                                            Map.of("role", "system", "content", QA_INSTRUCTIONS),
                                            Map.of(
                                                    "role",
                                                    "user",
                                                    "content",
                                                    mapper.writeValueAsString(userPayload)))));

            HttpRequest request =
                    HttpRequest.newBuilder(
                                    URI.create(
                                            config.baseUrl().replaceAll("/+$", "")
                                                    + "/chat/completions"))
                            .timeout(Duration.ofSeconds(25))
                            .header("Authorization", "Bearer " + config.apiKey())
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build();

            pending =
                    client.sendAsync(
                            request, info -> new LimitedBodySubscriber(MAX_RESPONSE_BYTES));
            HttpResponse<byte[]> response = pending.get(25, TimeUnit.SECONDS);
            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                        "QA model request failed: " + response.statusCode());
            }
            JsonNode choice = mapper.readTree(response.body()).path("choices").path(0);
            if (!"stop".equals(choice.path("finish_reason").asText())) {
                throw new IllegalStateException("QA model response incomplete");
            }
            return mapper.readTree(choice.path("message").path("content").asText());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("QA request interrupted");
        } catch (java.io.IOException | ExecutionException | TimeoutException e) {
            throw new IllegalStateException("QA request failed: " + e.getMessage());
        } finally {
            if (pending != null && !pending.isDone()) {
                pending.cancel(true);
            }
        }
    }

    private Answer parseAnswer(Document document, JsonNode response, Set<String> retrievedIds) {
        boolean abstain = response.path("abstain").asBoolean(false);
        if (abstain) {
            String reason =
                    response.path("abstainReason")
                            .asText(
                                    "The document does not contain enough information to answer this question.");
            if (reason.length() > 300) {
                reason = reason.substring(0, 300);
            }
            return new Answer(null, null, null, true, reason);
        }

        String answer = response.path("answer").asText(null);
        if (answer == null || answer.isBlank()) {
            return new Answer(null, null, null, true, "No answer was returned.");
        }
        if (answer.length() > 1200) {
            answer = answer.substring(0, 1200);
        }

        JsonNode citation = response.path("citation");
        String blockId = citation.path("blockId").asText(null);
        String quote = citation.path("quote").asText(null);

        if (blockId == null || quote == null || quote.length() < 10) {
            return new Answer(
                    null,
                    null,
                    null,
                    true,
                    "No supporting passage was identified in the document.");
        }

        if (!retrievedIds.contains(blockId)) {
            return new Answer(
                    null, null, null, true, "The cited block was not in the retrieved context.");
        }

        // Validate the citation against the actual document text.
        Optional<Analysis.Evidence> evidence = validator.locate(document, blockId, quote);
        if (evidence.isPresent()) {
            return new Answer(answer, blockId, quote, false, null);
        }

        return new Answer(
                null, null, null, true, "The cited passage could not be verified in the document.");
    }
}
