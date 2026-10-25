package dev.termsofsurrender.documents;

import java.util.List;

/** Offsets use UTF-16 code units, matching Java String and browser String indexing. */
public record Document(
        String title,
        String sourceType,
        String fingerprint,
        String text,
        List<Block> blocks,
        int pageCount,
        List<String> warnings) {
    public record Block(String id, int page, int start, int end, String text) {}
}
