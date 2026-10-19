package pl.seniordeveloper.pulsedigest.domain;

import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SavedItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.FeedbackPort;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** A feedback port that only knows which URLs were up-voted — all most tests need of it. */
public record TestFeedback(Set<String> upvoted) implements FeedbackPort {

    @Override
    public Set<String> downvotedUrls(int lookbackDays) {
        return Set.of();
    }

    @Override
    public Set<String> upvotedUrls(int lookbackDays) {
        return upvoted;
    }

    @Override
    public List<SavedItem> savedItems(int lookbackDays) {
        return List.of();
    }

    @Override
    public Map<String, Integer> netVotesBySource(int lookbackDays) {
        return Map.of();
    }

    @Override
    public Map<String, Integer> netVotesByCategory(int lookbackDays) {
        return Map.of();
    }
}
