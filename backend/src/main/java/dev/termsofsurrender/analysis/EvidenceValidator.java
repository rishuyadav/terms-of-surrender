package dev.termsofsurrender.analysis;

import dev.termsofsurrender.documents.Document;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class EvidenceValidator {
    /** Validate a known source location without confusing repeated identical sentences. */
    public Optional<Analysis.Evidence> locateAt(
            Document document, String blockId, String quote, int start) {
        if (quote == null || quote.isBlank() || quote.length() > 2_000) {
            return Optional.empty();
        }
        return document.blocks().stream()
                .filter(block -> block.id().equals(blockId))
                .filter(block -> start >= block.start() && start <= block.end() - quote.length())
                .filter(
                        block ->
                                block.text()
                                        .regionMatches(
                                                start - block.start(), quote, 0, quote.length()))
                .filter(block -> document.text().regionMatches(start, quote, 0, quote.length()))
                .findFirst()
                .map(block -> new Analysis.Evidence(blockId, start, start + quote.length(), quote));
    }

    public Optional<Analysis.Evidence> locate(Document document, String blockId, String quote) {
        if (quote == null || quote.isBlank() || quote.length() > 2_000) {
            return Optional.empty();
        }
        return document.blocks().stream()
                .filter(block -> block.id().equals(blockId))
                .findFirst()
                .flatMap(
                        block -> {
                            int local = block.text().indexOf(quote);
                            if (local < 0 || block.text().indexOf(quote, local + 1) >= 0) {
                                return Optional.empty();
                            }
                            int start = block.start() + local;
                            int end = start + quote.length();
                            if (!document.text().substring(start, end).equals(quote)) {
                                return Optional.empty();
                            }
                            return Optional.of(new Analysis.Evidence(blockId, start, end, quote));
                        });
    }
}
