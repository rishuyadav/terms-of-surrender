package dev.termsofsurrender.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.termsofsurrender.documents.DocumentExtractor;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Development regressions with provenance, not a statistically representative accuracy benchmark.
 */
class PreviewPublishedPatternsTest {
    @TestFactory
    java.util.List<DynamicTest> publishedPatterns() throws Exception {
        try (var input = getClass().getResourceAsStream("/preview/published-patterns.json")) {
            var cases = new ObjectMapper().readTree(input).path("cases");
            java.util.List<DynamicTest> tests = new java.util.ArrayList<>();
            for (var example : cases) {
                tests.add(
                        DynamicTest.dynamicTest(
                                example.path("name").asText(),
                                () -> {
                                    assertThat(example.path("source").asText())
                                            .startsWith("https://");
                                    var document =
                                            new DocumentExtractor()
                                                    .extract(example.path("text").asText(), null);
                                    var report =
                                            new PreviewAnalysisEngine(new EvidenceValidator())
                                                    .analyze(
                                                            new Analysis.Input(
                                                                    document,
                                                                    new Analysis.Context(
                                                                            Analysis.Jurisdiction
                                                                                    .US,
                                                                            Analysis.Category
                                                                                    .CONSUMER,
                                                                            "")));
                                    Set<String> expected = new HashSet<>();
                                    example.path("high")
                                            .forEach(node -> expected.add(node.asText()));
                                    assertThat(
                                                    report.findings().stream()
                                                            .filter(
                                                                    f ->
                                                                            f.impact()
                                                                                    == Analysis
                                                                                            .Impact
                                                                                            .HIGH)
                                                            .map(
                                                                    f ->
                                                                            f.id().replaceFirst(
                                                                                            "-\\d+$",
                                                                                            ""))
                                                            .collect(Collectors.toSet()))
                                            .isEqualTo(expected);
                                    assertThat(report.findings())
                                            .allSatisfy(
                                                    f ->
                                                            assertThat(
                                                                            document.text()
                                                                                    .substring(
                                                                                            f.evidence()
                                                                                                    .start(),
                                                                                            f.evidence()
                                                                                                    .end()))
                                                                    .isEqualTo(
                                                                            f.evidence().quote()));
                                    if (example.path("name").asText().contains("refund promise")) {
                                        assertThat(report.findings())
                                                .noneMatch(
                                                        f ->
                                                                f.impact()
                                                                        == Analysis.Impact
                                                                                .FAVORABLE);
                                    }
                                }));
            }
            return tests;
        }
    }
}
