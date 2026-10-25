package dev.termsofsurrender.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JurisdictionContextTest {

    @Test
    void labelIncludesRegionWhenProvided() {
        var ctx = new Analysis.Context(Analysis.Jurisdiction.US, Analysis.Category.CONSUMER, "California");
        assertThat(JurisdictionContext.label(ctx)).isEqualTo("United States · Consumer / subscription · California");
    }

    @Test
    void labelOmitsRegionWhenBlank() {
        var ctx = new Analysis.Context(Analysis.Jurisdiction.INDIA, Analysis.Category.FREELANCE, "");
        assertThat(JurisdictionContext.label(ctx)).isEqualTo("India · Freelance / services");
    }

    @Test
    void labelForUnspecifiedIsNotSpecified() {
        var ctx = new Analysis.Context(Analysis.Jurisdiction.UNSPECIFIED, Analysis.Category.OTHER, "");
        assertThat(JurisdictionContext.label(ctx)).isEqualTo("Not specified");
    }

    @Test
    void systemFramingMentionsRbiForIndiaConsumer() {
        var ctx = new Analysis.Context(Analysis.Jurisdiction.INDIA, Analysis.Category.CONSUMER, "");
        assertThat(JurisdictionContext.systemFraming(ctx))
                .contains("RBI")
                .contains("Digital Personal Data Protection Act");
    }

    @Test
    void systemFramingMentionsWorkForHireForUsFreelance() {
        var ctx = new Analysis.Context(Analysis.Jurisdiction.US, Analysis.Category.FREELANCE, "");
        assertThat(JurisdictionContext.systemFraming(ctx)).contains("work-for-hire");
    }

    @Test
    void systemFramingForUnspecifiedAvoidslegalClaims() {
        var ctx = new Analysis.Context(Analysis.Jurisdiction.UNSPECIFIED, Analysis.Category.OTHER, "");
        String framing = JurisdictionContext.systemFraming(ctx);
        assertThat(framing).contains("not specified").doesNotContain("US law").doesNotContain("Indian law");
    }

    @Test
    void limitationsContainJurisdictionSpecificNote() {
        var usConsumer = new Analysis.Context(Analysis.Jurisdiction.US, Analysis.Category.CONSUMER, "");
        assertThat(JurisdictionContext.limitations(usConsumer))
                .anyMatch(s -> s.contains("California") && s.contains("auto-renewal"));

        var indiaFreelance = new Analysis.Context(Analysis.Jurisdiction.INDIA, Analysis.Category.FREELANCE, "");
        assertThat(JurisdictionContext.limitations(indiaFreelance))
                .anyMatch(s -> s.contains("Copyright Act") || s.contains("GST"));
    }

    @Test
    void previewEngineFiresFreelanceRulesOnlyForFreelanceCategory() {
        var engine = new PreviewAnalysisEngine(new EvidenceValidator());
        var extractor = new dev.termsofsurrender.documents.DocumentExtractor();

        // Scope-of-work is a freelance-only rule
        String text = "The deliverables listed in the statement of work are fixed. "
                + "Additional work at the client's discretion will be billed separately.";

        var freelanceDoc = extractor.extract(text, null);
        var freelanceReport = engine.analyze(new Analysis.Input(
                freelanceDoc,
                new Analysis.Context(Analysis.Jurisdiction.US, Analysis.Category.FREELANCE, "")));
        assertThat(freelanceReport.findings())
                .anyMatch(f -> f.id().equals("scope-of-work"));

        var consumerDoc = extractor.extract(text, null);
        var consumerReport = engine.analyze(new Analysis.Input(
                consumerDoc,
                new Analysis.Context(Analysis.Jurisdiction.US, Analysis.Category.CONSUMER, "")));
        assertThat(consumerReport.findings())
                .noneMatch(f -> f.id().equals("scope-of-work"));
    }

    @Test
    void previewEngineFiresFreeTrialRuleOnlyForConsumerCategory() {
        var engine = new PreviewAnalysisEngine(new EvidenceValidator());
        var extractor = new dev.termsofsurrender.documents.DocumentExtractor();

        String text = "Your free trial lasts 30 days. After the trial period, your subscription begins automatically.";

        var consumerDoc = extractor.extract(text, null);
        var consumerReport = engine.analyze(new Analysis.Input(
                consumerDoc,
                new Analysis.Context(Analysis.Jurisdiction.US, Analysis.Category.CONSUMER, "")));
        assertThat(consumerReport.findings())
                .anyMatch(f -> f.id().equals("free-trial"));

        var freelanceDoc = extractor.extract(text, null);
        var freelanceReport = engine.analyze(new Analysis.Input(
                freelanceDoc,
                new Analysis.Context(Analysis.Jurisdiction.US, Analysis.Category.FREELANCE, "")));
        assertThat(freelanceReport.findings())
                .noneMatch(f -> f.id().equals("free-trial"));
    }

    @Test
    void previewEngineFavorableRulesFire() {
        var engine = new PreviewAnalysisEngine(new EvidenceValidator());
        var extractor = new dev.termsofsurrender.documents.DocumentExtractor();

        // Text contains opt-out, written-notice-for-changes, and refund provisions.
        String text = "Members may opt out of arbitration by sending written notice within thirty days. "
                + "The club may change these terms on thirty days written notice. "
                + "If the club closes, prepaid amounts for unused months will be refunded. "
                + "The membership renews automatically each year.";

        var doc = extractor.extract(text, null);
        var report = engine.analyze(new Analysis.Input(
                doc,
                new Analysis.Context(Analysis.Jurisdiction.US, Analysis.Category.CONSUMER, "")));

        assertThat(report.findings())
                .anyMatch(f -> f.impact() == Analysis.Impact.FAVORABLE);
        assertThat(report.findings())
                .anyMatch(f -> f.id().equals("opt-out") && f.impact() == Analysis.Impact.FAVORABLE);
        assertThat(report.findings())
                .anyMatch(f -> f.id().equals("refund-right") && f.impact() == Analysis.Impact.FAVORABLE);
    }

    @Test
    void previewReportContextLabelMatchesJurisdictionContextLabel() {
        var engine = new PreviewAnalysisEngine(new EvidenceValidator());
        var extractor = new dev.termsofsurrender.documents.DocumentExtractor();
        var ctx = new Analysis.Context(Analysis.Jurisdiction.INDIA, Analysis.Category.CONSUMER, "Maharashtra");

        var doc = extractor.extract(
                "This membership renews automatically each year. You agree to arbitration for all disputes. "
                + "Personal data may be shared with third parties for marketing purposes.", null);
        var report = engine.analyze(new Analysis.Input(doc, ctx));

        assertThat(report.contextLabel()).isEqualTo(JurisdictionContext.label(ctx));
        assertThat(report.contextLabel()).isEqualTo("India · Consumer / subscription · Maharashtra");
    }
}
