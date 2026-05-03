package pl.seniordeveloper.pulsedigest.shared.error;

/**
 * Already exists error - resource already present in the system. This is a specialized conflict
 * error for duplicate resource scenarios.
 */
public record AlreadyExists(String code, String message, String resourceType, String resourceId)
        implements DomainError {

    public AlreadyExists {
        java.util.Objects.requireNonNull(code, "Error code cannot be null");
        java.util.Objects.requireNonNull(message, "Error message cannot be null");
        java.util.Objects.requireNonNull(resourceType, "Resource type cannot be null");
    }

    public static AlreadyExists of(String resourceType, String identifier) {
        return new AlreadyExists(
                "ALREADY_EXISTS",
                resourceType + " '" + identifier + "' already exists",
                resourceType,
                identifier);
    }
}
