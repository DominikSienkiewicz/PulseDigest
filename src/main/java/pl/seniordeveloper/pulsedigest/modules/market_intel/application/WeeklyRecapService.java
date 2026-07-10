package pl.seniordeveloper.pulsedigest.modules.market_intel.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PastEdition;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RecapChange;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RecapEntry;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Signal;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SignalRank;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.WeeklyRecap;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Assembles the Friday "week in signals" block by comparing this edition's ranks against the
 * earliest rank each story held during the same week.
 *
 * <p>Deterministic and free of LLM calls: the ranks were already computed, all this does is diff
 * them. Only stories that actually moved — or that were called critical and then went quiet — earn
 * a line, so a week of steady noise produces no recap at all.
 */
@Slf4j
@Service
public class WeeklyRecapService {

    private static final int MAX_ENTRIES = 7;

    /**
     * @param today    the edition's date; recap is produced on Fridays only
     * @param signals  the signals of the edition being assembled
     * @param history  previously published editions (any window ≥ one week)
     * @return the recap, or empty when it is not Friday, there is no history, or nothing moved
     */
    public Optional<WeeklyRecap> assemble(LocalDate today, List<Signal> signals, List<PastEdition> history) {
        if (today.getDayOfWeek() != DayOfWeek.FRIDAY || signals == null || history == null || history.isEmpty()) {
            return Optional.empty();
        }
        List<PastEdition> thisWeek = editionsSince(history, today.with(DayOfWeek.MONDAY));
        if (thisWeek.isEmpty()) {
            return Optional.empty();
        }

        List<RecapEntry> entries = new ArrayList<>();
        for (Signal signal : signals) {
            String topicKey = signal.item().correlationKey();
            if (topicKey.isEmpty()) {
                continue;
            }
            earliestRankOfWeek(thisWeek, topicKey)
                    .flatMap(previous -> toEntry(signal, previous))
                    .ifPresent(entries::add);
        }
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        entries.sort(Comparator.comparing(e -> e.currentRank().ordinal()));
        List<RecapEntry> capped = entries.stream().limit(MAX_ENTRIES).toList();
        log.info("Weekly recap: {} line(s) from {} edition(s) this week", capped.size(), thisWeek.size());
        return Optional.of(new WeeklyRecap(capped));
    }

    private static List<PastEdition> editionsSince(List<PastEdition> history, LocalDate monday) {
        return history.stream()
                .filter(e -> !e.generatedAt().atZone(ZoneOffset.UTC).toLocalDate().isBefore(monday))
                .toList();
    }

    /** The rank the story held in the earliest edition of this week that carried it. */
    private static Optional<SignalRank> earliestRankOfWeek(List<PastEdition> thisWeek, String topicKey) {
        return thisWeek.stream()
                .filter(e -> e.carries(topicKey))
                .min(Comparator.comparing(PastEdition::generatedAt))
                .flatMap(e -> e.find(topicKey))
                .map(Signal::rank);
    }

    // Rank ordinals run CRITICAL(0) → WEAK(3), so a lower ordinal is a stronger signal.
    private static Optional<RecapEntry> toEntry(Signal signal, SignalRank previous) {
        SignalRank current = signal.rank();
        RecapChange change = classify(previous, current);
        if (change == null) {
            return Optional.empty();
        }
        return Optional.of(new RecapEntry(
                signal.item().title(), signal.item().url(), change, previous, current));
    }

    private static RecapChange classify(SignalRank previous, SignalRank current) {
        if (current.ordinal() < previous.ordinal()) {
            return RecapChange.ESCALATED;
        }
        if (previous == SignalRank.CRITICAL && current == SignalRank.CRITICAL) {
            return RecapChange.CONFIRMED;
        }
        if (previous == SignalRank.CRITICAL) {
            return RecapChange.FADED;
        }
        return null;
    }
}
