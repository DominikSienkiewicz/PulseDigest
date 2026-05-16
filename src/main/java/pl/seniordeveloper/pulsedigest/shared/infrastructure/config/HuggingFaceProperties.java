package pl.seniordeveloper.pulsedigest.shared.infrastructure.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@ConfigurationProperties(prefix = "report.hugging-face")
@Validated
public record HuggingFaceProperties(
        @NotBlank String baseUrl,
        @Min(1) int limit,
        @Min(0) long minLikes,
        @Min(0) long minDownloads,
        @NotEmpty List<@NotBlank String> relevantPipelines
) {
}
