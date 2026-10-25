package dev.termsofsurrender.documents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.termsofsurrender.api.ApiException;
import java.io.ByteArrayOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class DocumentExtractorTest {
    private final DocumentExtractor extractor = new DocumentExtractor();
    private static final String TEXT =
            "This agreement renews automatically each year unless written notice is received thirty days before renewal.";

    @Test
    void acceptsExactCharacterLimitWithoutInsertingArtificialParagraphs() {
        String source = "a".repeat(DocumentExtractor.MAX_CHARACTERS);
        Document document = extractor.extract(source, null);
        assertThat(document.text()).isEqualTo(source);
        assertThat(document.blocks()).hasSize(40);
        assertThat(
                        document.blocks().stream()
                                .map(Document.Block::text)
                                .collect(java.util.stream.Collectors.joining()))
                .isEqualTo(source);
    }

    @Test
    void doesNotSplitUnicodeSurrogatePairsAtBlockBoundaries() {
        String source = "a".repeat(1999) + "😀" + "z".repeat(100);
        Document document = extractor.extract(source, null);
        assertThat(document.text()).isEqualTo(source);
        document.blocks()
                .forEach(
                        block -> {
                            String roundTrip =
                                    new String(
                                            block.text()
                                                    .getBytes(
                                                            java.nio.charset.StandardCharsets
                                                                    .UTF_8),
                                            java.nio.charset.StandardCharsets.UTF_8);
                            assertThat(roundTrip).isEqualTo(block.text());
                        });
    }

    @Test
    void createsStableBlocksWithExactOffsets() {
        Document document = extractor.extract(TEXT + "\n\n" + TEXT, null);
        assertThat(document.blocks()).hasSize(2);
        document.blocks()
                .forEach(
                        block ->
                                assertThat(document.text().substring(block.start(), block.end()))
                                        .isEqualTo(block.text()));
        assertThat(extractor.extract(TEXT + "\n\n" + TEXT, null).fingerprint())
                .isEqualTo(document.fingerprint());
    }

    @Test
    void rejectsAmbiguousEmptyAndOversizeInput() {
        assertThatThrownBy(() -> extractor.extract("short", null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> extractor.extract("x".repeat(80_001), null))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> extractor.extract(null, null)).isInstanceOf(ApiException.class);
        MockMultipartFile fake =
                new MockMultipartFile(
                        "file",
                        "contract.pdf",
                        "application/pdf",
                        TEXT.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThatThrownBy(() -> extractor.extract(null, fake))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("text-based PDFs");
        assertThatThrownBy(() -> extractor.extract(TEXT, fake))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not both");
    }

    @Test
    void extractsActualPdfAndRejectsScans() throws Exception {
        try (PDDocument pdf = new PDDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            pdf.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(pdf, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                content.newLineAtOffset(20, 700);
                content.showText(TEXT);
                content.endText();
            }
            pdf.save(output);
            Document document =
                    extractor.extract(
                            null,
                            new MockMultipartFile(
                                    "file", "x.pdf", "application/pdf", output.toByteArray()));
            assertThat(document.text()).contains("renews automatically");
            assertThat(document.pageCount()).isEqualTo(1);
        }
        try (PDDocument pdf = new PDDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            pdf.addPage(new PDPage());
            pdf.save(output);
            assertThatThrownBy(
                            () ->
                                    extractor.extract(
                                            null,
                                            new MockMultipartFile(
                                                    "file",
                                                    "scan.pdf",
                                                    "application/pdf",
                                                    output.toByteArray())))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("OCR");
        }
    }
}
