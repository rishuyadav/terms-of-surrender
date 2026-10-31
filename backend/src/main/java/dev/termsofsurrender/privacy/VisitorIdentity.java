package dev.termsofsurrender.privacy;

import dev.termsofsurrender.api.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/** Only the server-side Vercel proxy can attest to the original visitor IP. */
@Component
public class VisitorIdentity {
    private final AiDemoProperties properties;

    public VisitorIdentity(AiDemoProperties properties) {
        this.properties = properties;
    }

    public record Verified(String visitorHash, String nonce, Instant expiresAt) {}

    public Optional<Verified> verify(HttpServletRequest request, String sessionHash) {
        if (!properties.enabled()) {
            return Optional.empty();
        }
        String ip = request.getHeader("X-Tos-Visitor-Ip");
        String timestamp = request.getHeader("X-Tos-Visitor-Time");
        String nonce = request.getHeader("X-Tos-Visitor-Nonce");
        String signature = request.getHeader("X-Tos-Visitor-Signature");
        String key = request.getHeader("Idempotency-Key");
        if (ip == null || timestamp == null || nonce == null || signature == null || key == null) {
            return Optional.empty();
        }
        try {
            if (!nonce.matches("[a-f0-9-]{36}")
                    || !key.matches("[a-f0-9-]{36}")
                    || !signature.matches("[a-f0-9]{64}")
                    || !timestamp.matches("[0-9]{1,12}")) {
                return Optional.empty();
            }
            long seconds = Long.parseLong(timestamp);
            long now = Instant.now().getEpochSecond();
            if (seconds < now - 120 || seconds > now + 30) {
                return Optional.empty();
            }
            String payload =
                    String.join(
                            "\n",
                            "tos-v1",
                            request.getMethod(),
                            request.getRequestURI(),
                            sessionHash,
                            ip,
                            timestamp,
                            nonce,
                            key);
            if (!MessageDigest.isEqual(
                    HexFormat.of().parseHex(signature),
                    HexFormat.of().parseHex(hmac(properties.proxySecret(), payload)))) {
                return Optional.empty();
            }
            return Optional.of(
                    new Verified(
                            hmac(properties.proxySecret(), "visitor\n" + normalize(ip)),
                            nonce,
                            Instant.ofEpochSecond(seconds + 120)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public static String idempotencyKey(HttpServletRequest request) {
        String key = request.getHeader("Idempotency-Key");
        if (key != null && !key.matches("[a-f0-9-]{36}")) {
            throw ApiException.invalid("INVALID_REQUEST_KEY", "The request identifier is invalid.");
        }
        return key;
    }

    static String normalize(String ip) {
        try {
            // Accept literals only: never resolve an attacker-controlled hostname.
            if (ip.length() > 45
                    || !(ip.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")
                            || (ip.contains(":") && ip.matches("[a-fA-F0-9:.]+")))) {
                throw new IllegalArgumentException("Invalid IP literal");
            }
            byte[] address = InetAddress.getByName(ip).getAddress();
            if (address.length == 16) {
                // IPv6 privacy-address rotation within one network must not renew the allowance.
                return HexFormat.of().formatHex(address, 0, 8) + "/64";
            }
            return HexFormat.of().formatHex(address);
        } catch (java.net.UnknownHostException exception) {
            throw new IllegalArgumentException("Invalid IP literal");
        }
    }

    static String hmac(String secret, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is required by the runtime", exception);
        }
    }
}
