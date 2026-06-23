package pl.seniordeveloper.pulsedigest.shared.error;

/**
 * External service error - integration failure.
 */
public record ExternalServiceError(
        String code, String message, String serviceName, String originalError) implements DomainError {

    private static final String SERVICE_PREFIX = "External service '";

    public ExternalServiceError {
        java.util.Objects.requireNonNull(code, "Error code cannot be null");
        java.util.Objects.requireNonNull(message, "Error message cannot be null");
        java.util.Objects.requireNonNull(serviceName, "Service name cannot be null");
    }

    public static ExternalServiceError timeout(String serviceName) {
        return new ExternalServiceError(
                "SERVICE_TIMEOUT", SERVICE_PREFIX + serviceName + "' timed out", serviceName, null);
    }

    public static ExternalServiceError unavailable(String serviceName) {
        return new ExternalServiceError(
                "SERVICE_UNAVAILABLE",
                SERVICE_PREFIX + serviceName + "' is unavailable",
                serviceName,
                null);
    }

    public static ExternalServiceError error(String serviceName, String details) {
        return new ExternalServiceError(
                "SERVICE_ERROR",
                SERVICE_PREFIX + serviceName + "' returned an error",
                serviceName,
                details);
    }
}
