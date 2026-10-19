package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * A previously delivered edition, as its scored signals were stored. Raw material for trend memory
 * ("3rd edition in a row"), the weekly recap, the source-yield ledger and the predictive radar.
 *
 * <p>It holds {@link Signal}s rather than a reduced projection because every consumer needs a
 * different slice of one — rank, score, source, domain count, past candidacy — and the signals were
 * persisted whole anyway.
 *
 * <p>{@code fetchReports} is what the run <em>attempted</em>, as opposed to what it published. The
 * two are different questions and only the second was ever read back: a source that returned an
 * empty result left no signal, so it vanished from the yield ledger instead of appearing in it with
 * a zero — indistinguishable from a source that does not exist (F7). The reports were being stored
 * for this purpose all along; nothing read them.
 */
public record PastEdition(Instant generatedAt, List<Signal> signals, List<SourceFetchReport> fetchReports) {

    public PastEdition {
        signals = signals != null ? List.copyOf(signals) : List.of();
        fetchReports = fetchReports != null ? List.copyOf(fetchReports) : List.of();
    }

    /** Whether this edition carried the given correlation key. */
    public boolean carries(String topicKey) {
        return find(topicKey).isPresent();
    }

    /** The signal this edition carried for the given correlation key, if any. */
    public Optional<Signal> find(String topicKey) {
        return signals.stream()
                .filter(signal -> signal.item().correlationKey().equals(topicKey))
                .findFirst();
    }
}
