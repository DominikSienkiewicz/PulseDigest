package pl.seniordeveloper.pulsedigest.modules.market_intel.application.command;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.MarketIntelJobTracker;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.MarketResearchService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.SignalScoringService;
import org.mockito.ArgumentCaptor;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ApiAccounts;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.EmailDeliveryReceipt;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.HackerNewsPost;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.QuotaAlert;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportData;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportJob;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportJobStatus;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchResult;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceFetchReport;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.EmailDeliveryPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.LlmSynthesisPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.ReportStoragePort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.TechDemandNarratorPort;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GenerateMarketReportProcessorTest {

    private final MarketIntelJobTracker jobTracker = new MarketIntelJobTracker();
    private final MarketResearchService researchService = mock(MarketResearchService.class);
    private final LlmSynthesisPort synthesisPort = mock(LlmSynthesisPort.class);
    private final ReportStoragePort storagePort = mock(ReportStoragePort.class);
    private final EmailDeliveryPort emailPort = mock(EmailDeliveryPort.class);
    private final TechDemandNarratorPort techDemandNarrator = mock(TechDemandNarratorPort.class);

    @Test
    void marksJobDeliveredOnlyAfterEmailDeliverySucceeds() {
        String jobId = "job-delivered";
        ResearchResult research = sampleResearch();
        ReportData report = sampleReport();
        jobTracker.track(ReportJob.pending(jobId));
        when(researchService.fetchAndFilter()).thenReturn(research);
        when(synthesisPort.synthesize(research)).thenReturn(report);
        when(emailPort.send(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(research)))
                .thenReturn(new EmailDeliveryReceipt("resend", "{\"id\":\"email-1\"}"));

        processor().process(jobId);

        ReportJob job = jobTracker.getJob(jobId).orElseThrow();
        assertThat(job.status()).isEqualTo(ReportJobStatus.DELIVERED);
        assertThat(job.report()).isNotNull();
        verify(storagePort).save(org.mockito.ArgumentMatchers.any());
        verify(emailPort).send(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(research));
    }

    @Test
    void preservesGeneratedReportWhenEmailDeliveryFails() {
        String jobId = "job-email-failed";
        ResearchResult research = sampleResearch();
        ReportData report = sampleReport();
        jobTracker.track(ReportJob.pending(jobId));
        when(researchService.fetchAndFilter()).thenReturn(research);
        when(synthesisPort.synthesize(research)).thenReturn(report);
        when(emailPort.send(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(research)))
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
    void sendsQuotaAlertWhenAllSourcesEmptyAndOneIsRateLimited() {
        String jobId = "job-empty-ratelimited";
        jobTracker.track(ReportJob.pending(jobId));
        ResearchResult emptyButRateLimited = new ResearchResult(
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(),
                LocalDateTime.now(), 0, 0, 0, 0, 0,
                List.of(SourceFetchReport.failed("Twitter/X topic", 12L, "429 Too Many Requests")));
        when(researchService.fetchAndFilter()).thenReturn(emptyButRateLimited);

        processor().process(jobId);

        ArgumentCaptor<QuotaAlert> captor = ArgumentCaptor.forClass(QuotaAlert.class);
        verify(emailPort).sendQuotaAlert(captor.capture());
        assertThat(captor.getValue().exhaustedAccounts()).contains("Twitter/X API");
    }

    private GenerateMarketReportProcessor processor() {
        return new GenerateMarketReportProcessor(
                jobTracker,
                researchService,
                synthesisPort,
                storagePort,
                emailPort,
                Optional.empty(),
                new SignalScoringService(),
                techDemandNarrator);
    }

    private static ReportData sampleReport() {
        return new ReportData(
                "preview",
                "editorial",
                List.of("insight"),
                List.of(new DigestItem(
                        "Java news",
                        "https://example.com/java",
                        "Hacker News",
                        "Java/JVM",
                        "DISCUSSION",
                        8,
                        1200,
                        "summary")));
    }

    private static ResearchResult sampleResearch() {
        return new ResearchResult(
                List.of(),
                List.of(new HackerNewsPost("Java news", "https://example.com/java", 1200)),
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
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                LocalDateTime.now(),
                0,
                1,
                0,
                0,
                0);
    }
}
