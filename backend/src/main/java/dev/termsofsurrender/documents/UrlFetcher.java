package dev.termsofsurrender.documents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.termsofsurrender.api.ApiException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

@Component
public class UrlFetcher {
    // Max response body to read from the remote server.
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024; // 2 MB
    // Max extracted text after stripping HTML.
    private static final int MAX_EXTRACTED_CHARS = DocumentExtractor.MAX_CHARACTERS;
    // Allowed schemes and ports.
    private static final Set<String> ALLOWED_SCHEMES = Set.of("https");
    // Private/loopback/link-local address prefixes blocked by DNS validation.
    private static final int CONNECT_TIMEOUT_S = 8;
    private static final int READ_TIMEOUT_S = 12;
    private static final int MAX_REDIRECTS = 3;

    public record FetchResult(String title, String text, String sourceUrl, boolean truncated) {}

    // Patterns to find embedded JSON blobs that may contain the actual page content.
    // Many SPAs (React/Vue/Next.js) embed their server-side data in a script tag.
    private static final List<Pattern> EMBEDDED_JSON_PATTERNS =
            List.of(
                    // Myntra: window["__myx_staticPage__"] = {...}
                    Pattern.compile(
                            "window\\[\"__myx_staticPage__\"\\]\\s*=\\s*(\\{.*?\\});",
                            Pattern.DOTALL),
                    // Next.js: <script id="__NEXT_DATA__" type="application/json">{...}</script>
                    Pattern.compile(
                            "<script[^>]+id=['\"]__NEXT_DATA__['\"][^>]*>(.*?)</script>",
                            Pattern.DOTALL),
                    // Generic: window.__INITIAL_STATE__ = {...}
                    Pattern.compile(
                            "window\\.__INITIAL_STATE__\\s*=\\s*(\\{.*?\\});", Pattern.DOTALL),
                    // Generic: window.__PRELOADED_STATE__ = {...}
                    Pattern.compile(
                            "window\\.__PRELOADED_STATE__\\s*=\\s*(\\{.*?\\});", Pattern.DOTALL));

    // Strip markdown formatting (**bold**, # headers, [link](url), *italic*)
    private static final Pattern MD_BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*", Pattern.DOTALL);
    private static final Pattern MD_ITALIC = Pattern.compile("\\*(.+?)\\*", Pattern.DOTALL);
    private static final Pattern MD_HEADER = Pattern.compile("^#{1,6}\\s+", Pattern.MULTILINE);
    private static final Pattern MD_LINK = Pattern.compile("\\[([^\\]]+)\\]\\([^)]+\\)");

    private final HttpClient client;
    private final ObjectMapper mapper;

    public UrlFetcher() {
        this(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_S))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build(),
                new ObjectMapper());
    }

    UrlFetcher(HttpClient client, ObjectMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    public FetchResult fetch(String rawUrl) {
        URI uri = parseAndValidate(rawUrl);
        return fetchWithRedirects(uri, MAX_REDIRECTS);
    }

    private FetchResult fetchWithRedirects(URI uri, int remaining) {
        validateDestination(uri);
        HttpRequest request =
                HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(READ_TIMEOUT_S))
                        .header("User-Agent", "TermsOfSurrender/1.0 (agreement review tool)")
                        .header("Accept", "text/html,application/xhtml+xml")
                        .header("Accept-Language", "en")
                        .GET()
                        .build();
        HttpResponse<byte[]> response;
        try {
            var future =
                    client.sendAsync(
                            request,
                            info ->
                                    new dev.termsofsurrender.analysis.LimitedBodySubscriber(
                                            MAX_RESPONSE_BYTES));
            response = future.get(READ_TIMEOUT_S + CONNECT_TIMEOUT_S, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ApiException.invalid(
                    "URL_UNAVAILABLE", "The request was interrupted. Please try again.");
        } catch (ExecutionException | TimeoutException e) {
            throw ApiException.invalid(
                    "URL_UNAVAILABLE",
                    "The page could not be reached. Check the URL or paste the text directly.");
        }

        int status = response.statusCode();
        if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
            if (remaining <= 0) {
                throw ApiException.invalid(
                        "URL_TOO_MANY_REDIRECTS",
                        "This URL redirects too many times. Try the final destination URL or paste the text.");
            }
            String location = response.headers().firstValue("Location").orElse(null);
            if (location == null || location.isBlank()) {
                throw ApiException.invalid(
                        "URL_UNAVAILABLE",
                        "The page redirected without a destination. Paste the text directly.");
            }
            URI next;
            try {
                next = uri.resolve(location);
            } catch (Exception e) {
                throw ApiException.invalid(
                        "URL_INVALID", "The redirect destination is not a valid URL.");
            }
            // Validate the redirect destination through the same rules as the initial URL.
            URI validatedNext = parseAndValidate(next.toString());
            return fetchWithRedirects(validatedNext, remaining - 1);
        }
        if (status == 401 || status == 403) {
            throw ApiException.invalid(
                    "URL_ACCESS_DENIED",
                    "This page requires a login or is restricted. Paste the text directly instead.");
        }
        if (status == 404) {
            throw ApiException.invalid(
                    "URL_NOT_FOUND", "The page was not found. Check the URL and try again.");
        }
        if (status != 200) {
            throw ApiException.invalid(
                    "URL_UNAVAILABLE",
                    "The page returned an error (" + status + "). Paste the text directly.");
        }

        String contentType = response.headers().firstValue("Content-Type").orElse("");
        if (!contentType.contains("html")) {
            throw ApiException.invalid(
                    "URL_NOT_HTML",
                    "Only HTML pages are supported. For PDFs, download the file and upload it instead.");
        }

        return extract(response.body(), uri.toString());
    }

    private FetchResult extract(byte[] body, String sourceUrl) {
        String rawHtml = new String(body, java.nio.charset.StandardCharsets.UTF_8);
        Document doc = Jsoup.parse(rawHtml);

        // Remove nav, header, footer, scripts, ads — keep main content
        doc.select(
                        "script, style, noscript, nav, header, footer, aside, "
                                + "[role=navigation], [role=banner], [role=complementary], "
                                + ".cookie-banner, .cookie-notice, #cookie, .nav, .navbar, "
                                + ".menu, .sidebar, .advertisement, .ads, iframe, form")
                .remove();

        // Try to find the main content area
        org.jsoup.nodes.Element main =
                doc.selectFirst(
                        "main, [role=main], article, "
                                + ".terms-content, .legal-content, .policy-content, #terms, #privacy, "
                                + "#content, .content, .container");
        org.jsoup.nodes.Element bodyEl = main != null ? main : doc.body();

        // Extract visible text with paragraph breaks
        String text = "";
        if (bodyEl != null) {
            text = bodyEl.wholeOwnText().isBlank() ? bodyEl.text() : extractStructuredText(bodyEl);
            text = text.replaceAll("[ \\t]+", " ").replaceAll("(\\n\\s*){3,}", "\n\n").strip();
        }

        // If visible text is too short, the page likely uses client-side rendering.
        // Try to recover content from embedded JSON blobs in script tags (SPAs often
        // server-side-render their data as JSON even when the DOM is empty).
        if (text.length() < 80) {
            String embedded = extractFromEmbeddedJson(rawHtml);
            if (embedded != null) {
                text = embedded;
            }
        }

        if (text.length() < 80) {
            throw ApiException.invalid(
                    "URL_NO_CONTENT",
                    "This page loads its content with JavaScript, which cannot be fetched"
                            + " server-side. Open the page in your browser, select all the text"
                            + " (Ctrl+A / Cmd+A), copy it, and paste it into the text field instead.");
        }

        boolean truncated = false;
        if (text.length() > MAX_EXTRACTED_CHARS) {
            truncated = true;
            text = text.substring(0, MAX_EXTRACTED_CHARS);
            int lastSpace = text.lastIndexOf(' ');
            if (lastSpace > MAX_EXTRACTED_CHARS - 200) {
                text = text.substring(0, lastSpace);
            }
        }

        String title = doc.title().isBlank() ? "Web page" : doc.title().strip();
        if (title.length() > 80) {
            title = title.substring(0, 80).strip() + "\u2026";
        }

        return new FetchResult(title, text, sourceUrl, truncated);
    }

    /**
     * Try to extract meaningful text from JavaScript-embedded JSON blobs in the raw HTML. Many SPAs
     * embed their server-side data as JSON in a script tag even when the DOM is empty. Returns
     * cleaned plain text, or null if nothing useful is found.
     */
    private String extractFromEmbeddedJson(String rawHtml) {
        for (Pattern pattern : EMBEDDED_JSON_PATTERNS) {
            Matcher m = pattern.matcher(rawHtml);
            if (!m.find()) {
                continue;
            }
            try {
                JsonNode root = mapper.readTree(m.group(1));
                String text = extractTextFromJson(root, 0);
                if (text != null && text.length() >= 80) {
                    return text;
                }
            } catch (Exception ignored) {
                // malformed JSON — try next pattern
            }
        }
        return null;
    }

    /**
     * Recursively walk a JSON tree to find the longest text that looks like legal content. Prefers
     * fields named "description", "content", "body", etc.
     */
    private String extractTextFromJson(JsonNode node, int depth) {
        if (depth > 12) {
            return null;
        }
        if (node.isTextual()) {
            String val = node.asText();
            if (val.length() > 200) {
                return stripMarkdown(val);
            }
            return null;
        }
        if (node.isArray()) {
            List<String> parts = new ArrayList<>();
            for (JsonNode child : node) {
                String part = extractTextFromJson(child, depth + 1);
                if (part != null && part.length() > 50) {
                    parts.add(part);
                }
            }
            if (!parts.isEmpty()) {
                String joined = String.join("\n\n", parts);
                if (joined.length() > 200) {
                    return joined;
                }
            }
            return null;
        }
        if (node.isObject()) {
            // Prefer well-known content field names
            String best = null;
            for (String key :
                    List.of(
                            "description",
                            "content",
                            "body",
                            "text",
                            "html",
                            "details",
                            "pageContent",
                            "page_content",
                            "termContent")) {
                if (node.has(key)) {
                    String candidate = extractTextFromJson(node.get(key), depth + 1);
                    if (candidate != null && (best == null || candidate.length() > best.length())) {
                        best = candidate;
                    }
                }
            }
            if (best != null && best.length() > 500) {
                return best;
            }
            // Fall back to scanning all children
            for (JsonNode child : node) {
                String candidate = extractTextFromJson(child, depth + 1);
                if (candidate != null && (best == null || candidate.length() > best.length())) {
                    best = candidate;
                }
            }
            return best;
        }
        return null;
    }

    /** Strip common markdown formatting from text (bold, italic, headers, links). */
    private String stripMarkdown(String text) {
        text = MD_BOLD.matcher(text).replaceAll("$1");
        text = MD_ITALIC.matcher(text).replaceAll("$1");
        text = MD_HEADER.matcher(text).replaceAll("");
        text = MD_LINK.matcher(text).replaceAll("$1");
        return text.replaceAll("[ \\t]+", " ").replaceAll("(\\n\\s*){3,}", "\n\n").strip();
    }

    private String extractStructuredText(org.jsoup.nodes.Element element) {
        StringBuilder sb = new StringBuilder();
        for (org.jsoup.nodes.Node node : element.childNodes()) {
            if (node instanceof org.jsoup.nodes.TextNode tn) {
                String t = tn.text().strip();
                if (!t.isEmpty()) {
                    sb.append(t).append(" ");
                }
            } else if (node instanceof org.jsoup.nodes.Element el) {
                String tag = el.tagName().toLowerCase(java.util.Locale.ROOT);
                String inner = extractStructuredText(el).strip();
                if (inner.isEmpty()) {
                    continue;
                }
                if (Set.of(
                                "p",
                                "div",
                                "li",
                                "h1",
                                "h2",
                                "h3",
                                "h4",
                                "h5",
                                "h6",
                                "tr",
                                "section",
                                "article",
                                "blockquote")
                        .contains(tag)) {
                    sb.append("\n\n").append(inner);
                } else {
                    sb.append(inner).append(" ");
                }
            }
        }
        return sb.toString().strip();
    }

    private URI parseAndValidate(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw ApiException.invalid("URL_INVALID", "Please enter a URL.");
        }
        String trimmed = rawUrl.strip();
        if (trimmed.length() > 2000) {
            throw ApiException.invalid("URL_INVALID", "The URL is too long.");
        }
        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (Exception e) {
            throw ApiException.invalid(
                    "URL_INVALID", "That doesn't look like a valid URL. Check it and try again.");
        }
        if (!ALLOWED_SCHEMES.contains(uri.getScheme())) {
            throw ApiException.invalid("URL_INVALID", "Only HTTPS URLs are supported.");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw ApiException.invalid("URL_INVALID", "The URL must include a hostname.");
        }
        if (uri.getUserInfo() != null) {
            throw ApiException.invalid("URL_INVALID", "URLs with credentials are not supported.");
        }
        // Block non-default ports (allow 443 and -1/default only)
        int port = uri.getPort();
        if (port != -1 && port != 443) {
            throw ApiException.invalid("URL_INVALID", "Only standard HTTPS port 443 is supported.");
        }
        return uri;
    }

    private void validateDestination(URI uri) {
        String host = uri.getHost();
        try {
            // Resolve DNS and block private/loopback/link-local addresses.
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress addr : addresses) {
                if (addr.isLoopbackAddress()
                        || addr.isSiteLocalAddress()
                        || addr.isLinkLocalAddress()
                        || addr.isAnyLocalAddress()
                        || addr.isMulticastAddress()) {
                    throw ApiException.invalid(
                            "URL_INVALID",
                            "The URL resolves to a private or internal address, which is not permitted.");
                }
                // Block 169.254.x.x (cloud metadata) explicitly
                byte[] raw = addr.getAddress();
                if (raw.length == 4 && raw[0] == (byte) 169 && raw[1] == (byte) 254) {
                    throw ApiException.invalid(
                            "URL_INVALID",
                            "The URL resolves to a reserved address, which is not permitted.");
                }
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.invalid(
                    "URL_UNAVAILABLE",
                    "The hostname could not be resolved. Check the URL or paste the text directly.");
        }
    }
}
