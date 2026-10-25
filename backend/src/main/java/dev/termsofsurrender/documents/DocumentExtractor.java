package dev.termsofsurrender.documents;

import dev.termsofsurrender.api.ApiException;
import dev.termsofsurrender.privacy.Sessions;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.web.multipart.MultipartFile;

/** Pure extraction engine. Production file parsing is invoked only by ParserWorker. */
public class DocumentExtractor {
    public static final int MAX_CHARACTERS = 80_000;
    private static final int MAX_PAGES = 40;
    private static final int MAX_BLOCK_CHARACTERS = 2_000;

    public Document extract(String text, MultipartFile file) {
        boolean hasText = text != null && !text.isBlank();
        boolean hasFile = file != null && !file.isEmpty();
        if (hasText == hasFile) {
            throw ApiException.invalid(
                    "INVALID_INPUT", "Provide either pasted text or one PDF/DOCX file, not both.");
        }
        if (hasText) {
            checkLength(text.length());
            return build("Pasted agreement", "TEXT", List.of(text), List.of());
        }
        if (file.getSize() > 10 * 1024 * 1024) {
            throw ApiException.invalid("UPLOAD_TOO_LARGE", "Upload a document smaller than 10 MB.");
        }
        try {
            return extractFile(
                    file.getOriginalFilename() != null
                                    && file.getOriginalFilename()
                                            .toLowerCase(java.util.Locale.ROOT)
                                            .endsWith(".docx")
                            ? "DOCX"
                            : "PDF",
                    file.getBytes());
        } catch (IOException exception) {
            throw ApiException.invalid("UNREADABLE_FILE", "The upload could not be read.");
        }
    }

    Document extractFile(String format, byte[] bytes) {
        if (bytes.length > 10 * 1024 * 1024) {
            throw ApiException.invalid("UPLOAD_TOO_LARGE", "Upload a document smaller than 10 MB.");
        }
        if (format.equals("DOCX")) {
            return build(
                    "Uploaded agreement",
                    "DOCX",
                    List.of(new DocxTextExtractor().extract(bytes)),
                    List.of(
                            "DOCX import reads body paragraphs and tables, not the complete Word layout. Headers, footers, footnotes, comments, images, text boxes, automatic numbering, and embedded content may be missing. Verify against the original before relying on this report.",
                            "DOCX has no reliable page mapping. Source references identify extracted text, not Word pages. Field results may be stale; hidden text may be included."));
        }
        return pdf(bytes);
    }

    private Document pdf(byte[] bytes) {
        try {
            if (bytes.length < 5
                    || bytes[0] != '%'
                    || bytes[1] != 'P'
                    || bytes[2] != 'D'
                    || bytes[3] != 'F'
                    || bytes[4] != '-') {
                throw ApiException.invalid(
                        "UNSUPPORTED_FILE", "Upload a text-based PDF or DOCX document.");
            }
            try (PDDocument pdf = Loader.loadPDF(bytes)) {
                if (pdf.isEncrypted() || !pdf.getCurrentAccessPermission().canExtractContent()) {
                    throw ApiException.invalid(
                            "PROTECTED_PDF",
                            "Upload an unprotected PDF that permits text extraction.");
                }
                if (pdf.getNumberOfPages() > MAX_PAGES) {
                    throw ApiException.invalid(
                            "TOO_MANY_PAGES", "Please use an agreement with 40 pages or fewer.");
                }
                List<String> pages = new ArrayList<>();
                List<String> warnings = new ArrayList<>();
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setSortByPosition(true);
                int total = 0;
                for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                    stripper.setStartPage(page);
                    stripper.setEndPage(page);
                    String extracted = stripper.getText(pdf);
                    total += extracted.length();
                    checkLength(total);
                    pages.add(extracted);
                    if (extracted.strip().length() < 30) {
                        warnings.add(
                                "Page "
                                        + page
                                        + " has little readable text. Scanned content may be missing.");
                    }
                }
                return build("Uploaded agreement", "PDF", pages, warnings);
            }
        } catch (IOException exception) {
            throw ApiException.invalid(
                    "UNREADABLE_PDF",
                    "The PDF could not be read. It may be damaged or password protected.");
        }
    }

    Document build(String title, String sourceType, List<String> pages, List<String> warnings) {
        List<Document.Block> blocks = new ArrayList<>();
        StringBuilder canonical = new StringBuilder();
        for (int page = 0; page < pages.size(); page++) {
            String clean =
                    pages.get(page)
                            .replace("\r\n", "\n")
                            .replace('\r', '\n')
                            .replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "")
                            .strip();
            int index = 0;
            for (String paragraph : clean.split("\\n\\s*\\n")) {
                String paragraphText = paragraph.strip();
                if (paragraphText.isEmpty()) {
                    continue;
                }
                if (!canonical.isEmpty()) {
                    canonical.append("\n\n");
                }
                int paragraphStart = canonical.length();
                canonical.append(paragraphText);
                checkLength(canonical.length());
                int offset = 0;
                while (offset < paragraphText.length()) {
                    int cut = Math.min(paragraphText.length(), offset + MAX_BLOCK_CHARACTERS);
                    if (cut < paragraphText.length()) {
                        int whitespace = paragraphText.lastIndexOf(' ', cut);
                        if (whitespace > offset + MAX_BLOCK_CHARACTERS / 2) {
                            cut = whitespace;
                        }
                        if (Character.isHighSurrogate(paragraphText.charAt(cut - 1))
                                && Character.isLowSurrogate(paragraphText.charAt(cut))) {
                            cut--;
                        }
                    }
                    blocks.add(
                            new Document.Block(
                                    "p" + (page + 1) + "-b" + (++index),
                                    page + 1,
                                    paragraphStart + offset,
                                    paragraphStart + cut,
                                    paragraphText.substring(offset, cut)));
                    offset = cut;
                }
            }
        }
        if (canonical.length() < 80) {
            throw ApiException.invalid(
                    "NOT_ENOUGH_TEXT",
                    "Provide at least 80 characters of readable contract text. Photos and scanned PDFs need OCR, which is not yet available.");
        }
        return new Document(
                title,
                sourceType,
                Sessions.hash(canonical.toString()),
                canonical.toString(),
                List.copyOf(blocks),
                pages.size(),
                List.copyOf(warnings));
    }

    private void checkLength(int length) {
        if (length > MAX_CHARACTERS) {
            throw ApiException.invalid(
                    "DOCUMENT_TOO_LONG",
                    "This release supports up to 80,000 characters per document.");
        }
    }
}
