package pl.seniordeveloper.pulsedigest.modules.market_intel.application.command;

import pl.seniordeveloper.pulsedigest.modules.market_intel.application.EditionDeliveryGuard;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RawCounts;

import pl.seniordeveloper.pulsedigest.testing.ResearchResults;

import pl.seniordeveloper.pulsedigest.testing.TestModels;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.ScoringPolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.MarketIntelJobTracker;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.MarketResearchService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.CalibrationService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.SignalScoringService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.StackMatchService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.ReaderProfileService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.SourceYieldService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.TrendVelocityService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.WeeklyRecapService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.FeedbackNudgePolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.ReaderProfilePolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.ReportHistoryPolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.SourceFloorPolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.EolRadarService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.ReadingTimeService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.SelfCritiqueService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.SemanticClusteringPolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.SemanticClusteringService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.SelfCritiquePolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.CritiqueVerdict;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.SavedBacklogService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.DeliveryGuardPolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.SavedBacklogPolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.EditionDeliveryPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PersistedReport;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.EolPolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.EolNotice;
import org.mockito.ArgumentCaptor;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ApiAccounts;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.EmailDeliveryReceipt;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.HackerNewsPost;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.QuotaAlert;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportData;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportJob;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportJobStatus;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchResult;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceFetchReport;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.EmailDeliveryPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.FeedbackPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.LlmSynthesisPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.ReaderProfileDistillerPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.ReaderProfilePort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.ReportHistoryPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.EditionArchivePort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.DependencyProfilePort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.EditionInputPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.ReleaseRadarPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.ReportStoragePort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.RunSummaryPort;

import java.time.LocalDate;
import java.util.Optional;
import java.time.Instant;
import java.time.Duration;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SavedItem;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GenerateMarketReportProcessorTest {

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
    void grantTheDeliveryClaim() {
        // F16's guard is on in every test, so the claim has to be granted for the existing cases to
        // reach the send at all. The cases that revoke it stub it themselves.
        when(editionDeliveryPort.claim(any(), any())).thenReturn(true);
    }

    @Test
    void marksJobDeliveredOnlyAfterEmailDeliverySucceeds() {
        String jobId = "job-delivered";
        ResearchResult research = sampleResearch();
        ReportData report = sampleReport();
        jobTracker.track(ReportJob.pending(jobId));
        when(researchService.fetchAndFilter()).thenReturn(research);
        when(synthesisPort.synthesize(research)).thenReturn(report);
        when(emailPort.send(any(), eq(research)))
                .thenReturn(new EmailDeliveryReceipt("resend", "{\"id\":\"email-1\"}"));

        processor().process(jobId);

        ReportJob job = jobTracker.getJob(jobId).orElseThrow();
        assertThat(job.status()).isEqualTo(ReportJobStatus.DELIVERED);
        assertThat(job.report()).isNotNull();
        verify(storagePort).save(any());
        verify(emailPort).send(any(), eq(research));
    }

    @Test
    void readsReportHistoryBeforePersistingThisEdition() {
        // Reading after the save would count the edition being assembled as its own history.
        String jobId = "job-history-order";
        ResearchResult research = sampleResearch();
        jobTracker.track(ReportJob.pending(jobId));
        when(researchService.fetchAndFilter()).thenReturn(research);
        when(synthesisPort.synthesize(research)).thenReturn(sampleReport());
        when(emailPort.send(any(), any())).thenReturn(new EmailDeliveryReceipt("resend", "{}"));

        processor().process(jobId);

        InOrder ordered = inOrder(reportHistoryPort, storagePort);
        ordered.verify(reportHistoryPort).recentEditions(21);
        ordered.verify(storagePort).save(any());
    }

    @Test
    void unavailableHistoryDegradesToAnAmnesiacDigestRatherThanLosingTheRun() {
        String jobId = "job-history-down";
        ResearchResult research = sampleResearch();
        jobTracker.track(ReportJob.pending(jobId));
        when(researchService.fetchAndFilter()).thenReturn(research);
        when(synthesisPort.synthesize(research)).thenReturn(sampleReport());
        when(reportHistoryPort.recentEditions(anyInt())).thenThrow(new IllegalStateException("db down"));
        when(emailPort.send(any(), any())).thenReturn(new EmailDeliveryReceipt("resend", "{}"));

        processor().process(jobId);

        assertThat(jobTracker.getJob(jobId).orElseThrow().status()).isEqualTo(ReportJobStatus.DELIVERED);
    }

    @Test
    void preservesGeneratedReportWhenEmailDeliveryFails() {
        String jobId = "job-email-failed";
        ResearchResult research = sampleResearch();
        ReportData report = sampleReport();
        jobTracker.track(ReportJob.pending(jobId));
        when(researchService.fetchAndFilter()).thenReturn(research);
        when(synthesisPort.synthesize(research)).thenReturn(report);
        when(emailPort.send(any(), eq(research)))
                .thenThrow(new IllegalStateException("resend unavailable"));

        processor().process(jobId);

        ReportJob job = jobTracker.getJob(jobId).orElseThrow();
        assertThat(job.status()).isEqualTo(ReportJobStatus.EMAIL_FAILED);
        assertThat(job.report()).isNotNull();
        assertThat(job.error()).contains("resend unavailable");
    }

    @Test
    void sendsQuotaAlertNamingLlmWhenSynthesisFailsOnExhaustedCredits() {
        String jobId = "job-llm-quota";
        ResearchResult research = sampleResearch();
        jobTracker.track(ReportJob.pending(jobId));
        when(researchService.fetchAndFilter()).thenReturn(research);
        when(synthesisPort.synthesize(research))
                .thenThrow(new RuntimeException("429 Too Many Requests: insufficient_quota"));

        processor().process(jobId);

        ArgumentCaptor<QuotaAlert> captor = ArgumentCaptor.forClass(QuotaAlert.class);
        verify(emailPort).sendQuotaAlert(captor.capture());
        assertThat(captor.getValue().exhaustedAccounts()).contains(ApiAccounts.LLM);

        ReportJob job = jobTracker.getJob(jobId).orElseThrow();
        assertThat(job.status()).isEqualTo(ReportJobStatus.ERROR);
    }

    @Test
    void doesNotSendQuotaAlertOnOrdinaryDeliveryFailure() {
        String jobId = "job-plain-failure";
        ResearchResult research = sampleResearch();
        jobTracker.track(ReportJob.pending(jobId));
        when(researchService.fetchAndFilter()).thenReturn(research);
        when(synthesisPort.synthesize(research)).thenReturn(sampleReport());
        when(emailPort.send(any(), any())).thenThrow(new IllegalStateException("resend unavailable"));

        processor().process(jobId);

        verify(emailPort, never()).sendQuotaAlert(any());
    }

    @Test
    void sendsFailureAlertWhenSynthesisFailsWithoutQuotaSignal() {
        String jobId = "job-generic-failure";
        ResearchResult research = sampleResearch();
        jobTracker.track(ReportJob.pending(jobId));
        when(researchService.fetchAndFilter()).thenReturn(research);
        when(synthesisPort.synthesize(research)).thenThrow(new RuntimeException("unexpected boom"));

        processor().process(jobId);

        ArgumentCaptor<QuotaAlert> captor = ArgumentCaptor.forClass(QuotaAlert.class);
        verify(emailPort).sendQuotaAlert(captor.capture());
        assertThat(captor.getValue().detail()).contains("unexpected boom");

        ReportJob job = jobTracker.getJob(jobId).orElseThrow();
        assertThat(job.status()).isEqualTo(ReportJobStatus.ERROR);
    }

    @Test
    void sendsFailureAlertWhenAllSourcesEmptyEvenWithoutQuotaSignal() {
        String jobId = "job-empty-noquota";
        jobTracker.track(ReportJob.pending(jobId));
        ResearchResult emptyNonQuota = ResearchResults.builder()
                .collectedAt(LocalDateTime.now())
                .fetchReports(List.of(SourceFetchReport.failed("RSS", 12L, "connection timeout")))
                .build();
        when(researchService.fetchAndFilter()).thenReturn(emptyNonQuota);

        processor().process(jobId);

        verify(emailPort).sendQuotaAlert(any());

        ReportJob job = jobTracker.getJob(jobId).orElseThrow();
        assertThat(job.status()).isEqualTo(ReportJobStatus.ERROR);
    }

    @Test
    void sendsQuotaAlertWhenAllSourcesEmptyAndOneIsRateLimited() {
        String jobId = "job-empty-ratelimited";
        jobTracker.track(ReportJob.pending(jobId));
        ResearchResult emptyButRateLimited = ResearchResults.builder()
                .collectedAt(LocalDateTime.now())
                .fetchReports(List.of(SourceFetchReport.failed("Twitter/X topic", 12L, "429 Too Many Requests")))
                .build();
        when(researchService.fetchAndFilter()).thenReturn(emptyButRateLimited);

        processor().process(jobId);

        ArgumentCaptor<QuotaAlert> captor = ArgumentCaptor.forClass(QuotaAlert.class);
        verify(emailPort).sendQuotaAlert(captor.capture());
        assertThat(captor.getValue().exhaustedAccounts()).contains("Twitter/X API");
    }

    @Test
    void attachesAnAlreadyPastEndOfLifeDateToTheReport() {
        // N08. The radar is wired behind scoring, so a date it reports has to survive every later
        // enrichment step and reach the persisted report — the only place the mail can read it from.
        String jobId = "job-eol";
        ResearchResult research = sampleResearch();
        jobTracker.track(ReportJob.pending(jobId));
        when(researchService.fetchAndFilter()).thenReturn(research);
        when(synthesisPort.synthesize(research)).thenReturn(sampleReport());
        when(emailPort.send(any(), any())).thenReturn(new EmailDeliveryReceipt("resend", "{}"));
        LocalDate checked = LocalDate.now(ZoneOffset.UTC);
        EolRadarService radar = new EolRadarService(new EolPolicy(true, 6, checked,
                List.of(new EolNotice("Eclipse Temurin 26", checked.minusMonths(1), checked))));

        processor(radar).process(jobId);

        ArgumentCaptor<PersistedReport> captor = ArgumentCaptor.forClass(PersistedReport.class);
        verify(storagePort).save(captor.capture());
        assertThat(captor.getValue().report().eolNotices())
                .extracting(EolNotice::name)
                .containsExactly("Eclipse Temurin 26");
    }

    @Test
    void carriesTheReadersSavedShelfIntoTheReport() {
        // N05 follow-up. The shelf is read from the same feedback rows the votes come from and the
        // titles from history, so this proves both reach the persisted report rather than one of them.
        String jobId = "job-saved";
        ResearchResult research = sampleResearch();
        ReportData report = sampleReport();
        String savedUrl = report.items().getFirst().url();
        jobTracker.track(ReportJob.pending(jobId));
        when(researchService.fetchAndFilter()).thenReturn(research);
        when(synthesisPort.synthesize(research)).thenReturn(report);
        when(emailPort.send(any(), any())).thenReturn(new EmailDeliveryReceipt("resend", "{}"));
        when(feedbackPort.savedItems(30)).thenReturn(List.of(new SavedItem(null, savedUrl, "Hacker News", "Java/JVM",
                        Instant.now().minus(Duration.ofDays(4)))));

        processor(new EolRadarService(new EolPolicy(false, 6, null, List.of()))).process(jobId);

        ArgumentCaptor<PersistedReport> captor = ArgumentCaptor.forClass(PersistedReport.class);
        verify(storagePort).save(captor.capture());
        assertThat(captor.getValue().report().savedBacklog().shown())
                .extracting(SavedItem::url)
                .containsExactly(savedUrl);
    }

    @Test
    void refusesToSendAnEditionThatAlreadyWentOut() {
        // F16's cheap half. The point is not only that no email is sent — it is that the run stops
        // before fetching a single source, so a duplicate dispatch costs nothing at all.
        String jobId = "job-duplicate";
        jobTracker.track(ReportJob.pending(jobId));
        when(editionDeliveryPort.deliveredAt(any())).thenReturn(Optional.of(Instant.now()));

        processor().process(jobId);

        verify(researchService, never()).fetchAndFilter();
        verify(emailPort, never()).send(any(), any());
        assertThat(jobTracker.getJob(jobId).orElseThrow().status()).isEqualTo(ReportJobStatus.ERROR);
    }

    @Test
    void refusesToSendWhenAnotherRunHoldsTheClaim() {
        // F16's authoritative half: two runs that both passed the advisory check reach here, and the
        // conditional insert tells exactly one of them no.
        String jobId = "job-claim-lost";
        ResearchResult research = sampleResearch();
        jobTracker.track(ReportJob.pending(jobId));
        when(researchService.fetchAndFilter()).thenReturn(research);
        when(synthesisPort.synthesize(research)).thenReturn(sampleReport());
        when(editionDeliveryPort.claim(any(), any())).thenReturn(false);

        processor().process(jobId);

        verify(emailPort, never()).send(any(), any());
        assertThat(jobTracker.getJob(jobId).orElseThrow().status()).isEqualTo(ReportJobStatus.ERROR);
    }

    @Test
    void recordsTheDeliveryOnlyAfterTheEmailActuallyWentOut() {
        // Marking before the send would turn a failed send into a permanently blocked edition —
        // the retry case the whole claim/deliver split exists to keep open.
        String jobId = "job-claim-marked";
        ResearchResult research = sampleResearch();
        jobTracker.track(ReportJob.pending(jobId));
        when(researchService.fetchAndFilter()).thenReturn(research);
        when(synthesisPort.synthesize(research)).thenReturn(sampleReport());
        when(emailPort.send(any(), any())).thenReturn(new EmailDeliveryReceipt("resend", "{}"));

        processor().process(jobId);

        InOrder ordered = inOrder(editionDeliveryPort, emailPort);
        ordered.verify(editionDeliveryPort).claim(any(), eq(jobId));
        ordered.verify(emailPort).send(any(), any());
        ordered.verify(editionDeliveryPort).markDelivered(any(), eq(jobId));
    }

    @Test
    void leavesTheEditionRetryableWhenTheSendItselfFails() {
        String jobId = "job-send-failed";
        ResearchResult research = sampleResearch();
        jobTracker.track(ReportJob.pending(jobId));
        when(researchService.fetchAndFilter()).thenReturn(research);
        when(synthesisPort.synthesize(research)).thenReturn(sampleReport());
        when(emailPort.send(any(), any())).thenThrow(new RuntimeException("resend down"));

        processor().process(jobId);

        verify(editionDeliveryPort, never()).markDelivered(any(), any());
    }

    @Test
    void anUnreadableGuardDoesNotStopTheFirstSendButDoesStopAnUnclaimableOne() {
        // The two failure directions are deliberately opposite. The advisory read fails OPEN — a
        // database that cannot answer must not become the reason an edition never goes out. The
        // claim fails CLOSED — it is the last gate before an irreversible action.
        String jobId = "job-guard-unreadable";
        ResearchResult research = sampleResearch();
        jobTracker.track(ReportJob.pending(jobId));
        when(researchService.fetchAndFilter()).thenReturn(research);
        when(synthesisPort.synthesize(research)).thenReturn(sampleReport());
        when(editionDeliveryPort.deliveredAt(any())).thenThrow(new RuntimeException("db down"));
        when(editionDeliveryPort.claim(any(), any())).thenThrow(new RuntimeException("db down"));

        processor().process(jobId);

        verify(researchService).fetchAndFilter();
        verify(emailPort, never()).send(any(), any());
    }

    private GenerateMarketReportProcessor processor() {
        // Silent by default: the end-of-life radar is time-dependent, and letting it speak in every
        // existing test would make unrelated assertions start failing on a calendar date.
        return processor(new EolRadarService(new EolPolicy(false, 6, null, List.of())));
    }

    private GenerateMarketReportProcessor processor(EolRadarService eolRadarService) {
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
                releaseRadar(),
                new StackMatchService(),
                new CalibrationService(),
                eolRadarService,
                new SavedBacklogService(SAVED_SHELF_ON),
                new ReadingTimeService(),
                // Critique off in the pipeline tests: it is SelfCritiqueServiceTest's subject, and a
                // stub that silently returned "nothing to cut" would make these pass for the wrong reason.
                new SelfCritiqueService(items -> CritiqueVerdict.NOTHING,
                        new SelfCritiquePolicy(false, false)),
                // Clustering off here for the same reason the critique is: it is
                // SemanticClusteringServiceTest's subject, and an embedder that returned nothing
                // would make these pass without exercising anything.
                new SemanticClusteringService(texts -> java.util.List.of(),
                        new SemanticClusteringPolicy(false, false, 0.86, 4)),
                today -> java.util.List.of(),
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
                // F10's floor is wired in here and inert in these cases by construction: the
                // fixtures below carry no fetch ledger, so the run attempted no source the floor
                // could ask about. The cases that exercise it supply one — see
                // GenerateMarketReportProcessorSourceFloorTest.
                new SourceFloorPolicy(3, 3));
    }

    /** An empty radar, not a bare mock: a null return would blow up {@code withDependencyReleases}. */
    private static ReleaseRadarPort releaseRadar() {
        return profile -> java.util.List.of();
    }

    private static ReportData sampleReport() {
        return new ReportData(
                "preview",
                "editorial",
                List.of("insight"),
                List.of(TestModels.digestItem(
                        "Java news",
                        "https://example.com/java",
                        "Hacker News",
                        "Java/JVM",
                        "DISCUSSION",
                        8,
                        1200,
                        "summary",
                        "why it matters")));
    }

    private static ResearchResult sampleResearch() {
        return ResearchResults.builder()
                .hackerNews(List.of(new HackerNewsPost("Java news", "https://example.com/java", 1200)))
                .collectedAt(LocalDateTime.now())
                .rawCounts(RawCounts.builder().hackerNews(1).build())
                .build();
    }
}
