package dev.termsofsurrender.documents;

import dev.termsofsurrender.api.ApiException;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** Routes plain text locally, but never invokes a file parser inside the API JVM. */
@Component
public class DocumentIngestion {
    private final IsolatedFileParser files;
    private final DocumentExtractor text = new DocumentExtractor();

    public DocumentIngestion(IsolatedFileParser files) {
        this.files = files;
    }

    public Document extract(String value, MultipartFile file) {
        boolean hasText = value != null && !value.isBlank();
        boolean hasFile = file != null && !file.isEmpty();
        if (hasText == hasFile) {
            throw ApiException.invalid(
                    "INVALID_INPUT", "Provide either pasted text or one PDF/DOCX file, not both.");
        }
        if (hasText) {
            return text.extract(value, null);
        }
        String filename = file.getOriginalFilename();
        String format =
                filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".docx")
                        ? "DOCX"
                        : "PDF";
        return files.extract(format, file);
    }
}
