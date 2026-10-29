package dev.termsofsurrender.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeout;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.termsofsurrender.documents.DocumentExtractor;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PreviewConsequencesTest {
    private final PreviewAnalysisEngine engine = new PreviewAnalysisEngine(new EvidenceValidator());

    static Stream<Arguments> consequences() {
        return Stream.of(
                Arguments.of(
                        Analysis.Category.CONSUMER,
                        "renewal-deadline",
                        "Membership renews automatically for twelve months unless cancellation notice is received at least thirty days before expiry."),
                Arguments.of(
                        Analysis.Category.CONSUMER,
                        "renewal-deadline",
                        "The subscription automatically renews annually unless you cancel at least 7 days before renewal."),
                Arguments.of(
                        Analysis.Category.CONSUMER,
                        "trial-conversion",
                        "At the end of your free trial, your account automatically converts to a paid subscription unless you cancel beforehand."),
                Arguments.of(
                        Analysis.Category.CONSUMER,
                        "trial-conversion",
                        "Unless you cancel before the trial ends, you will automatically be charged INR 2,400 for an annual subscription."),
                Arguments.of(
                        Analysis.Category.CONSUMER,
                        "cancellation-cost",
                        "If you cancel after 14 days, you will be charged a lump sum of 50% of your remaining contract obligation."),
                Arguments.of(
                        Analysis.Category.CONSUMER,
                        "cancellation-cost",
                        "Early termination carries an early termination fee equal to two monthly payments."),
                Arguments.of(
                        Analysis.Category.CONSUMER,
                        "cancellation-cost",
                        "On cancellation by notice, an early termination fee equal to two monthly payments applies."),
                Arguments.of(
                        Analysis.Category.CONSUMER,
                        "cancellation-cost",
                        "If you cancel the booking, your prepaid deposit will be forfeited."),
                Arguments.of(
                        Analysis.Category.FREELANCE,
                        "payment-condition",
                        "The final invoice may be issued only upon the client's final approval of the deliverables."),
                Arguments.of(
                        Analysis.Category.FREELANCE,
                        "payment-condition",
                        "Payment is contingent upon receipt of payment from the client's end customer."),
                Arguments.of(
                        Analysis.Category.FREELANCE,
                        "payment-condition",
                        "Payment is due net 60 upon final acceptance by the client."),
                Arguments.of(
                        Analysis.Category.FREELANCE,
                        "ip-before-payment",
                        "All intellectual property rights in final deliverables transfer to the client upon creation, regardless of payment."),
                Arguments.of(
                        Analysis.Category.FREELANCE,
                        "ip-before-payment",
                        "Ownership of deliverables shall vest in the client before full payment of the project fee."),
                Arguments.of(
                        Analysis.Category.FREELANCE,
                        "unpaid-work-on-termination",
                        "On termination, work in progress that has not been accepted will not be compensated unless agreed in writing."));
    }

    @ParameterizedTest
    @MethodSource("consequences")
    void identifiesExplicitConsequences(Analysis.Category category, String detector, String text) {
        var report = analyze(category, text);
        assertThat(report.findings())
                .anySatisfy(
                        finding -> {
                            assertThat(finding.id()).startsWith(detector + "-");
                            assertThat(finding.impact()).isEqualTo(Analysis.Impact.HIGH);
                            assertThat(finding.evidence().quote()).contains(text);
                        });
        assertThat(report.priorities()).isNotEmpty();
        assertThat(report.checklist()).isNotEmpty();
    }

    static Stream<Arguments> controls() {
        return Stream.of(
                Arguments.of(
                        Analysis.Category.CONSUMER,
                        "The subscription renews automatically each month. You may cancel online before your next billing date."),
                Arguments.of(
                        Analysis.Category.CONSUMER,
                        "Your subscription renews automatically each year and we send a renewal notice thirty days before renewal."),
                Arguments.of(
                        Analysis.Category.CONSUMER,
                        "Following your free trial, you will be charged only if you choose a paid subscription and confirm the purchase."),
                Arguments.of(
                        Analysis.Category.CONSUMER,
                        "Your free trial automatically starts when you register; a paid subscription is available only if you choose to purchase it."),
                Arguments.of(
                        Analysis.Category.CONSUMER,
                        "When you cancel your subscription, a cancellation fee of $0.00 applies and access continues until the end of the month."),
                Arguments.of(
                        Analysis.Category.CONSUMER,
                        "The membership does not renew automatically. Cancellation notice must be sent thirty days before expiry."),
                Arguments.of(
                        Analysis.Category.CONSUMER,
                        "Your subscription will not automatically renew. We will remind you thirty days before expiry."),
                Arguments.of(
                        Analysis.Category.CONSUMER,
                        "Your trial does not automatically convert to a paid subscription. You must choose a paid plan to continue."),
                Arguments.of(
                        Analysis.Category.CONSUMER,
                        "If you cancel before your trial ends, your payment method will not be charged for a subscription."),
                Arguments.of(
                        Analysis.Category.CONSUMER,
                        "If you cancel your subscription, no cancellation fee applies and no remaining payments are due."),
                Arguments.of(
                        Analysis.Category.CONSUMER,
                        "You may cancel your membership without a cancellation fee of any kind at any point in the paid period."),
                Arguments.of(
                        Analysis.Category.CONSUMER,
                        "The early termination fee is waived if you cancel after the minimum term has ended."),
                Arguments.of(
                        Analysis.Category.FREELANCE,
                        "Payment is not conditional on acceptance or approval. All invoices are due thirty days after delivery."),
                Arguments.of(
                        Analysis.Category.FREELANCE,
                        "Payment is due only after thirty days from delivery of the project files. There is no client acceptance requirement."),
                Arguments.of(
                        Analysis.Category.FREELANCE,
                        "All rights transfer to the designer upon creation, regardless of payment from the client."),
                Arguments.of(
                        Analysis.Category.FREELANCE,
                        "No ownership rights transfer to the client before full payment of the agreed project fee."),
                Arguments.of(
                        Analysis.Category.FREELANCE,
                        "All rights transfer upon creation and full payment of the agreed project fee. Background tools remain the freelancer's property."),
                Arguments.of(
                        Analysis.Category.FREELANCE,
                        "Ownership shall not transfer before full payment. The freelancer retains all intellectual property rights until paid."),
                Arguments.of(
                        Analysis.Category.FREELANCE,
                        "Rights transfer only after full payment of all outstanding invoices and approved expenses under this agreement."),
                Arguments.of(
                        Analysis.Category.FREELANCE,
                        "On termination, the client shall pay for work in progress and all completed work, whether accepted or unaccepted."),
                Arguments.of(
                        Analysis.Category.FREELANCE,
                        "No compensation for lost profits is payable on termination. All work in progress will be paid at the agreed rate."),
                Arguments.of(
                        Analysis.Category.FREELANCE,
                        "On termination, all work in progress will be paid at the agreed rate, but no compensation for lost profits is payable."),
                Arguments.of(
                        Analysis.Category.FREELANCE,
                        "The designer grants a non-exclusive license solely to use background tools embedded in the final deliverables."),
                Arguments.of(
                        Analysis.Category.CONSUMER,
                        "We provide a free trial. This document contains no information about the subscription price or conversion process."));
    }

    @ParameterizedTest
    @MethodSource("controls")
    void doesNotInflateNeutralOrNegatedTerms(Analysis.Category category, String text) {
        assertThat(analyze(category, text).findings())
                .noneMatch(f -> f.impact() == Analysis.Impact.HIGH);
    }

    @Test
    void strongerLaterPassageIsNotHiddenByEarlierTopicMention() {
        var report =
                analyze(
                        Analysis.Category.CONSUMER,
                        "Subscription overview: subscriptions may renew automatically. Read the applicable conditions below.\n\n"
                                + "The annual plan renews automatically unless you cancel at least thirty days before its scheduled end date.");
        assertThat(report.findings())
                .anySatisfy(
                        f -> {
                            assertThat(f.id()).startsWith("renewal-deadline-");
                            assertThat(f.evidence().quote()).contains("thirty days");
                        });
        assertThat(report.findings()).noneMatch(f -> f.id().equals("renewal"));
    }

    @Test
    void retainsLocalExceptionsAndLinksSeparateQualificationsConservatively() {
        var report =
                analyze(
                        Analysis.Category.CONSUMER,
                        "If you cancel, a cancellation fee of $90 applies. No early termination fee applies if the club closes permanently.\n\n"
                                + "However, members with a documented relocation may request an exemption.");
        var finding =
                report.findings().stream()
                        .filter(f -> f.id().startsWith("cancellation-cost-"))
                        .findFirst()
                        .orElseThrow();
        assertThat(finding.impact()).isEqualTo(Analysis.Impact.REVIEW);
        assertThat(finding.evidence().quote()).contains("closes permanently");
        assertThat(finding.relatedBlockIds()).containsExactly("p1-b2");
    }

    @Test
    void unresolvedReferenceDoesNotProduceHighImpact() {
        var report =
                analyze(
                        Analysis.Category.CONSUMER,
                        "If you cancel after 14 days, you will be charged 50% of remaining fees, except as provided in section 9.");
        assertThat(report.findings())
                .anySatisfy(
                        f -> {
                            assertThat(f.id()).startsWith("cancellation-cost-");
                            assertThat(f.impact()).isEqualTo(Analysis.Impact.REVIEW);
                            assertThat(f.explanation()).contains("cross-reference");
                        });
    }

    @Test
    void doesNotCombinePlanAmountsOrConditionsAcrossSentences() {
        var report =
                analyze(
                        Analysis.Category.CONSUMER,
                        "The monthly plan costs $12 and may be cancelled at any time. The annual plan carries different conditions.\n\n"
                                + "For customers in California, if you cancel after 14 days, you will be charged 30% of your annual commitment.");
        var finding =
                report.findings().stream()
                        .filter(f -> f.id().startsWith("cancellation-cost-"))
                        .findFirst()
                        .orElseThrow();
        assertThat(finding.impact()).isEqualTo(Analysis.Impact.REVIEW);
        assertThat(finding.evidence().quote()).doesNotContain("$12");
        assertThat(finding.explanation()).doesNotContain("$12");
    }

    @Test
    void unknownRoleGetsOnlyNeutralTopics() {
        var report =
                analyze(
                        Analysis.Category.OTHER,
                        "All intellectual property rights transfer before full payment. On cancellation, you will be charged 50% of remaining fees.");
        assertThat(report.findings()).noneMatch(f -> f.impact() != Analysis.Impact.REVIEW);
        assertThat(report.limitations()).anyMatch(s -> s.contains("role is unspecified"));
        assertThat(
                        analyze(
                                        Analysis.Category.OTHER,
                                        "You may opt out of marketing emails through account settings at any time after registration.")
                                .findings())
                .noneMatch(f -> f.impact() == Analysis.Impact.FAVORABLE);
    }

    @Test
    void sortsCostsBeforeRenewalAndBuildsActionableChecklist() {
        var report =
                analyze(
                        Analysis.Category.CONSUMER,
                        "Your subscription renews automatically unless you cancel thirty days before renewal.\n\n"
                                + "If you cancel after fourteen days, a cancellation fee of $100 applies.");
        assertThat(report.findings().getFirst().id()).startsWith("cancellation-cost-");
        assertThat(report.priorities().getFirst()).isEqualTo(report.findings().getFirst().id());
        assertThat(report.checklist())
                .extracting(Analysis.ChecklistItem::action)
                .contains(Analysis.ChecklistAction.ASK, Analysis.ChecklistAction.VERIFY);
    }

    @Test
    void preservesSmartQuotesDecimalsAndExactOffsets() {
        String text =
                "📝 Example terms\n\nIf you cancel after 14 days, you’ll be charged a lump sum of 50% of your remaining contract obligation. Late interest is 1.5% per month.";
        var report = analyze(Analysis.Category.CONSUMER, text);
        assertThat(report.findings()).anyMatch(f -> f.impact() == Analysis.Impact.HIGH);
        assertThat(report.findings())
                .allSatisfy(f -> assertThat(f.evidence().quote()).contains("1.5% per month"));
    }

    @Test
    void splitParagraphCannotEstablishAHighImpactConsequence() {
        String text =
                "If you cancel, a cancellation fee of $90 applies. "
                        + "Ordinary membership information. ".repeat(90)
                        + "The cancellation fee is waived in every case of permanent relocation.";
        var report = analyze(Analysis.Category.CONSUMER, text);
        assertThat(report.findings()).noneMatch(f -> f.impact() == Analysis.Impact.HIGH);
        assertThat(report.findings())
                .anySatisfy(
                        f -> {
                            assertThat(f.id()).startsWith("cancellation-cost-");
                            assertThat(f.explanation()).contains("split paragraph");
                        });
    }

    @Test
    void scansPastFirstThreeUncertainPassagesAndDeduplicatesRepeatedClauses() {
        String text =
                "If you cancel, a cancellation fee of $10 applies, except as provided in section 1.\n\n"
                        + "If you cancel, a cancellation fee of $20 applies, except as provided in section 2.\n\n"
                        + "If you cancel, a cancellation fee of $30 applies, except as provided in section 3.\n\n"
                        + "If you cancel, a cancellation fee of $90 applies.\n\n"
                        + "If you cancel, a cancellation fee of $90 applies.";
        var findings =
                analyze(Analysis.Category.CONSUMER, text).findings().stream()
                        .filter(f -> f.id().startsWith("cancellation-cost-"))
                        .toList();
        assertThat(findings).hasSize(3);
        assertThat(findings.getFirst().impact()).isEqualTo(Analysis.Impact.HIGH);
        assertThat(findings.getFirst().evidence().quote()).contains("$90");
        assertThat(findings).extracting(f -> f.evidence().quote()).doesNotHaveDuplicates();
    }

    @Test
    void handlesMaximumSizeAndAdversarialRepeatedWordsWithinBudget() {
        String text =
                ("Payment invoice acceptance termination rights trial automatically notice. ")
                        .repeat(1100);
        assertTimeout(
                Duration.ofSeconds(5),
                () -> {
                    var report = analyze(Analysis.Category.FREELANCE, text.substring(0, 79_000));
                    assertThat(report.findings())
                            .noneMatch(f -> f.impact() == Analysis.Impact.HIGH);
                });
    }

    @Test
    void reportRoundTripRetainsTheExistingJsonContract() throws Exception {
        var mapper = new ObjectMapper();
        var report =
                analyze(
                        Analysis.Category.CONSUMER,
                        "This subscription renews automatically each month. You can cancel online before the next billing date.");
        assertThat(mapper.readValue(mapper.writeValueAsString(report), Analysis.Report.class))
                .isEqualTo(report);
        var legacy =
                new Analysis.Report(
                        Analysis.Mode.RULE_BASED_PREVIEW,
                        "preview-1",
                        List.of(),
                        List.of("Not legal advice"),
                        false,
                        "United States",
                        null,
                        List.of(),
                        List.of());
        assertThat(mapper.readValue(mapper.writeValueAsString(legacy), Analysis.Report.class))
                .isEqualTo(legacy);
    }

    private Analysis.Report analyze(Analysis.Category category, String text) {
        var document =
                new DocumentExtractor()
                        .extract(
                                text.length() < 80
                                        ? text
                                                + " This agreement is a fictional example for testing only."
                                        : text,
                                null);
        var report =
                engine.analyze(
                        new Analysis.Input(
                                document,
                                new Analysis.Context(Analysis.Jurisdiction.US, category, "")));
        assertThat(report.findings())
                .allSatisfy(
                        f -> {
                            assertThat(
                                            document.text()
                                                    .substring(
                                                            f.evidence().start(),
                                                            f.evidence().end()))
                                    .isEqualTo(f.evidence().quote());
                            assertThat(document.blocks())
                                    .anyMatch(
                                            b ->
                                                    b.id().equals(f.evidence().blockId())
                                                            && b.text()
                                                                    .contains(
                                                                            f.evidence().quote()));
                        });
        return report;
    }
}
