package pl.seniordeveloper.pulsedigest.shared.error;

import pl.seniordeveloper.pulsedigest.shared.util.UuidV7Generator;

import java.time.Instant;

/**
 * Base interface for all domain errors. Uses sealed interfaces to create a closed hierarchy of
 * known error types.
 */
public interface DomainError {

    /**
     * Unique error code for client-side handling.
     */
    String code();

    /**
     * Human-readable error message.
     */
    String message();

    /**
     * Timestamp when the error occurred.
     */
    default Instant timestamp() {
        return Instant.now();
    }

    /**
     * Unique identifier for this error instance (for tracing).
     */
    default String traceId() {
        return UuidV7Generator.next().toString();
    }
}
