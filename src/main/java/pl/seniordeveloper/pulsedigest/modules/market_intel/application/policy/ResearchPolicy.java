package pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy;

import java.util.List;

/**
 * Narrow policy for tweet research filtering. Owned by the application layer so that
 * {@code MarketResearchService} does not depend on infrastructure-bound configuration types.
 *
 * <p>Wired in {@code market_intel/infrastructure/config} from the bound {@code ResearchProperties}
 * and {@code InterestProfileProperties} (the relevance keywords come from the interest profile).
 */
public record ResearchPolicy(int minLikes, int daysBack, List<String> authorityUsernames,
                             List<String> relevanceKeywords) {

    public ResearchPolicy {
        authorityUsernames = List.copyOf(authorityUsernames);
        relevanceKeywords = List.copyOf(relevanceKeywords);
    }
}
