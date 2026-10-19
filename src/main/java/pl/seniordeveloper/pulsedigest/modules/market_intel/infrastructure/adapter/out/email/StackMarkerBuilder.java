package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

/**
 * The "this one is in your build file" marker (N06).
 *
 * <p>Deliberately a separate, loud badge rather than a nuance in the summary text: the whole reason
 * to match advisories against the reader's own coordinates is that one of these demands action today
 * and the other is industry news, and a difference that matters that much should not need reading
 * the sentence to notice.
 */
final class StackMarkerBuilder {

    private StackMarkerBuilder() {
    }

    static String buildStackMarker(DigestStory story) {
        if (story == null || !story.affectsStack()) {
            return "";
        }
        return " <span style=\"background:#fef2f2;color:#b91c1c;border:1px solid #fecaca;"
                + "border-radius:4px;padding:1px 5px;font-size:10px;font-weight:700;"
                + "white-space:nowrap\">&#9888;&#65039; W TWOIM STOSIE</span>";
    }
}
