package dev.termsofsurrender.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import dev.termsofsurrender.documents.DocumentExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PreviewProtectionsTest {
    @ParameterizedTest
    @ValueSource(
            strings = {
                "No amounts will be refunded if the customer cancels after the paid term begins.",
                "Payments are non-refundable except where refunds are required by law.",
                "The client will be refunded for undelivered work when the service provider terminates.",
                "We may refund you at our discretion but do not promise any refund.",
                "The member cannot opt out of arbitration and waives any opt-out right.",
                "The freelancer may not display the work in any public portfolio.",
                "The client may display all deliverables in the client's portfolio."
            })
    void doesNotInventProtections(String clause) {
        for (var category : Analysis.Category.values()) {
            assertThat(report(category, clause).findings())
                    .noneMatch(f -> f.impact() == Analysis.Impact.FAVORABLE);
        }
    }

    @Test
    void refundMustBenefitTheAssumedReader() {
        String text =
                "If we discontinue the service, you will receive a prorated refund of unused subscription fees.";
        assertThat(report(Analysis.Category.CONSUMER, text).findings())
                .anyMatch(f -> f.id().equals("refund-right"));
        assertThat(report(Analysis.Category.FREELANCE, text).findings())
                .noneMatch(f -> f.id().equals("refund-right"));
    }

    @Test
    void marketingOptOutIsNotDescribedAsArbitrationOptOut() {
        var finding =
                report(
                                Analysis.Category.CONSUMER,
                                "You may opt out of marketing emails at any time through account settings. Arbitration is mandatory for covered disputes.")
                        .findings()
                        .stream()
                        .filter(f -> f.id().equals("opt-out"))
                        .findFirst()
                        .orElseThrow();
        assertThat(finding.title() + finding.explanation() + finding.whyItMatters())
                .doesNotContain("arbitration");
        assertThat(finding.evidence().quote()).contains("marketing emails");
    }

    @Test
    void nonExclusiveLicenseIsNotAFutureWorkRestriction() {
        assertThat(
                        report(
                                        Analysis.Category.FREELANCE,
                                        "The designer grants a perpetual, non-exclusive license to background tools solely as embedded in the deliverables.")
                                .findings())
                .noneMatch(f -> f.id().equals("restriction"));
    }

    @Test
    void confidentialityProhibitionDoesNotBecomeDataSharingPermission() {
        assertThat(
                        report(
                                        Analysis.Category.FREELANCE,
                                        "The service provider must not disclose personal information to third parties and shall keep all client materials confidential.")
                                .findings())
                .noneMatch(f -> f.id().equals("data"));
    }

    @Test
    void terminationNoticeIsNotNoticeOfChanges() {
        assertThat(
                        report(
                                        Analysis.Category.FREELANCE,
                                        "Either party may terminate on fourteen days' written notice. The parties will settle outstanding invoices on termination.")
                                .findings())
                .noneMatch(f -> f.id().equals("notice-of-changes"));
    }

    @Test
    void reviewTopicsStillProduceChecklist() {
        var report =
                report(
                        Analysis.Category.CONSUMER,
                        "This agreement requires individual arbitration of covered disputes, with an exception for eligible small claims proceedings.");
        assertThat(report.findings()).noneMatch(f -> f.impact() == Analysis.Impact.HIGH);
        assertThat(report.checklist()).isNotEmpty();
    }

    private Analysis.Report report(Analysis.Category category, String text) {
        var document =
                new DocumentExtractor()
                        .extract(
                                text
                                        + "\n\nFictional agreement used to test local clause rules only.",
                                null);
        return new PreviewAnalysisEngine(new EvidenceValidator())
                .analyze(
                        new Analysis.Input(
                                document,
                                new Analysis.Context(Analysis.Jurisdiction.US, category, "")));
    }
}
