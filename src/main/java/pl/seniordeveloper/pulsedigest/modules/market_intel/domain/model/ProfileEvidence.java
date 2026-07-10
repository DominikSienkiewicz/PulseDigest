package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import java.util.List;
import java.util.Map;

/**
 * Everything the distiller is allowed to reason from: what the reader actually clicked, and the
 * headlines behind the clicks it disliked.
 *
 * <p>Deliberately narrow. A model handed the whole archive would invent patterns; handed only the
 * votes, it can at worst mis-summarise them — and every hypothesis it emits must cite them.
 */
public record ProfileEvidence(
        Map<String, Integer> netVotesByCategory,
        Map<String, Integer> netVotesBySource,
        List<String> dislikedTitles
) {

    public ProfileEvidence {
        netVotesByCategory = netVotesByCategory != null ? Map.copyOf(netVotesByCategory) : Map.of();
        netVotesBySource = netVotesBySource != null ? Map.copyOf(netVotesBySource) : Map.of();
        dislikedTitles = dislikedTitles != null ? List.copyOf(dislikedTitles) : List.of();
    }

    /** Total clicks behind this evidence, counting an up-vote and a down-vote alike. */
    public int totalVotes() {
        return absSum(netVotesByCategory) + absSum(netVotesBySource);
    }

    private static int absSum(Map<String, Integer> votes) {
        return votes.values().stream().mapToInt(Math::abs).sum();
    }
}
