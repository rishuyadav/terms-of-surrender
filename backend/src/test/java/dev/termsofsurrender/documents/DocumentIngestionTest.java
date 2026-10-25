package dev.termsofsurrender.documents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.termsofsurrender.api.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class DocumentIngestionTest {
    private static final String TEXT =
            "This agreement renews automatically each year unless written notice is received thirty days before renewal.";
    private final IsolatedFileParser parser = mock(IsolatedFileParser.class);
    private final DocumentIngestion ingestion = new DocumentIngestion(parser);

    @Test
    void plainTextNeverStartsAFileWorker() {
        assertThat(ingestion.extract(TEXT, null).sourceType()).isEqualTo("TEXT");
        verifyNoInteractions(parser);
    }

    @Test
    void fileInputsAlwaysCrossTheWorkerBoundaryWithoutForwardingFilenameOrMime() {
        var file =
                new MockMultipartFile(
                        "file",
                        "private-name.DOCX",
                        "application/pdf",
                        TEXT.getBytes(StandardCharsets.UTF_8));
        Document document =
                new DocumentExtractor()
                        .build("Uploaded agreement", "DOCX", List.of(TEXT), List.of());
        when(parser.extract(eq("DOCX"), eq(file))).thenReturn(document);
        assertThat(ingestion.extract(null, file)).isSameAs(document);
        verify(parser).extract("DOCX", file);
    }

    @Test
    void invalidOrAmbiguousInputsNeverConsumeWorkerCapacity() {
        var file = new MockMultipartFile("file", new byte[] {1});
        assertThatThrownBy(() -> ingestion.extract(TEXT, file)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> ingestion.extract(" ", null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(
                        () -> ingestion.extract(null, new MockMultipartFile("file", new byte[0])))
                .isInstanceOf(ApiException.class);
        verifyNoInteractions(parser);
    }
}
