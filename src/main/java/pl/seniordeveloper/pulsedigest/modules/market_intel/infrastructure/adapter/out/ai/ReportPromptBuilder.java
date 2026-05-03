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
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Component
public class ReportPromptBuilder {

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
        List<Map<String, Object>> payload = new ArrayList<>();

        for (var tweet : research.tweets()) {
            String url = "https://x.com/" + tweet.authorUsername() + "/status/" + tweet.id();
            String title = tweet.text().length() > 120
                    ? tweet.text().substring(0, 120).replace("\n", " ")
                    : tweet.text().replace("\n", " ");
            payload.add(Map.of(
                    "source", "Twitter/X",
                    "title", title,
                    "url", url,
                    "engagement_score", tweet.likeCount(),
                    "text_preview", tweet.text().substring(0, Math.min(300, tweet.text().length()))
            ));
        }

        for (var hn : research.hackerNewsPosts()) {
            payload.add(Map.of(
                    "source", "Hacker News",
                    "title", hn.title(),
                    "url", hn.url(),
                    "engagement_score", hn.points(),
                    "text_preview", ""
            ));
        }

        for (var repo : research.githubRepos()) {
            String desc = repo.description() != null ? repo.description() : "";
            payload.add(Map.of(
                    "source", "GitHub",
                    "title", repo.name(),
                    "url", repo.url(),
                    "engagement_score", repo.stars(),
                    "text_preview", desc.substring(0, Math.min(200, desc.length()))
            ));
        }

        for (var rss : research.rssItems()) {
            String preview = rss.description() != null ? rss.description() : "";
            payload.add(Map.of(
                    "source", "RSS/" + rss.feedName(),
                    "title", rss.title(),
                    "url", rss.url(),
                    "engagement_score", 0,
                    "text_preview", preview.substring(0, Math.min(300, preview.length()))
            ));
        }

        for (var reddit : research.redditPosts()) {
            payload.add(Map.of(
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
            payload.add(Map.of(
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
            payload.add(Map.of(
                    "source", "GitHub Releases",
                    "title", release.repoFullName() + " " + release.version(),
                    "url", release.url(),
                    "engagement_score", 0,
                    "text_preview", releaseExcerpt.substring(0, Math.min(300, releaseExcerpt.length()))
            ));
        }

        try {
            String json = objectMapper.writeValueAsString(payload);
            log.info("Prompt payload: {} itemów (tweets={}, hn={}, gh={}, rss={}, reddit={}, papers={}, releases={})",
                    payload.size(),
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
}
