package pl.seniordeveloper.pulsedigest.shared.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Technologies the reader wants explicit coverage of, whether or not anything happened.
 *
 * <p>Every keyword gets a line in the digest — including "0 wzmianek". Half of the job the digest
 * exists to do is "tell me what's new"; the other half is "confirm I missed nothing", and only an
 * explicit zero can do that.
 */
@ConfigurationProperties(prefix = "report.watchlist")
@Validated
public record WatchlistProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue List<String> technologies
) {

    public WatchlistProperties {
        technologies = technologies != null ? List.copyOf(technologies) : List.of();
    }
}
