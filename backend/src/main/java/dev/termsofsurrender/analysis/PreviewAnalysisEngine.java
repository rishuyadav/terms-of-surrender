package dev.termsofsurrender.analysis;

import dev.termsofsurrender.documents.Document;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Evidence-backed local rules, not a legal assessment or contract-wide interpretation. */
public class PreviewAnalysisEngine implements AnalysisEngine {

    private static final EnumSet<Analysis.Category> ALL_CATEGORIES =
            EnumSet.allOf(Analysis.Category.class);
    private static final Pattern NO_RESTRICTION =
            Pattern.compile(
                    "\\b(?:no|without|not subject to).{0,70}(?:non-compete|non compete|exclusiv|non-solicit)");
    private static final Pattern NO_CHARGE =
            Pattern.compile(
                    "\\b(?:without|no|not owe|not pay).{0,35}(?:additional charge|additional fee|cancellation fee|termination fee|late fee)");
    private static final Pattern CHANGE = Pattern.compile("change|modif|amend|price|rate");
    private static final Pattern TIMING =
            Pattern.compile("\\bdue\\b|\\bwithin\\b|\\bnet[- ]|\\bpayable\\b|acceptance");

    private static final List<Rule> RULES =
            List.of(
                    // ── REVIEW rules ────────────────────────────────────────────────────────────
                    rule(
                            "renewal",
                            "Renewal & cancellation",
                            "Check how renewal works",
                            "auto(?:matic(?:ally)?)?[- ]renew|renew(?:s|ed)? automatically|continues? indefinitely|recurring basis",
                            "Check whether renewal is automatic, how notice must be given, and whether exceptions"
                                    + " apply.",
                            "What must I do to prevent renewal, and when must you receive my notice?",
                            ALL_CATEGORIES),
                    rule(
                            "arbitration",
                            "Disputes",
                            "Review the dispute process",
                            "arbitrat(?:ion|or|e)|class[- ]action",
                            "Dispute procedures may affect where and how a disagreement is handled. This preview"
                                    + " does not determine enforceability or whether an opt-out exists.",
                            "Does this agreement offer a dispute-process opt-out, and what exact procedure"
                                    + " applies?",
                            ALL_CATEGORIES),
                    rule(
                            "fees",
                            "Fees & charges",
                            "Look closely at additional charges",
                            "cancellation fee|early termination fee|late fee|non[- ]refundable|additional"
                                    + " (?:fee|charge)",
                            "Review the trigger, amount, and exceptions for each charge before deciding what it"
                                    + " could cost you.",
                            "Which additional charges can apply, and can you give me an example of the total"
                                    + " cost?",
                            ALL_CATEGORIES),
                    rule(
                            "liability",
                            "Liability",
                            "Understand who carries the risk",
                            "indemnif|hold harmless|limitation of liability|liability (?:shall|will|is)|liable"
                                    + " for",
                            "Risk allocation depends on the full clause, exclusions, and related provisions. A"
                                    + " keyword match does not establish who is liable.",
                            "What losses could I be responsible for, and are there any limits or mutual"
                                    + " protections?",
                            ALL_CATEGORIES),
                    rule(
                            "changes",
                            "Changes to terms",
                            "Check who can change the agreement",
                            "(?:modify|change|amend) (?:these|the) terms|(?:change|increase) (?:our |the |your )?(?:price|fees|rate)",
                            "Find out whether changes require notice, consent, or an opportunity to leave the"
                                    + " agreement.",
                            "How will I be notified of changes, and can I leave without a penalty?",
                            ALL_CATEGORIES),
                    rule(
                            "ip",
                            "Intellectual property",
                            "Check ownership and license rights",
                            "intellectual property|work[- ](?:made[- ])?for[- ]hire|assign(?:s|ment)? (?:all|the|any)"
                                    + " rights|perpetual.*licen[cs]e",
                            "Ownership and licensing can affect reuse of your work. Check scope, payment"
                                    + " conditions, pre-existing materials, and exceptions.",
                            "Who owns the content, what rights are licensed or transferred, and what purpose and duration limit those rights?",
                            ALL_CATEGORIES),
                    rule(
                            "payment",
                            "Payment",
                            "Review when payment becomes due",
                            "payment (?:is|shall|will)|net[- ](?:30|60|90)|upon (?:final )?acceptance|invoice",
                            "For freelance work, check payment timing, acceptance criteria, expenses, and what"
                                    + " happens if payment is late.",
                            "What objective event triggers payment, and is there a final date for paying each"
                                    + " invoice?",
                            EnumSet.of(Analysis.Category.FREELANCE)),
                    rule(
                            "termination",
                            "Termination",
                            "Understand how the agreement ends",
                            "terminat(?:e|ion).*?(?:notice|cause|convenience)|(?:notice|cause|convenience).*?terminat",
                            "Check notice periods, payment for completed work, and obligations that continue after"
                                    + " termination.",
                            "If either party ends the agreement, what must still be paid or performed?",
                            ALL_CATEGORIES),
                    rule(
                            "data",
                            "Data & publicity",
                            "Review data handling and publicity",
                            "likeness|marketing purposes|personal (?:data|information)|(?:share|sell|disclos|transfer).{0,80}(?:data|information)",
                            "Read the purpose, recipients, duration, and any limitations on the permission. A"
                                    + " reference to third parties does not itself mean data is sold.",
                            "What may be shared or reused, for which purposes, and what choices do I have?",
                            ALL_CATEGORIES),
                    rule(
                            "restriction",
                            "Restrictions on work",
                            "Review restrictions on future work",
                            "non[- ]compet|non[- ]solicit|\\bexclusiv(?:e|ity).{0,70}(?:services|clients)|(?:shall not|will not|must not).{0,60}(?:competing|competitors)",
                            "Check the activities, time period, and geographic scope covered. This preview cannot"
                                    + " determine whether a restriction is enforceable.",
                            "Can this restriction be narrowed so I can continue working with other clients?",
                            EnumSet.of(Analysis.Category.FREELANCE)),
                    // Freelance-specific rules
                    rule(
                            "scope-of-work",
                            "Scope of work",
                            "Confirm what work is included",
                            "deliverable|milestone|scope of work|statement of work|as directed|at"
                                    + " (?:our|the client's) discretion",
                            "Scope wording determines what work is included. Check what exactly is"
                                    + " promised, how changes are agreed, and whether additional work requires a new"
                                    + " order.",
                            "Which deliverables are fixed in this agreement, and how must changes to scope be"
                                    + " formally agreed?",
                            EnumSet.of(Analysis.Category.FREELANCE)),
                    rule(
                            "expenses",
                            "Expenses",
                            "Check who pays for costs",
                            "reimburs(?:e|ement|able)|out[- ]of[- ]pocket|expense(?:s)?|cost(?:s)?"
                                    + " (?:shall|will|are) (?:be )?(?:paid|borne|covered)",
                            "Unrecovered expenses reduce your effective rate. Confirm the approval process, caps,"
                                    + " and documentation requirements.",
                            "Which expenses are reimbursable, and what approval or documentation is required"
                                    + " before I incur them?",
                            EnumSet.of(Analysis.Category.FREELANCE)),
                    rule(
                            "acceptance",
                            "Acceptance & payment trigger",
                            "Understand what triggers payment",
                            "upon (?:client )?acceptance|acceptance criteria|final approval|sign[- ]off|deemed"
                                    + " accepted",
                            "Acceptance conditions can affect payment timing. Check what objective"
                                    + " event triggers final acceptance and what happens if the client does not"
                                    + " respond.",
                            "What objective criteria define acceptance, and is there a deadline after which"
                                    + " silence counts as acceptance?",
                            EnumSet.of(Analysis.Category.FREELANCE)),
                    // Consumer-specific rules
                    rule(
                            "free-trial",
                            "Free trial",
                            "Check what happens after the trial",
                            "free trial|trial period|introductory (?:period|offer)|first (?:\\d+ )?(?:days?|months?)?"
                                    + " free",
                            "Trials often convert to paid subscriptions automatically. Check the exact conversion"
                                    + " date, the price after trial, and how to cancel before being charged.",
                            "What is the exact date my trial converts to a paid plan, and what steps must I take"
                                    + " to cancel before then?",
                            EnumSet.of(Analysis.Category.CONSUMER)),

                    // ── FAVORABLE rules ──────────────────────────────────────────────────────────
                    favorableRule(
                            "opt-out",
                            "Opt-out right",
                            "This passage offers an opt-out; check its scope",
                            "(?<!(?:not |cannot |waive ))\\b(?:may opt out|right to opt out|opt[- ]out"
                                    + " (?:by|within|at any time)|you (?:may|can) opt out)",
                            "An explicit opt-out is a meaningful protection. Verify the deadline, the required"
                                    + " method, and what remains in effect if you opt out.",
                            "What is the deadline and exact method to exercise this opt-out?",
                            ALL_CATEGORIES),
                    rule(
                            "notice-of-changes",
                            "Notice before changes",
                            "Review notice for changes",
                            "(?:days?|weeks?|months?) (?:written |advance )?notice|(?:written |advance )?notice"
                                    + " of.*?(?:change|modification|amendment)",
                            "A notice requirement gives you time to review changes and decide whether to stay."
                                    + " Confirm the notice period, the delivery method, and your right to leave"
                                    + " without penalty if you disagree.",
                            "How long is the notice period, and can I cancel without a fee if I reject the"
                                    + " change?",
                            ALL_CATEGORIES),
                    favorableRule(
                            "refund-right",
                            "Refund or repayment right",
                            "Agreement includes a refund or repayment provision",
                            "refund(?:ed|able)?\\b|reimburse",
                            "An explicit refund or repayment provision is a meaningful protection. Verify the"
                                    + " trigger, the timeline, and any conditions or exclusions.",
                            "Under what conditions will a refund be issued, and within how many days?",
                            ALL_CATEGORIES),
                    favorableRule(
                            "portfolio",
                            "Portfolio right",
                            "Freelancer may keep a portfolio copy",
                            "(?:freelancer|designer|service provider|contractor).{0,30}(?:may|can|retains? the right to).{0,100}portfolio",
                            "A portfolio right lets you show your work to future clients. Confirm any restrictions"
                                    + " on medium, audience, or duration.",
                            "Are there any restrictions on how or where I may display this work in my portfolio?",
                            EnumSet.of(Analysis.Category.FREELANCE)));

    private final EvidenceValidator validator;

    public PreviewAnalysisEngine(EvidenceValidator validator) {
        this.validator = validator;
    }

    @Override
    public String progressStage() {
        return "Checking clause patterns and consequences…";
    }

    @Override
    public Analysis.Report analyze(Analysis.Input input) {
        Analysis.Category category =
                input.context().category() != null
                        ? input.context().category()
                        : Analysis.Category.OTHER;

        List<PreviewClause> clauses = PreviewClause.from(input.document());
        List<RankedFinding> candidates = new ArrayList<>();
        Set<String> coveredTopics = new HashSet<>();
        for (var detector : PreviewConsequences.DETECTORS) {
            if (category != detector.category()) {
                continue;
            }
            Set<String> seen = new HashSet<>();
            Set<String> seenBlocks = new HashSet<>();
            List<RankedFinding> consequences = new ArrayList<>();
            for (PreviewClause clause : clauses) {
                if (!detector.matches(clause)
                        || !seen.add(clause.normalized())
                        || !seenBlocks.add(clause.block().id())) {
                    continue;
                }
                var evidence =
                        validator.locate(
                                input.document(), clause.block().id(), clause.block().text());
                if (evidence.isEmpty()) {
                    continue;
                }
                boolean uncertain = detector.uncertain(clause);
                Analysis.Impact impact = uncertain ? Analysis.Impact.REVIEW : Analysis.Impact.HIGH;
                String explanation =
                        "Operative wording: “"
                                + clause.text()
                                + "” "
                                + detector.explanation()
                                + " The quoted paragraph includes the local wording and exceptions; confirm that it applies to your plan."
                                + (uncertain
                                        ? " A cross-reference, separate qualification, or split paragraph needs review, so this has not been classified as high impact."
                                        : "");
                Analysis.Finding finding =
                        new Analysis.Finding(
                                detector.id() + "-" + evidence.get().start(),
                                topicLabel(detector.topic()),
                                impact,
                                detector.title(),
                                explanation,
                                "The practical consequence comes from explicit wording, not from a topic keyword. Conditions and exceptions can change the outcome.",
                                evidence.get(),
                                detector.action(),
                                clause.related());
                consequences.add(new RankedFinding(finding, detector.priority()));
                coveredTopics.add(detector.topic());
            }
            // Scan the entire document before limiting output, so later explicit clauses can win.
            consequences.sort(
                    Comparator.comparingInt((RankedFinding c) -> impactOrder(c.finding().impact()))
                            .thenComparing(
                                    Comparator.comparingInt(
                                                    (RankedFinding c) ->
                                                            c.finding().evidence().quote().length())
                                            .reversed())
                            .thenComparingInt(c -> c.finding().evidence().start()));
            candidates.addAll(consequences.stream().limit(3).toList());
        }
        for (Rule rule : RULES) {
            if (!rule.applicableCategories().contains(category)
                    || coveredTopics.contains(rule.id())) {
                continue;
            }
            // Scan every clause, preferring substantive wording over short introductory mentions.
            clauses.stream()
                    .filter(clause -> rule.pattern().matcher(clause.normalized()).find())
                    .filter(clause -> applicable(rule, clause, category))
                    .sorted(
                            Comparator.comparingInt(
                                            (PreviewClause clause) ->
                                                    topicSpecificity(rule, clause))
                                    .reversed())
                    .map(clause -> topicFinding(input.document(), rule, clause))
                    .flatMap(java.util.Optional::stream)
                    .findFirst()
                    .ifPresent(finding -> candidates.add(new RankedFinding(finding, 10)));
        }
        candidates.sort(
                Comparator.comparingInt(
                                (RankedFinding candidate) ->
                                        impactOrder(candidate.finding().impact()))
                        .thenComparing(Comparator.comparingInt(RankedFinding::priority).reversed())
                        .thenComparingInt(candidate -> candidate.finding().evidence().start())
                        .thenComparing(candidate -> candidate.finding().id()));
        List<Analysis.Finding> findings = candidates.stream().map(RankedFinding::finding).toList();

        List<String> limitations = new ArrayList<>();
        limitations.add(
                "This is an offline rules-based preview, not AI analysis or legal advice. High impact means an explicit potentially consequential condition, not an unlawful or unenforceable term.");
        limitations.add(
                "Only a limited set of local clause patterns is supported. Definitions, distant exceptions, cross-references, and missing attachments are not resolved. No matches does not mean the agreement is safe.");
        limitations.add(
                "At most three distinct passages per consequence pattern and one representative passage per other topic are shown. This is not an exhaustive clause inventory.");
        limitations.add(
                category == Analysis.Category.CONSUMER
                        ? "This preview assumes you are the customer or member. Plan-specific and regional provisions are not selected automatically; confirm which passages apply to you."
                        : category == Analysis.Category.FREELANCE
                                ? "This preview assumes you are the freelancer or service provider, not the client. Plan-specific and regional applicability is not determined."
                                : "Your reviewing role is unspecified. Only neutral topic navigation is provided; consequence classification requires consumer or freelance context.");
        limitations.add(
                "Only the supplied text was inspected. Any unreadable pages, external policies, and omitted"
                        + " sections are outside this review.");
        limitations.addAll(JurisdictionContext.limitations(input.context()));

        List<String> priorities = new ArrayList<>();
        for (Analysis.Finding f : findings) {
            if (priorities.size() >= 3) {
                break;
            }
            if (f.impact() == Analysis.Impact.HIGH) {
                priorities.add(f.id());
            }
        }
        if (priorities.size() < 3) {
            for (Analysis.Finding f : findings) {
                if (priorities.size() >= 3) {
                    break;
                }
                if (f.impact() == Analysis.Impact.REVIEW) {
                    priorities.add(f.id());
                }
            }
        }

        List<Analysis.ChecklistItem> checklist = new ArrayList<>();
        for (Analysis.Finding f : findings) {
            if (checklist.size() >= 3) {
                break;
            }
            if (f.impact() != Analysis.Impact.FAVORABLE) {
                checklist.add(
                        new Analysis.ChecklistItem(
                                f.id().startsWith("renewal-deadline")
                                                || f.id().startsWith("trial-conversion")
                                        ? Analysis.ChecklistAction.VERIFY
                                        : f.id().startsWith("ip-before-payment")
                                                ? Analysis.ChecklistAction.CONSIDER
                                                : Analysis.ChecklistAction.ASK,
                                f.suggestedQuestion()));
            }
        }

        return new Analysis.Report(
                Analysis.Mode.RULE_BASED_PREVIEW,
                "preview-2",
                List.copyOf(findings),
                List.copyOf(limitations),
                false,
                JurisdictionContext.label(input.context()),
                "Rules inspect explicit local conditions, not the whole legal effect of the agreement. "
                        + findings.stream().filter(f -> f.impact() == Analysis.Impact.HIGH).count()
                        + " potentially high-impact passages identified. Zero does not establish safety.",
                List.copyOf(priorities),
                List.copyOf(checklist));
    }

    private static Rule rule(
            String id,
            String category,
            String title,
            String expression,
            String reason,
            String question,
            EnumSet<Analysis.Category> applicableCategories) {
        return new Rule(
                id,
                category,
                Analysis.Impact.REVIEW,
                title,
                Pattern.compile(expression, Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
                reason,
                question,
                applicableCategories);
    }

    private static Rule favorableRule(
            String id,
            String category,
            String title,
            String expression,
            String reason,
            String question,
            EnumSet<Analysis.Category> applicableCategories) {
        return new Rule(
                id,
                category,
                Analysis.Impact.FAVORABLE,
                title,
                Pattern.compile(expression, Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
                reason,
                question,
                applicableCategories);
    }

    private java.util.Optional<Analysis.Finding> topicFinding(
            Document document, Rule rule, PreviewClause clause) {
        return validator
                .locate(document, clause.block().id(), clause.block().text())
                .map(
                        evidence ->
                                new Analysis.Finding(
                                        rule.id(),
                                        rule.category(),
                                        rule.impact(),
                                        rule.title(),
                                        rule.impact() == Analysis.Impact.FAVORABLE
                                                ? "Operative wording: “"
                                                        + clause.text()
                                                        + "” This option is limited to the subject of that sentence. Check its recipient, conditions, and exceptions; it may not apply to other topics."
                                                : "This passage mentions "
                                                        + rule.category()
                                                                .toLowerCase(java.util.Locale.ROOT)
                                                        + ". A topic match does not establish an adverse consequence. Review the quoted wording.",
                                        rule.reason(),
                                        evidence,
                                        rule.question(),
                                        clause.related()));
    }

    private static boolean applicable(Rule rule, PreviewClause clause, Analysis.Category category) {
        String text = clause.normalized();
        if (rule.impact() == Analysis.Impact.FAVORABLE
                && (category == Analysis.Category.OTHER
                        || clause.fragmented()
                        || !clause.related().isEmpty())) {
            return false;
        }
        return switch (rule.id()) {
            case "restriction" ->
                    !text.contains("non-exclusive")
                            && !text.contains("non exclusive")
                            && !NO_RESTRICTION.matcher(text).find();
            case "fees" -> !NO_CHARGE.matcher(text).find();
            case "data" ->
                    !text.contains("must not disclose")
                            && !text.contains("shall not disclose")
                            && !text.contains("may not disclose");
            case "notice-of-changes" ->
                    CHANGE.matcher(text).find() && !text.contains("without notice");
            case "refund-right" -> PreviewProtections.refund(text, category);
            case "opt-out" -> PreviewProtections.optOut(text);
            case "portfolio" -> !PreviewProtections.negated(text) && clause.related().isEmpty();
            default -> true;
        };
    }

    private static int topicSpecificity(Rule rule, PreviewClause clause) {
        int score = Math.min(clause.text().length(), 300);
        if (rule.id().equals("payment") && TIMING.matcher(clause.normalized()).find()) {
            score += 1000;
        }
        return score;
    }

    private static int impactOrder(Analysis.Impact impact) {
        return switch (impact) {
            case HIGH -> 0;
            case REVIEW -> 1;
            case FAVORABLE -> 2;
        };
    }

    private static String topicLabel(String topic) {
        return RULES.stream()
                .filter(rule -> rule.id().equals(topic))
                .findFirst()
                .orElseThrow()
                .category();
    }

    private record RankedFinding(Analysis.Finding finding, int priority) {}

    private record Rule(
            String id,
            String category,
            Analysis.Impact impact,
            String title,
            Pattern pattern,
            String reason,
            String question,
            EnumSet<Analysis.Category> applicableCategories) {}
}
