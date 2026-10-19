package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * What produced this edition (N26).
 *
 * <p>Without it, "the digest got worse in October" is unattributable: the prompt, the model and the
 * items all change over time, and nothing recorded which of them changed when. With it, every stored
 * edition names the exact inputs that shaped it — the same discipline the project already applies to
 * source metadata.
 *
 * <p>The prompt's <em>hash</em> is its version, deliberately. A hand-maintained version number is a
 * thing to forget to bump; a content hash cannot disagree with the content it describes, and two
 * editions produced by different prompts are distinguishable in the database whether or not anyone
 * remembered to say so.
 *
 * @param promptHash     SHA-256 of the system prompt that produced this edition
 * @param model          the model that actually answered — not the one configured
 * @param fallbackUsed   whether the configured model failed and the fallback produced this edition
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EditionProvenance(
        @JsonProperty("prompt_hash") String promptHash,
        String model,
        @JsonProperty("fallback_used") boolean fallbackUsed
) {

    /** The short form that goes in a log line, where a 64-character hash is noise. */
    @JsonIgnore
    public String promptVersion() {
        return promptHash == null || promptHash.length() < 12 ? "unknown" : promptHash.substring(0, 12);
    }
}
