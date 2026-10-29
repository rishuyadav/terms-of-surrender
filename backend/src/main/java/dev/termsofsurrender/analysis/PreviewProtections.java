package dev.termsofsurrender.analysis;

import java.util.regex.Pattern;

/** Protection claims require an affirmative grant to the assumed reviewing party. */
final class PreviewProtections {
    private PreviewProtections() {}

    private static final Pattern NEGATION =
            Pattern.compile(
                    "\\b(?:not|no|never|cannot|can't|waive|waives|without|non-refundable|nonrefundable)\\b");
    private static final Pattern REFUND =
            Pattern.compile(
                    "(?:you|members?|customers?|subscribers?).{0,35}(?:will|shall) (?:be refunded|receive a (?:full |prorated )?refund)|"
                            + "(?:we|the club|the provider).{0,25}(?:will|shall) (?:refund|reimburse) (?:you|members?|customers?)|"
                            + "(?:prepaid|unused) (?:amounts|fees|membership months).{0,60}(?:will|shall) be refunded");
    private static final Pattern FREELANCER_REFUND =
            Pattern.compile(
                    "(?:freelancer|contractor|service provider|designer).{0,35}(?:will|shall) (?:be refunded|receive a refund)");
    private static final Pattern OPT_OUT =
            Pattern.compile(
                    "(?:you|members?|customers?|subscribers?|freelancer|service provider).{0,15}(?:may|can|have the right to) opt[- ]out");

    static boolean negated(String text) {
        return NEGATION.matcher(text).find();
    }

    static boolean refund(String text, Analysis.Category category) {
        if (negated(text)
                || text.contains("required by law")
                || text.contains("at our discretion")) {
            return false;
        }
        return category == Analysis.Category.CONSUMER
                ? REFUND.matcher(text).find()
                : category == Analysis.Category.FREELANCE && FREELANCER_REFUND.matcher(text).find();
    }

    static boolean optOut(String text) {
        return !negated(text) && OPT_OUT.matcher(text).find();
    }
}
