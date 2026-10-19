package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * An item the reader asked to keep, from the mail's third action link (N05).
 *
 * <p>The {@code feedback} table stores no title — the receiver only ever sees a URL — so the title
 * is recovered from the stored edition the item was saved out of and is null until then. Rendering
 * a bare URL is the fallback, not the intent: a reminder the reader cannot recognise at a glance is
 * one they will skip.
 *
 * @param title    headline from the edition this was saved out of; null when that edition has aged
 *                 out of the history window
 * @param url      canonical item URL, the only identifier the feedback row carries
 * @param source   source label as the mail rendered it
 * @param category item category, or null for rows written before the receiver sent one
 * @param savedAt  when the reader clicked "zapisz"
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SavedItem(
        String title,
        String url,
        String source,
        String category,
        @JsonProperty("saved_at") Instant savedAt
) {

    /** The same item once its headline has been recovered from a stored edition. */
    public SavedItem withTitle(String resolved) {
        return new SavedItem(resolved, url, source, category, savedAt);
    }

    /**
     * What to show the reader: the headline when it is known, the URL when it is not — without its
     * scheme, which is four characters of noise in a line the reader is scanning for a subject.
     */
    @JsonIgnore
    public String label() {
        if (title != null && !title.isBlank()) {
            return title;
        }
        if (url == null) {
            return "";
        }
        return url.replaceFirst("^https?://", "");
    }

    /** Whole days this has been sitting on the shelf. */
    @JsonIgnore
    public long daysWaiting(LocalDate today) {
        if (savedAt == null) {
            return 0;
        }
        return ChronoUnit.DAYS.between(savedAt.atZone(ZoneOffset.UTC).toLocalDate(), today);
    }
}
