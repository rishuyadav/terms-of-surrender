package dev.termsofsurrender.analysis;

import dev.termsofsurrender.documents.Document;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A keyword navigator, not a legal assessment or substitute for model analysis. */
public class PreviewAnalysisEngine implements AnalysisEngine {
    private static final List<Rule> RULES = List.of(
            rule("renewal", "Renewal & cancellation", "Check how renewal works",
                    "auto(?:matic(?:ally)?)?[- ]renew|renew(?:s|ed)? automatically",
                    "Check whether renewal is automatic, how notice must be given, and whether exceptions apply.",
                    "What must I do to prevent renewal, and when must you receive my notice?"),
            rule("arbitration", "Disputes", "Review the dispute process",
                    "arbitrat(?:ion|or|e)|class[- ]action",
                    "Dispute procedures may affect where and how a disagreement is handled. This preview does not determine enforceability or whether an opt-out exists.",
                    "Does this agreement offer a dispute-process opt-out, and what exact procedure applies?"),
            rule("fees", "Fees & charges", "Look closely at additional charges",
                    "cancellation fee|early termination fee|late fee|non[- ]refundable|additional (?:fee|charge)",
                    "Review the trigger, amount, and exceptions for each charge before deciding what it could cost you.",
                    "Which additional charges can apply, and can you give me an example of the total cost?"),
            rule("liability", "Liability", "Understand who carries the risk",
                    "indemnif|hold harmless|limitation of liability|liability (?:shall|will|is)|liable for",
                    "Risk allocation depends on the full clause, exclusions, and related provisions. A keyword match does not establish who is liable.",
                    "What losses could I be responsible for, and are there any limits or mutual protections?"),
            rule("changes", "Changes to terms", "Check who can change the agreement",
                    "sole discretion|modify (?:these|the) terms|change (?:these|the) terms|without (?:prior )?notice",
                    "Find out whether changes require notice, consent, or an opportunity to leave the agreement.",
                    "How will I be notified of changes, and can I leave without a penalty?"),
            rule("ip", "Intellectual property", "Check ownership and license rights",
                    "intellectual property|work[- ](?:made[- ])?for[- ]hire|assign(?:s|ment)? (?:all|the|any) rights|perpetual.*licen[cs]e",
                    "Ownership and licensing can affect reuse of your work. Check scope, payment conditions, pre-existing materials, and exceptions.",
                    "Which rights transfer, when do they transfer, and can I retain my pre-existing work and portfolio rights?"),
            rule("payment", "Payment", "Review when payment becomes due",
                    "payment (?:is|shall|will)|net[- ](?:30|60|90)|upon (?:final )?acceptance|invoice",
                    "For freelance work, check payment timing, acceptance criteria, expenses, and what happens if payment is late.",
                    "What objective event triggers payment, and is there a final date for paying each invoice?"),
            rule("termination", "Termination", "Understand how the agreement ends",
                    "terminat(?:e|ion).*?(?:notice|cause|convenience)|(?:notice|cause|convenience).*?terminat",
                    "Check notice periods, payment for completed work, and obligations that continue after termination.",
                    "If either party ends the agreement, what must still be paid or performed?"),
            rule("data", "Data & publicity", "Review permissions over your data or likeness",
                    "third[- ]part(?:y|ies)|likeness|marketing purposes|personal (?:data|information)",
                    "Read the purpose, recipients, duration, and any limitations on the permission. A reference to third parties does not itself mean data is sold.",
                    "What may be shared or reused, for which purposes, and what choices do I have?"),
            rule("restriction", "Restrictions on work", "Review restrictions on future work",
                    "non[- ]compet|exclusiv(?:e|ity)|non[- ]solicit",
                    "Check the activities, time period, and geographic scope covered. This preview cannot determine whether a restriction is enforceable.",
                    "Can this restriction be narrowed so I can continue working with other clients?"));

    private final EvidenceValidator validator;

    public PreviewAnalysisEngine(EvidenceValidator validator) {
        this.validator = validator;
    }

    @Override
    public Analysis.Report analyze(Analysis.Input input) {
        List<Analysis.Finding> findings = new ArrayList<>();
        for (Rule rule : RULES) {
            for (Document.Block block : input.document().blocks()) {
                Matcher matcher = rule.pattern().matcher(block.text());
                if (!matcher.find()) {
                    continue;
                }
                String quote = sentenceAround(block.text(), matcher.start(), matcher.end());
                validator.locate(input.document(), block.id(), quote).ifPresent(evidence ->
                        findings.add(new Analysis.Finding(rule.id(), rule.category(), Analysis.Impact.REVIEW,
                                rule.title(), "This passage mentions " + rule.category().toLowerCase(java.util.Locale.ROOT)
                                + ". Review the exact wording and surrounding sections.",
                                rule.reason(), evidence, rule.question())));
                break;
            }
        }
        return new Analysis.Report(Analysis.Mode.RULE_BASED_PREVIEW, "preview-1", List.copyOf(findings), List.of(
                "This is an offline keyword-based preview, not AI analysis or legal advice. Matches identify topics, not adverse or unlawful terms.",
                "The preview does not resolve definitions, exceptions, cross-references, or missing attachments. No matches does not mean the agreement is safe.",
                "US and India selections provide context only. No reviewed jurisdiction-specific legal guidance is enabled.",
                "Only the supplied text was inspected. Any unreadable pages, external policies, and omitted sections are outside this review."), false);
    }

    private static Rule rule(String id, String category, String title, String expression, String reason, String question) {
        return new Rule(id, category, title, Pattern.compile(expression, Pattern.CASE_INSENSITIVE | Pattern.DOTALL), reason, question);
    }

    private String sentenceAround(String text, int matchStart, int matchEnd) {
        int start = matchStart;
        while (start > 0 && ".!?;\n".indexOf(text.charAt(start - 1)) < 0) {
            start--;
        }
        int end = matchEnd;
        while (end < text.length() && ".!?;\n".indexOf(text.charAt(end)) < 0) {
            end++;
        }
        if (end < text.length()) {
            end++;
        }
        return text.substring(start, end).strip();
    }

    private record Rule(String id, String category, String title, Pattern pattern, String reason, String question) {}
}
