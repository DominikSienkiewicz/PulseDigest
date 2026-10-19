package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

/**
 * Reprezentuje pojedynczy tweet po filtrowaniu.
 * Carries the author, the text and the engagement metrics.
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
     * The tweet as the prompt renders it.
     */
    public String toPromptString() {
        return "@%s (%s) [%d likes, %d RT] – %s – \"%s\""
                .formatted(authorUsername, authorName, likeCount, retweetCount, createdAt, text);
    }
}
