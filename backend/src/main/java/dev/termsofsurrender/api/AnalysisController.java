package dev.termsofsurrender.api;

import dev.termsofsurrender.AppProperties;
import dev.termsofsurrender.analysis.Analysis;
import dev.termsofsurrender.analysis.ContractQa;
import dev.termsofsurrender.documents.Document;
import dev.termsofsurrender.documents.DocumentExtractor;
import dev.termsofsurrender.documents.DocumentIngestion;
import dev.termsofsurrender.documents.UrlFetcher;
import dev.termsofsurrender.jobs.AnalysisRepository;
import dev.termsofsurrender.privacy.Sessions;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
public class AnalysisController {
    private final Sessions sessions;
    private final AppProperties properties;
    private final DocumentIngestion extractor;
    private final AnalysisRepository repository;
    private final AnalysisStreams streams;
    private final UrlFetcher urlFetcher;
    private final ContractQa qa;

    public AnalysisController(
            Sessions sessions,
            AppProperties properties,
            DocumentIngestion extractor,
            AnalysisRepository repository,
            AnalysisStreams streams,
            UrlFetcher urlFetcher,
            ContractQa qa) {
        this.sessions = sessions;
        this.properties = properties;
        this.extractor = extractor;
        this.repository = repository;
        this.streams = streams;
        this.urlFetcher = urlFetcher;
        this.qa = qa;
    }

    @GetMapping("/session")
    Map<String, Object> session(HttpServletRequest request, HttpServletResponse response) {
        sessions.establish(request, response);
        return Map.of(
                "mode",
                properties.model().enabled()
                        ? Analysis.Mode.MODEL_ASSISTED
                        : Analysis.Mode.RULE_BASED_PREVIEW,
                "retentionHours",
                properties.retentionHours(),
                "legalContextAvailable",
                false,
                "maxCharacters",
                DocumentExtractor.MAX_CHARACTERS);
    }

    @GetMapping("/fetch")
    Map<String, Object> fetchUrl(@RequestParam String url, HttpServletRequest request) {
        sessions.require(request); // Must have a session; does not create one.
        UrlFetcher.FetchResult result = urlFetcher.fetch(url);
        return Map.of(
                "title", result.title(),
                "text", result.text(),
                "sourceUrl", result.sourceUrl(),
                "charCount", result.text().length(),
                "truncated", result.truncated());
    }

    @PostMapping(value = "/analyses", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<Analysis.View> create(
            @RequestParam(required = false) String text,
            @RequestParam(required = false) MultipartFile file,
            @RequestParam(defaultValue = "UNSPECIFIED") Analysis.Jurisdiction jurisdiction,
            @RequestParam(defaultValue = "OTHER") Analysis.Category category,
            @RequestParam(defaultValue = "") String region,
            HttpServletRequest request) {
        String owner = sessions.require(request);
        if (text != null
                && (text.stripLeading().startsWith("http://")
                        || text.stripLeading().startsWith("https://"))) {
            throw ApiException.invalid(
                    "URL_NOT_SUPPORTED",
                    "Paste the page text rather than its URL, or use the fetch endpoint first.");
        }
        if (region.length() > 80) {
            throw ApiException.invalid(
                    "INVALID_REGION", "Keep the state or region under 80 characters.");
        }
        Document document = extractor.extract(text, file);
        Analysis.Context context = new Analysis.Context(jurisdiction, category, region.strip());
        Analysis.View view = repository.create(owner, new Analysis.Input(document, context));
        return ResponseEntity.accepted()
                .location(URI.create("/api/analyses/" + view.id()))
                .body(view);
    }

    @GetMapping("/analyses/{id}")
    Analysis.View read(@PathVariable UUID id, HttpServletRequest request) {
        return repository.find(id, sessions.require(request)).orElseThrow(ApiException::notFound);
    }

    @GetMapping(value = "/analyses/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter events(@PathVariable UUID id, HttpServletRequest request) {
        return streams.open(id, sessions.require(request));
    }

    @DeleteMapping("/analyses/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID id, HttpServletRequest request) {
        if (!repository.delete(id, sessions.require(request))) {
            throw ApiException.notFound();
        }
        return ResponseEntity.noContent().build();
    }

    record AskRequest(String question) {}

    @PostMapping("/analyses/{id}/ask")
    Map<String, Object> ask(
            @PathVariable UUID id, @RequestBody AskRequest body, HttpServletRequest request) {
        if (!qa.isAvailable()) {
            throw new ApiException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "QA_UNAVAILABLE",
                    "Contract Q&A requires a configured AI provider. This deployment uses keyword"
                            + " preview only.");
        }
        Analysis.View view =
                repository.find(id, sessions.require(request)).orElseThrow(ApiException::notFound);
        if (view.status() != Analysis.Status.COMPLETED) {
            throw ApiException.invalid(
                    "ANALYSIS_NOT_READY", "The analysis must be complete before asking questions.");
        }
        if (body.question() == null
                || body.question().isBlank()
                || body.question().length() > 300) {
            throw ApiException.invalid("INVALID_QUESTION", "Question must be 1–300 characters.");
        }
        ContractQa.Answer answer =
                qa.ask(view.document(), body.question().strip())
                        .orElseThrow(
                                () ->
                                        ApiException.invalid(
                                                "QA_UNAVAILABLE", "Q&A is not available."));
        return Map.of(
                "question",
                body.question().strip(),
                "answer",
                answer.answer() != null ? answer.answer() : "",
                "blockId",
                answer.blockId() != null ? answer.blockId() : "",
                "quote",
                answer.quote() != null ? answer.quote() : "",
                "abstain",
                answer.abstain(),
                "abstainReason",
                answer.abstainReason() != null ? answer.abstainReason() : "");
    }
}
