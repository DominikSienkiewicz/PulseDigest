package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ArchivedEdition;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.EmailDeliveryReceipt;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PastEdition;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.QuotaAlert;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReaderProfile;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportData;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchResult;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Retrospective;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SavedItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.EditionDeliveryPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.EditionInputPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.EmailDeliveryPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.FeedbackPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.PublishedUrlsPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.ReaderProfilePort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.ReportHistoryPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.ReportStoragePort;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The {@code preview} profile: the real pipeline with the two irreversible ends unplugged (F12).
 *
 * <p>What this answers is the last part of F12 — clone-and-run ends at "it compiles". {@code
 * --dry-run} re-renders a stored edition and needs nothing at all, which proves the rendering chain
 * but not the product: nothing is fetched and nothing is written by a model. A live run needs three
 * accounts (OpenAI, Resend, Supabase) because it mails a reader and keeps a record. {@code --preview}
 * is the middle that was missing: today's real sources, a real synthesis, the whole enrichment
 * chain, and then the edition written to disk instead of to an inbox and a database. One paid key
 * (OpenAI), no mail account, no database.
 *
 * <p><strong>Why no-op ports rather than a preview pipeline.</strong> The thing worth showing is the
 * pipeline, and a second code path that skipped persistence would be a different pipeline that
 * drifts from the real one — the demo would gradually stop demonstrating. So
 * {@link pl.seniordeveloper.pulsedigest.modules.market_intel.application.command.GenerateMarketReportProcessor}
 * runs completely unmodified, with every database-backed port replaced by one that returns nothing
 * and every delivery port by one that logs what it would have done. The enrichment steps that read
 * history therefore see an empty history, which is exactly what a first run on a new installation
 * sees; the sections that need a past edition are absent for the same reason they would be absent on
 * day one, and that is honest rather than hidden.
 *
 * <p>The beans are {@link Primary} rather than replacements: the Supabase adapters are still
 * constructed (they need only a {@code DataSource} bean, and Hikari connects lazily), they are simply
 * never the ones injected. Nothing dials the fake database URL in {@code application-preview.yaml}.
 *
 * <p>That the guarantee lives in the beans and not in the properties is deliberate. A property can be
 * overridden from the environment — {@code .env} and real exported credentials outrank a profile's
 * yaml — so a preview whose safety rested on a fake Resend key would mail a real reader on the first
 * machine that happened to have a real one. These beans are selected by profile alone, so a preview
 * run cannot send or store anything whatever is in the environment.
 */
@Slf4j
@Profile("preview")
@Configuration
public class PreviewConfig {

    /** Says what is off, at the top of the run, where nobody can miss it. */
    @PostConstruct
    void announce() {
        log.warn("=== PREVIEW PROFILE: real sources and a real synthesis; NO mail, NO database, "
                + "empty history ===");
    }

    /** Logs the subject line and the item count instead of sending, and reports success. */
    @Bean
    @Primary
    EmailDeliveryPort previewEmailDeliveryPort() {
        return new EmailDeliveryPort() {
            @Override
            public EmailDeliveryReceipt send(ReportData report, ResearchResult research) {
                log.info("PREVIEW: would mail {} item(s); the rendered edition is on disk instead",
                        report.items() != null ? report.items().size() : 0);
                return new EmailDeliveryReceipt("preview", "nothing was sent");
            }

            @Override
            public EmailDeliveryReceipt sendQuotaAlert(QuotaAlert alert) {
                log.info("PREVIEW: would send a quota alert");
                return new EmailDeliveryReceipt("preview", "nothing was sent");
            }

            @Override
            public EmailDeliveryReceipt sendRetrospective(Retrospective retrospective) {
                log.info("PREVIEW: would send a retrospective");
                return new EmailDeliveryReceipt("preview", "nothing was sent");
            }
        };
    }

    /** Accepts the edition and forgets it. */
    @Bean
    @Primary
    ReportStoragePort previewReportStoragePort() {
        return report -> log.info("PREVIEW: would store the edition; not writing to any database");
    }

    /** Accepts the run's inputs and forgets them. */
    @Bean
    @Primary
    EditionInputPort previewEditionInputPort() {
        return new EditionInputPort() {
            @Override
            public void save(String jobId, ResearchResult research) {
                log.debug("PREVIEW: not storing the inputs of {}", jobId);
            }

            @Override
            public Optional<ResearchResult> load(String jobId) {
                return Optional.empty();
            }
        };
    }

    /**
     * Always grants the claim. The double-send guard (F16) protects an inbox, and a preview has no
     * inbox to protect — refusing the claim would stop the run for a reason that does not exist here.
     */
    @Bean
    @Primary
    EditionDeliveryPort previewEditionDeliveryPort() {
        return new EditionDeliveryPort() {
            @Override
            public boolean claim(LocalDate edition, String jobId) {
                return true;
            }

            @Override
            public void markDelivered(LocalDate edition, String jobId) {
                // Nothing was delivered, so there is nothing to record.
            }

            @Override
            public Optional<Instant> deliveredAt(LocalDate edition) {
                return Optional.empty();
            }
        };
    }

    /** An empty history: what a first run on a new installation genuinely has. */
    @Bean
    @Primary
    ReportHistoryPort previewReportHistoryPort() {
        return new ReportHistoryPort() {
            @Override
            public List<PastEdition> recentEditions(int lookbackDays) {
                return List.of();
            }

            @Override
            public Optional<PastEdition> edition(String jobId) {
                return Optional.empty();
            }

            @Override
            public List<ArchivedEdition> archivedEditions(int lookbackDays) {
                return List.of();
            }
        };
    }

    /** No votes yet, so no nudges, no shelf and no calibration — the day-one state. */
    @Bean
    @Primary
    FeedbackPort previewFeedbackPort() {
        return new FeedbackPort() {
            @Override
            public Set<String> downvotedUrls(int lookbackDays) {
                return Set.of();
            }

            @Override
            public Set<String> upvotedUrls(int lookbackDays) {
                return Set.of();
            }

            @Override
            public List<SavedItem> savedItems(int lookbackDays) {
                return List.of();
            }

            @Override
            public Map<String, Integer> netVotesBySource(int lookbackDays) {
                return Map.of();
            }

            @Override
            public Map<String, Integer> netVotesByCategory(int lookbackDays) {
                return Map.of();
            }
        };
    }

    /** Nothing has been published before, so cross-edition dedup has nothing to remove. */
    @Bean
    @Primary
    PublishedUrlsPort previewPublishedUrlsPort() {
        return new PublishedUrlsPort() {
            @Override
            public Set<String> recentlyPublishedUrls(int lookbackDays) {
                return Set.of();
            }

            @Override
            public List<String> recentlyPublishedTitles(int lookbackDays, int maxTitles) {
                return List.of();
            }
        };
    }

    /** No distilled reader model: the prompt carries the frozen persona alone. */
    @Bean
    @Primary
    ReaderProfilePort previewReaderProfilePort() {
        return new ReaderProfilePort() {
            @Override
            public Optional<ReaderProfile> latest() {
                return Optional.empty();
            }

            @Override
            public void save(ReaderProfile profile) {
                log.debug("PREVIEW: not storing the distilled reader profile");
            }
        };
    }
}
