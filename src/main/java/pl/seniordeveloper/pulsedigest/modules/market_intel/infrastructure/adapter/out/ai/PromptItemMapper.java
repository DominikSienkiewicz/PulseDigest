package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.ai;

import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.CncfProjectUpdate;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ConferenceTalk;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.GithubRepo;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.HackerNewsPost;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.HuggingFaceModel;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.JepUpdate;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.LabAnnouncement;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ProductHuntPost;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RadarEntry;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RedditPost;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchPaper;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RssItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SecurityAdvisory;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SocialPost;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SoftwareRelease;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Tweet;

import java.util.Map;

/**
 * Flattens each source's domain record into the uniform {@code {source,title,url,engagement_score,
 * text_preview}} shape the LLM prompt speaks.
 *
 * <p>Extracted from {@code ReportPromptBuilder} so that class stays about prompt assembly — and under
 * the 500-line file budget — rather than about sixteen per-source field mappings.
 */
final class PromptItemMapper {

    private static final String KEY_SOURCE = "source";
    private static final String KEY_TITLE = "title";
    private static final String KEY_URL = "url";
    private static final String KEY_ENGAGEMENT = "engagement_score";
    private static final String KEY_TEXT_PREVIEW = "text_preview";

    private PromptItemMapper() {
    }

    static Map<String, Object> mapTweet(Tweet tweet) {
        String url = "https://x.com/" + tweet.authorUsername() + "/status/" + tweet.id();
        String title = tweet.text().length() > 120
                ? tweet.text().substring(0, 120).replace("\n", " ")
                : tweet.text().replace("\n", " ");
        return Map.of(
                KEY_SOURCE, "Twitter/X",
                KEY_TITLE, title,
                KEY_URL, url,
                KEY_ENGAGEMENT, tweet.likeCount(),
                KEY_TEXT_PREVIEW, tweet.text().substring(0, Math.min(300, tweet.text().length()))
        );
    }

    static Map<String, Object> mapHackerNews(HackerNewsPost hn) {
        return Map.of(
                KEY_SOURCE, "Hacker News",
                KEY_TITLE, hn.title(),
                KEY_URL, hn.url(),
                KEY_ENGAGEMENT, hn.points(),
                KEY_TEXT_PREVIEW, ""
        );
    }

    static Map<String, Object> mapGithubRepo(GithubRepo repo) {
        String desc = repo.description() != null ? repo.description() : "";
        return Map.of(
                KEY_SOURCE, "GitHub",
                KEY_TITLE, repo.name(),
                KEY_URL, repo.url(),
                KEY_ENGAGEMENT, repo.stars(),
                KEY_TEXT_PREVIEW, desc.substring(0, Math.min(200, desc.length()))
        );
    }

    static Map<String, Object> mapRssItem(RssItem rss) {
        String preview = rss.description() != null ? rss.description() : "";
        return Map.of(
                KEY_SOURCE, "RSS/" + rss.feedName(),
                KEY_TITLE, rss.title(),
                KEY_URL, rss.url(),
                KEY_ENGAGEMENT, 0,
                KEY_TEXT_PREVIEW, preview.substring(0, Math.min(300, preview.length()))
        );
    }

    static Map<String, Object> mapRedditPost(RedditPost reddit) {
        return Map.of(
                KEY_SOURCE, "Reddit/r/" + reddit.subreddit(),
                KEY_TITLE, reddit.title(),
                KEY_URL, reddit.url(),
                KEY_ENGAGEMENT, reddit.score(),
                KEY_TEXT_PREVIEW, ""
        );
    }

    static Map<String, Object> mapPaper(ResearchPaper paper) {
        String authorsStr = paper.authors().isEmpty() ? "Unknown"
                : String.join(", ", paper.authors().subList(0, Math.min(3, paper.authors().size())));
        return Map.of(
                KEY_SOURCE, "arXiv/" + paper.primaryCategory(),
                KEY_TITLE, paper.title(),
                KEY_URL, paper.url(),
                KEY_ENGAGEMENT, 0,
                KEY_TEXT_PREVIEW, paper.abstractText().substring(0, Math.min(300, paper.abstractText().length())),
                "authors", authorsStr
        );
    }

    static Map<String, Object> mapRelease(SoftwareRelease release) {
        String releaseExcerpt = release.releaseNotesExcerpt() != null ? release.releaseNotesExcerpt() : "";
        return Map.of(
                KEY_SOURCE, "GitHub Releases",
                KEY_TITLE, release.repoFullName() + " " + release.version(),
                KEY_URL, release.url(),
                KEY_ENGAGEMENT, 0,
                KEY_TEXT_PREVIEW, releaseExcerpt.substring(0, Math.min(300, releaseExcerpt.length()))
        );
    }

    static Map<String, Object> mapHuggingFaceModel(HuggingFaceModel model) {
        String preview = "Pipeline: " + (model.pipelineTag() == null ? "n/a" : model.pipelineTag())
                + " · " + model.downloads() + " downloads";
        return Map.of(
                KEY_SOURCE, "Hugging Face",
                KEY_TITLE, model.id(),
                KEY_URL, model.url(),
                KEY_ENGAGEMENT, (int) Math.min(Integer.MAX_VALUE, model.likes()),
                KEY_TEXT_PREVIEW, preview
        );
    }

    static Map<String, Object> mapProductHuntPost(ProductHuntPost post) {
        String preview = post.tagline() == null ? "" : post.tagline();
        return Map.of(
                KEY_SOURCE, "Product Hunt",
                KEY_TITLE, post.name(),
                KEY_URL, post.url(),
                KEY_ENGAGEMENT, post.votesCount(),
                KEY_TEXT_PREVIEW, preview.substring(0, Math.min(300, preview.length()))
        );
    }

    static Map<String, Object> mapSecurityAdvisory(SecurityAdvisory advisory) {
        String preview = (advisory.summary() == null ? "" : advisory.summary())
                + " · severity=" + advisory.severity()
                + (advisory.affectedEcosystems().isEmpty()
                        ? "" : " · ecosystems=" + String.join(",", advisory.affectedEcosystems()));
        return Map.of(
                KEY_SOURCE, "Security Advisories",
                KEY_TITLE, advisory.ghsaId() + " " + (advisory.summary() == null ? "" : advisory.summary()),
                KEY_URL, advisory.url(),
                KEY_ENGAGEMENT, severityScore(advisory.severity()),
                KEY_TEXT_PREVIEW, preview.substring(0, Math.min(300, preview.length()))
        );
    }

    static Map<String, Object> mapJepUpdate(JepUpdate jep) {
        String preview = "Status: " + jep.status() + (jep.title() != null ? " · " + jep.title() : "");
        return Map.of(
                KEY_SOURCE, "OpenJDK JEP",
                KEY_TITLE, jep.jepId() + " " + (jep.title() != null ? jep.title() : ""),
                KEY_URL, jep.url(),
                KEY_ENGAGEMENT, jepStatusScore(jep.status()),
                KEY_TEXT_PREVIEW, preview.substring(0, Math.min(300, preview.length()))
        );
    }

    static Map<String, Object> mapCncfProjectUpdate(CncfProjectUpdate cncf) {
        String preview = "Status: " + cncf.status()
                + (cncf.category() != null ? " · Category: " + cncf.category() : "");
        return Map.of(
                KEY_SOURCE, "CNCF Landscape",
                KEY_TITLE, cncf.projectName(),
                KEY_URL, cncf.url(),
                KEY_ENGAGEMENT, cncfStatusScore(cncf.status()),
                KEY_TEXT_PREVIEW, preview.substring(0, Math.min(300, preview.length()))
        );
    }

    static Map<String, Object> mapRadarEntry(RadarEntry radar) {
        String preview = "Ring: " + radar.ring() + " · Quadrant: " + radar.quadrant();
        return Map.of(
                KEY_SOURCE, "Tech Radar",
                KEY_TITLE, radar.name(),
                KEY_URL, radar.url(),
                KEY_ENGAGEMENT, ringScore(radar.ring()),
                KEY_TEXT_PREVIEW, preview.substring(0, Math.min(300, preview.length()))
        );
    }

    static Map<String, Object> mapConferenceTalk(ConferenceTalk talk) {
        String preview = talk.conferenceName() + " · " + talk.channelName()
                + " · " + talk.viewCount() + " views";
        return Map.of(
                KEY_SOURCE, "YouTube/" + talk.conferenceName(),
                KEY_TITLE, talk.title(),
                KEY_URL, talk.url(),
                KEY_ENGAGEMENT, (int) Math.min(Integer.MAX_VALUE, talk.viewCount()),
                KEY_TEXT_PREVIEW, preview.substring(0, Math.min(300, preview.length()))
        );
    }

    // Official AI-lab announcements (Anthropic, OpenAI, Google Gemini blogs) carry no public
    // engagement metric. A nominal 10k stands in for "widely read" without faking the +50 engagement
    // bonus outright; survival through trimming comes from the honest 0.95 source weight instead.
    static Map<String, Object> mapLabAnnouncement(LabAnnouncement ann) {
        String preview = ann.source() + (ann.summary().isBlank() ? "" : " · " + ann.summary());
        return Map.of(
                KEY_SOURCE, ann.source(),
                KEY_TITLE, ann.title(),
                KEY_URL, ann.url(),
                KEY_ENGAGEMENT, 10_000,
                KEY_TEXT_PREVIEW, preview.substring(0, Math.min(300, preview.length()))
        );
    }

    static Map<String, Object> mapSocialPost(SocialPost post) {
        String text = post.text() != null ? post.text() : "";
        String title = text.length() > 120
                ? text.substring(0, 120).replace("\n", " ")
                : text.replace("\n", " ");
        return Map.of(
                KEY_SOURCE, post.network(),
                KEY_TITLE, title,
                KEY_URL, post.url(),
                KEY_ENGAGEMENT, post.likeCount(),
                KEY_TEXT_PREVIEW, text.substring(0, Math.min(300, text.length()))
        );
    }

    /**
     * Synthetic engagement score for advisories so LLM can still rank them by severity.
     */
    private static int severityScore(String severity) {
        if (severity == null) {
            return 0;
        }
        return switch (severity.toUpperCase()) {
            case "CRITICAL" -> 1000;
            case "HIGH" -> 500;
            case "MEDIUM" -> 100;
            case "LOW" -> 10;
            default -> 0;
        };
    }

    private static int jepStatusScore(String status) {
        if (status == null) {
            return 0;
        }
        return switch (status.toLowerCase()) {
            case "delivered", "integrated" -> 300;
            case "proposed to target" -> 200;
            case "candidate" -> 100;
            default -> 0;
        };
    }

    private static int cncfStatusScore(String status) {
        if (status == null) {
            return 0;
        }
        return switch (status.toLowerCase()) {
            case "graduated" -> 300;
            case "incubating" -> 200;
            case "sandbox" -> 100;
            default -> 0;
        };
    }

    private static int ringScore(String ring) {
        if (ring == null) {
            return 0;
        }
        return switch (ring.toLowerCase()) {
            case "adopt" -> 1000;
            case "trial" -> 500;
            case "assess" -> 100;
            case "hold" -> 10;
            default -> 0;
        };
    }
}
