package dev.termsofsurrender.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import dev.termsofsurrender.documents.Document;
import dev.termsofsurrender.documents.DocumentExtractor;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class PreviewExtractionTest {
    private static final String FIRST =
            "Unless you cancel before the trial ends, you will automatically be charged";
    private static final String SECOND =
            "$120 for an annual subscription. You can cancel using account settings.";

    @Test
    void pdfLineWrappingPreservesConsequenceAndEvidence() throws Exception {
        try (PDDocument pdf = new PDDocument();
                var bytes = new ByteArrayOutputStream()) {
            var page = new PDPage();
            pdf.addPage(page);
            try (var content = new PDPageContentStream(pdf, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                content.newLineAtOffset(25, 700);
                content.showText(FIRST);
                content.newLineAtOffset(0, -14);
                content.showText(SECOND);
                content.endText();
            }
            pdf.save(bytes);
            assertConversion(
                    new DocumentExtractor()
                            .extract(
                                    null,
                                    new MockMultipartFile(
                                            "file",
                                            "trial.pdf",
                                            "application/pdf",
                                            bytes.toByteArray())));
        }
    }

    @Test
    void docxRunsPreserveConsequenceAndEvidence() throws Exception {
        try (var bytes = new ByteArrayOutputStream()) {
            try (var zip = new ZipOutputStream(bytes)) {
                entry(
                        zip,
                        "[Content_Types].xml",
                        """
                        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                        <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                        <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                        </Types>
                        """);
                entry(
                        zip,
                        "_rels/.rels",
                        """
                        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                        <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                        </Relationships>
                        """);
                entry(
                        zip,
                        "word/document.xml",
                        """
                        <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p>
                        <w:r><w:t xml:space="preserve">%s </w:t></w:r><w:r><w:t>%s</w:t></w:r>
                        </w:p></w:body></w:document>
                        """
                                .formatted(FIRST, SECOND));
            }
            assertConversion(
                    new DocumentExtractor()
                            .extract(
                                    null,
                                    new MockMultipartFile(
                                            "file",
                                            "trial.docx",
                                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                            bytes.toByteArray())));
        }
    }

    private void entry(ZipOutputStream zip, String path, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private void assertConversion(Document document) {
        var report =
                new PreviewAnalysisEngine(new EvidenceValidator())
                        .analyze(
                                new Analysis.Input(
                                        document,
                                        new Analysis.Context(
                                                Analysis.Jurisdiction.US,
                                                Analysis.Category.CONSUMER,
                                                "")));
        assertThat(report.findings())
                .anySatisfy(
                        f -> {
                            assertThat(f.id()).startsWith("trial-conversion-");
                            assertThat(f.impact()).isEqualTo(Analysis.Impact.HIGH);
                            assertThat(
                                            document.text()
                                                    .substring(
                                                            f.evidence().start(),
                                                            f.evidence().end()))
                                    .isEqualTo(f.evidence().quote());
                            assertThat(f.evidence().quote()).contains("$120");
                        });
    }
}
