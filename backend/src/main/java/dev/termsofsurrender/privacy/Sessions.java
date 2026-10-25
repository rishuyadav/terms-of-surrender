package dev.termsofsurrender.privacy;

import dev.termsofsurrender.AppProperties;
import dev.termsofsurrender.api.ApiException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class Sessions {
    private static final String COOKIE = "tos_session";
    private final SecureRandom random = new SecureRandom();
    private final AppProperties properties;

    public Sessions(AppProperties properties) {
        this.properties = properties;
    }

    public String establish(HttpServletRequest request, HttpServletResponse response) {
        String token = read(request);
        if (token == null) {
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        }
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(COOKIE, token)
                .httpOnly(true).secure(properties.secureCookies()).sameSite("Lax").path("/api")
                .maxAge(Duration.ofHours(properties.retentionHours())).build().toString());
        return hash(token);
    }

    public String require(HttpServletRequest request) {
        String token = read(request);
        if (token == null) {
            throw ApiException.notFound();
        }
        return hash(token);
    }

    private String read(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (COOKIE.equals(cookie.getName()) && cookie.getValue().matches("[A-Za-z0-9_-]{43}")) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    public static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }
}
