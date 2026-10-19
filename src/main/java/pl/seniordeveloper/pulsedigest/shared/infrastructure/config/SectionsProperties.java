package pl.seniordeveloper.pulsedigest.shared.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Sections of the digest switched off without touching code (N19).
 *
 * <p>Empty by default. An entry here is an experiment — the audit could not determine which sections
 * are ever read, and turning one off for a few editions is the cheapest way to find out.
 */
@ConfigurationProperties(prefix = "report.sections")
@Validated
public record SectionsProperties(@DefaultValue List<String> disabled) {

    public SectionsProperties {
        disabled = disabled != null ? List.copyOf(disabled) : List.of();
    }
}
