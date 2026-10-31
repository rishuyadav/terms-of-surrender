package dev.termsofsurrender.privacy;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Production opt-in; local AI development remains unrestricted by default. */
@Validated
@ConfigurationProperties(prefix = "app.ai-demo")
public record AiDemoProperties(
        boolean enabled,
        String proxySecret,
        @Min(1) int analysesPerIp,
        @Min(1) int questionsPerIp,
        @Min(1) @Max(80000) int maxCharacters,
        @Min(800) @Max(8000) int maxCompletionTokens,
        Set<@Pattern(regexp = "[a-f0-9]{64}", message = "must be a lowercase SHA-256 HMAC") String>
                exemptVisitorHashes) {
    public AiDemoProperties {
        exemptVisitorHashes =
                exemptVisitorHashes == null ? Set.of() : Set.copyOf(exemptVisitorHashes);
    }

    @AssertTrue(
            message =
                    "AI_DEMO_PROXY_SECRET must contain at least 32 characters when demo quotas are enabled")
    public boolean isSecretConfigured() {
        return !enabled || (proxySecret != null && proxySecret.length() >= 32);
    }
}
