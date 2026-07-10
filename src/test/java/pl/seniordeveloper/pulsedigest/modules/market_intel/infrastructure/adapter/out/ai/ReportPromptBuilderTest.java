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
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.LabAnnouncement;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PreScoringCandidate;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ProductHuntPost;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PromptItemMeta;
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
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.PreScoringPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.PublishedUrlsPort;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.DedupProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.FeedbackProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.InterestProfileProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.PreScoringProperties;

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
        return builder(publishedUrls, Set.of());
    }

    private ReportPromptBuilder builder(Set<String> publishedUrls, Set<String> downvotedUrls) {
        return new ReportPromptBuilder(objectMapper, publishedUrlsPort(publishedUrls, List.of()),
                new DedupProperties(true, 10), new InterestProfileProperties("Test Persona", List.of("java")),
                feedbackPort(downvotedUrls), new FeedbackProperties(true, 30, "", ""),
                noPreScoring(), new PreScoringProperties(false, 50));
    }

    private ReportPromptBuilder builderWithTitles(List<String> publishedTitles) {
        return new ReportPromptBuilder(objectMapper, publishedUrlsPort(Set.of(), publishedTitles),
                new DedupProperties(true, 10), new InterestProfileProperties("Test Persona", List.of("java")),
                feedbackPort(Set.of()), new FeedbackProperties(true, 30, "", ""),
                noPreScoring(), new PreScoringProperties(false, 50));
    }

    /** PreScoringPort test double — triage disabled by default so payload assertions stay deterministic. */
    private static PreScoringPort noPreScoring() {
        return candidates -> java.util.Map.of();
    }

    /** PublishedUrlsPort test double — fixed URLs for dedup, fixed titles for the semantic block. */
    private static PublishedUrlsPort publishedUrlsPort(Set<String> urls, List<String> titles) {
        return new PublishedUrlsPort() {
            @Override
            public Set<String> recentlyPublishedUrls(int lookbackDays) {
                return urls;
            }

            @Override
            public List<String> recentlyPublishedTitles(int lookbackDays, int maxTitles) {
                return titles;
            }
        };
    }

    /** FeedbackPort test double — fixed down-votes and fixed per-category net votes. */
    private static FeedbackPort feedbackPort(Set<String> downvotedUrls) {
        return feedbackPort(downvotedUrls, Map.of());
    }

    private static FeedbackPort feedbackPort(Set<String> downvotedUrls, Map<String, Integer> categoryVotes) {
        return new FeedbackPort() {
            @Override
            public Set<String> downvotedUrls(int lookbackDays) {
                return downvotedUrls;
            }

            @Override
            public Map<String, Integer> netVotesBySource(int lookbackDays) {
                return Map.of();
            }

            @Override
            public Map<String, Integer> netVotesByCategory(int lookbackDays) {
                return categoryVotes;
            }
        };
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
    void buildUserPromptSerializesSocialPosts() throws Exception {
        LocalDateTime now = LocalDateTime.parse("2026-05-14T10:00:00");
        ResearchResult research = new ResearchResult(
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(),
                List.of(
                        new SocialPost("Bluesky", "alice.bsky.social", "Spring Boot 4 perf tip",
                                "https://bsky.app/profile/alice.bsky.social/post/abc", 42),
                        new SocialPost("Mastodon", "bob@fosstodon.org", "Quarkus native image win",
                                "https://fosstodon.org/@bob/123", 30)),
                now, 0, 0, 0, 0, 0, List.of(), null);

        List<Map<String, Object>> payload = payload(builder(Set.of()).buildUserPrompt(research));

        assertThat(payload)
                .extracting(item -> item.get("source"))
                .contains("Bluesky", "Mastodon");
        assertThat(payload).anySatisfy(item -> {
            assertThat(item).containsEntry("source", "Bluesky");
            assertThat(item).containsEntry("engagement_score", 42);
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
        ReportPromptBuilder builder = new ReportPromptBuilder(failingMapper, publishedUrlsPort(Set.of(), List.of()),
                new DedupProperties(true, 10), new InterestProfileProperties("Test Persona", List.of("java")),
                feedbackPort(Set.of()), new FeedbackProperties(true, 30, "", ""),
                noPreScoring(), new PreScoringProperties(false, 50));

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
    void buildUserPromptDropsDownvotedItems() throws Exception {
        // The reader down-voted the HN item ("less like this") → it is suppressed before scoring.
        List<Map<String, Object>> payload = payload(
                builder(Set.of(), Set.of("https://news.example/hn")).buildUserPrompt(researchWithEverySource()));

        assertThat(payload).hasSize(13);   // 14 sources minus the down-voted HN item
        assertThat(payload)
                .extracting(item -> item.get("url"))
                .doesNotContain("https://news.example/hn");
    }

    @Test
    void buildUserPromptWithReducedItemCapTrimsThePayload() throws Exception {
        // Truncation recovery: the same research is re-sent with half the items so the model's
        // output fits under the token cap.
        ReportPromptBuilder builder = builder(Set.of());

        List<Map<String, Object>> payload = payload(builder.buildUserPrompt(researchWithEverySource(), 5));

        assertThat(payload).hasSize(5);
    }

    @Test
    void buildPromptExposesInputMetadataKeyedByCanonicalUrl() {
        // The re-join map must describe exactly the items that went into the prompt, so the adapter
        // can overwrite the model's echoed source/engagement and reject URLs it never saw.
        PromptPayload payload = builder(Set.of()).buildPrompt(researchWithEverySource());

        assertThat(payload.userPrompt()).isEqualTo(builder(Set.of()).buildUserPrompt(researchWithEverySource()));
        assertThat(payload.inputMeta()).hasSize(14);
        assertThat(payload.inputMeta())
                .containsEntry("https://news.example/hn", new PromptItemMeta("Hacker News", 120))
                .containsEntry("https://github.com/owner/repo", new PromptItemMeta("GitHub", 5_000));
    }

    @Test
    void buildPromptDropsInputMetadataForItemsTrimmedOutOfThePrompt() {
        PromptPayload payload = builder(Set.of()).buildPrompt(researchWithEverySource(), 3);

        assertThat(payload.inputMeta()).hasSize(3);
    }

    @Test
    void promptCarriesRecentlyPublishedTitlesSoTheModelCanSpotTheSameStoryTwice() {
        // URL dedup misses the same story republished by InfoQ on Monday and Hacker News on Wednesday.
        ReportPromptBuilder builder = builderWithTitles(List.of("Spring Boot 4.2 released", "MCP hits 1.0"));

        String prompt = builder.buildUserPrompt(researchWithEverySource());

        assertThat(prompt).contains("JUŻ OPUBLIKOWANE");
        assertThat(prompt).contains("Spring Boot 4.2 released").contains("MCP hits 1.0");
    }

    @Test
    void promptOmitsThePublishedBlockWhenNothingWasPublishedRecently() {
        String prompt = builderWithTitles(List.of()).buildUserPrompt(researchWithEverySource());

        assertThat(prompt).doesNotContain("JUŻ OPUBLIKOWANE");
    }

    @Test
    void promptOmitsThePublishedBlockWhenDedupIsDisabled() {
        ReportPromptBuilder builder = new ReportPromptBuilder(objectMapper,
                publishedUrlsPort(Set.of(), List.of("Spring Boot 4.2 released")),
                new DedupProperties(false, 10), new InterestProfileProperties("Test Persona", List.of("java")),
                feedbackPort(Set.of()), new FeedbackProperties(false, 30, "", ""),
                noPreScoring(), new PreScoringProperties(false, 50));

        assertThat(builder.buildUserPrompt(researchWithEverySource())).doesNotContain("JUŻ OPUBLIKOWANE");
    }

    @Test
    void preScoringTrimsThePayloadBeforeTheExpensiveModelReadsIt() throws Exception {
        // The triage model dislikes everything; only the `keep` best-rated survive.
        PreScoringPort dismissive = candidates -> candidates.stream()
                .collect(java.util.stream.Collectors.toMap(PreScoringCandidate::url, c -> 1));
        ReportPromptBuilder builder = new ReportPromptBuilder(objectMapper, publishedUrlsPort(Set.of(), List.of()),
                new DedupProperties(true, 10), new InterestProfileProperties("Test Persona", List.of("java")),
                feedbackPort(Set.of()), new FeedbackProperties(true, 30, "", ""),
                dismissive, new PreScoringProperties(true, 3));

        List<Map<String, Object>> payload = payload(builder.buildUserPrompt(researchWithEverySource()));

        assertThat(payload).hasSize(3);
        assertThat(payload)
                .as("GitHub Releases pre-scores 95 — above the floor, so triage cannot cut it")
                .extracting(item -> item.get("source"))
                .contains("GitHub Releases");
    }

    @Test
    void labAnnouncementsReachThePromptWithRealisticEngagement() throws Exception {
        // Regression: lab labels matched no PromptItemSelector route, so the "highest-signal source"
        // was silently dropped before the prompt — the fake engagement of 1_000_000 never even applied.
        List<Map<String, Object>> payload = payload(builder(Set.of()).buildUserPrompt(researchWithLabAnnouncement()));

        assertThat(payload).anySatisfy(item -> {
            assertThat(item).containsEntry("source", "Anthropic News");
            assertThat(item).containsEntry("engagement_score", 10_000);
        });
    }

    @Test
    void labAnnouncementSurvivesTotalCapAgainstAViralTweet() {
        // preScore("Anthropic News", 10_000) = 95 + 10 = 105 > preScore("Twitter/X", huge) = 90.
        assertThat(PromptItemSelector.preScore("Anthropic News", 10_000))
                .isGreaterThan(PromptItemSelector.preScore("Twitter/X", 999_999));
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

    private static ResearchResult researchWithLabAnnouncement() {
        LocalDateTime now = LocalDateTime.parse("2026-05-14T10:00:00");
        return new ResearchResult(
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(new LabAnnouncement("Claude 5 released", "https://www.anthropic.com/news/claude-5",
                        "New frontier model", "Anthropic News", now)),
                List.of(),
                now, 0, 0, 0, 0, 0, List.of(), null);
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
