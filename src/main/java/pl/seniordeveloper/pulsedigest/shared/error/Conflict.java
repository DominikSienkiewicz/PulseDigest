package pl.seniordeveloper.pulsedigest.shared.error;

/**
 * Conflict error - operation cannot be completed due to current state.
 */
public record Conflict(String code, String message, String conflictingResource)
        implements DomainError {

    public Conflict {
        java.util.Objects.requireNonNull(code, "Error code cannot be null");
        java.util.Objects.requireNonNull(message, "Error message cannot be null");
    }

    public static Conflict alreadyExists(String entityType, String identifier) {
        return new Conflict(
                "ALREADY_EXISTS", entityType + " '" + identifier + "' already exists", identifier);
    }

    public static Conflict staleData(String entityType) {
        return new Conflict(
                "STALE_DATA", entityType + " has been modified by another user", entityType);
    }

    public static Conflict invalidState(String message) {
        return new Conflict("INVALID_STATE", message, null);
    }
}
