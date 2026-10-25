package dev.termsofsurrender.documents;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.termsofsurrender.api.ApiException;
import java.io.OutputStream;
import java.io.PrintStream;

/** Single-request child entry point. Does not start Spring or load application configuration. */
public final class ParserWorker {
    private ParserWorker() {}

    public record Response(Document document, String errorCode) {}

    public static void main(String[] args) throws Exception {
        PrintStream protocol = System.out;
        // Third-party diagnostics must never corrupt the protocol or leak contract text into logs.
        System.setOut(new PrintStream(OutputStream.nullOutputStream()));
        if (args.length < 1 || args.length > 2 || !(args[0].equals("PDF") || args[0].equals("DOCX"))) {
            System.exit(2);
            return;
        }
        String format = args[0];
        // Optional second arg is the original filename for display title derivation.
        // Validated: must be a plain filename (no path separators) up to 255 chars.
        String filename = args.length == 2 && args[1].length() <= 255
                && !args[1].contains("/") && !args[1].contains("\\") ? args[1] : null;
        String title = filename != null ? DocumentExtractor.titleFromFilename(filename)
                : "Uploaded agreement";
        byte[] bytes = System.in.readNBytes(IsolatedFileParser.MAX_INPUT_BYTES + 1);
        Response response;
        try {
            response = new Response(new DocumentExtractor().extractFile(format, title, bytes), null);
        } catch (ApiException exception) {
            // Return only stable codes, never exception messages or stack traces.
            response = new Response(null, exception.code());
        }
        new ObjectMapper().writeValue(protocol, response);
    }
}
