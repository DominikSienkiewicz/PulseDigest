package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.ai;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Component
public class ReportPromptBuilder {

    // Per-source caps applied before sending to LLM to reduce noise while keeping diversity
    private static final int CAP_RSS       = 30;
    private static final int CAP_TWITTER   = 20;
    private static final int CAP_REDDIT    = 15;
    private static final int CAP_HN        = 10;
    private static final int CAP_GITHUB    = 10;
    private static final int CAP_ARXIV     = 8;
    private static final int CAP_RELEASES  = 10;
    private static final int TOTAL_CAP     = 80;

    private final ObjectMapper objectMapper;

    @Value("classpath:prompts/system-prompt.txt")
    private Resource systemPromptResource;

    private String systemPrompt;

    @PostConstruct
    void init() throws IOException {
        this.systemPrompt = systemPromptResource.getContentAsString(StandardCharsets.UTF_8);
        log.debug("Loaded system prompt ({} chars) from {}", systemPrompt.length(), systemPromptResource.getFilename());
    }

    public String buildSystemPrompt() {
        return systemPrompt;
    }

    public String buildUserPrompt(ResearchResult research) {
        List<Map<String, Object>> all = new ArrayList<>();

        for (var tweet : research.tweets()) {
            String url = "https://x.com/" + tweet.authorUsername() + "/status/" + tweet.id();
            String title = tweet.text().length() > 120
                    ? tweet.text().substring(0, 120).replace("\n", " ")
                    : tweet.text().replace("\n", " ");
            all.add(Map.of(
                    "source", "Twitter/X",
                    "title", title,
                    "url", url,
                    "engagement_score", tweet.likeCount(),
                    "text_preview", tweet.text().substring(0, Math.min(300, tweet.text().length()))
            ));
        }

        for (var hn : research.hackerNewsPosts()) {
            all.add(Map.of(
                    "source", "Hacker News",
                    "title", hn.title(),
                    "url", hn.url(),
                    "engagement_score", hn.points(),
                    "text_preview", ""
            ));
        }

        for (var repo : research.githubRepos()) {
            String desc = repo.description() != null ? repo.description() : "";
            all.add(Map.of(
                    "source", "GitHub",
                    "title", repo.name(),
                    "url", repo.url(),
                    "engagement_score", repo.stars(),
                    "text_preview", desc.substring(0, Math.min(200, desc.length()))
            ));
        }

        for (var rss : research.rssItems()) {
            String preview = rss.description() != null ? rss.description() : "";
            all.add(Map.of(
                    "source", "RSS/" + rss.feedName(),
                    "title", rss.title(),
                    "url", rss.url(),
                    "engagement_score", 0,
                    "text_preview", preview.substring(0, Math.min(300, preview.length()))
            ));
        }

        for (var reddit : research.redditPosts()) {
            all.add(Map.of(
                    "source", "Reddit/r/" + reddit.subreddit(),
                    "title", reddit.title(),
                    "url", reddit.url(),
                    "engagement_score", reddit.score(),
                    "text_preview", ""
            ));
        }

        for (var paper : research.papers()) {
            String authorsStr = paper.authors().isEmpty() ? "Unknown"
                    : String.join(", ", paper.authors().subList(0, Math.min(3, paper.authors().size())));
            all.add(Map.of(
                    "source", "arXiv/" + paper.primaryCategory(),
                    "title", paper.title(),
                    "url", paper.url(),
                    "engagement_score", 0,
                    "text_preview", paper.abstractText().substring(0, Math.min(300, paper.abstractText().length())),
                    "authors", authorsStr
            ));
        }

        for (var release : research.releases()) {
            String releaseExcerpt = release.releaseNotesExcerpt() != null ? release.releaseNotesExcerpt() : "";
            all.add(Map.of(
                    "source", "GitHub Releases",
                    "title", release.repoFullName() + " " + release.version(),
                    "url", release.url(),
                    "engagement_score", 0,
                    "text_preview", releaseExcerpt.substring(0, Math.min(300, releaseExcerpt.length()))
            ));
        }

        List<Map<String, Object>> payload = selectTopItems(all);

        try {
            String json = objectMapper.writeValueAsString(payload);
            log.info("Prompt payload: {} itemów wybranych z {} (tweets={}, hn={}, gh={}, rss={}, reddit={}, papers={}, releases={})",
                    payload.size(), all.size(),
                    research.tweets().size(),
                    research.hackerNewsPosts().size(),
                    research.githubRepos().size(),
                    research.rssItems().size(),
                    research.redditPosts().size(),
                    research.papers().size(),
                    research.releases().size());
            return "Oto posty z ostatnich 24 godzin:\n\n" + json;
        } catch (JsonProcessingException e) {
            log.error("Błąd serializacji payloadu: {}", e.getMessage());
            return "Oto posty z ostatnich 24 godzin:\n\n[]";
        }
    }

    /**
     * Selects the best items per source (by engagement desc), then caps the total at TOTAL_CAP.
     * Prevents a single noisy source (e.g. 130 RSS items) from flooding the LLM prompt.
     */
    private List<Map<String, Object>> selectTopItems(List<Map<String, Object>> all) {
        List<Map<String, Object>> twitter   = new ArrayList<>();
        List<Map<String, Object>> hn        = new ArrayList<>();
        List<Map<String, Object>> github    = new ArrayList<>();
        List<Map<String, Object>> rss       = new ArrayList<>();
        List<Map<String, Object>> reddit    = new ArrayList<>();
        List<Map<String, Object>> arxiv     = new ArrayList<>();
        List<Map<String, Object>> releases  = new ArrayList<>();

        for (var item : all) {
            String src = (String) item.get("source");
            if (src.startsWith("Twitter"))          twitter.add(item);
            else if (src.startsWith("Hacker News")) hn.add(item);
            else if (src.equals("GitHub"))          github.add(item);
            else if (src.startsWith("RSS"))         rss.add(item);
            else if (src.startsWith("Reddit"))      reddit.add(item);
            else if (src.startsWith("arXiv"))       arxiv.add(item);
            else if (src.equals("GitHub Releases")) releases.add(item);
        }

        Comparator<Map<String, Object>> byEngagement =
                Comparator.comparingInt(m -> -((Number) m.get("engagement_score")).intValue());

        List<Map<String, Object>> selected = new ArrayList<>();
        selected.addAll(topN(twitter,  CAP_TWITTER,  byEngagement));
        selected.addAll(topN(hn,       CAP_HN,       byEngagement));
        selected.addAll(topN(github,   CAP_GITHUB,   byEngagement));
        selected.addAll(topN(rss,      CAP_RSS,      byEngagement));
        selected.addAll(topN(reddit,   CAP_REDDIT,   byEngagement));
        selected.addAll(topN(arxiv,    CAP_ARXIV,    byEngagement));
        selected.addAll(topN(releases, CAP_RELEASES, byEngagement));

        if (selected.size() > TOTAL_CAP) {
            selected.sort(byEngagement);
            selected = selected.subList(0, TOTAL_CAP);
        }

        return selected;
    }

    private List<Map<String, Object>> topN(
            List<Map<String, Object>> items,
            int n,
            Comparator<Map<String, Object>> comparator) {
        return items.stream()
                .sorted(comparator)
                .limit(n)
                .toList();
    }
}
