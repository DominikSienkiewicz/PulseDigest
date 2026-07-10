package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.HackerNewsPost;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RadarAccuracy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RecapChange;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RecapEntry;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportData;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchResult;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.WeeklyRecap;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RssItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Signal;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SignalRank;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceDomain;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceFetchReport;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.TechDemandEntry;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.TechDemandSignal;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.TrendRecurrence;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.TrendVelocity;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.FeedbackProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.WatchlistProperties;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReportEmailBuilderTest {

    private final ReportEmailBuilder builder =
            new ReportEmailBuilder(new FeedbackProperties(false, 30, "", ""), new WatchlistProperties(false, List.of()));

    @Test
    void buildSubjectUsesDigestPrefix() {
        assertThat(builder.buildSubject(null)).contains("PulseDigest");
    }

    @Test
    void buildSubjectLeadsWithCriticalMarkerAndCarriesThePreview() {
        // The inbox slot has to answer "read now or tonight?" without opening the mail.
        String subject = builder.buildSubject(fullReport());

        assertThat(subject)
                .startsWith("🔴 ")
                .contains("Preview")
                .endsWith(today());
    }

    @Test
    void buildSubjectUsesMustKnowMarkerWhenNoCriticalTrend() {
        ReportData report = new ReportData("Spring Boot 4.2 GA", "Lead", List.of(),
                List.of(itemWithScore(8)), List.of());

        assertThat(builder.buildSubject(report)).startsWith("⚡ ").contains("Spring Boot 4.2 GA");
    }

    @Test
    void buildSubjectUsesNeutralMarkerWhenNothingClearsTheMustKnowBar() {
        ReportData report = new ReportData("Quiet day", "Lead", List.of(),
                List.of(itemWithScore(6)), List.of());

        assertThat(builder.buildSubject(report)).startsWith("📡 ").contains("Quiet day");
    }

    @Test
    void buildSubjectFallsBackToDatedFormatWhenPreviewIsBlank() {
        ReportData report = new ReportData("   ", "Lead", List.of(), List.of(itemWithScore(9)), List.of());

        assertThat(builder.buildSubject(report)).isEqualTo("📡 PulseDigest " + today());
    }

    @Test
    void buildSubjectTruncatesAnOverlongPreviewOnAWordBoundary() {
        String longPreview = "Spring Boot 4.2 GA, Quarkus 4 native image, Kubernetes 1.35 sidecars, "
                + "PyTorch 3 compile, Rust in the kernel";
        ReportData report = new ReportData(longPreview, "Lead", List.of(), List.of(), List.of());

        String subject = builder.buildSubject(report);

        assertThat(subject).contains("…").endsWith(today());
        assertThat(subject).doesNotContain("Rust in the kernel");
        // Marker + preview + separator + date must stay inside the inbox-visible budget.
        assertThat(subject.length()).isLessThanOrEqualTo(100);
    }

    private static String today() {
        return java.time.LocalDate.now(java.time.ZoneOffset.UTC)
                .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy", java.util.Locale.of("pl", "PL")));
    }

    private static DigestItem itemWithScore(int score) {
        return new DigestItem("Item", "https://example.com/item", "GitHub", "Java", "RELEASE",
                score, 10, "Summary", null);
    }

    @Test
    void buildHtmlRendersDigestSectionsAndSourceHealthWarnings() {
        String html = builder.buildHtml(fullReport(), researchWithFailedSource());

        assertThat(html)
                .contains("PulseDigest")
                .contains("Top insights dnia")
                .contains("Krytyczne trendy")
                .contains("Top picks (1)")
                .contains("Signals (2)")
                .contains("z ostrze")
                .contains("&lt;unsafe&gt;")
                .contains("12k &#9733;")
                .contains("42 &#10084;")
                .contains("8.8k pkt");
    }

    @Test
    void criticalTrendNamesTheSourcesThatConfirmedTheTopic() {
        // "Critical" must be legible as evidence, not as a colour: say who confirmed the story.
        DigestItem paper = topicItem("arXiv/cs.AI", "mcp", 9);
        DigestItem repo = topicItem("GitHub", "mcp", 8);
        DigestItem discussion = topicItem("Hacker News", "mcp", 8);
        ReportData report = new ReportData("Preview", "Lead", List.of(),
                List.of(paper, repo, discussion),
                List.of(
                        new Signal(paper, SignalRank.CRITICAL, 120,
                                List.of(SourceDomain.SCIENCE, SourceDomain.CODE, SourceDomain.BUSINESS)),
                        new Signal(repo, SignalRank.CRITICAL, 135,
                                List.of(SourceDomain.SCIENCE, SourceDomain.CODE, SourceDomain.BUSINESS)),
                        new Signal(discussion, SignalRank.CRITICAL, 130,
                                List.of(SourceDomain.SCIENCE, SourceDomain.CODE, SourceDomain.BUSINESS))));

        String html = builder.buildHtml(report, null);

        assertThat(html).contains("Potwierdzone w:");
        assertThat(html).contains("arXiv/cs.AI").contains("GitHub").contains("Hacker News");
    }

    private static DigestItem topicItem(String source, String topicKey, int score) {
        return new DigestItem("Title " + source, "https://example.com/" + source.hashCode(), source,
                "AI/LLM", "RELEASE", score, 10, "Summary", null, topicKey);
    }

    @Test
    void criticalTrendShowsHowLongTheStoryHasBeenBuildingAndWhenItFirstAppeared() {
        DigestItem item = topicItem("GitHub", "mcp", 9);
        Signal building = new Signal(item, SignalRank.CRITICAL, 130, List.of(SourceDomain.CODE),
                new TrendRecurrence(3, LocalDate.of(2026, 6, 18)));
        ReportData report = new ReportData("Preview", "Lead", List.of(), List.of(item), List.of(building));

        String html = builder.buildHtml(report, null);

        assertThat(html).contains("narasta").contains("3. edycja");
        assertThat(html).contains("Pierwszy sygnał: 18.06.2026");
    }

    @Test
    void aFirstTimeStoryCarriesNoRecurrenceBadge() {
        DigestItem item = topicItem("GitHub", "gemini-3", 9);
        Signal fresh = new Signal(item, SignalRank.CRITICAL, 130, List.of(SourceDomain.CODE),
                new TrendRecurrence(1, null));
        ReportData report = new ReportData("Preview", "Lead", List.of(), List.of(item), List.of(fresh));

        String html = builder.buildHtml(report, null);

        assertThat(html).doesNotContain("narasta").doesNotContain("Pierwszy sygnał");
    }

    @Test
    void weeklyRecapSectionNamesWhatClimbedHeldAndFaded() {
        WeeklyRecap recap = new WeeklyRecap(List.of(
                new RecapEntry("MCP everywhere", "https://example.com/mcp",
                        RecapChange.ESCALATED, SignalRank.MODERATE, SignalRank.CRITICAL),
                new RecapEntry("Gemini 3", "https://example.com/gemini",
                        RecapChange.CONFIRMED, SignalRank.CRITICAL, SignalRank.CRITICAL),
                new RecapEntry("Hype train", "https://example.com/hype",
                        RecapChange.FADED, SignalRank.CRITICAL, SignalRank.WEAK)));
        ReportData report = new ReportData("Preview", "Lead", List.of(), List.of(), List.of())
                .withWeeklyRecap(recap);

        String html = builder.buildHtml(report, null);

        assertThat(html).contains("Tydzień w sygnałach");
        assertThat(html).contains("MCP everywhere").contains("Gemini 3").contains("Hype train");
        assertThat(html).contains("urosło").contains("potwierdzony").contains("wygasł");
    }

    @Test
    void weeklyRecapSectionIsAbsentOnNonFridayEditions() {
        String html = builder.buildHtml(fullReport(), null);

        assertThat(html).doesNotContain("Tydzień w sygnałach");
    }

    @Test
    void watchlistSectionConfirmsSilenceForTechnologiesNobodyMentioned() {
        ReportEmailBuilder withWatchlist = new ReportEmailBuilder(new FeedbackProperties(false, 30, "", ""),
                new WatchlistProperties(true, List.of("spring ai", "kubernetes")));

        String html = withWatchlist.buildHtml(fullReport(), researchWithFailedSource());

        assertThat(html).contains("Twój radar");
        assertThat(html).contains("spring ai").contains("0 wzmianek");
    }

    @Test
    void watchlistSectionIsAbsentWhenDisabled() {
        assertThat(builder.buildHtml(fullReport(), researchWithFailedSource())).doesNotContain("Twój radar");
    }

    @Test
    void aCriticalCandidateIsMarkedOnItsRowSoTheReaderSeesItBeforeItBreaks() {
        DigestItem item = topicItem("GitHub", "mcp", 9);
        Signal candidate = new Signal(item, SignalRank.STRONG, 95,
                List.of(SourceDomain.CODE, SourceDomain.SCIENCE), null, new TrendVelocity(1, 25, true));
        ReportData report = new ReportData("Preview", "Lead", List.of(), List.of(item), List.of(candidate));

        String html = builder.buildHtml(report, null);

        assertThat(html).contains("&#128992; Title GitHub");
    }

    @Test
    void anOrdinarySignalCarriesNoRadarMarker() {
        DigestItem item = topicItem("GitHub", "mcp", 9);
        Signal plain = new Signal(item, SignalRank.STRONG, 95,
                List.of(SourceDomain.CODE, SourceDomain.SCIENCE), null, new TrendVelocity(0, 2, false));
        ReportData report = new ReportData("Preview", "Lead", List.of(), List.of(item), List.of(plain));

        assertThat(builder.buildHtml(report, null)).doesNotContain("&#128992;");
    }

    @Test
    void footerPublishesTheRadarsOwnHitRate() {
        ReportData report = fullReport().withRadarAccuracy(new RadarAccuracy(10, 7));

        assertThat(builder.buildHtml(report, null)).contains("radar: 7/10 kandydatów osiągnęło CRITICAL (70%)");
    }

    @Test
    void footerOmitsTheRadarLineUntilAPredictionHasBeenJudged() {
        ReportData report = fullReport().withRadarAccuracy(new RadarAccuracy(0, 0));

        assertThat(builder.buildHtml(report, null)).doesNotContain("radar:");
    }

    @Test
    void feedbackLinksReachEveryTierNotJustTheMustKnowFive() {
        // The learning loop never heard about the mid-tier: thumbs rendered on ≤5 items only.
        ReportEmailBuilder withFeedback = new ReportEmailBuilder(
                new FeedbackProperties(true, 30, "https://fb.example/vote", ""),
                new WatchlistProperties(false, List.of()));

        String html = withFeedback.buildHtml(fullReport(), researchWithFailedSource());

        // fullReport() has 4 items: one Must-know (9), one deal (7), one top pick (9), two mid-tier.
        assertThat(html.split("vote=up", -1).length - 1)
                .as("every rendered item across Must-know, Deals, Top picks and Signals gets a thumb")
                .isGreaterThan(4);
    }

    @Test
    void feedbackLinksAreSignedWhenASecretIsConfigured() {
        ReportEmailBuilder signed = new ReportEmailBuilder(
                new FeedbackProperties(true, 30, "https://fb.example/vote", "secret"),
                new WatchlistProperties(false, List.of()));

        String html = signed.buildHtml(fullReport(), researchWithFailedSource());

        assertThat(html).contains("sig=").contains("edition=");
    }

    @Test
    void feedbackLinksStayUnsignedUntilTheSecretIsSet() {
        ReportEmailBuilder unsigned = new ReportEmailBuilder(
                new FeedbackProperties(true, 30, "https://fb.example/vote", ""),
                new WatchlistProperties(false, List.of()));

        String html = unsigned.buildHtml(fullReport(), researchWithFailedSource());

        assertThat(html).contains("vote=up").doesNotContain("sig=");
    }

    @Test
    void buildHtmlRendersMustKnowSectionWithWhyItMatters() {
        String html = builder.buildHtml(fullReport(), researchWithFailedSource());

        assertThat(html)
                .contains("Must-know")
                .contains("Action: update your dependency now")   // top (score 9) why_it_matters
                .contains("Action: try this new API");            // strong (score 7) why_it_matters
    }

    @Test
    void buildHtmlRendersDealsAndToolsSectionForToolTypes() {
        String html = builder.buildHtml(fullReport(), researchWithFailedSource());

        // top=RELEASE and strong=FEATURE qualify; moderate=OPINION, weak=DISCUSSION do not.
        assertThat(html)
                .contains("Deals &amp; Tools")
                .contains("Strong signal");
    }

    @Test
    void buildHtmlRendersFeedbackLinksWhenReceiverConfigured() {
        ReportEmailBuilder withFeedback = new ReportEmailBuilder(
                new FeedbackProperties(true, 30, "https://fb.example/vote", ""),
                new WatchlistProperties(false, List.of()));

        String html = withFeedback.buildHtml(fullReport(), researchWithFailedSource());

        assertThat(html)
                .contains("https://fb.example/vote?url=")   // feedback receiver link on Must-know items
                .contains("vote=up")
                .contains("vote=down")
                .contains("&#128077;")                       // 👍
                .contains("&#128078;");                      // 👎
    }

    @Test
    void buildHtmlOmitsFeedbackLinksWhenReceiverBlank() {
        String html = builder.buildHtml(fullReport(), researchWithFailedSource());

        assertThat(html).doesNotContain("vote=up");
    }

    @Test
    void buildHtmlOmitsLongTailAndLowScoreItems() {
        String html = builder.buildHtml(fullReport(), researchWithFailedSource());

        assertThat(html)
                .doesNotContain("Long tail")
                .doesNotContain("Weak signal");   // score 2 < signal threshold, no long-tail section
    }

    @Test
    void buildHtmlHandlesEmptyOptionalSections() {
        ReportData report = new ReportData(null, null, null, null, null);

        String html = builder.buildHtml(report, null);

        assertThat(html)
                .contains("z ostatnich kilku dni")
                .contains("Wybrano 0 z 0 item")
                .doesNotContain("Top insights dnia")
                .doesNotContain("Top picks");
    }

    @Test
    void buildHtmlShowsExhaustedLimitBannerWhenSourceRateLimited() {
        ResearchResult research = new ResearchResult(
                List.of(), List.of(new HackerNewsPost("HN", "https://news.example/1", 1)),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                LocalDateTime.parse("2026-05-14T10:00:00"), 0, 1, 0, 0, 0,
                List.of(SourceFetchReport.failed("Twitter/X topic", 12, "429 Too Many Requests")));

        String html = builder.buildHtml(fullReport(), research);

        assertThat(html)
                .contains("Wyczerpane limity API")
                .contains("Twitter/X API")
                .contains("doładuj");
    }

    @Test
    void buildHtmlOmitsBannerWhenOnlyNonQuotaFailures() {
        String html = builder.buildHtml(fullReport(), researchWithFailedSource());

        assertThat(html).doesNotContain("Wyczerpane limity API");
    }

    @Test
    void buildHtmlShowsTechDemandPulseWhenSignalPresent() {
        TechDemandSignal demand = new TechDemandSignal(
                "czerwiec 2026",
                "maj 2026",
                "https://news.ycombinator.com/item?id=999",
                120,
                "Front-end dominuje, JVM marginalny na HN.",
                List.of(
                        new TechDemandEntry("kubernetes", 38, 0.32, 4.0),
                        new TechDemandEntry("spring", 24, 0.20, -2.0)),
                List.of(new TechDemandEntry("java", 6, 0.05, null)));
        ResearchResult research = researchWithFailedSource().withTechDemand(demand);

        String html = builder.buildHtml(fullReport(), research);

        assertThat(html)
                .contains("Puls rynku")
                .contains("Front-end dominuje")        // narrative
                .contains("kubernetes")
                .contains("32%")                        // share, not raw count
                .contains("&#9650;4")                   // ▲ delta arrow
                .contains("Twój stack:")
                .contains("czerwiec 2026")
                .contains("vs maj 2026")
                .contains("na podstawie 120 og");
    }

    @Test
    void buildHtmlOmitsTechDemandPulseWhenAbsent() {
        String html = builder.buildHtml(fullReport(), researchWithFailedSource());

        assertThat(html).doesNotContain("Puls rynku");
    }

    private static ReportData fullReport() {
        DigestItem top = new DigestItem(
                "Top <unsafe>",
                "https://example.com/top",
                "GitHub",
                "Java",
                "RELEASE",
                9,
                12_345,
                "Important <summary>",
                "Action: update your dependency now");
        DigestItem strong = new DigestItem(
                "Strong signal",
                "https://example.com/strong",
                "Twitter/X",
                "AI",
                "FEATURE",
                7,
                42,
                "Strong summary",
                "Action: try this new API");
        DigestItem moderate = new DigestItem(
                "Moderate signal",
                "https://example.com/moderate",
                "Hacker News",
                null,
                "OPINION",
                6,
                8_800,
                "Moderate summary",
                null);
        DigestItem weak = new DigestItem(
                "Weak signal",
                "https://example.com/weak",
                "Reddit/r/java",
                "Community",
                "DISCUSSION",
                2,
                10,
                "Weak summary",
                null);
        return new ReportData(
                "Preview",
                "Editorial lead",
                List.of("Insight one"),
                List.of(top, strong, moderate, weak),
                List.of(
                        new Signal(top, SignalRank.CRITICAL, 120,
                                List.of(SourceDomain.CODE, SourceDomain.SCIENCE, SourceDomain.BUSINESS)),
                        new Signal(strong, SignalRank.STRONG, 90, List.of(SourceDomain.SOCIAL)),
                        new Signal(moderate, SignalRank.MODERATE, 60, List.of(SourceDomain.BUSINESS)),
                        new Signal(weak, SignalRank.WEAK, 10, List.of(SourceDomain.SOCIAL)))
        );
    }

    private static ResearchResult researchWithFailedSource() {
        LocalDateTime now = LocalDateTime.parse("2026-05-14T10:00:00");
        return new ResearchResult(
                List.of(),
                List.of(new HackerNewsPost("HN", "https://news.example/1", 1)),
                List.of(),
                List.of(new RssItem("RSS", "https://blog.example/1", "desc", "Java")),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                now,
                0,
                1,
                0,
                1,
                0,
                List.of(
                        SourceFetchReport.success("Hacker News", 1, 10),
                        SourceFetchReport.failed("RSS", 12, "timeout"))
        );
    }
}
