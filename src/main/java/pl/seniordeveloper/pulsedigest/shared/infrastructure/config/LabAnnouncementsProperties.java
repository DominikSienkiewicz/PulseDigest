package pl.seniordeveloper.pulsedigest.shared.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Configuration for the AI-lab announcement scraper (Anthropic, OpenAI, Google Gemini).
 * Each source declares a scrape {@link Strategy} matching how that site exposes publish dates.
 */
@ConfigurationProperties(prefix = "report.lab-announcements")
@Validated
public record LabAnnouncementsProperties(
        @Min(1) int lookbackHours,
        @Min(1) int postFetchLimit,
        @Valid @NotEmpty List<Source> sources
) {

    /**
     * How a site exposes article metadata.
     *
     * <ul>
     *   <li>{@code SANITY} — Sanity CMS data embedded inline in the listing page
     *       ({@code publishedOn} + slug + title + summary). One fetch. (anthropic.com)</li>
     *   <li>{@code JSONLD} — listing has post links but no dates; each post page carries
     *       {@code datePublished} in JSON-LD. Listing + per-post fetch. (claude.com/blog, blog.google)</li>
     *   <li>{@code OPENAI_DEV} — listing renders cards with a short date ("Feb 4"), title and
     *       description inline. One fetch. (developers.openai.com/blog)</li>
     * </ul>
     */
    public enum Strategy {
        SANITY,
        JSONLD,
        OPENAI_DEV
    }

    public record Source(
            @NotBlank String name,
            @NotNull Strategy strategy,
            @NotBlank String listingUrl,
            /** Regex with one capture group yielding an article URL. Required only for JSONLD. */
            String articleLinkRegex
    ) {
    }
}
