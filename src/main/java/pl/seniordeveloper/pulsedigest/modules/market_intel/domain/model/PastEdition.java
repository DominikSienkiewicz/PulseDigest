package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * A previously delivered edition, reduced to the stories it carried and the rank each one held.
 * This is the raw material for trend memory ("3rd edition in a row") and the weekly recap.
 */
public record PastEdition(Instant generatedAt, List<PastTopic> topics) {

    public PastEdition {
        topics = topics != null ? List.copyOf(topics) : List.of();
    }

    /** Whether this edition carried the given correlation key. */
    public boolean carries(String topicKey) {
        return topics.stream().anyMatch(t -> topicKey.equals(t.topicKey()));
    }
}
