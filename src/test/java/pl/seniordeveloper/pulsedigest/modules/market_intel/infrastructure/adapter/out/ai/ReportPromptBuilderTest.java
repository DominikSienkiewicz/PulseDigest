package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.CncfProjectUpdate;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ConferenceTalk;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.GithubRepo;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.HackerNewsPost;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.HuggingFaceModel;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.JepUpdate;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ProductHuntPost;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RadarEntry;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RedditPost;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchPaper;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchResult;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RssItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SecurityAdvisory;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SoftwareRelease;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Tweet;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.DedupProperties;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ReportPromptBuilderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Builds a prompt builder whose cross-edition dedup sees {@code publishedUrls} as already sent. */
    private ReportPromptBuilder builder(Set<String> publishedUrls) {
        return new ReportPromptBuilder(objectMapper, lookbackDays -> publishedUrls,
                new DedupProperties(true, 10));
    }

    @Test
    void buildUserPromptSerializesAllSupportedSources() throws Exception {
        ReportPromptBuilder builder = builder(Set.of());

        List<Map<String, Object>> payload = payload(builder.buildUserPrompt(researchWithEverySource()));

        assertThat(payload).hasSize(14);
        assertThat(payload)
                .extracting(item -> item.get("source"))
                .containsExactlyInAnyOrder(
                        "Twitter/X",
                        "Hacker News",
                        "GitHub",
                        "RSS/Java",
                        "Reddit/r/java",
                        "arXiv/cs.SE",
                        "GitHub Releases",
                        "Hugging Face",
                        "Product Hunt",
                        "Security Advisories",
                        "OpenJDK JEP",
                        "CNCF Landscape",
                        "Tech Radar",
                        "YouTube/Devoxx");
        assertThat(payload).anySatisfy(item -> {
            assertThat(item).containsEntry("source", "Security Advisories");
            assertThat(item).containsEntry("engagement_score", 1000);
        });
        assertThat(payload).anySatisfy(item -> {
            assertThat(item).containsEntry("source", "OpenJDK JEP");
            assertThat(item).containsEntry("engagement_score", 300);
        });
    }

    @Test
    void buildUserPromptHandlesMissingOptionalFields() throws Exception {
        ReportPromptBuilder builder = builder(Set.of());

        List<Map<String, Object>> payload = payload(builder.buildUserPrompt(researchWithNullOptionalFields()));

        assertThat(payload).hasSize(11);
        assertThat(payload).allSatisfy(item -> assertThat(item.values()).doesNotContainNull());
        assertThat(payload).anySatisfy(item -> {
            assertThat(item).containsEntry("source", "arXiv/cs.AI");
            assertThat(item).containsEntry("authors", "Unknown");
        });
        assertThat(payload).anySatisfy(item -> {
            assertThat(item).containsEntry("source", "Hugging Face");
            assertThat(item.get("text_preview")).asString().contains("Pipeline: n/a");
        });
    }

    @Test
    void buildUserPromptFallsBackToEmptyPayloadWhenSerializationFails() {
        ObjectMapper failingMapper = new ObjectMapper() {
            @Override
            public String writeValueAsString(Object value) throws JsonProcessingException {
                throw JsonMappingException.fromUnexpectedIOE(new IOException("boom"));
            }
        };
        ReportPromptBuilder builder = new ReportPromptBuilder(failingMapper, lookbackDays -> Set.of(),
                new DedupProperties(true, 10));

        String prompt = builder.buildUserPrompt(researchWithEverySource());

        assertThat(prompt).endsWith("[]");
    }

    @Test
    void buildUserPromptDropsItemsAlreadyPublishedInRecentEditions() throws Exception {
        // The Hacker News item's URL was published in a recent edition → cross-edition dedup drops it.
        List<Map<String, Object>> payload =
                payload(builder(Set.of("https://news.example/hn")).buildUserPrompt(researchWithEverySource()));

        assertThat(payload).hasSize(13);   // 14 sources minus the already-published HN item
        assertThat(payload)
                .extracting(item -> item.get("url"))
                .doesNotContain("https://news.example/hn");
    }

    @Test
    void preScoreHighWeightSourceBeatsLowWeightHighEngagement() {
        // arXiv eng=0: round(0.70×100)+0 = 70
        // Twitter/X eng=999: round(0.40×100)+0 = 40
        assertThat(PromptItemSelector.preScore("arXiv/cs.AI", 0))
                .isGreaterThan(PromptItemSelector.preScore("Twitter/X", 999));
    }

    @Test
    void preScoreEngagementBonusCappedAt50() {
        // Twitter/X: round(0.40×100) + min(50, 999_999/1_000) = 40 + 50 = 90
        assertThat(PromptItemSelector.preScore("Twitter/X", 999_999)).isEqualTo(90);
    }

    @Test
    void preScoreArxivWithMaxEngagementReaches120() {
        // arXiv: 70 + 50 = 120
        assertThat(PromptItemSelector.preScore("arXiv/cs.AI", 999_999)).isEqualTo(120);
    }

    @Test
    void applyTotalCapKeepsHighWeightItemOverLowWeightHighEngagement() {
        // 100 Twitter items (preScore=40) + 1 arXiv item (preScore=70)
        // arXiv must survive despite zero engagement
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            items.add(Map.of("source", "Twitter/X", "engagement_score", 999));
        }
        items.add(Map.of("source", "arXiv/cs.AI", "engagement_score", 0));

        List<Map<String, Object>> result = PromptItemSelector.applyTotalCap(items, 100);

        assertThat(result).hasSize(100);
        assertThat(result.stream().anyMatch(m -> "arXiv/cs.AI".equals(m.get("source"))))
                .as("arXiv item must survive the cap despite zero engagement")
                .isTrue();
        assertThat(result.get(0).get("source"))
                .as("arXiv must be first — highest preScore")
                .isEqualTo("arXiv/cs.AI");
    }

    @Test
    void applyTotalCapIsNoopWhenUnderCap() {
        List<Map<String, Object>> items = List.of(
                Map.of("source", "GitHub", "engagement_score", 100),
                Map.of("source", "arXiv/cs.AI", "engagement_score", 0)
        );
        List<Map<String, Object>> result = PromptItemSelector.applyTotalCap(items, 100);
        assertThat(result).hasSize(2);
        assertThat(result).isSameAs(items);
    }

    private List<Map<String, Object>> payload(String prompt) throws Exception {
        String json = prompt.substring(prompt.indexOf("["));
        return objectMapper.readValue(json, new TypeReference<>() {
        });
    }

    private static ResearchResult researchWithEverySource() {
        LocalDateTime now = LocalDateTime.parse("2026-05-14T10:00:00");
        return new ResearchResult(
                List.of(new Tweet("tweet-1", "Java agents and LLM systems", "dev", "Dev", "now", 42, 4, 2)),
                List.of(new HackerNewsPost("HN item", "https://news.example/hn", 120)),
                List.of(new GithubRepo("owner/repo", "Framework for agents", 5_000, "https://github.com/owner/repo")),
                List.of(new RssItem("RSS item", "https://blog.example/post", "Deep dive", "Java")),
                List.of(new RedditPost("Reddit item", "https://reddit.example/r/java", 80, "java")),
                List.of(new ResearchPaper("2401.00001", "Paper", "Research abstract", List.of("Ada", "Grace"),
                        "https://arxiv.org/abs/2401.00001", "cs.SE", now)),
                List.of(new SoftwareRelease("spring/project", "v1.0.0", "Release notes",
                        "https://github.com/spring/project/releases/v1", now)),
                List.of(new HuggingFaceModel("org/model", "org", 2_000, 30, now, "text-generation",
                        "https://huggingface.co/org/model")),
                List.of(new ProductHuntPost("Launch", "A useful launch", "https://producthunt.example/launch",
                        77, List.of("Developer Tools"), now)),
                List.of(new SecurityAdvisory("GHSA-1234", "Critical issue", "CRITICAL", now,
                        List.of("Maven"), "https://github.com/advisories/GHSA-1234")),
                List.of(new JepUpdate("JEP 999", "Virtual Threads Next", "delivered", now,
                        "https://openjdk.org/jeps/999")),
                List.of(new CncfProjectUpdate("Kubernetes", "Orchestration", "graduated", "Update",
                        "https://landscape.cncf.io/card-mode?selected=kubernetes", now)),
                List.of(new RadarEntry("Tool", "adopt", "tools", "Radar item", "https://radar.example/tool", now)),
                List.of(new ConferenceTalk("Talk", "Devoxx", "Devoxx", "https://youtube.example/talk", now, 12_000)),
                now,
                1,
                1,
                1,
                1,
                1
        );
    }

    private static ResearchResult researchWithNullOptionalFields() {
        LocalDateTime now = LocalDateTime.parse("2026-05-14T10:00:00");
        return new ResearchResult(
                List.of(),
                List.of(),
                List.of(new GithubRepo("owner/repo", null, 5, "https://github.com/owner/repo")),
                List.of(new RssItem("RSS item", "https://blog.example/post", null, "Java")),
                List.of(),
                List.of(new ResearchPaper("2401.00001", "Paper", "Research abstract", List.of(),
                        "https://arxiv.org/abs/2401.00001", "cs.AI", now)),
                List.of(new SoftwareRelease("spring/project", "v1.0.0", null,
                        "https://github.com/spring/project/releases/v1", now)),
                List.of(new HuggingFaceModel("org/model", "org", 2_000, 30, now, null,
                        "https://huggingface.co/org/model")),
                List.of(new ProductHuntPost("Launch", null, "https://producthunt.example/launch",
                        77, List.of(), now)),
                List.of(new SecurityAdvisory("GHSA-1234", null, null, now, List.of(),
                        "https://github.com/advisories/GHSA-1234")),
                List.of(new JepUpdate("JEP 999", null, null, now, "https://openjdk.org/jeps/999")),
                List.of(new CncfProjectUpdate("Kubernetes", null, null, "Update",
                        "https://landscape.cncf.io/card-mode?selected=kubernetes", now)),
                List.of(new RadarEntry("Tool", null, "tools", "Radar item", "https://radar.example/tool", now)),
                List.of(new ConferenceTalk("Talk", "Devoxx", "Devoxx", "https://youtube.example/talk", now, 12)),
                now,
                0,
                0,
                1,
                1,
                0
        );
    }
}
