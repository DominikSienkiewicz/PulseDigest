package pl.seniordeveloper.pulsedigest.shared.error;

/**
 * Entity not found error.
 */
public record NotFound(String code, String message, String entityType, String entityId)
        implements DomainError {

    public NotFound {
        java.util.Objects.requireNonNull(code, "Error code cannot be null");
        java.util.Objects.requireNonNull(message, "Error message cannot be null");
        java.util.Objects.requireNonNull(entityType, "Entity type cannot be null");
    }

    public static NotFound of(String entityType, String identifier) {
        return new NotFound(
                "NOT_FOUND", entityType + " '" + identifier + "' not found", entityType, identifier);
    }
}
