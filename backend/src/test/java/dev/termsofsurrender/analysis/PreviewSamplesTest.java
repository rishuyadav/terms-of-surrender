package dev.termsofsurrender.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.termsofsurrender.documents.DocumentExtractor;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Uses the exact paragraphs served by the frontend, not shortened copies tailored to the rules. */
class PreviewSamplesTest {
    @ParameterizedTest
    @CsvSource({
        "gym,US,CONSUMER,renewal-deadline;cancellation-cost",
        "app,INDIA,CONSUMER,trial-conversion",
        "freelance,US,FREELANCE,payment-condition;ip-before-payment;unpaid-work-on-termination",
        "india-freelance,INDIA,FREELANCE,none"
    })
    void analyzesTheActualProductSamples(
            String id,
            Analysis.Jurisdiction jurisdiction,
            Analysis.Category category,
            String expected)
            throws Exception {
        JsonNode samples;
        try (var input = getClass().getResourceAsStream("/samples/sample-documents.json")) {
            assertThat(input).as("shared frontend sample fixture").isNotNull();
            samples = new ObjectMapper().readTree(input);
        }
        List<String> paragraphs = new ArrayList<>();
        samples.path(id).forEach(node -> paragraphs.add(node.asText()));
        String text = String.join("\n\n", paragraphs);
        assertThat(text.split("\\s+").length).isGreaterThan(900);
        var document = new DocumentExtractor().extract(text, null);
        var report =
                new PreviewAnalysisEngine(new EvidenceValidator())
                        .analyze(
                                new Analysis.Input(
                                        document,
                                        new Analysis.Context(jurisdiction, category, "")));
        Set<String> highPatterns =
                report.findings().stream()
                        .filter(f -> f.impact() == Analysis.Impact.HIGH)
                        .map(f -> f.id().replaceFirst("-\\d+$", ""))
                        .collect(Collectors.toSet());
        assertThat(highPatterns)
                .as(id + " high-impact patterns")
                .isEqualTo(expected.equals("none") ? Set.of() : Set.of(expected.split(";")));
        assertThat(report.findings()).extracting(Analysis.Finding::id).doesNotHaveDuplicates();
        assertThat(report.checklist()).isNotEmpty();
        assertThat(report.findings())
                .allSatisfy(
                        f -> {
                            assertThat(
                                            document.text()
                                                    .substring(
                                                            f.evidence().start(),
                                                            f.evidence().end()))
                                    .isEqualTo(f.evidence().quote());
                            assertThat(f.relatedBlockIds())
                                    .allSatisfy(
                                            block ->
                                                    assertThat(document.blocks())
                                                            .anyMatch(b -> b.id().equals(block)));
                        });
        if (id.equals("india-freelance")) {
            assertThat(report.findings())
                    .noneMatch(f -> f.id().equals("restriction") || f.id().equals("refund-right"));
            assertThat(report.findings()).anyMatch(f -> f.id().equals("portfolio"));
        }
    }
}
