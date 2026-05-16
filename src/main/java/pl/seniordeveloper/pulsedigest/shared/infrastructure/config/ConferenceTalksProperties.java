package pl.seniordeveloper.pulsedigest.shared.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@ConfigurationProperties(prefix = "report.conference-talks")
@Validated
public record ConferenceTalksProperties(
        @NotBlank String baseUrl,
        String apiKey,
        @Min(1) int lookbackDays,
        @Min(1) int maxResults,
        @Valid @NotEmpty List<ChannelConfig> channels
) {

    public record ChannelConfig(
            @NotBlank String channelName,
            @NotBlank String conferenceName,
            @NotBlank String channelId
    ) {
    }
}
