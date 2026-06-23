package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.ai;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
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
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchResult;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RssItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SecurityAdvisory;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SocialPost;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SoftwareRelease;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Tweet;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.FeedbackPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.PublishedUrlsPort;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.DedupProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.FeedbackProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.InterestProfileProperties;
import pl.seniordeveloper.pulsedigest.shared.util.UrlCanonicalizer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

@Slf4j
@RequiredArgsConstructor
@Component
public class ReportPromptBuilder {

    private static final String KEY_SOURCE = "source";
    private static final String KEY_TITLE = "title";
    private static final String KEY_URL = "url";
    private static final String KEY_ENGAGEMENT = "engagement_score";
    private static final String KEY_TEXT_PREVIEW = "text_preview";

    private final ObjectMapper objectMapper;
    private final PublishedUrlsPort publishedUrlsPort;
    private final DedupProperties dedupProperties;
    private final InterestProfileProperties interestProfile;
    private final FeedbackPort feedbackPort;
    private final FeedbackProperties feedbackProperties;

    @Value("classpath:prompts/system-prompt.txt")
    private Resource systemPromptResource;

    private String systemPrompt;

    @PostConstruct
    void init() throws IOException {
        this.systemPrompt = systemPromptResource.getContentAsString(StandardCharsets.UTF_8);
        log.debug("Loaded system prompt ({} chars) from {}", systemPrompt.length(), systemPromptResource.getFilename());
    }

    public String buildSystemPrompt() {
        // Profil odbiorcy doklejany z interest-profile (jedno źródło prawdy) — rubryka scoringu
        // w system-prompt.txt odsyła do tej sekcji zamiast hardcodować persony.
        return systemPrompt + "\n\n== PROFIL ODBIORCY ==\n" + interestProfile.persona() + "\n";
    }

    public String buildUserPrompt(ResearchResult research) {
        List<Map<String, Object>> all = new ArrayList<>();
        List.of(
                mapItems(research.tweets(), this::mapTweet),
                mapItems(research.hackerNewsPosts(), this::mapHackerNews),
                mapItems(research.githubRepos(), this::mapGithubRepo),
                mapItems(research.rssItems(), this::mapRssItem),
                mapItems(research.redditPosts(), this::mapRedditPost),
                mapItems(research.papers(), this::mapPaper),
                mapItems(research.releases(), this::mapRelease),
                mapItems(research.huggingFaceModels(), this::mapHuggingFaceModel),
                mapItems(research.productHuntPosts(), this::mapProductHuntPost),
                mapItems(research.securityAdvisories(), this::mapSecurityAdvisory),
                mapItems(research.jepUpdates(), this::mapJepUpdate),
                mapItems(research.cncfProjectUpdates(), this::mapCncfProjectUpdate),
                mapItems(research.radarEntries(), this::mapRadarEntry),
                mapItems(research.conferenceTalks(), this::mapConferenceTalk),
                mapItems(research.labAnnouncements(), this::mapLabAnnouncement),
                mapItems(research.socialPosts(), this::mapSocialPost)
        ).forEach(all::addAll);

        all = filterAlreadyPublished(all);
        List<Map<String, Object>> payload = PromptItemSelector.selectTopItems(all);

        try {
            String json = objectMapper.writeValueAsString(payload);
            log.info("Prompt payload: {} itemów wybranych z {} (tweets={}, hn={}, gh={}, rss={}, reddit={},"
                            + " papers={}, releases={}, hf={}, ph={}, advisories={},"
                            + " jep={}, cncf={}, radar={}, talks={}, social={}, labs={})",
                    payload.size(), all.size(),
                    research.tweets().size(),
                    research.hackerNewsPosts().size(),
                    research.githubRepos().size(),
                    research.rssItems().size(),
                    research.redditPosts().size(),
                    research.papers().size(),
                    research.releases().size(),
                    research.huggingFaceModels().size(),
                    research.productHuntPosts().size(),
                    research.securityAdvisories().size(),
                    research.jepUpdates().size(),
                    research.cncfProjectUpdates().size(),
                    research.radarEntries().size(),
                    research.conferenceTalks().size(),
                    research.socialPosts().size(),
                    research.labAnnouncements().size());
            return "Oto posty z ostatnich kilku dni:\n\n" + json;
        } catch (JsonProcessingException e) {
            log.error("Błąd serializacji payloadu: {}", e.getMessage());
            return "Oto posty z ostatnich kilku dni:\n\n[]";
        }
    }

    private static <T> List<Map<String, Object>> mapItems(List<T> items, Function<T, Map<String, Object>> fn) {
        return items.stream().map(fn).toList();
    }

    private Map<String, Object> mapTweet(Tweet tweet) {
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

    private Map<String, Object> mapHackerNews(HackerNewsPost hn) {
        return Map.of(
                KEY_SOURCE, "Hacker News",
                KEY_TITLE, hn.title(),
                KEY_URL, hn.url(),
                KEY_ENGAGEMENT, hn.points(),
                KEY_TEXT_PREVIEW, ""
        );
    }

    private Map<String, Object> mapGithubRepo(GithubRepo repo) {
        String desc = repo.description() != null ? repo.description() : "";
        return Map.of(
                KEY_SOURCE, "GitHub",
                KEY_TITLE, repo.name(),
                KEY_URL, repo.url(),
                KEY_ENGAGEMENT, repo.stars(),
                KEY_TEXT_PREVIEW, desc.substring(0, Math.min(200, desc.length()))
        );
    }

    private Map<String, Object> mapRssItem(RssItem rss) {
        String preview = rss.description() != null ? rss.description() : "";
        return Map.of(
                KEY_SOURCE, "RSS/" + rss.feedName(),
                KEY_TITLE, rss.title(),
                KEY_URL, rss.url(),
                KEY_ENGAGEMENT, 0,
                KEY_TEXT_PREVIEW, preview.substring(0, Math.min(300, preview.length()))
        );
    }

    private Map<String, Object> mapRedditPost(RedditPost reddit) {
        return Map.of(
                KEY_SOURCE, "Reddit/r/" + reddit.subreddit(),
                KEY_TITLE, reddit.title(),
                KEY_URL, reddit.url(),
                KEY_ENGAGEMENT, reddit.score(),
                KEY_TEXT_PREVIEW, ""
        );
    }

    private Map<String, Object> mapPaper(ResearchPaper paper) {
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

    private Map<String, Object> mapRelease(SoftwareRelease release) {
        String releaseExcerpt = release.releaseNotesExcerpt() != null ? release.releaseNotesExcerpt() : "";
        return Map.of(
                KEY_SOURCE, "GitHub Releases",
                KEY_TITLE, release.repoFullName() + " " + release.version(),
                KEY_URL, release.url(),
                KEY_ENGAGEMENT, 0,
                KEY_TEXT_PREVIEW, releaseExcerpt.substring(0, Math.min(300, releaseExcerpt.length()))
        );
    }

    private Map<String, Object> mapHuggingFaceModel(HuggingFaceModel model) {
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

    private Map<String, Object> mapProductHuntPost(ProductHuntPost post) {
        String preview = post.tagline() == null ? "" : post.tagline();
        return Map.of(
                KEY_SOURCE, "Product Hunt",
                KEY_TITLE, post.name(),
                KEY_URL, post.url(),
                KEY_ENGAGEMENT, post.votesCount(),
                KEY_TEXT_PREVIEW, preview.substring(0, Math.min(300, preview.length()))
        );
    }

    private Map<String, Object> mapSecurityAdvisory(SecurityAdvisory advisory) {
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

    private Map<String, Object> mapJepUpdate(JepUpdate jep) {
        String preview = "Status: " + jep.status() + (jep.title() != null ? " · " + jep.title() : "");
        return Map.of(
                KEY_SOURCE, "OpenJDK JEP",
                KEY_TITLE, jep.jepId() + " " + (jep.title() != null ? jep.title() : ""),
                KEY_URL, jep.url(),
                KEY_ENGAGEMENT, jepStatusScore(jep.status()),
                KEY_TEXT_PREVIEW, preview.substring(0, Math.min(300, preview.length()))
        );
    }

    private Map<String, Object> mapCncfProjectUpdate(CncfProjectUpdate cncf) {
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

    private Map<String, Object> mapRadarEntry(RadarEntry radar) {
        String preview = "Ring: " + radar.ring() + " · Quadrant: " + radar.quadrant();
        return Map.of(
                KEY_SOURCE, "Tech Radar",
                KEY_TITLE, radar.name(),
                KEY_URL, radar.url(),
                KEY_ENGAGEMENT, ringScore(radar.ring()),
                KEY_TEXT_PREVIEW, preview.substring(0, Math.min(300, preview.length()))
        );
    }

    private Map<String, Object> mapConferenceTalk(ConferenceTalk talk) {
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

    // Official AI-lab announcements (Anthropic, OpenAI, Google Gemini blogs) get a very
    // high engagement_score so they're guaranteed to survive PromptItemSelector trimming —
    // this is THE highest-signal source for AI model/product news.
    private Map<String, Object> mapLabAnnouncement(LabAnnouncement ann) {
        String preview = ann.source() + (ann.summary().isBlank() ? "" : " · " + ann.summary());
        return Map.of(
                KEY_SOURCE, ann.source(),
                KEY_TITLE, ann.title(),
                KEY_URL, ann.url(),
                KEY_ENGAGEMENT, 1_000_000,
                KEY_TEXT_PREVIEW, preview.substring(0, Math.min(300, preview.length()))
        );
    }

    private Map<String, Object> mapSocialPost(SocialPost post) {
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

    // Drops items whose canonical URL should be suppressed before LLM scoring — cross-edition
    // duplicates (already published in a recent edition) and reader down-votes (feedback "less like
    // this"). Filters the unified payload by URL, so every source is covered uniformly — including
    // tweets, whose URL is synthesized above even though the Tweet record has none.
    private List<Map<String, Object>> filterAlreadyPublished(List<Map<String, Object>> all) {
        Set<String> suppressed = collectSuppressedUrls();
        if (suppressed.isEmpty()) {
            return all;
        }
        List<Map<String, Object>> fresh = all.stream()
                .filter(item -> {
                    Object url = item.get(KEY_URL);
                    return !(url instanceof String s) || !suppressed.contains(UrlCanonicalizer.canonicalize(s));
                })
                .toList();
        int dropped = all.size() - fresh.size();
        if (dropped > 0) {
            log.info("Pominięto {} itemów przed scoringiem (cross-edition dedup + feedback)", dropped);
        }
        return fresh;
    }

    private Set<String> collectSuppressedUrls() {
        Set<String> suppressed = new HashSet<>();
        if (dedupProperties.enabled()) {
            suppressed.addAll(publishedUrlsPort.recentlyPublishedUrls(dedupProperties.lookbackDays()));
        }
        if (feedbackProperties.enabled()) {
            suppressed.addAll(feedbackPort.downvotedUrls(feedbackProperties.lookbackDays()));
        }
        return suppressed;
    }

    /**
     * Synthetic engagement score for advisories so LLM can still rank them by severity.
     */
    private int severityScore(String severity) {
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

    private int jepStatusScore(String status) {
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

    private int cncfStatusScore(String status) {
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

    private int ringScore(String ring) {
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
