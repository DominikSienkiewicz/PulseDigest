package pl.seniordeveloper.pulsedigest;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import lombok.RequiredArgsConstructor;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.RetrospectiveService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.SourceDiscoveryService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.RetrospectivePolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceProposal;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.EmailDeliveryReceipt;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Retrospective;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.EmailDeliveryPort;

import java.time.LocalDate;
import java.util.List;
import java.time.ZoneOffset;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.ArchivePublishService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.DryRunService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.ReplayService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.command.GenerateMarketReportCommand;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.command.GenerateMarketReportService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.error.MarketIntelError;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.RunSummaryPort;
import pl.seniordeveloper.pulsedigest.shared.result.Result;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.metrics.MetricsLogger;
import pl.seniordeveloper.pulsedigest.shared.util.UuidV7Generator;

/**
 * Entry point of the batch run: runs one digest generation and maps its outcome to a process exit
 * code. The pipeline runs synchronously, so no polling is needed — once
 * {@link GenerateMarketReportService#handle} returns, the run is over and its {@code Result} says
 * how it ended.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class DigestRunner implements ApplicationRunner {

    private final GenerateMarketReportService generateService;
    private final RunSummaryPort runSummaryPort;
    private final ReplayService replayService;
    private final MetricsLogger metricsLogger;
    private final RetrospectiveService retrospectiveService;
    private final EmailDeliveryPort emailPort;
    private final SourceDiscoveryService sourceDiscoveryService;
    private final RetrospectivePolicy retrospectivePolicy;
    private final DryRunService dryRunService;
    private final ArchivePublishService archivePublishService;

    @Override
    public void run(ApplicationArguments args) {
        if (args != null && args.containsOption("publish-archive")) {
            System.exit(publishArchive());
            return;
        }
        if (args != null && args.containsOption("dry-run")) {
            System.exit(dryRun());
            return;
        }
        if (args != null && args.containsOption("discover-sources")) {
            System.exit(discoverSources());
            return;
        }
        if (args != null && args.containsOption("retrospective")) {
            System.exit(retrospective());
            return;
        }
        if (args != null && args.containsOption("replay-prompt")) {
            System.exit(replayPrompt(args.getOptionValues("replay-prompt")));
            return;
        }
        if (args != null && args.containsOption("replay")) {
            System.exit(replay(args.getOptionValues("replay")));
            return;
        }

        String jobId = UuidV7Generator.next().toString();
        // N41. Put the job id in the MDC rather than only in the opening line: under the `ci`
        // profile the structured appender lifts MDC entries to top-level fields, so every record a
        // run emits is attributable to it without a human reading downwards to find the header.
        MDC.put("jobId", jobId);
        try {
            log.info("=== PulseDigest pipeline starting (jobId={}) ===", jobId);
            System.exit(exitCode(generateService.handle(new GenerateMarketReportCommand(jobId))));
        } finally {
            MDC.remove("jobId");
        }
    }

    /**
     * Rebuilds the public archive from stored editions: {@code --publish-archive} (N31–N33).
     *
     * <p>Writes files and nothing else; the workflow decides whether they are deployed. Making
     * editions public is the one irreversible thing this project does, so it is an explicit step in
     * CI rather than something that happens as a side effect of a run.
     *
     * <p>Exit {@code 1} on an empty archive: no stored editions means the database could not be
     * read, and publishing an empty site over a good one would be the worst available outcome.
     */
    int publishArchive() {
        int published = archivePublishService.publish(retrospectivePolicy.lookbackDays());
        if (published == 0) {
            log.error("Nothing to publish — refusing to overwrite the archive with an empty site");
            return 1;
        }
        log.info("=== Archive rebuilt: {} edition(s) ===", published);
        return 0;
    }

    /**
     * Renders the committed sample edition and exits: {@code --dry-run} (N37).
     *
     * <p>No network, no credentials, no mail. It exists because cloning this repository otherwise
     * ends at "it compiles" — four accounts and a paid tier stand between a visitor and seeing the
     * product work. Run it with {@code --spring.profiles.active=dry-run}, which supplies obviously
     * fake values for the four properties that are {@code @NotBlank} precisely so a real run fails
     * early.
     */
    int dryRun() {
        log.info("=== PulseDigest dry run — rendering the committed sample edition ===");
        if (!dryRunService.render()) {
            return 1;
        }
        log.info("=== Dry run complete: nothing was fetched, nothing was sent ===");
        return 0;
    }

    /**
     * Proposes sources from the published record: {@code --discover-sources} (N28).
     *
     * <p>Prints a report and changes nothing. Exit {@code 0} even when it proposes nothing, because
     * "the record does not argue for a missing source" is a successful answer to the question.
     */
    int discoverSources() {
        List<SourceProposal> proposals = sourceDiscoveryService.discover(retrospectivePolicy.lookbackDays());
        log.info("=== Source discovery complete: {} proposal(s) — nothing was changed ===",
                proposals.size());
        return 0;
    }

    /**
     * Sends the quarterly retrospective: {@code --retrospective} (N20).
     *
     * <p>Reads only stored editions, so it can be run on any day against real history and produce
     * the same thing twice. That matters more here than anywhere else in the project: a quarterly
     * artefact otherwise gets four chances a year to be right, which is no way to get it right.
     *
     * <p>Exit {@code 1} on an empty window — a retrospective over no editions is not a quiet quarter,
     * it is a database that could not be read, and the two must not look the same from CI.
     */
    int retrospective() {
        Retrospective retrospective = retrospectiveService.assemble(LocalDate.now(ZoneOffset.UTC));
        if (retrospective.isEmpty()) {
            log.error("No stored editions in the retrospective window — nothing to send");
            return 1;
        }
        EmailDeliveryReceipt receipt = emailPort.sendRetrospective(retrospective);
        log.info("=== Retrospective sent via {}: {} edition(s), {} graded 🔴 call(s) ===",
                receipt.provider(), retrospective.editions(), retrospective.scoreboard().graded());
        return 0;
    }

    /**
     * Replays a past edition's rubric instead of producing a new digest: {@code --replay=<jobId>}.
     * Sends nothing, writes nothing, costs nothing. Exit {@code 0} when the edition was found and
     * replayed — a replay that reports differences is a successful replay, not a failed run.
     */
    int replay(java.util.List<String> jobIds) {
        if (jobIds == null || jobIds.isEmpty() || jobIds.getFirst() == null || jobIds.getFirst().isBlank()) {
            log.error("--replay needs an edition's job id, e.g. --replay=0199a1b2-...");
            return 1;
        }
        String jobId = jobIds.getFirst();
        return replayService.replay(jobId)
                .map(report -> {
                    replayService.logReplay(report);
                    return 0;
                })
                .orElseGet(() -> {
                    log.error("No edition with job id {} in the archive", jobId);
                    return 1;
                });
    }

    /**
     * Replays a past edition's <em>prompt</em>: {@code --replay-prompt=<jobId>}.
     *
     * <p>A separate flag from {@code --replay} on purpose. The rubric replay is free and
     * deterministic and is the one you run in a loop while tuning scoring; this one spends a real
     * synthesis call every time, so making it the same flag would have made the common case the
     * expensive one. The estimate is logged before the call, not the bill after it.
     */
    int replayPrompt(java.util.List<String> jobIds) {
        if (jobIds == null || jobIds.isEmpty() || jobIds.getFirst() == null || jobIds.getFirst().isBlank()) {
            log.error("--replay-prompt needs an edition's job id, e.g. --replay-prompt=0199a1b2-...");
            return 1;
        }
        String jobId = jobIds.getFirst();
        return replayService.replayPrompt(jobId)
                .map(report -> {
                    replayService.logPromptReplay(report);
                    return 0;
                })
                .orElseGet(() -> {
                    log.error("Edition {} cannot be prompt-replayed: no stored input, or not in the archive."
                            + " Editions written before edition_inputs existed have nothing to re-run.", jobId);
                    return 1;
                });
    }

    /**
     * Maps the run's outcome to a process exit code: {@code 0} only when the email was actually
     * delivered, {@code 1} for every failure. GitHub Actions reads this, and the scheduled
     * workflow's failure alert hangs off it, so a non-delivering run must never exit {@code 0}.
     */
    int exitCode(Result<String, MarketIntelError> outcome) {
        // Before the exit, not from a shutdown hook: everything F11, N35 and N40 report is read off
        // this snapshot, and a hook racing the logging system's own hook is not a place to keep it.
        metricsLogger.dump();
        return switch (outcome) {
            case Result.Success(var deliveredJobId) -> {
                log.info("=== Digest complete — email sent (jobId={}) ===", deliveredJobId);
                runSummaryPort.writeOutcome("delivered", "Edition sent. Job " + deliveredJobId + ".");
                yield 0;
            }
            case Result.Failure(var error) -> {
                log.error("=== Digest failed: {} ===", error.message());
                runSummaryPort.writeOutcome("failed", error.message());
                yield 1;
            }
        };
    }
}
