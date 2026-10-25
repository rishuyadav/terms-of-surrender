package dev.termsofsurrender;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotEmpty List<String> allowedOrigins,
        boolean secureCookies,
        @Min(1) @Max(168) int retentionHours,
        @Min(1) int maxActiveJobsPerSession,
        @Min(1) int maxJobsPerSessionPerHour,
        @Min(1) int maxPendingJobs,
        boolean workerEnabled,
        @Valid Model model) {
    public record Model(boolean enabled, String baseUrl, String apiKey, String name) {}
}
