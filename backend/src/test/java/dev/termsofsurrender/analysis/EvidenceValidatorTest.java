package dev.termsofsurrender.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import dev.termsofsurrender.documents.Document;
import dev.termsofsurrender.documents.DocumentExtractor;
import org.junit.jupiter.api.Test;

class EvidenceValidatorTest {
    private final EvidenceValidator validator = new EvidenceValidator();
    private final Document document = new DocumentExtractor().extract(
            "This agreement renews automatically each year. You must give written notice thirty days before the renewal date.", null);

    @Test
    void mapsExactQuotesToCanonicalOffsets() {
        var evidence = validator.locate(document, "p1-b1", "renews automatically").orElseThrow();
        assertThat(document.text().substring(evidence.start(), evidence.end())).isEqualTo(evidence.quote());
    }

    @Test
    void rejectsInventedOrMislocatedEvidence() {
        assertThat(validator.locate(document, "p1-b1", "You can opt out anytime")).isEmpty();
        assertThat(validator.locate(document, "p9-b1", "renews automatically")).isEmpty();
        assertThat(validator.locate(document, "p1-b1", "")).isEmpty();
        assertThat(validator.locate(document, "p1-b1", "renew")).isEmpty(); // repeated text is ambiguous
    }
}
