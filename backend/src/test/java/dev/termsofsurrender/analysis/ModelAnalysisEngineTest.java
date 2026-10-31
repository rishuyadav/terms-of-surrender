package dev.termsofsurrender.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.termsofsurrender.AppProperties;
import dev.termsofsurrender.documents.DocumentExtractor;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class ModelAnalysisEngineTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = mock(HttpClient.class);
    private final AppProperties.Model config =
            new AppProperties.Model(true, "https://example.com/v1", "test-key", "test-model");
    private final Analysis.Input input =
            new Analysis.Input(
                    new DocumentExtractor()
                            .extract(
                                    "This agreement renews automatically each year. You must give written notice thirty days before the renewal date.",
                                    null),
                    new Analysis.Context(Analysis.Jurisdiction.US, Analysis.Category.CONSUMER, ""));

    @Test
    void acceptsOnlyFindingsWithExactEvidence() throws Exception {
        Map<String, String> valid =
                Map.of(
                        "blockId",
                        "p1-b1",
                        "quote",
                        "This agreement renews automatically each year.",
                        "category",
                        "Renewal",
                        "impact",
                        "REVIEW",
                        "title",
                        "Check automatic renewal",
                        "explanation",
                        "The agreement describes annual renewal.",
                        "whyItMatters",
                        "Notice may be needed.",
                        "suggestedQuestion",
                        "How do I provide notice?");
        Map<String, String> invented = new java.util.HashMap<>(valid);
        invented.put("quote", "You can cancel any time without a fee.");
        respond(200, Map.of("findings", List.of(valid, invented)), "stop");
        Analysis.Report report =
                new ModelAnalysisEngine(config, mapper, new EvidenceValidator(), client)
                        .analyze(input);
        assertThat(report.mode()).isEqualTo(Analysis.Mode.MODEL_ASSISTED);
        assertThat(report.findings()).hasSize(1);
        assertThat(report.findings().getFirst().evidence().start()).isZero();
        assertThat(report.limitations()).anyMatch(value -> value.contains("withheld"));
    }

    @Test
    void failsRatherThanSilentlyDowngradingWhenProviderFails() throws Exception {
        respond(429, Map.of(), "stop");
        assertThatThrownBy(
                        () ->
                                new ModelAnalysisEngine(
                                                config, mapper, new EvidenceValidator(), client)
                                        .analyze(input))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Model request failed");
    }

    @Test
    void rejectsTruncatedResponsesAndInvalidConfiguration() throws Exception {
        respond(200, Map.of("findings", List.of()), "length");
        assertThatThrownBy(
                        () ->
                                new ModelAnalysisEngine(
                                                config, mapper, new EvidenceValidator(), client)
                                        .analyze(input))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Model response incomplete: finish_reason=length");
        assertThatThrownBy(
                        () ->
                                new ModelAnalysisEngine(
                                        new AppProperties.Model(
                                                true, "http://example.com", "key", "model"),
                                        mapper,
                                        new EvidenceValidator(),
                                        client))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new ModelAnalysisEngine(
                                        new AppProperties.Model(
                                                true, "https://example.com", "", ""),
                                        mapper,
                                        new EvidenceValidator(),
                                        client))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void respond(int status, Object content, String finishReason) throws Exception {
        var reply = response(status, content, finishReason);
        when(client.sendAsync(
                        any(HttpRequest.class),
                        org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenReturn(CompletableFuture.completedFuture(reply));
    }

    private HttpResponse<byte[]> response(int status, Object content, String finishReason)
            throws Exception {
        byte[] body =
                mapper.writeValueAsString(
                                Map.of(
                                        "choices",
                                        List.of(
                                                Map.of(
                                                        "finish_reason",
                                                        finishReason,
                                                        "message",
                                                        Map.of(
                                                                "content",
                                                                mapper.writeValueAsString(
                                                                        content))))))
                        .getBytes(StandardCharsets.UTF_8);
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        return response;
    }

    @Test
    void progressiveSnapshotsPreserveTheExactFinalReportAndOnlyPublishAcceptedFindings()
            throws Exception {
        var longerInput = multiGroupInput();
        Map<String, String> first = finding("p1-b1");
        Map<String, String> last = finding("p1-b6");
        Map<String, String> invented = new java.util.HashMap<>(first);
        invented.put("quote", "An invented contractual right that does not exist.");
        var firstResponse =
                response(200, Map.of("findings", List.of(first, first, invented)), "stop");
        var lastResponse = response(200, Map.of("findings", List.of(last)), "stop");
        when(client.sendAsync(
                        any(HttpRequest.class),
                        org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenReturn(
                        CompletableFuture.completedFuture(firstResponse),
                        CompletableFuture.completedFuture(lastResponse),
                        CompletableFuture.completedFuture(firstResponse),
                        CompletableFuture.completedFuture(lastResponse));
        var engine = new ModelAnalysisEngine(config, mapper, new EvidenceValidator(), client);
        List<Analysis.Progress> snapshots = new java.util.ArrayList<>();
        Analysis.Report progressive = engine.analyze(longerInput, snapshots::add);
        Analysis.Report original = engine.analyze(longerInput);

        assertThat(progressive).isEqualTo(original);
        assertThat(snapshots).hasSize(3);
        assertThat(snapshots.get(0)).isEqualTo(new Analysis.Progress(0, 2, List.of()));
        assertThat(snapshots.get(1).completedGroups()).isEqualTo(1);
        assertThat(snapshots.get(1).findings()).hasSize(1);
        assertThat(snapshots.get(2).findings()).isEqualTo(progressive.findings());
        assertThat(progressive.findings())
                .extracting(Analysis.Finding::id)
                .containsExactly("finding-1", "finding-2");
        assertThat(progressive.limitations())
                .anyMatch(value -> value.startsWith("1 model finding"));
    }

    @Test
    void aLaterProviderFailureDoesNotTurnEarlyFindingsIntoACompleteReport() throws Exception {
        var firstResponse = response(200, Map.of("findings", List.of(finding("p1-b1"))), "stop");
        var failedResponse = response(429, Map.of(), "stop");
        when(client.sendAsync(
                        any(HttpRequest.class),
                        org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenReturn(
                        CompletableFuture.completedFuture(firstResponse),
                        CompletableFuture.completedFuture(failedResponse));
        List<Analysis.Progress> snapshots = new java.util.ArrayList<>();
        assertThatThrownBy(
                        () ->
                                new ModelAnalysisEngine(
                                                config, mapper, new EvidenceValidator(), client)
                                        .analyze(multiGroupInput(), snapshots::add))
                .isInstanceOf(IllegalStateException.class);
        assertThat(snapshots).hasSize(2);
        assertThat(snapshots.getLast().completedGroups()).isEqualTo(1);
        assertThat(snapshots.getLast().findings()).hasSize(1);
    }

    private Analysis.Input multiGroupInput() {
        String paragraph =
                ("This agreement renews automatically each year. "
                                + "Neutral supplied text. ".repeat(100))
                        .substring(0, 1900);
        return new Analysis.Input(
                new DocumentExtractor()
                        .extract(
                                String.join("\n\n", java.util.Collections.nCopies(6, paragraph)),
                                null),
                input.context());
    }

    private Map<String, String> finding(String blockId) {
        return Map.of(
                "blockId", blockId,
                "quote", "This agreement renews automatically each year.",
                "category", "Renewal",
                "impact", "HIGH",
                "title", "Check renewal",
                "explanation", "The agreement describes annual renewal.",
                "whyItMatters", "Notice may be needed.",
                "suggestedQuestion", "How do I provide notice?");
    }

    @Test
    void rejectsMalformedResponsesWithoutLeakingProviderContent() throws Exception {
        respond(200, Map.of("findings", "not-an-array"), "stop");
        assertThatThrownBy(
                        () ->
                                new ModelAnalysisEngine(
                                                config, mapper, new EvidenceValidator(), client)
                                        .analyze(input))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid model response shape");
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body())
                .thenReturn("private-provider-content, not JSON".getBytes(StandardCharsets.UTF_8));
        when(client.sendAsync(
                        any(HttpRequest.class),
                        org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenReturn(CompletableFuture.completedFuture(response));
        assertThatThrownBy(
                        () ->
                                new ModelAnalysisEngine(
                                                config, mapper, new EvidenceValidator(), client)
                                        .analyze(input))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Model request unavailable");
    }

    @Test
    void cancelsTheNetworkRequestWhenTheFullResponseTimesOut() throws Exception {
        @SuppressWarnings("unchecked")
        CompletableFuture<HttpResponse<byte[]>> pending = mock(CompletableFuture.class);
        when(pending.get(35, java.util.concurrent.TimeUnit.SECONDS))
                .thenThrow(new java.util.concurrent.TimeoutException());
        when(client.sendAsync(
                        any(HttpRequest.class),
                        org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenReturn(pending);
        assertThatThrownBy(
                        () ->
                                new ModelAnalysisEngine(
                                                config, mapper, new EvidenceValidator(), client)
                                        .analyze(input))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Model request unavailable");
        org.mockito.Mockito.verify(pending).cancel(true);
    }

    @Test
    void deduplicatesQuotesAndWithholdsInvalidSeverityAndMissingFields() throws Exception {
        Map<String, String> valid =
                Map.of(
                        "blockId",
                        "p1-b1",
                        "quote",
                        "This agreement renews automatically each year.",
                        "category",
                        "Renewal",
                        "impact",
                        "HIGH",
                        "title",
                        "Check renewal",
                        "explanation",
                        "The agreement describes annual renewal.",
                        "whyItMatters",
                        "Notice may be needed.",
                        "suggestedQuestion",
                        "How do I provide notice?");
        Map<String, String> invalid = new java.util.HashMap<>(valid);
        invalid.put("impact", "ILLEGAL");
        Map<String, String> missing = new java.util.HashMap<>(valid);
        missing.remove("title");
        respond(200, Map.of("findings", List.of(valid, valid, invalid, missing)), "stop");
        var report =
                new ModelAnalysisEngine(config, mapper, new EvidenceValidator(), client)
                        .analyze(input);
        assertThat(report.findings()).hasSize(1);
        assertThat(report.findings().getFirst().impact()).isEqualTo(Analysis.Impact.HIGH);
        assertThat(report.limitations()).anyMatch(value -> value.startsWith("2 model finding(s)"));
    }
}
