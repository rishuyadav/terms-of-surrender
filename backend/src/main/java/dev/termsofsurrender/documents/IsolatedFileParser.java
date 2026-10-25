package dev.termsofsurrender.documents;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.termsofsurrender.api.ApiException;
import dev.termsofsurrender.privacy.Sessions;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * A bounded process boundary, not an OS security sandbox. Workers have no application environment
 * variables, but still run as the service's OS user; hostile-file deployments need additional
 * isolation.
 */
@Component
public class IsolatedFileParser implements AutoCloseable {
    static final int MAX_INPUT_BYTES = 10 * 1024 * 1024;
    static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;
    private static final Logger LOG = LoggerFactory.getLogger(IsolatedFileParser.class);
    private static final Map<String, String> ERRORS =
            Map.ofEntries(
                    Map.entry("UNSUPPORTED_FILE", "Upload a text-based PDF or DOCX document."),
                    Map.entry("UPLOAD_TOO_LARGE", "Upload a document smaller than 10 MB."),
                    Map.entry(
                            "PROTECTED_PDF",
                            "Upload an unprotected PDF that permits text extraction."),
                    Map.entry("TOO_MANY_PAGES", "Please use an agreement with 40 pages or fewer."),
                    Map.entry(
                            "UNREADABLE_PDF",
                            "The PDF could not be read. It may be damaged or password protected."),
                    Map.entry(
                            "UNREADABLE_DOCX",
                            "The DOCX could not be read within this release’s format and extraction limits. Export a plain DOCX or text-based PDF."),
                    Map.entry(
                            "DOCX_TRACKED_CHANGES",
                            "This DOCX contains tracked changes. Accept or reject changes in Word, then upload again."),
                    Map.entry(
                            "DOCUMENT_TOO_LONG",
                            "This release supports up to 80,000 characters per document."),
                    Map.entry(
                            "NOT_ENOUGH_TEXT",
                            "Provide at least 80 characters of readable contract text. Photos and scanned PDFs need OCR, which is not yet available."));

    private final ObjectMapper mapper;
    private final Duration timeout;
    private final Semaphore slots;
    private final BiFunction<Path, String, List<String>> command;
    private final ExecutorService io = Executors.newVirtualThreadPerTaskExecutor();
    private final Set<Process> active = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean();

    @Autowired
    public IsolatedFileParser(ObjectMapper mapper) {
        this(
                mapper,
                Duration.ofSeconds(20),
                2,
                (directory, format) ->
                        ParserCommand.create(directory, ParserWorker.class.getName(), format));
    }

    IsolatedFileParser(
            ObjectMapper mapper,
            Duration timeout,
            int concurrency,
            BiFunction<Path, String, List<String>> command) {
        if (timeout.isZero() || timeout.isNegative() || concurrency < 1) {
            throw new IllegalArgumentException("Parser budgets must be positive");
        }
        this.mapper = mapper;
        this.timeout = timeout;
        this.slots = new Semaphore(concurrency);
        this.command = command;
    }

    public Document extract(String format, MultipartFile file) {
        if (!Set.of("PDF", "DOCX").contains(format)) {
            throw new IllegalArgumentException("Unsupported parser format");
        }
        if (file.getSize() > MAX_INPUT_BYTES) {
            throw ApiException.invalid("UPLOAD_TOO_LARGE", ERRORS.get("UPLOAD_TOO_LARGE"));
        }
        if (closed.get()) {
            throw unavailable();
        }
        if (!slots.tryAcquire()) {
            throw new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "PARSER_BUSY",
                    "Document extraction is busy. Please try again shortly.");
        }
        Path directory = null;
        Process worker = null;
        Future<?> writer = null;
        Future<byte[]> reader = null;
        try {
            byte[] bytes;
            try (var input = file.getInputStream()) {
                bytes = input.readNBytes(MAX_INPUT_BYTES + 1);
            }
            if (bytes.length > MAX_INPUT_BYTES) {
                throw ApiException.invalid("UPLOAD_TOO_LARGE", ERRORS.get("UPLOAD_TOO_LARGE"));
            }
            directory = Files.createTempDirectory("tos-parser-");
            // Pass format as first arg; filename as optional second arg for title derivation.
            List<String> cmd = command.apply(directory, format);
            String originalFilename = file.getOriginalFilename();
            if (originalFilename != null && !originalFilename.isBlank()
                    && !originalFilename.contains("/") && !originalFilename.contains("\\")
                    && originalFilename.length() <= 255) {
                // Append filename only when using the real worker (test workers ignore extra args).
                cmd = new java.util.ArrayList<>(cmd);
                cmd.add(originalFilename);
            }
            ProcessBuilder builder = new ProcessBuilder(cmd);
            builder.directory(directory.toFile());
            builder.environment().clear();
            builder.environment().put("LANG", "C.UTF-8");
            builder.redirectError(ProcessBuilder.Redirect.DISCARD);
            long deadline = System.nanoTime() + timeout.toNanos();
            worker = builder.start();
            active.add(worker);
            if (closed.get()) {
                throw unavailable();
            }
            Process process = worker;
            writer =
                    io.submit(
                            () -> {
                                try (var output = process.getOutputStream()) {
                                    output.write(bytes);
                                }
                                return null;
                            });
            reader =
                    io.submit(
                            () -> {
                                try (var input = process.getInputStream()) {
                                    byte[] response = input.readNBytes(MAX_RESPONSE_BYTES + 1);
                                    if (response.length > MAX_RESPONSE_BYTES) {
                                        throw new IOException("Parser output budget exceeded");
                                    }
                                    return response;
                                }
                            });
            byte[] response = reader.get(remaining(deadline), TimeUnit.NANOSECONDS);
            writer.get(remaining(deadline), TimeUnit.NANOSECONDS);
            if (!worker.waitFor(remaining(deadline), TimeUnit.NANOSECONDS)) {
                throw new TimeoutException();
            }
            if (worker.exitValue() != 0) {
                LOG.warn("File parser exited with code {}", worker.exitValue());
                throw unavailable();
            }
            ParserWorker.Response result =
                    mapper.readerFor(ParserWorker.Response.class)
                            .with(
                                    com.fasterxml.jackson.core.JsonParser.Feature
                                            .STRICT_DUPLICATE_DETECTION)
                            .with(
                                    com.fasterxml.jackson.databind.DeserializationFeature
                                            .FAIL_ON_UNKNOWN_PROPERTIES)
                            .with(
                                    com.fasterxml.jackson.databind.DeserializationFeature
                                            .FAIL_ON_TRAILING_TOKENS)
                            .readValue(response);
            if (result == null || (result.document() == null) == (result.errorCode() == null)) {
                throw unavailable();
            }
            if (result.errorCode() != null) {
                String message = ERRORS.get(result.errorCode());
                if (message == null) {
                    throw unavailable();
                }
                throw ApiException.invalid(result.errorCode(), message);
            }
            validate(result.document(), format);
            return result.document();
        } catch (TimeoutException exception) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "PARSER_TIMEOUT",
                    "Document extraction exceeded its time limit. Try a smaller or simpler document.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (IOException | ExecutionException | RejectedExecutionException exception) {
            // No worker output, filenames, exception messages, or source content enter application
            // logs.
            LOG.warn("File parser unavailable: {}", exception.getClass().getSimpleName());
            throw unavailable();
        } finally {
            if (worker != null) {
                terminate(worker);
            }
            if (writer != null) {
                writer.cancel(true);
            }
            if (reader != null) {
                reader.cancel(true);
            }
            Process process = worker;
            Path temporary = directory;
            if (process != null && process.isAlive()) {
                // Never admit a replacement while an old process is still consuming its slot.
                process.onExit().thenRun(() -> release(process, temporary));
            } else {
                release(process, temporary);
            }
        }
    }

    private long remaining(long deadline) throws TimeoutException {
        long nanos = deadline - System.nanoTime();
        if (nanos <= 0) {
            throw new TimeoutException();
        }
        return nanos;
    }

    private void validate(Document document, String format) {
        if (document.title() == null
                || document.title().isBlank()
                || document.title().length() > 82  // 80 chars + possible ellipsis "…"
                || !format.equals(document.sourceType())
                || document.text() == null
                || document.text().length() < 80
                || document.text().length() > DocumentExtractor.MAX_CHARACTERS
                || !Sessions.hash(document.text()).equals(document.fingerprint())
                || document.pageCount() < 1
                || document.pageCount() > (format.equals("PDF") ? 40 : 1)
                || document.blocks() == null
                || document.blocks().isEmpty()
                || document.blocks().size() > 27_000
                || document.warnings() == null
                || document.warnings().size() > 50) {
            throw unavailable();
        }
        Set<String> ids = new HashSet<>();
        int end = 0;
        int page = 1;
        for (Document.Block block : document.blocks()) {
            if (block == null
                    || block.id() == null
                    || !ids.add(block.id())
                    || !block.id().matches("p" + block.page() + "-b[1-9][0-9]{0,4}")
                    || block.page() < page
                    || block.page() > document.pageCount()
                    || block.start() < end
                    || block.end() <= block.start()
                    || block.end() > document.text().length()
                    || !document.text().substring(end, block.start()).isBlank()
                    || !document.text()
                            .substring(block.start(), block.end())
                            .equals(block.text())) {
                throw unavailable();
            }
            end = block.end();
            page = block.page();
        }
        if (!document.text().substring(end).isBlank()
                || document.warnings().stream()
                        .anyMatch(value -> value == null || value.length() > 1024)) {
            throw unavailable();
        }
    }

    private static ApiException unavailable() {
        return new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "PARSER_UNAVAILABLE",
                "The document extraction worker could not complete this request. Please try again.");
    }

    private void terminate(Process process) {
        process.descendants().forEach(child -> child.destroyForcibly());
        if (process.isAlive()) {
            process.destroyForcibly();
        }
    }

    private void release(Process process, Path directory) {
        if (process != null) {
            active.remove(process);
        }
        try {
            if (directory != null) {
                try (var paths = Files.walk(directory)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                        Files.deleteIfExists(path);
                    }
                }
            }
        } catch (IOException | UncheckedIOException exception) {
            LOG.warn(
                    "Parser temporary-directory cleanup failed: {}",
                    exception.getClass().getSimpleName());
        } finally {
            slots.release();
        }
    }

    @Override
    @PreDestroy
    public void close() {
        closed.set(true);
        active.forEach(this::terminate);
        io.shutdownNow();
    }
}
