package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.HackerNewsPost;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportData;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchResult;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RssItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Signal;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SignalRank;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceDomain;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceFetchReport;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.TechDemandEntry;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.TechDemandSignal;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.TrendInsight;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReportEmailBuilderTest {

    private final ReportEmailBuilder builder = new ReportEmailBuilder();

    @Test
    void buildSubjectUsesDigestPrefix() {
        assertThat(builder.buildSubject(fullReport())).contains("Daily Digest");
    }

    @Test
    void buildHtmlRendersDigestSectionsAndSourceHealthWarnings() {
        String html = builder.buildHtml(fullReport(), researchWithFailedSource());

        assertThat(html)
                .contains("Daily Tech Digest")
                .contains("Top insights dnia")
                .contains("Krytyczne trendy")
                .contains("W tym tygodniu wraca")
                .contains("Top picks (1)")
                .contains("Signals (2)")
                .contains("z ostrze")
                .contains("&lt;unsafe&gt;")
                .contains("12k &#9733;")
                .contains("42 &#10084;")
                .contains("8.8k pkt");
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
    void buildHtmlOmitsLongTailAndLowScoreItems() {
        String html = builder.buildHtml(fullReport(), researchWithFailedSource());

        assertThat(html)
                .doesNotContain("Long tail")
                .doesNotContain("Weak signal");   // score 2 < signal threshold, no long-tail section
    }

    @Test
    void buildHtmlHandlesEmptyOptionalSections() {
        ReportData report = new ReportData(null, null, null, null, null, null);

        String html = builder.buildHtml(report, null);

        assertThat(html)
                .contains("daily digest tech news")
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
                List.of(new TrendInsight("Java", 3, "Trend narrative", List.of("Example A", "Example B"))),
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
