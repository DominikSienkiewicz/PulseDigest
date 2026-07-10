package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

/**
 * One scored story as it appeared in an already-published edition, read back from report history.
 */
public record PastTopic(String topicKey, String title, String url, SignalRank rank, String source) {

    /** Convenience constructor for callers that do not care which source carried the story. */
    public PastTopic(String topicKey, String title, String url, SignalRank rank) {
        this(topicKey, title, url, rank, null);
    }
}
