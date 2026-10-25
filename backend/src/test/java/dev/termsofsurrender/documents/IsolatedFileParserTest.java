package dev.termsofsurrender.documents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.termsofsurrender.api.ApiException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

class IsolatedFileParserTest {
    private static final String TEXT =
            "This agreement renews automatically each year unless written notice is received thirty days before renewal.";
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void productionWorkerExtractsAnActualPdfAndReturnsSafeInputErrors() throws Exception {
        try (IsolatedFileParser parser = new IsolatedFileParser(mapper);
                PDDocument pdf = new PDDocument();
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
            Document result = parser.extract("PDF", file(output.toByteArray()));
            assertThat(result.text()).isEqualTo(TEXT);
            assertThat(result.sourceType()).isEqualTo("PDF");
            assertThatThrownBy(
                            () ->
                                    parser.extract(
                                            "PDF", file(TEXT.getBytes(StandardCharsets.UTF_8))))
                    .isInstanceOfSatisfying(
                            ApiException.class,
                            error -> {
                                assertThat(error.code()).isEqualTo("UNSUPPORTED_FILE");
                                assertThat(error.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                            });
        }
    }

    @Test
    void workerHasSanitizedEnvironmentBoundedHeapAndPrivateDirectory() {
        List<Path> directories = new ArrayList<>();
        try (IsolatedFileParser parser = fixture("NORMAL", Duration.ofSeconds(5), directories)) {
            assertThat(parser.extract("DOCX", textFile()).text()).isEqualTo(TEXT);
        }
        assertCleaned(directories);
    }

    @Test
    void crashesMalformedOrOversizedOutputAndInvalidEvidenceNeverBecomeReports() {
        for (String mode :
                List.of(
                        "CRASH",
                        "OOM",
                        "BAD_JSON",
                        "FLOOD",
                        "BAD_EVIDENCE",
                        "UNKNOWN_CODE",
                        "TRAILING",
                        "UNKNOWN_FIELD",
                        "DUPLICATE_FIELD")) {
            List<Path> directories = new ArrayList<>();
            try (IsolatedFileParser parser = fixture(mode, Duration.ofSeconds(5), directories)) {
                assertThatThrownBy(() -> parser.extract("DOCX", textFile()))
                        .isInstanceOfSatisfying(
                                ApiException.class,
                                error -> {
                                    assertThat(error.code()).isEqualTo("PARSER_UNAVAILABLE");
                                    assertThat(error.getMessage())
                                            .doesNotContain("private", "PRIVATE_SOURCE", TEXT);
                                });
            }
            assertCleaned(directories);
        }
    }

    @Test
    void knownErrorsUseParentOwnedMessages() {
        try (IsolatedFileParser parser =
                fixture("KNOWN_CODE", Duration.ofSeconds(5), new ArrayList<>())) {
            assertThatThrownBy(() -> parser.extract("DOCX", textFile()))
                    .isInstanceOfSatisfying(
                            ApiException.class,
                            error -> {
                                assertThat(error.code()).isEqualTo("DOCX_TRACKED_CHANGES");
                                assertThat(error.getMessage()).contains("Accept or reject changes");
                            });
        }
    }

    @Test
    void blockedInputWriterIsCoveredByTheSameDeadlineAndWorkerIsReaped() {
        List<Path> directories = new ArrayList<>();
        long start = System.nanoTime();
        try (IsolatedFileParser parser = fixture("NO_READ", Duration.ofSeconds(1), directories)) {
            assertThatThrownBy(
                            () ->
                                    parser.extract(
                                            "PDF",
                                            file(new byte[IsolatedFileParser.MAX_INPUT_BYTES])))
                    .isInstanceOfSatisfying(
                            ApiException.class,
                            error -> assertThat(error.code()).isEqualTo("PARSER_TIMEOUT"));
        }
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
        assertCleaned(directories);
    }

    @Test
    void saturationRejectsImmediatelyAndCapacityRecoversAfterTimeout() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        AtomicInteger launches = new AtomicInteger();
        List<Path> directories = new java.util.concurrent.CopyOnWriteArrayList<>();
        try (IsolatedFileParser parser =
                        new IsolatedFileParser(
                                mapper,
                                Duration.ofSeconds(3),
                                1,
                                (directory, format) -> {
                                    directories.add(directory);
                                    started.countDown();
                                    return ParserCommand.create(
                                            directory,
                                            ParserTestWorker.class.getName(),
                                            launches.getAndIncrement() == 0 ? "HANG" : "NORMAL");
                                });
                var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first =
                    executor.submit(
                            () -> {
                                assertThatThrownBy(() -> parser.extract("DOCX", textFile()))
                                        .isInstanceOfSatisfying(
                                                ApiException.class,
                                                error ->
                                                        assertThat(error.code())
                                                                .isEqualTo("PARSER_TIMEOUT"));
                            });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> parser.extract("DOCX", textFile()))
                    .isInstanceOfSatisfying(
                            ApiException.class,
                            error -> assertThat(error.code()).isEqualTo("PARSER_BUSY"));
            first.get(8, TimeUnit.SECONDS);
            assertCleaned(directories);
            assertThat(parser.extract("DOCX", textFile()).text()).isEqualTo(TEXT);
            assertThat(launches.get()).isEqualTo(2);
        }
        assertCleaned(directories);
    }

    @Test
    void shutdownKillsActiveWorkerAndRefusesNewWork() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        List<Path> directories = new java.util.concurrent.CopyOnWriteArrayList<>();
        try (IsolatedFileParser parser =
                        new IsolatedFileParser(
                                mapper,
                                Duration.ofSeconds(20),
                                1,
                                (directory, format) -> {
                                    directories.add(directory);
                                    started.countDown();
                                    return ParserCommand.create(
                                            directory, ParserTestWorker.class.getName(), "HANG");
                                });
                var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var request =
                    executor.submit(
                            () ->
                                    assertThatThrownBy(() -> parser.extract("DOCX", textFile()))
                                            .isInstanceOf(ApiException.class));
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            parser.close();
            request.get(5, TimeUnit.SECONDS);
            assertThatThrownBy(() -> parser.extract("DOCX", textFile()))
                    .isInstanceOf(ApiException.class);
        }
        assertCleaned(directories);
    }

    @Test
    void oversizedInputIsRejectedBeforeStartingAWorker() {
        List<Path> directories = new ArrayList<>();
        try (IsolatedFileParser parser = fixture("NORMAL", Duration.ofSeconds(5), directories)) {
            assertThatThrownBy(
                            () ->
                                    parser.extract(
                                            "PDF",
                                            file(new byte[IsolatedFileParser.MAX_INPUT_BYTES + 1])))
                    .isInstanceOfSatisfying(
                            ApiException.class,
                            error -> assertThat(error.code()).isEqualTo("UPLOAD_TOO_LARGE"));
            assertThat(directories).isEmpty();
        }
    }

    @Test
    void requestInterruptionKillsWorkerAndPreservesTheInterruptFlag() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        var interrupted = new java.util.concurrent.atomic.AtomicBoolean();
        List<Path> directories = new java.util.concurrent.CopyOnWriteArrayList<>();
        try (IsolatedFileParser parser =
                new IsolatedFileParser(
                        mapper,
                        Duration.ofSeconds(20),
                        1,
                        (directory, format) -> {
                            directories.add(directory);
                            started.countDown();
                            return ParserCommand.create(
                                    directory, ParserTestWorker.class.getName(), "HANG");
                        })) {
            Thread request =
                    Thread.ofPlatform()
                            .start(
                                    () -> {
                                        try {
                                            parser.extract("DOCX", textFile());
                                        } catch (ApiException exception) {
                                            interrupted.set(Thread.currentThread().isInterrupted());
                                        }
                                    });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            request.interrupt();
            request.join(5000);
            assertThat(request.isAlive()).isFalse();
            assertThat(interrupted).isTrue();
        }
        assertCleaned(directories);
    }

    @Test
    void capacityRecoversAfterAWorkerCrash() {
        AtomicInteger launches = new AtomicInteger();
        List<Path> directories = new ArrayList<>();
        try (IsolatedFileParser parser =
                new IsolatedFileParser(
                        mapper,
                        Duration.ofSeconds(5),
                        1,
                        (directory, format) -> {
                            directories.add(directory);
                            return ParserCommand.create(
                                    directory,
                                    ParserTestWorker.class.getName(),
                                    launches.getAndIncrement() == 0 ? "CRASH" : "NORMAL");
                        })) {
            assertThatThrownBy(() -> parser.extract("DOCX", textFile()))
                    .isInstanceOf(ApiException.class);
            assertCleaned(directories);
            assertThat(parser.extract("DOCX", textFile()).text()).isEqualTo(TEXT);
        }
        assertCleaned(directories);
    }

    private IsolatedFileParser fixture(String mode, Duration timeout, List<Path> directories) {
        return new IsolatedFileParser(
                mapper,
                timeout,
                1,
                (directory, format) -> {
                    directories.add(directory);
                    return ParserCommand.create(directory, ParserTestWorker.class.getName(), mode);
                });
    }

    private MockMultipartFile textFile() {
        return file(TEXT.getBytes(StandardCharsets.UTF_8));
    }

    private MockMultipartFile file(byte[] bytes) {
        return new MockMultipartFile(
                "file", "not-forwarded-to-worker.docx", "application/octet-stream", bytes);
    }

    private void assertCleaned(List<Path> directories) {
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(
                        () ->
                                directories.forEach(
                                        path -> assertThat(Files.exists(path)).isFalse()));
    }
}
