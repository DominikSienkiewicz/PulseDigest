package pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy;

import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.EolNotice;

import java.time.LocalDate;
import java.util.List;

/**
 * The end-of-life table and how far ahead to warn (N08).
 *
 * <p>{@code validAsOf} is not decoration. A hand-kept table of dates rots, and one that cannot show
 * its own age lets the digest state an EOL with full confidence years after the entry was last
 * checked. The rendered line carries this date so a stale table is visible to the reader rather than
 * only to whoever edited it.
 *
 * <p>The classpath briefing this comment used to point at as the cautionary example,
 * {@code market-knowledge-2026.txt}, was deleted on 2026-10-19 (N01) — not because it was stale but
 * because nothing read it. It had never been loaded into a prompt, and three comments including this
 * one described it as a live hazard.
 */
public record EolPolicy(boolean enabled, int warnWithinMonths, LocalDate validAsOf, List<EolNotice> entries) {

    public EolPolicy {
        entries = entries != null ? List.copyOf(entries) : List.of();
    }
}
