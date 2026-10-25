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
        if (args.length != 1 || !(args[0].equals("PDF") || args[0].equals("DOCX"))) {
            System.exit(2);
            return;
        }
        byte[] bytes = System.in.readNBytes(IsolatedFileParser.MAX_INPUT_BYTES + 1);
        Response response;
        try {
            response = new Response(new DocumentExtractor().extractFile(args[0], bytes), null);
        } catch (ApiException exception) {
            // Return only stable codes, never exception messages or stack traces.
            response = new Response(null, exception.code());
        }
        new ObjectMapper().writeValue(protocol, response);
    }
}
