package dev.termsofsurrender.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/** Exercise the actual servlet connector: MockMvc cannot reproduce body-draining failures. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class UploadHttpTest {
    @LocalServerPort private int port;

    @Test
    void oversizedMultipartReturns413InsteadOfResettingTheConnection() throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            String boundary = "http-upload-boundary";
            byte[] prefix =
                    ("--"
                                    + boundary
                                    + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"large.pdf\"\r\n"
                                    + "Content-Type: application/pdf\r\n\r\n")
                            .getBytes(StandardCharsets.UTF_8);
            byte[] suffix = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
            HttpRequest request =
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/analyses"))
                            .timeout(Duration.ofSeconds(20))
                            .header("Origin", "http://localhost:3000")
                            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                            .POST(
                                    HttpRequest.BodyPublishers.concat(
                                            HttpRequest.BodyPublishers.ofByteArray(prefix),
                                            HttpRequest.BodyPublishers.ofByteArray(
                                                    new byte[11 * 1024 * 1024]),
                                            HttpRequest.BodyPublishers.ofByteArray(suffix)))
                            .build();
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(413);
            assertThat(response.body()).contains("UPLOAD_TOO_LARGE");
        }
    }
}
