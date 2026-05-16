package pl.seniordeveloper.pulsedigest.shared.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@ConfigurationProperties(prefix = "report.rss")
@Validated
public record RssProperties(
        @Min(1) int limit,
        @Valid @NotEmpty List<FeedConfig> feeds
) {

    public record FeedConfig(@NotBlank String name, @NotBlank String url) {
    }
}
