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
    private final AppProperties.Model config = new AppProperties.Model(true, "https://example.com/v1", "test-key", "test-model");
    private final Analysis.Input input = new Analysis.Input(new DocumentExtractor().extract(
            "This agreement renews automatically each year. You must give written notice thirty days before the renewal date.", null),
            new Analysis.Context(Analysis.Jurisdiction.US, Analysis.Category.CONSUMER, ""));

    @Test
    void acceptsOnlyFindingsWithExactEvidence() throws Exception {
        Map<String, String> valid = Map.of("blockId", "p1-b1", "quote", "This agreement renews automatically each year.",
                "category", "Renewal", "impact", "REVIEW", "title", "Check automatic renewal",
                "explanation", "The agreement describes annual renewal.", "whyItMatters", "Notice may be needed.",
                "suggestedQuestion", "How do I provide notice?");
        Map<String, String> invented = new java.util.HashMap<>(valid);
        invented.put("quote", "You can cancel any time without a fee.");
        respond(200, Map.of("findings", List.of(valid, invented)), "stop");
        Analysis.Report report = new ModelAnalysisEngine(config, mapper, new EvidenceValidator(), client).analyze(input);
        assertThat(report.mode()).isEqualTo(Analysis.Mode.MODEL_ASSISTED);
        assertThat(report.findings()).hasSize(1);
        assertThat(report.findings().getFirst().evidence().start()).isZero();
        assertThat(report.limitations()).anyMatch(value -> value.contains("withheld"));
    }

    @Test
    void failsRatherThanSilentlyDowngradingWhenProviderFails() throws Exception {
        respond(429, Map.of(), "stop");
        assertThatThrownBy(() -> new ModelAnalysisEngine(config, mapper, new EvidenceValidator(), client).analyze(input))
                .isInstanceOf(IllegalStateException.class).hasMessage("Model request failed");
    }

    @Test
    void rejectsTruncatedResponsesAndInvalidConfiguration() throws Exception {
        respond(200, Map.of("findings", List.of()), "length");
        assertThatThrownBy(() -> new ModelAnalysisEngine(config, mapper, new EvidenceValidator(), client).analyze(input))
                .isInstanceOf(IllegalStateException.class).hasMessage("Model response incomplete");
        assertThatThrownBy(() -> new ModelAnalysisEngine(new AppProperties.Model(true, "http://example.com", "key", "model"), mapper, new EvidenceValidator(), client))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ModelAnalysisEngine(new AppProperties.Model(true, "https://example.com", "", ""), mapper, new EvidenceValidator(), client))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void respond(int status, Object content, String finishReason) throws Exception {
        byte[] body = mapper.writeValueAsString(Map.of("choices", List.of(Map.of("finish_reason", finishReason,
                "message", Map.of("content", mapper.writeValueAsString(content)))))).getBytes(StandardCharsets.UTF_8);
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        when(client.sendAsync(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenReturn(CompletableFuture.completedFuture(response));
    }
}
