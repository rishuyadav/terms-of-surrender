package dev.termsofsurrender.analysis;

import java.util.List;

public final class JurisdictionContext {
    private JurisdictionContext() {}

    /**
     * Returns a concise framing sentence for the model system prompt. Used to focus the model's
     * attention on jurisdiction-relevant topics. This is analytical context, NOT a reviewed legal
     * corpus or legal advice.
     */
    public static String systemFraming(Analysis.Context context) {
        Analysis.Jurisdiction jurisdiction =
                context.jurisdiction() != null
                        ? context.jurisdiction()
                        : Analysis.Jurisdiction.UNSPECIFIED;
        Analysis.Category category =
                context.category() != null ? context.category() : Analysis.Category.OTHER;

        if (jurisdiction == Analysis.Jurisdiction.US && category == Analysis.Category.CONSUMER) {
            return "The user is reviewing a consumer or subscription agreement under US law."
                    + " Focus on automatic renewal, cancellation notice periods, arbitration clauses and"
                    + " class-action waivers, data sharing with third parties, and unilateral amendment"
                    + " rights. Note that California, New York, and other states have additional consumer"
                    + " protections beyond federal law; flag where state-specific rules may apply.";
        }
        if (jurisdiction == Analysis.Jurisdiction.US && category == Analysis.Category.FREELANCE) {
            return "The user is reviewing a freelance or independent contractor agreement under US law."
                    + " Focus on payment timing and conditions, IP ownership and work-for-hire classification,"
                    + " non-compete and non-solicitation scope and enforceability, indemnification exposure,"
                    + " and termination without cause. Note that worker classification and non-compete"
                    + " enforceability vary significantly by state.";
        }
        if (jurisdiction == Analysis.Jurisdiction.US) {
            return "The user is reviewing a contract under US law. Focus on payment, liability"
                    + " allocation, termination, dispute resolution, and amendment rights.";
        }
        if (jurisdiction == Analysis.Jurisdiction.INDIA && category == Analysis.Category.CONSUMER) {
            return "The user is reviewing a consumer or subscription agreement under Indian law."
                    + " Focus on automatic renewal and cancellation under RBI auto-debit rules, arbitration"
                    + " under the Arbitration and Conciliation Act 1996, data handling under the Digital"
                    + " Personal Data Protection Act 2023, and unfair terms under the Consumer Protection"
                    + " Act 2019. Note that state-specific consumer court jurisdiction may apply.";
        }
        if (jurisdiction == Analysis.Jurisdiction.INDIA
                && category == Analysis.Category.FREELANCE) {
            return "The user is reviewing a freelance or service agreement under Indian law."
                    + " Focus on payment terms and GST applicability, IP ownership under the Copyright"
                    + " Act 1957, arbitration clauses under the Arbitration and Conciliation Act 1996,"
                    + " and whether the agreement could be recharacterised as employment under the Industrial"
                    + " Disputes Act. Note that enforcement varies significantly by state.";
        }
        if (jurisdiction == Analysis.Jurisdiction.INDIA) {
            return "The user is reviewing a contract under Indian law. Focus on payment, liability,"
                    + " termination, dispute resolution under the Arbitration and Conciliation Act 1996,"
                    + " and stamp duty implications.";
        }
        return "The user has not specified a jurisdiction. Explain terms in general plain English."
                + " Do not assume specific legal rights or protections from any particular country.";
    }

    /**
     * Returns limitations text appropriate for this jurisdiction/category combination. These
     * replace or augment the generic limitations in the report.
     */
    public static List<String> limitations(Analysis.Context context) {
        Analysis.Jurisdiction jurisdiction =
                context.jurisdiction() != null
                        ? context.jurisdiction()
                        : Analysis.Jurisdiction.UNSPECIFIED;
        Analysis.Category category =
                context.category() != null ? context.category() : Analysis.Category.OTHER;

        String jurisdictionNote;
        if (jurisdiction == Analysis.Jurisdiction.US && category == Analysis.Category.CONSUMER) {
            jurisdictionNote =
                    "US and state consumer protections vary significantly. California, New York, Illinois,"
                            + " and others have additional auto-renewal and data rights that may apply. This"
                            + " analysis does not determine which state rules apply to your agreement.";
        } else if (jurisdiction == Analysis.Jurisdiction.US
                && category == Analysis.Category.FREELANCE) {
            jurisdictionNote =
                    "Worker classification (employee vs. contractor), non-compete enforceability, and IP"
                            + " assignment rules vary by US state. California bans most non-competes; other"
                            + " states enforce them. This analysis does not determine your classification or your"
                            + " state's specific rules.";
        } else if (jurisdiction == Analysis.Jurisdiction.INDIA
                && category == Analysis.Category.CONSUMER) {
            jurisdictionNote =
                    "Indian consumer protections under the Consumer Protection Act 2019 and DPDP Act 2023"
                            + " continue to evolve. RBI auto-debit rules apply to recurring payments. This"
                            + " analysis does not determine which rules apply to your specific agreement or"
                            + " provide legal advice.";
        } else if (jurisdiction == Analysis.Jurisdiction.INDIA
                && category == Analysis.Category.FREELANCE) {
            jurisdictionNote =
                    "IP ownership under the Copyright Act 1957, GST obligations, and worker reclassification"
                            + " risk vary by contract structure. This analysis does not determine your tax"
                            + " obligations or employment status.";
        } else if (jurisdiction == Analysis.Jurisdiction.UNSPECIFIED) {
            jurisdictionNote =
                    "No jurisdiction was specified. General explanations are provided. Consult a professional"
                            + " familiar with the laws of your country and region.";
        } else {
            jurisdictionNote =
                    "No jurisdiction was specified. General explanations are provided. Consult a professional"
                            + " familiar with the laws of your country and region.";
        }

        return List.of(jurisdictionNote);
    }

    /**
     * Returns a short human-readable label for the context, e.g. "United States · Consumer /
     * subscription · California". Used in the report header.
     */
    public static String label(Analysis.Context context) {
        Analysis.Jurisdiction jurisdiction =
                context.jurisdiction() != null
                        ? context.jurisdiction()
                        : Analysis.Jurisdiction.UNSPECIFIED;
        Analysis.Category category =
                context.category() != null ? context.category() : Analysis.Category.OTHER;
        String region = context.region() != null ? context.region().strip() : "";

        if (jurisdiction == Analysis.Jurisdiction.UNSPECIFIED) {
            return "Not specified";
        }

        String countryPart = jurisdiction == Analysis.Jurisdiction.US ? "United States" : "India";

        String categoryPart;
        if (category == Analysis.Category.CONSUMER) {
            categoryPart = "Consumer / subscription";
        } else if (category == Analysis.Category.FREELANCE) {
            categoryPart = "Freelance / services";
        } else {
            categoryPart = "Other";
        }

        String base = countryPart + " \u00b7 " + categoryPart;
        if (!region.isEmpty()) {
            return base + " \u00b7 " + region;
        }
        return base;
    }
}
