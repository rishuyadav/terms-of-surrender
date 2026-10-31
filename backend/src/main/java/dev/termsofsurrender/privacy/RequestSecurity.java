package dev.termsofsurrender.privacy;

import dev.termsofsurrender.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestSecurity extends OncePerRequestFilter {
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private final AppProperties properties;

    public RequestSecurity(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "no-referrer");
        if (request.getRequestURI().startsWith("/api/")) {
            response.setHeader("Cache-Control", "no-store, private");
            if (request.getRequestURI().matches("/api/analyses/[^/]+/events")) {
                // Gzip buffering can hold SSE milestones until the stream ends.
                response.setHeader("Cache-Control", "no-store, private, no-transform");
                response.setHeader("X-Accel-Buffering", "no");
            }
            if (!SAFE_METHODS.contains(request.getMethod())
                    && !properties.allowedOrigins().contains(request.getHeader("Origin"))) {
                response.setStatus(403);
                response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                response.getWriter()
                        .write(
                                "{\"title\":\"UNTRUSTED_ORIGIN\",\"status\":403,"
                                        + "\"detail\":\"This request must originate from the application.\","
                                        + "\"code\":\"UNTRUSTED_ORIGIN\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
