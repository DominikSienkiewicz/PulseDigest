package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

/**
 * Reprezentuje pojedynczy tweet po filtrowaniu.
 * Zawiera dane autora, treść oraz metryki zaangażowania.
 */
public record Tweet(
        String id,
        String text,
        String authorUsername,
        String authorName,
        String createdAt,
        int likeCount,
        int retweetCount,
        int replyCount
) {

    /**
     * Zwraca sformatowaną reprezentację tweeta do wstawienia w prompt LLM.
     */
    public String toPromptString() {
        return "@%s (%s) [%d likes, %d RT] – %s – \"%s\""
                .formatted(authorUsername, authorName, likeCount, retweetCount, createdAt, text);
    }
}
