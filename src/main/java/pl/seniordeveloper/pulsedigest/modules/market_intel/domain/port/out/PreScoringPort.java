package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out;

import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PreScoringCandidate;

import java.util.List;
import java.util.Map;

/**
 * Cheap triage of prompt candidates before the expensive scoring call.
 *
 * <p>The main model reads ~100 items and picks 5–10 of them, paying full rate to read the noise.
 * A triage pass over titles alone halves the payload for a fraction of a cent.
 */
public interface PreScoringPort {

    /**
     * Relevance 0–10 per candidate URL. Implementations degrade to an empty map on any failure or
     * missing credentials — an empty map means "no opinion", and the caller passes everything
     * through rather than dropping items it was merely unable to score.
     */
    Map<String, Integer> score(List<PreScoringCandidate> candidates);
}
