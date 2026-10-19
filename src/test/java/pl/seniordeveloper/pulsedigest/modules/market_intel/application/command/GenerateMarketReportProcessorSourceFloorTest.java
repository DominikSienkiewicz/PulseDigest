package pl.seniordeveloper.pulsedigest.modules.market_intel.application.command;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.CalibrationService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.EditionDeliveryGuard;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.EolRadarService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.MarketIntelJobTracker;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.MarketResearchService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.ReaderProfileService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.ReadingTimeService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.SavedBacklogService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.SelfCritiqueService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.SemanticClusteringService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.SignalScoringService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.SourceYieldService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.StackMatchService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.TrendVelocityService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.WeeklyRecapService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.DeliveryGuardPolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.EolPolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.FeedbackNudgePolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.ReaderProfilePolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.ReportHistoryPolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.SavedBacklogPolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.ScoringPolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.SelfCritiquePolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.SemanticClusteringPolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.SourceFloorPolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.CritiqueVerdict;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.EmailDeliveryReceipt;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.HackerNewsPost;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.QuotaAlert;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RawCounts;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportData;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportJob;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportJobStatus;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RssItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SoftwareRelease;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchResult;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceFetchReport;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.DependencyProfilePort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.EditionArchivePort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.EditionDeliveryPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.EditionInputPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.EmailDeliveryPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.FeedbackPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.LlmSynthesisPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.ReaderProfileDistillerPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.ReaderProfilePort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.ReportHistoryPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.ReportStoragePort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.RunSummaryPort;
import pl.seniordeveloper.pulsedigest.testing.ResearchResults;
import pl.seniordeveloper.pulsedigest.testing.TestModels;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F10. The floor under an edition's source breadth.
 *
 * <p>Lives beside {@link GenerateMarketReportProcessorTest} rather than inside it because that file
 * is within thirty lines of the 500-line ceiling Checkstyle enforces — the same reason
 * {@code MarketResearchServiceLabAnnouncementsTest} is a class of its own.
 *
 * <p>Every case here supplies a fetch ledger, because the ledger is what the floor reads: a run that
 * recorded no source attempt is a run with nothing to hold to a floor.
 */
class GenerateMarketReportProcessorSourceFloorTest {

    private static final int FLOOR = 3;
    private static final int SECTION_FLOOR = 3;
    private static final SoftwareRelease SOFTWARE_RELEASE = new SoftwareRelease(
            "spring-projects/spring-boot", "4.1.0", "notes", "https://example.com/release",
            LocalDateTime.now());
    private static final SavedBacklogPolicy SAVED_SHELF_ON = new SavedBacklogPolicy(true, 5, 30);

    private final MarketIntelJobTracker jobTracker = new MarketIntelJobTracker();
    private final MarketResearchService researchService = mock(MarketResearchService.class);
    private final LlmSynthesisPort synthesisPort = mock(LlmSynthesisPort.class);
    private final ReportStoragePort storagePort = mock(ReportStoragePort.class);
    private final EmailDeliveryPort emailPort = mock(EmailDeliveryPort.class);
    private final FeedbackPort feedbackPort = mock(FeedbackPort.class);
    private final ReportHistoryPort reportHistoryPort = mock(ReportHistoryPort.class);
    private final ReaderProfilePort readerProfilePort = mock(ReaderProfilePort.class);
    private final ReaderProfileDistillerPort readerProfileDistiller = mock(ReaderProfileDistillerPort.class);
    private final EditionDeliveryPort editionDeliveryPort = mock(EditionDeliveryPort.class);

    @BeforeEach
    void letAHealthyRunReachTheSend() {
        when(editionDeliveryPort.claim(any(), any())).thenReturn(true);
        when(synthesisPort.synthesize(any())).thenReturn(sampleReport());
        when(emailPort.send(any(), any())).thenReturn(new EmailDeliveryReceipt("resend", "{}"));
        when(emailPort.sendQuotaAlert(any())).thenReturn(new EmailDeliveryReceipt("resend", "{}"));
    }

    @Test
    void aRunThatLostAlmostEverySourceStopsBeforeSynthesis() {
        String jobId = "job-floor-collapsed";
        jobTracker.track(ReportJob.pending(jobId));
        when(researchService.fetchAndFilter()).thenReturn(researchWith(ledger(1, 18, 0)));

        processor().process(jobId);

        verify(synthesisPort, never()).synthesize(any());
        verify(storagePort, never()).save(any());
        verify(emailPort, never()).send(any(), any());
        assertThat(jobTracker.getJob(jobId).orElseThrow().status()).isEqualTo(ReportJobStatus.ERROR);
    }

    @Test
    void theAlertForANarrowRunNamesTheShortfallAndTheFloor() {
        String jobId = "job-floor-alert";
        jobTracker.track(ReportJob.pending(jobId));
        when(researchService.fetchAndFilter()).thenReturn(researchWith(ledger(1, 18, 0)));

        processor().process(jobId);

        ArgumentCaptor<QuotaAlert> alert = ArgumentCaptor.forClass(QuotaAlert.class);
        verify(emailPort).sendQuotaAlert(alert.capture());
        assertThat(alert.getValue().detail())
                .contains("18 z 19")
                .contains("próg: 3");
    }

    @Test
    void aRunWithEnoughActiveSourcesProceeds() {
        String jobId = "job-floor-met";
        jobTracker.track(ReportJob.pending(jobId));
        when(researchService.fetchAndFilter()).thenReturn(researchWith(ledger(FLOOR, 16, 0)));

        processor().process(jobId);

        verify(synthesisPort).synthesize(any());
        verify(emailPort).send(any(), any());
        verify(emailPort, never()).sendQuotaAlert(any());
        assertThat(jobTracker.getJob(jobId).orElseThrow().status()).isEqualTo(ReportJobStatus.DELIVERED);
    }

    @Test
    void sourcesDisabledByConfigurationDoNotPushARunBelowTheFloor() {
        // K7 switched the three X searches off. That is a decision, not a degradation: a run where
        // every source anybody asked for answered must ship, even when there are fewer of them than
        // the configured floor names.
        String jobId = "job-floor-disabled";
        jobTracker.track(ReportJob.pending(jobId));
        when(researchService.fetchAndFilter()).thenReturn(researchWith(ledger(2, 0, 3)));

        processor().process(jobId);

        verify(synthesisPort).synthesize(any());
        assertThat(jobTracker.getJob(jobId).orElseThrow().status()).isEqualTo(ReportJobStatus.DELIVERED);
    }

    @Test
    void aSourceThatAnsweredWithNothingDoesNotCountTowardsTheFloor() {
        // The decision the floor rests on: breadth means sources that brought something back, not
        // sources that were reachable. Eighteen healthy-but-silent sources are an empty edition.
        String jobId = "job-floor-silent";
        jobTracker.track(ReportJob.pending(jobId));
        List<SourceFetchReport> mostlySilent = new ArrayList<>(ledger(1, 0, 0));
        for (int i = 0; i < 18; i++) {
            mostlySilent.add(SourceFetchReport.success("silent-" + i, 0, 12L));
        }
        when(researchService.fetchAndFilter()).thenReturn(researchWith(mostlySilent));

        processor().process(jobId);

        verify(synthesisPort, never()).synthesize(any());
        assertThat(jobTracker.getJob(jobId).orElseThrow().status()).isEqualTo(ReportJobStatus.ERROR);
    }

    @Test
    void theFailureAlertCountsSourcesInsteadOfClaimingAWindowThatNoLongerExists() {
        // The alert used to say the sources were empty "w oknie 24h". The lookbacks have been per
        // source, up to 104h, since 35ec589 — so the one number in the sentence was the false one.
        String jobId = "job-empty-ledger";
        jobTracker.track(ReportJob.pending(jobId));
        when(researchService.fetchAndFilter()).thenReturn(ResearchResults.builder()
                .collectedAt(LocalDateTime.now())
                .fetchReports(ledger(0, 3, 2))
                .build());

        processor().process(jobId);

        ArgumentCaptor<QuotaAlert> alert = ArgumentCaptor.forClass(QuotaAlert.class);
        verify(emailPort).sendQuotaAlert(alert.capture());
        assertThat(alert.getValue().detail())
                .doesNotContain("24h")
                .contains("3 z 3")
                .contains("wyłączonych konfiguracją: 2");
    }

    /**
     * A fetch ledger: sources that returned items, sources that blew up, and sources configuration
     * switched off before the run even tried them.
     */
    @Test
    void aRunWhoseSourcesAllAnsweredButWhoseFiltersTookEverythingStopsBeforeSynthesis() {
        // N60. Every source healthy, so F10's floor is satisfied — and one section carries content,
        // because the ledger counts what each source fetched, not what survived the filters.
        String jobId = "job-thin-after-filters";
        jobTracker.track(ReportJob.pending(jobId));
        when(researchService.fetchAndFilter()).thenReturn(thinResearchWith(ledger(19, 0, 0)));

        processor().process(jobId);

        verify(synthesisPort, never()).synthesize(any());
        verify(emailPort, never()).send(any(), any());
        assertThat(jobTracker.getJob(jobId).orElseThrow().status()).isEqualTo(ReportJobStatus.ERROR);
    }

    @Test
    void theAlertForAThinRunSaysWhereTheContentWentAndWhatTheFloorIs() {
        String jobId = "job-thin-alert";
        jobTracker.track(ReportJob.pending(jobId));
        when(researchService.fetchAndFilter()).thenReturn(thinResearchWith(ledger(19, 0, 0)));

        processor().process(jobId);

        ArgumentCaptor<QuotaAlert> alert = ArgumentCaptor.forClass(QuotaAlert.class);
        verify(emailPort).sendQuotaAlert(alert.capture());
        assertThat(alert.getValue().detail())
                .as("the alert must distinguish this from an outage: the sources answered, the content "
                        + "did not survive")
                .contains("Po filtrach")
                .contains("próg to 3");
    }

    @Test
    void theThinFloorCannotAskForMoreSectionsThanThereWereLiveSources() {
        // Several sources feed one section, so contributing sections can never exceed live sources.
        // A run with one live source therefore cannot be asked for three sections — an unreachable
        // floor would abort every edition, which is the failure the cap exists to prevent. Here the
        // single live source clears the fetch floor only because that floor is capped too.
        String jobId = "job-thin-capped";
        jobTracker.track(ReportJob.pending(jobId));
        when(researchService.fetchAndFilter()).thenReturn(thinResearchWith(ledger(1, 0, 18)));

        processor().process(jobId);

        verify(synthesisPort).synthesize(any());
        assertThat(jobTracker.getJob(jobId).orElseThrow().status()).isEqualTo(ReportJobStatus.DELIVERED);
    }

    private static List<SourceFetchReport> ledger(int active, int failed, int disabled) {
        List<SourceFetchReport> reports = new ArrayList<>();
        for (int i = 0; i < active; i++) {
            reports.add(SourceFetchReport.success("active-" + i, 4, 12L));
        }
        for (int i = 0; i < failed; i++) {
            reports.add(SourceFetchReport.failed("down-" + i, 12L, "connection timeout"));
        }
        for (int i = 0; i < disabled; i++) {
            reports.add(SourceFetchReport.disabled("off-" + i));
        }
        return reports;
    }

    /**
     * Content in three sections, so the run reaches the source floor rather than being stopped by the
     * emptiness check or by N60's post-filter floor. Three is not arbitrary: it is the section floor,
     * so these cases exercise the breadth check they are about and nothing else.
     */
    private static ResearchResult researchWith(List<SourceFetchReport> ledger) {
        return ResearchResults.builder()
                .hackerNews(List.of(new HackerNewsPost("Java news", "https://example.com/java", 1200)))
                .rss(List.of(new RssItem("Release notes", "https://example.com/rss",
                        "what the feed said", "InfoQ")))
                .releases(List.of(SOFTWARE_RELEASE))
                .collectedAt(LocalDateTime.now())
                .rawCounts(RawCounts.builder().hackerNews(1).rss(1).build())
                .fetchReports(ledger)
                .build();
    }

    /** One section with content: what a run looks like when the filters took almost everything (N60). */
    private static ResearchResult thinResearchWith(List<SourceFetchReport> ledger) {
        return ResearchResults.builder()
                .hackerNews(List.of(new HackerNewsPost("Java news", "https://example.com/java", 1200)))
                .collectedAt(LocalDateTime.now())
                .rawCounts(RawCounts.builder().hackerNews(1).build())
                .fetchReports(ledger)
                .build();
    }

    private static ReportData sampleReport() {
        return new ReportData("preview", "editorial", List.of("insight"),
                List.of(TestModels.digestItem("Java news", "https://example.com/java", "Hacker News",
                        "Java/JVM", "DISCUSSION", 8, 1200, "summary", "why it matters")));
    }

    private GenerateMarketReportProcessor processor() {
        return new GenerateMarketReportProcessor(
                jobTracker,
                researchService,
                synthesisPort,
                storagePort,
                emailPort,
                mock(EditionArchivePort.class),
                mock(RunSummaryPort.class),
                mock(EditionInputPort.class),
                mock(DependencyProfilePort.class),
                profile -> List.of(),
                new StackMatchService(),
                new CalibrationService(),
                new EolRadarService(new EolPolicy(false, 6, null, List.of())),
                new SavedBacklogService(SAVED_SHELF_ON),
                new ReadingTimeService(),
                new SelfCritiqueService(items -> CritiqueVerdict.NOTHING,
                        new SelfCritiquePolicy(false, false)),
                new SemanticClusteringService(texts -> List.of(),
                        new SemanticClusteringPolicy(false, false, 0.86, 4)),
                today -> List.of(),
                SAVED_SHELF_ON,
                new EditionDeliveryGuard(new DeliveryGuardPolicy(true), editionDeliveryPort),
                new SignalScoringService(ScoringPolicy.defaults()),
                feedbackPort,
                new FeedbackNudgePolicy(true, 30),
                reportHistoryPort,
                new WeeklyRecapService(),
                new SourceYieldService(),
                new TrendVelocityService(),
                new ReaderProfileService(readerProfilePort, readerProfileDistiller,
                        new ReaderProfilePolicy(false, 10, 7, 60)),
                new ReportHistoryPolicy(true, 21),
                new SourceFloorPolicy(FLOOR, SECTION_FLOOR));
    }
}
