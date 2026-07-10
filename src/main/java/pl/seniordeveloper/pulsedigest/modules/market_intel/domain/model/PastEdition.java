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
 */
public record PastEdition(Instant generatedAt, List<Signal> signals) {

    public PastEdition {
        signals = signals != null ? List.copyOf(signals) : List.of();
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
