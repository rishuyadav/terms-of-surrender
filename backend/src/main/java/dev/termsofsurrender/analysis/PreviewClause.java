package dev.termsofsurrender.analysis;

import dev.termsofsurrender.documents.Document;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Local matching windows. Matching text is normalized; evidence always uses the original text. */
record PreviewClause(
        Document.Block block,
        String text,
        String normalized,
        int start,
        List<String> related,
        boolean fragmented) {
    private static final Pattern BOUNDARY =
            Pattern.compile("(?<=[.!?])\\s+(?=[\\p{Lu}\"“])|\\n\\s*\\n|\\n(?=\\s*\\d+[.)]\\s)");
    private static final Pattern QUALIFICATION =
            Pattern.compile(
                    "(?i)^(?:except|however|unless|notwithstanding|provided that|for customers|for members|no (?:fee|charge)|this does not)\\b");
    private static final Pattern SPACE = Pattern.compile("\\s+");
    private static final Pattern HEADING =
            Pattern.compile(
                    "^(?:\\d+[.)] [^\\n.!?]{2,100}|SCHEDULE [A-Z][^\\n]{0,100}|[A-Z][A-Z &—-]{5,100})\\n");

    static List<PreviewClause> from(Document document) {
        List<PreviewClause> clauses = new ArrayList<>();
        List<Document.Block> blocks = document.blocks();
        for (int i = 0; i < blocks.size(); i++) {
            Document.Block block = blocks.get(i);
            boolean fragmented =
                    (i > 0 && blocks.get(i - 1).end() == block.start())
                            || (i + 1 < blocks.size() && block.end() == blocks.get(i + 1).start());
            // A modifying paragraph is linked, never silently merged into an operative sentence.
            List<String> related = new ArrayList<>();
            if (i > 0 && blocks.get(i - 1).end() == block.start()) {
                related.add(blocks.get(i - 1).id());
            }
            if (i + 1 < blocks.size()) {
                Document.Block next = blocks.get(i + 1);
                if (block.end() == next.start()
                        || (next.page() == block.page()
                                && QUALIFICATION.matcher(next.text().strip()).find())) {
                    related.add(next.id());
                }
            }
            Matcher heading = HEADING.matcher(block.text());
            int start = heading.find() ? heading.end() : 0;
            Matcher boundaries = BOUNDARY.matcher(block.text());
            boundaries.region(start, block.text().length());
            while (boundaries.find()) {
                add(clauses, block, start, boundaries.start(), related, fragmented);
                start = boundaries.end();
            }
            add(clauses, block, start, block.text().length(), related, fragmented);
        }
        return List.copyOf(clauses);
    }

    private static void add(
            List<PreviewClause> clauses,
            Document.Block block,
            int start,
            int end,
            List<String> related,
            boolean fragmented) {
        String raw = block.text().substring(start, end);
        String text = raw.strip();
        if (text.length() >= 25) {
            int absoluteStart = block.start() + start + raw.length() - raw.stripLeading().length();
            clauses.add(
                    new PreviewClause(
                            block,
                            text,
                            normalize(text),
                            absoluteStart,
                            List.copyOf(related),
                            fragmented));
        }
    }

    static String normalize(String text) {
        return SPACE.matcher(
                        text.toLowerCase(Locale.ROOT)
                                .replace('’', '\'')
                                .replace('‘', '\'')
                                .replace('‑', '-')
                                .replace('–', '-')
                                .replace('—', '-'))
                .replaceAll(" ")
                .strip();
    }
}
