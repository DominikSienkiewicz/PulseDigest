package pl.seniordeveloper.pulsedigest.shared.infrastructure.metrics;

/**
 * Thrown when a run has spent its per-run budget and the call about to be made is not optional (N40).
 *
 * <p>Unchecked on purpose: it travels from an adapter through the pipeline to the processor's own
 * failure path, which already turns any exception before delivery into a tracked error and an alert
 * email. Making it checked would force every layer in between to declare an outcome none of them
 * can do anything about.
 */
public class BudgetExceededException extends RuntimeException {

    public BudgetExceededException(String message) {
        super(message);
    }
}
