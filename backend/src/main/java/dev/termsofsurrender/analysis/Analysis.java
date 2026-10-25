package dev.termsofsurrender.analysis;

import dev.termsofsurrender.documents.Document;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class Analysis {
    private Analysis() {}

    public enum Jurisdiction {
        US,
        INDIA,
        UNSPECIFIED
    }

    public enum Category {
        CONSUMER,
        FREELANCE,
        OTHER
    }

    public enum Impact {
        HIGH,
        REVIEW,
        FAVORABLE
    }

    public enum Status {
        QUEUED,
        PROCESSING,
        COMPLETED,
        FAILED
    }

    public enum Mode {
        RULE_BASED_PREVIEW,
        MODEL_ASSISTED
    }

    public record Context(Jurisdiction jurisdiction, Category category, String region) {}

    public record Input(Document document, Context context) {}

    public record Evidence(String blockId, int start, int end, String quote) {}

    public record Finding(
            String id,
            String category,
            Impact impact,
            String title,
            String explanation,
            String whyItMatters,
            Evidence evidence,
            String suggestedQuestion) {}

    public record Report(
            Mode mode,
            String engineVersion,
            List<Finding> findings,
            List<String> limitations,
            boolean legalContextAvailable) {}

    public record View(
            UUID id,
            Status status,
            String stage,
            Document document,
            Context context,
            Report report,
            String errorCode,
            Instant createdAt,
            Instant expiresAt,
            int version) {}
}
