package pl.seniordeveloper.pulsedigest.shared.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "twitter")
public record TwitterProperties(
        String bearerToken,
        List<String> queries,
        List<String> accounts
) {
}
