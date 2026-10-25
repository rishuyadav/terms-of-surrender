package dev.termsofsurrender.documents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.termsofsurrender.api.ApiException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class DocxTextExtractorTest {
    private static final String TEXT =
            "This agreement renews automatically each year unless written notice is received thirty days before renewal.";
    private static final String OPEN =
            "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>";
    private static final String CLOSE = "</w:body></w:document>";
    private final DocumentExtractor extractor = new DocumentExtractor();

    @Test
    void extractsRunsTablesUnicodeAndExactEvidenceWithoutFollowingLinks() throws Exception {
        Document result =
                extract(
                        OPEN
                                + "<w:p><w:r><w:t>This agreement renews </w:t></w:r><w:hyperlink><w:r><w:t>automatically each year.</w:t></w:r></w:hyperlink></w:p>"
                                + "<w:tbl><w:tr><w:tc><w:p><w:r><w:t>Payment: ₹500 😀 &amp; fees.</w:t><w:tab/><w:t>Due monthly.</w:t></w:r></w:p></w:tc></w:tr></w:tbl>"
                                + CLOSE);
        assertThat(result.sourceType()).isEqualTo("DOCX");
        assertThat(result.text())
                .contains("renews automatically", "₹500 😀 & fees.", "\tDue monthly.");
        assertThat(result.warnings())
                .anyMatch(warning -> warning.contains("no reliable page mapping"));
        result.blocks()
                .forEach(
                        block ->
                                assertThat(result.text().substring(block.start(), block.end()))
                                        .isEqualTo(block.text()));
    }

    @Test
    void omitsTextBoxesAndInstructionsAndWarnsAboutPartialImport() throws Exception {
        Document result =
                extract(
                        OPEN
                                + "<w:p><w:r><w:t>"
                                + TEXT
                                + "</w:t><w:instrText>PRIVATE_FIELD_INSTRUCTION</w:instrText><w:drawing><w:txbxContent><w:p><w:r><w:t>OMITTED_TEXT_BOX</w:t></w:r></w:p></w:txbxContent></w:drawing></w:r></w:p>"
                                + CLOSE);
        assertThat(result.text()).isEqualTo(TEXT);
        assertThat(result.warnings())
                .anyMatch(
                        warning -> warning.contains("footnotes") && warning.contains("text boxes"));
    }

    @Test
    void rejectsTrackedChangesInsteadOfGuessingTheEffectiveAgreement() {
        for (String revision :
                new String[] {
                    "ins", "del", "moveFrom", "pPrChange", "cellDel", "numberingChange", "delText"
                }) {
            assertThatThrownBy(
                            () ->
                                    extract(
                                            OPEN
                                                    + "<w:p><w:r><w:t>"
                                                    + TEXT
                                                    + "</w:t></w:r><w:"
                                                    + revision
                                                    + "/></w:p>"
                                                    + CLOSE))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("tracked changes");
        }
    }

    @Test
    void neverConcatenatesAlternateRepresentationsOfEmbeddedContent() throws Exception {
        Document result =
                extract(
                        OPEN
                                + "<w:p><w:r><w:t>"
                                + TEXT
                                + "</w:t></w:r></w:p>"
                                + "<mc:AlternateContent xmlns:mc=\"http://schemas.openxmlformats.org/markup-compatibility/2006\"><mc:Choice><w:p><w:r><w:t>CHOICE</w:t></w:r></w:p></mc:Choice><mc:Fallback><w:p><w:r><w:t>FALLBACK</w:t></w:r></w:p></mc:Fallback></mc:AlternateContent>"
                                + CLOSE);
        assertThat(result.text()).isEqualTo(TEXT);
    }

    @Test
    void rejectsDtdEntitiesMalformedXmlAndUnsupportedNamespaces() {
        for (String xml :
                new String[] {
                    "<!DOCTYPE document [<!ENTITY xxe SYSTEM 'file:///etc/passwd'>]>"
                            + OPEN
                            + "<w:p><w:r><w:t>&xxe;</w:t></w:r></w:p>"
                            + CLOSE,
                    OPEN + "<w:p>",
                    "<document><body>" + TEXT + "</body></document>",
                    OPEN + CLOSE
                }) {
            assertThatThrownBy(() -> extract(xml)).isInstanceOf(ApiException.class);
        }
    }

    @Test
    void rejectsNonDocxArchivesMacrosAndTooManyEntries() throws Exception {
        assertThatThrownBy(
                        () ->
                                extractor.extract(
                                        null, file("not a zip".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(ApiException.class);
        Map<String, String> entries =
                packageEntries(OPEN + "<w:p><w:r><w:t>" + TEXT + "</w:t></w:r></w:p>" + CLOSE);
        entries.put("word/vbaProject.bin", "macro");
        assertThatThrownBy(() -> extractor.extract(null, file(zip(entries))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Macro");
        entries.remove("word/vbaProject.bin");
        for (int i = 0; i < 256; i++) entries.put("extra/" + i, "");
        assertThatThrownBy(() -> extractor.extract(null, file(zip(entries))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("too many");
    }

    @Test
    void boundsInflationXmlDepthAndExtractedText() throws Exception {
        Map<String, String> entries = packageEntries(OPEN + CLOSE);
        entries.put("word/media/large.bin", "x".repeat(16 * 1024 * 1024));
        assertThatThrownBy(() -> extractor.extract(null, file(zip(entries))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("16 MB");
        assertThatThrownBy(() -> extract(OPEN + "<w:p>".repeat(130) + "</w:p>".repeat(130) + CLOSE))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("deeply");
        assertThatThrownBy(
                        () ->
                                extract(
                                        OPEN
                                                + "<w:p><w:r><w:t>"
                                                + "a".repeat(80_001)
                                                + "</w:t></w:r></w:p>"
                                                + CLOSE))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("80,000");
        assertThatThrownBy(() -> extract(OPEN + " ".repeat(4 * 1024 * 1024) + CLOSE))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("4 MB");
    }

    @Test
    void rejectsDuplicateBodyEntriesAndIncompletePackages() throws Exception {
        Map<String, String> entries =
                packageEntries(OPEN + "<w:p><w:r><w:t>" + TEXT + "</w:t></w:r></w:p>" + CLOSE);
        entries.put("word/duplicat.xml", entries.get("word/document.xml"));
        byte[] duplicate =
                new String(zip(entries), StandardCharsets.ISO_8859_1)
                        .replace("word/duplicat.xml", "word/document.xml")
                        .getBytes(StandardCharsets.ISO_8859_1);
        assertThatThrownBy(() -> extractor.extract(null, file(duplicate)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("duplicate");
        entries.remove("word/duplicat.xml");
        entries.remove("_rels/.rels");
        assertThatThrownBy(() -> extractor.extract(null, file(zip(entries))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("standard Word document body");
    }

    @Test
    void ignoresTabStopMetadataButPreservesActualRunBreaks() throws Exception {
        Document result =
                extract(
                        OPEN
                                + "<w:p><w:pPr><w:tabs><w:tab w:pos=\"1440\"/></w:tabs></w:pPr><w:r><w:t>"
                                + TEXT
                                + "</w:t><w:br/><w:t>Next line.</w:t></w:r></w:p>"
                                + CLOSE);
        assertThat(result.text()).isEqualTo(TEXT + "\nNext line.");
    }

    @Test
    void acceptsTheExactCharacterBoundary() throws Exception {
        assertThat(
                        extract(
                                        OPEN
                                                + "<w:p><w:r><w:t>"
                                                + "a".repeat(80_000)
                                                + "</w:t></w:r></w:p>"
                                                + CLOSE)
                                .text())
                .hasSize(80_000);
        assertThat(
                        extract(
                                        OPEN
                                                + "<w:tbl><w:tr><w:tc><w:p><w:r><w:t>"
                                                + "a".repeat(80_000)
                                                + "</w:t></w:r></w:p></w:tc></w:tr></w:tbl>"
                                                + CLOSE)
                                .text())
                .hasSize(80_000);
    }

    private Document extract(String xml) throws Exception {
        return extractor.extract(null, file(zip(packageEntries(xml))));
    }

    private MockMultipartFile file(byte[] bytes) {
        // Deliberately incorrect MIME: never trust it for parsing decisions.
        return new MockMultipartFile("file", "contract.DOCX", "application/octet-stream", bytes);
    }

    private Map<String, String> packageEntries(String xml) {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put(
                "[Content_Types].xml",
                "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"/>");
        entries.put(
                "_rels/.rels",
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"/>");
        entries.put("word/document.xml", xml);
        return entries;
    }

    private byte[] zip(Map<String, String> entries) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
