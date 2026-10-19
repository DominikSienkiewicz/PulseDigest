package pl.seniordeveloper.pulsedigest.shared.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Sources switched off by hand (N27).
 *
 * <p>Empty by default, and expected to stay empty: an entry here is a decision that a source is not
 * worth the fetch, taken on the evidence the run already prints.
 */
@ConfigurationProperties(prefix = "report.sources")
@Validated
public record SourcesProperties(@DefaultValue List<String> disabled) {

    public SourcesProperties {
        disabled = disabled != null ? List.copyOf(disabled) : List.of();
    }
}
