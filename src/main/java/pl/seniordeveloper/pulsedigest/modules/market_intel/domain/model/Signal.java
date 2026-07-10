package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Objects;

/**
 * A scored intelligence signal wrapping a DigestItem with its deterministic rank,
 * computed signal score, and the source domain types that confirmed the topic.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Signal(
        DigestItem item,
        SignalRank rank,
        int signalScore,
        List<SourceDomain> sourceDomains,
        TrendRecurrence recurrence
) {

    public Signal {
        Objects.requireNonNull(item, "item must not be null");
        Objects.requireNonNull(rank, "rank must not be null");
        Objects.requireNonNull(sourceDomains, "sourceDomains must not be null");
        if (sourceDomains.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("sourceDomains must not contain null elements");
        }
        if (signalScore < 0) {
            throw new IllegalArgumentException("signalScore must be >= 0");
        }
        sourceDomains = List.copyOf(sourceDomains);
    }

    /** A freshly scored signal, before report history has been consulted. */
    public Signal(DigestItem item, SignalRank rank, int signalScore, List<SourceDomain> sourceDomains) {
        this(item, rank, signalScore, sourceDomains, null);
    }

    /** Copy of this signal carrying its cross-edition recurrence. */
    public Signal withRecurrence(TrendRecurrence newRecurrence) {
        return new Signal(item, rank, signalScore, sourceDomains, newRecurrence);
    }

    /**
     * Returns {@code true} if this signal has CRITICAL rank.
     * CRITICAL rank is assigned when signalScore >= 101, which can result from:
     * a high-credibility source (e.g. arXiv=1.00) with high engagement, OR
     * a cross-source correlation bonus (+50) pushing a source above the threshold.
     * Delegating to {@code rank} rather than re-deriving the threshold here preserves the single
     * source of truth in {@code SignalScoringService}.
     */
    @JsonIgnore
    public boolean isCriticalTrend() {
        return rank == SignalRank.CRITICAL;
    }
}
