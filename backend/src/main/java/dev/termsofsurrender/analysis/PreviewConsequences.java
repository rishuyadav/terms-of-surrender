package dev.termsofsurrender.analysis;

import java.util.List;
import java.util.regex.Pattern;

/** Deliberately narrow consequence detectors, not a contract-wide interpretation or legal test. */
final class PreviewConsequences {
    private PreviewConsequences() {}

    private static final String PERIOD =
            "(?:\\d+|one|two|three|seven|fourteen|thirty|sixty|ninety)(?:[- ](?:\\d+|one))?"
                    + "[- ](?:business )?(?:days?|months?|years?)";
    private static final Pattern DEPENDENCY =
            pattern(
                    "except as|unless otherwise|\\bhowever\\b|notwithstanding|provided that|(?:section|schedule|clause|paragraph) [a-z0-9]|"
                            + "for (?:customers|residents|members) (?:in|of)|where (?:required|permitted) by law");
    private static final Pattern NEGATED_CHARGE =
            pattern(
                    "\\b(?:no|without|waiv\\w*|not (?:owe|pay|be charged)|will not be charged|does not (?:apply|carry))\\b"
                            + ".{0,55}(?:fee|charge|penalty|remaining|forfeit)|"
                            + "(?:fee|charge|penalty).{0,35}(?:does not apply|is waived|will not apply|shall not apply)");
    private static final Pattern NEGATED_RENEWAL =
            pattern(
                    "(?:not|never|no longer|doesn't) (?:be |automatically )?renew|no automatic renewal");
    private static final Pattern NEGATED_CONVERSION =
            pattern(
                    "(?:not|never|won't) (?:be )?(?:automatically )?(?:charged|convert|begin charging)|"
                            + "(?:does|will|shall) not automatically|no automatic (?:conversion|charge)");
    private static final Pattern NEGATED_TRANSFER =
            pattern(
                    "(?:not|never) (?:transfer|vest|assign)|no (?:ownership|rights|copyright).{0,30}(?:transfer|vest|assign)|"
                            + "(?:only after|and full payment)");
    private static final Pattern ZERO_CHARGE =
            pattern(
                    "(?:fee|charge|penalty) (?:of |is |equal to )?(?:(?:usd|inr) |\\$|₹)?(?:zero|0(?:\\.0+)?)(?![\\d.])(?:\\s|[%,;.]|$)");

    static final List<Detector> DETECTORS =
            List.of(
                    detector(
                            "renewal-deadline",
                            "renewal",
                            Analysis.Category.CONSUMER,
                            80,
                            "Renewal has an advance cancellation deadline",
                            "This passage links automatic renewal to advance cancellation or notice. Missing the stated deadline may start another paid period.",
                            "Verify the cancellation deadline and method for your plan; keep confirmation that notice was received.",
                            "renews? automatically|automatically renew(?:s|ed)?|auto[- ]renew(?:al|ing)?|continues? indefinitely",
                            "unless.{0,100}(?:cancel|notice)|(?:must|required to).{0,80}(?:cancel|notice)|"
                                    + "(?:cancel|non-renewal).{0,80}(?:at least|no later than)|(?:cancellation|non-renewal) notice",
                            "(?:at least |no later than )?"
                                    + PERIOD
                                    + "(?:'s|s')? (?:before|prior to|in advance)|"
                                    + PERIOD
                                    + "(?:'s|s')? (?:written |advance )?notice"),
                    detector(
                            "trial-conversion",
                            "free-trial",
                            Analysis.Category.CONSUMER,
                            75,
                            "The trial can convert to a paid subscription",
                            "This passage states that billing or a paid subscription starts automatically after the trial. Check the cancellation condition and the price that applies.",
                            "Verify when the trial ends, the price after conversion, and where you must cancel to avoid a charge.",
                            "\\btrial\\b",
                            "automatically (?:be (?:charged|billed)|charge(?:d|s)?|bill(?:ed|s)?|become (?:a )?(?:subscriber|paid)|"
                                    + "(?:begin|start) (?:charging|billing)|convert(?:s)? (?:to|into) (?:a )?paid)|converts? automatically",
                            "paid|charg|bill|subscription"),
                    detector(
                            "cancellation-cost",
                            "fees",
                            Analysis.Category.CONSUMER,
                            100,
                            "Cancellation can carry a charge or forfeiture",
                            "This passage expressly attaches a charge, remaining-payment obligation, or forfeiture to cancellation. Read the amount or formula together with the stated exceptions.",
                            "Ask for the cancellation cost for your intended end date, including the applicable refund window and exemptions.",
                            "cancel|early termination",
                            "(?:cancellation|early termination) (?:fee|charge|penalty) (?:of|equal to|applies|will|shall|is)|"
                                    + "(?:charged|owe|pay).{0,70}(?:remaining|%|percent|lump sum)|"
                                    + "(?:forfeit|forfeited|forfeiture).{0,50}(?:prepaid|payment|deposit|fee)|"
                                    + "(?:prepaid|payment|deposit|fee).{0,50}(?:forfeit|forfeited)"),
                    detector(
                            "payment-condition",
                            "payment",
                            Analysis.Category.FREELANCE,
                            85,
                            "Payment depends on client approval or upstream payment",
                            "This passage makes payment or invoicing conditional on acceptance, approval, or receipt of another party's payment. That condition can affect when you receive money; it does not by itself prove an unlimited delay.",
                            "Ask for objective acceptance criteria and an outside payment date that does not depend on an upstream customer paying.",
                            "payment|invoice|invoicing|paid",
                            "(?:payment|invoic\\w*|paid).{0,100}(?:only (?:after|upon).{0,55}(?:acceptance|approval|receipt of payment)|"
                                    + "(?:conditional on|subject to|contingent (?:on|upon)).{0,55}(?:acceptance|approval|receipt of payment)|"
                                    + "upon (?:final |client(?:'s)? )?(?:acceptance|approval))|"
                                    + "pay[- ]when[- ]paid|pay[- ]if[- ]paid"),
                    detector(
                            "ip-before-payment",
                            "ip",
                            Analysis.Category.FREELANCE,
                            90,
                            "Rights can transfer before you are paid",
                            "This passage expressly transfers ownership or rights on creation or before payment. Check the precise transfer event and any retained background materials.",
                            "Consider asking that transfer of the final deliverables take effect only after full payment, with background materials excluded.",
                            "rights|copyright|ownership|intellectual property",
                            "(?:transfer(?:s|red)?|vest(?:s|ed)?|assign(?:s|ed)?).{0,45}(?:to|in) (?:the )?client",
                            "(?:transfer|vest|assign).{0,100}(?:upon creation|on creation|before (?:full )?payment|regardless of (?:whether|payment)|whether or not.{0,30}paid)"),
                    detector(
                            "unpaid-work-on-termination",
                            "termination",
                            Analysis.Category.FREELANCE,
                            95,
                            "Unaccepted work can go unpaid on termination",
                            "This passage expressly excludes payment for work in progress or unaccepted work when the agreement ends. Review any exceptions and how acceptance is determined.",
                            "Ask that termination payments include work performed through the end date and approved non-cancellable commitments.",
                            "terminat|on cancellation",
                            "(?:work in progress|unaccepted (?:work|deliverables)|work.{0,35}not.{0,15}accepted)"
                                    + ".{0,60}(?:(?:will|shall) not be (?:paid|compensated)|(?:is|are) not (?:payable|compensable)|without (?:payment|compensation))|"
                                    + "no (?:payment|compensation).{0,30}for (?:work in progress|unaccepted (?:work|deliverables))"));

    private static Detector detector(
            String id,
            String topic,
            Analysis.Category category,
            int priority,
            String title,
            String explanation,
            String action,
            String... expressions) {
        return new Detector(
                id,
                topic,
                category,
                priority,
                title,
                explanation,
                action,
                java.util.Arrays.stream(expressions).map(PreviewConsequences::pattern).toList());
    }

    private static Pattern pattern(String expression) {
        return Pattern.compile(expression, Pattern.CASE_INSENSITIVE);
    }

    record Detector(
            String id,
            String topic,
            Analysis.Category category,
            int priority,
            String title,
            String explanation,
            String action,
            List<Pattern> required) {
        boolean matches(PreviewClause clause) {
            String text = clause.normalized();
            if (!required.stream().allMatch(pattern -> pattern.matcher(text).find())) {
                return false;
            }
            return switch (id) {
                case "renewal-deadline" -> !NEGATED_RENEWAL.matcher(text).find();
                case "trial-conversion" -> !NEGATED_CONVERSION.matcher(text).find();
                case "cancellation-cost" ->
                        !NEGATED_CHARGE.matcher(text).find() && !ZERO_CHARGE.matcher(text).find();
                case "payment-condition" ->
                        !text.contains("not conditional")
                                && !text.contains("not subject to")
                                && !text.contains("not contingent")
                                && !text.contains("not depend")
                                && !text.contains("may not")
                                && !text.contains("not required")
                                && !text.contains("payment to the client")
                                && !text.contains("refund");
                case "ip-before-payment" -> !NEGATED_TRANSFER.matcher(text).find();
                case "unpaid-work-on-termination" ->
                        !text.contains("notwithstanding")
                                && !text.contains("not excluded")
                                && !text.contains("not be withheld");
                default -> false;
            };
        }

        boolean uncertain(PreviewClause clause) {
            // Explicit dependencies and separately stated qualifications require human
            // reconciliation.
            return DEPENDENCY
                            .matcher(clause.block().text().toLowerCase(java.util.Locale.ROOT))
                            .find()
                    || !clause.related().isEmpty()
                    || clause.fragmented();
        }
    }
}
