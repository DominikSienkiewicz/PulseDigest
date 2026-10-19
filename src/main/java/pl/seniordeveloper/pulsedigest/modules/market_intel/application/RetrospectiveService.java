package pl.seniordeveloper.pulsedigest.modules.market_intel.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.RetrospectivePolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PastEdition;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Retrospective;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Signal;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.FeedbackPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.ReportHistoryPort;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Assembles the quarterly retrospective from stored editions alone (N20).
 *
 * <p>No live fetch, by design rather than by convenience. A quarterly artefact gets four chances a
 * year to be right, so it has to be runnable on demand against real history — otherwise it can only
 * be iterated on in production, once a quarter, which is no way to get anything right.
 *
 * <p>Deliberately not written by the model. Every line here is derived arithmetic over stored data,
 * which means it can be checked. A narrative pass over the quarter is the obvious next step and
 * wants the prompt replay to tune it; folding it in now would make the one artefact that grades the
 * system's honesty the one artefact nobody can verify.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class RetrospectiveService {

    private static final int MAX_ENDORSED = 10;
    private static final int MAX_PERSISTENT = 10;

    private final ReportHistoryPort reportHistoryPort;
    private final FeedbackPort feedbackPort;
    private final PredictionScoreboardService scoreboardService;
    private final SourceYieldService sourceYieldService;
    private final RetrospectivePolicy policy;

    public Retrospective assemble(LocalDate today) {
        List<PastEdition> editions = reportHistoryPort.recentEditions(policy.lookbackDays());
        if (editions.isEmpty()) {
            log.warn("retrospective window={}d has no stored editions — nothing to report",
                    policy.lookbackDays());
            return new Retrospective(today.minusDays(policy.lookbackDays()), today, 0,
                    pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PredictionScoreboard
                            .empty(policy.lookbackDays(), policy.maturityDays()),
                    List.of(), List.of(), List.of());
        }
        Set<String> upvoted = feedbackPort.upvotedUrls(policy.lookbackDays());

        Retrospective retrospective = new Retrospective(
                today.minusDays(policy.lookbackDays()),
                today,
                editions.size(),
                scoreboardService.measure(editions, upvoted, Instant.now(),
                        policy.lookbackDays(), policy.maturityDays()),
                endorsed(editions, upvoted),
                persistent(editions),
                sourceYieldService.scoreboard(editions));

        log.info("retrospective window={}d editions={} endorsed={} persistent={} graded={}",
                policy.lookbackDays(), retrospective.editions(), retrospective.endorsed().size(),
                retrospective.persistent().size(), retrospective.scoreboard().graded());
        return retrospective;
    }

    /** What the reader actually said yes to, newest first — the one signal they gave deliberately. */
    private static List<DigestItem> endorsed(List<PastEdition> editions, Set<String> upvoted) {
        Map<String, DigestItem> byUrl = new LinkedHashMap<>();
        editions.stream()
                .sorted(Comparator.comparing(PastEdition::generatedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())).reversed())
                .flatMap(edition -> edition.signals().stream())
                .map(Signal::item)
                .filter(item -> item.url() != null && upvoted.contains(item.url()))
                .forEach(item -> byUrl.putIfAbsent(item.url(), item));
        return byUrl.values().stream().limit(MAX_ENDORSED).toList();
    }

    /**
     * Stories the window kept carrying, counted on the URL — or on the topic key where the model
     * gave one.
     *
     * <p>Deliberately not {@code correlationKey()}, which falls back to the <em>category</em> for
     * items without a topic key. That fallback is right for the clustering it was written for and
     * wrong here: almost every edition carries something in "Java/JVM", so counting on it would
     * report one enormous persistent story per category and nothing true.
     */
    private static List<Retrospective.RecurringStory> persistent(List<PastEdition> editions) {
        Map<String, int[]> counts = new LinkedHashMap<>();
        Map<String, DigestItem> latest = new LinkedHashMap<>();
        for (PastEdition edition : editions) {
            for (Signal signal : edition.signals()) {
                String topicKey = signal.item().topicKey();
                String key = topicKey != null && !topicKey.isBlank()
                        ? topicKey.toLowerCase(java.util.Locale.ROOT)
                        : signal.item().url();
                if (key == null) {
                    continue;
                }
                counts.computeIfAbsent(key, k -> new int[1])[0]++;
                latest.putIfAbsent(key, signal.item());
            }
        }
        return counts.entrySet().stream()
                .filter(e -> e.getValue()[0] > 1)
                .sorted(Comparator.<Map.Entry<String, int[]>>comparingInt(e -> e.getValue()[0]).reversed())
                .limit(MAX_PERSISTENT)
                .map(e -> new Retrospective.RecurringStory(
                        latest.get(e.getKey()).title(), latest.get(e.getKey()).url(), e.getValue()[0]))
                .toList();
    }
}
