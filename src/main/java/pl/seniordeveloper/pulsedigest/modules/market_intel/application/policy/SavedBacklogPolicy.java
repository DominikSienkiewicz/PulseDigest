package pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy;

/**
 * Whether to remind the reader what they saved, and how much of it to show (N05 follow-up).
 *
 * <p>Owned by the application layer so the processor does not depend on infrastructure-bound
 * configuration types, exactly as {@link FeedbackNudgePolicy} is.
 *
 * <p>The window is deliberately not a key of its own: it is {@code feedback.lookback-days}, the same
 * window the rest of the feedback loop honours. A shelf with a different memory from the loop that
 * fills it would be a second, silently diverging notion of "recent".
 *
 * @param enabled      whether the shelf is read and rendered at all
 * @param maxItems     how many of the oldest saved items the mail shows
 * @param lookbackDays how far back saves are read, from {@code feedback.lookback-days}
 */
public record SavedBacklogPolicy(boolean enabled, int maxItems, int lookbackDays) {
}
