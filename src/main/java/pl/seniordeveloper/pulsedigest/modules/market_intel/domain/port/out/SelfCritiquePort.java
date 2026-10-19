package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out;

import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.CritiqueVerdict;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;

import java.util.List;

/** Asks a second, cheaper model which items a senior engineer would find useless (N23). */
public interface SelfCritiquePort {

    /**
     * @return what it would cut, or {@link CritiqueVerdict#NOTHING} when the call fails — a critique
     *         that could not run must never be read as "nothing to cut"
     */
    CritiqueVerdict critique(List<DigestItem> items);
}
