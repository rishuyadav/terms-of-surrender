package dev.termsofsurrender.analysis;

import dev.termsofsurrender.documents.Document;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class EvidenceValidator {
    public Optional<Analysis.Evidence> locate(Document document, String blockId, String quote) {
        if (quote == null || quote.isBlank() || quote.length() > 2_000) {
            return Optional.empty();
        }
        return document.blocks().stream().filter(block -> block.id().equals(blockId)).findFirst()
                .flatMap(block -> {
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
