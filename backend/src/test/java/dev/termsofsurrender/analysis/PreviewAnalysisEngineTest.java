package dev.termsofsurrender.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import dev.termsofsurrender.documents.DocumentExtractor;
import org.junit.jupiter.api.Test;

class PreviewAnalysisEngineTest {
    private final PreviewAnalysisEngine engine = new PreviewAnalysisEngine(new EvidenceValidator());

    @Test
    void labelsKeywordMatchesHonestlyAndPreservesEvidence() {
        var document = new DocumentExtractor().extract("The membership renews automatically every year. Arbitration is not required under this agreement. You may cancel by writing to us.", null);
        var report = engine.analyze(new Analysis.Input(document, new Analysis.Context(Analysis.Jurisdiction.INDIA, Analysis.Category.CONSUMER, "")));
        assertThat(report.mode()).isEqualTo(Analysis.Mode.RULE_BASED_PREVIEW);
        assertThat(report.legalContextAvailable()).isFalse();
        assertThat(report.findings()).hasSize(2).allSatisfy(finding -> {
            assertThat(finding.impact()).isEqualTo(Analysis.Impact.REVIEW);
            assertThat(document.text().substring(finding.evidence().start(), finding.evidence().end())).isEqualTo(finding.evidence().quote());
        });
        assertThat(report.findings().get(1).explanation()).doesNotContain("requires arbitration");
        assertThat(report.limitations()).anyMatch(value -> value.contains("not AI analysis"));
    }

    @Test
    void noMatchesDoesNotProduceASafetyClaim() {
        var document = new DocumentExtractor().extract("This document describes a schedule for the project. The parties will discuss their next meeting on Monday morning at the office.", null);
        var report = engine.analyze(new Analysis.Input(document, new Analysis.Context(Analysis.Jurisdiction.US, Analysis.Category.FREELANCE, "CA")));
        assertThat(report.findings()).isEmpty();
        assertThat(report.limitations()).anyMatch(value -> value.contains("does not mean"));
    }
}
