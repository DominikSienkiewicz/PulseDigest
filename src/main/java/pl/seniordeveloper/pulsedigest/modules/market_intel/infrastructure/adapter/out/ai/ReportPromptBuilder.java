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

        for (var model : research.huggingFaceModels()) {
            String preview = "Pipeline: " + (model.pipelineTag() == null ? "n/a" : model.pipelineTag())
                    + " · " + model.downloads() + " downloads";
            all.add(Map.of(
                    "source", "Hugging Face",
                    "title", model.id(),
                    "url", model.url(),
                    "engagement_score", (int) Math.min(Integer.MAX_VALUE, model.likes()),
                    "text_preview", preview
            ));
        }

        for (var post : research.productHuntPosts()) {
            String preview = post.tagline() == null ? "" : post.tagline();
            all.add(Map.of(
                    "source", "Product Hunt",
                    "title", post.name(),
                    "url", post.url(),
                    "engagement_score", post.votesCount(),
                    "text_preview", preview.substring(0, Math.min(300, preview.length()))
            ));
        }

        for (var advisory : research.securityAdvisories()) {
            String preview = (advisory.summary() == null ? "" : advisory.summary())
                    + " · severity=" + advisory.severity()
                    + (advisory.affectedEcosystems().isEmpty()
                            ? "" : " · ecosystems=" + String.join(",", advisory.affectedEcosystems()));
            all.add(Map.of(
                    "source", "Security Advisories",
                    "title", advisory.ghsaId() + " " + (advisory.summary() == null ? "" : advisory.summary()),
                    "url", advisory.url(),
                    "engagement_score", severityScore(advisory.severity()),
                    "text_preview", preview.substring(0, Math.min(300, preview.length()))
            ));
        }

        for (var vuln : research.nvdVulnerabilities()) {
            String preview = "CVSS " + vuln.cvssScore() + " · severity=" + vuln.severity()
                    + " · " + String.join(", ", vuln.affectedProducts());
            all.add(Map.of(
                    "source", "NVD/CVE",
                    "title", vuln.cveId() + " " + truncate(vuln.description(), 120),
                    "url", vuln.url(),
                    "engagement_score", (int) (vuln.cvssScore() * 100),
                    "text_preview", preview.substring(0, Math.min(300, preview.length()))
            ));
        }

        for (var trend : research.packageTrends()) {
            String preview = "Platform: " + trend.platform()
                    + " · " + trend.dependentProjects() + " dependents"
                    + (trend.description() != null ? " · " + trend.description() : "");
            all.add(Map.of(
                    "source", "Libraries.io",
                    "title", trend.name(),
                    "url", trend.url(),
                    "engagement_score", (int) Math.min(Integer.MAX_VALUE, trend.stars()),
                    "text_preview", preview.substring(0, Math.min(300, preview.length()))
            ));
        }

        for (var jep : research.jepUpdates()) {
            String preview = "Status: " + jep.status() + (jep.title() != null ? " · " + jep.title() : "");
            all.add(Map.of(
                    "source", "OpenJDK JEP",
                    "title", jep.jepId() + " " + (jep.title() != null ? jep.title() : ""),
                    "url", jep.url(),
                    "engagement_score", jepStatusScore(jep.status()),
                    "text_preview", preview.substring(0, Math.min(300, preview.length()))
            ));
        }

        for (var cncf : research.cncfProjectUpdates()) {
            String preview = "Status: " + cncf.status()
                    + (cncf.category() != null ? " · Category: " + cncf.category() : "");
            all.add(Map.of(
                    "source", "CNCF Landscape",
                    "title", cncf.projectName(),
                    "url", cncf.url(),
                    "engagement_score", cncfStatusScore(cncf.status()),
                    "text_preview", preview.substring(0, Math.min(300, preview.length()))
            ));
        }

        for (var radar : research.radarEntries()) {
            String preview = "Ring: " + radar.ring() + " · Quadrant: " + radar.quadrant();
            all.add(Map.of(
                    "source", "Tech Radar",
                    "title", radar.name(),
                    "url", radar.url(),
                    "engagement_score", ringScore(radar.ring()),
                    "text_preview", preview.substring(0, Math.min(300, preview.length()))
            ));
        }

        for (var talk : research.conferenceTalks()) {
            String preview = talk.conferenceName() + " · " + talk.channelName()
                    + " · " + talk.viewCount() + " views";
            all.add(Map.of(
                    "source", "YouTube/" + talk.conferenceName(),
                    "title", talk.title(),
                    "url", talk.url(),
                    "engagement_score", (int) Math.min(Integer.MAX_VALUE, talk.viewCount()),
                    "text_preview", preview.substring(0, Math.min(300, preview.length()))
            ));
        }

        for (var db : research.dbEngineRankings()) {
            String preview = "Rank #" + db.rank()
                    + " · Score: " + String.format("%.1f", db.score())
                    + (db.scoreChange() != 0 ? " · Change: " + String.format("%+.1f", db.scoreChange()) : "");
            all.add(Map.of(
                    "source", "DB-Engines",
                    "title", db.dbName(),
                    "url", db.url(),
                    "engagement_score", 1000 - db.rank(),
                    "text_preview", preview.substring(0, Math.min(300, preview.length()))
            ));
        }

        List<Map<String, Object>> payload = PromptItemSelector.selectTopItems(all);

        try {
            String json = objectMapper.writeValueAsString(payload);
            log.info("Prompt payload: {} itemów wybranych z {} (tweets={}, hn={}, gh={}, rss={}, reddit={},"
                            + " papers={}, releases={}, hf={}, ph={}, advisories={},"
                            + " nvd={}, libs={}, jep={}, cncf={}, radar={}, talks={}, db={})",
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
                    research.nvdVulnerabilities().size(),
                    research.packageTrends().size(),
                    research.jepUpdates().size(),
                    research.cncfProjectUpdates().size(),
                    research.radarEntries().size(),
                    research.conferenceTalks().size(),
                    research.dbEngineRankings().size());
            return "Oto posty z ostatnich 24 godzin:\n\n" + json;
        } catch (JsonProcessingException e) {
            log.error("Błąd serializacji payloadu: {}", e.getMessage());
            return "Oto posty z ostatnich 24 godzin:\n\n[]";
        }
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

    private String truncate(String s, int maxLen) {
        if (s == null) {
            return "";
        }
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }
}
