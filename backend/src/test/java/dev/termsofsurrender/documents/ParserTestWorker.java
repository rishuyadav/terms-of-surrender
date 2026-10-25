package dev.termsofsurrender.documents;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/** Real subprocess failure fixtures; never exposed by production worker arguments. */
public final class ParserTestWorker {
    private ParserTestWorker() {}

    public static void main(String[] args) throws Exception {
        // macOS injects this locale variable even when ProcessBuilder clears the environment.
        Set<String> allowed =
                System.getProperty("os.name").startsWith("Mac")
                        ? Set.of("LANG", "__CF_USER_TEXT_ENCODING")
                        : Set.of("LANG");
        if (!allowed.containsAll(System.getenv().keySet())) {
            System.exit(80);
            return;
        }
        if (Runtime.getRuntime().maxMemory() > 128L * 1024 * 1024) {
            System.exit(81);
            return;
        }
        if (!java.nio.file.Files.isSameFile(
                Path.of(System.getProperty("user.home")), Path.of("").toAbsolutePath())) {
            System.exit(82);
            return;
        }
        String mode = args[0];
        if (mode.equals("NO_READ")) {
            Thread.sleep(60_000);
            return;
        }
        String text = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
        if (mode.equals("HANG")) {
            Thread.sleep(60_000);
            return;
        }
        if (mode.equals("CRASH")) {
            Runtime.getRuntime().halt(23);
        }
        if (mode.equals("OOM")) {
            System.out.write(new byte[256 * 1024 * 1024]);
            return;
        }
        if (mode.equals("FLOOD")) {
            System.out.write(new byte[IsolatedFileParser.MAX_RESPONSE_BYTES + 1]);
            return;
        }
        if (mode.equals("BAD_JSON")) {
            System.out.print("private source and exception details must never reach the client");
            return;
        }
        ObjectMapper mapper = new ObjectMapper();
        if (mode.equals("UNKNOWN_CODE")) {
            mapper.writeValue(System.out, new ParserWorker.Response(null, "PRIVATE_SOURCE"));
            return;
        }
        if (mode.equals("KNOWN_CODE")) {
            mapper.writeValue(System.out, new ParserWorker.Response(null, "DOCX_TRACKED_CHANGES"));
            return;
        }
        Document document =
                new DocumentExtractor()
                        .build("Uploaded agreement", "DOCX", List.of(text), List.of());
        if (mode.equals("BAD_EVIDENCE")) {
            document =
                    new Document(
                            document.title(),
                            document.sourceType(),
                            document.fingerprint(),
                            document.text(),
                            List.of(new Document.Block("p1-b1", 1, 0, 5, "wrong")),
                            1,
                            List.of());
        }
        byte[] response = mapper.writeValueAsBytes(new ParserWorker.Response(document, null));
        if (mode.equals("UNKNOWN_FIELD")) {
            System.out.print(
                    "{\"extra\":true," + new String(response, StandardCharsets.UTF_8).substring(1));
            return;
        }
        if (mode.equals("DUPLICATE_FIELD")) {
            System.out.print(
                    "{\"document\":null,"
                            + new String(response, StandardCharsets.UTF_8).substring(1));
            return;
        }
        System.out.write(response);
        if (mode.equals("TRAILING")) {
            System.out.print("{}");
        }
    }
}
