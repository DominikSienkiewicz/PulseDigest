package pl.seniordeveloper.pulsedigest.shared.error;

/**
 * Authorization error - identity established but action not permitted.
 */
public record Forbidden(String code, String message, String requiredPermission)
        implements DomainError {

    public Forbidden {
        java.util.Objects.requireNonNull(code, "Error code cannot be null");
        java.util.Objects.requireNonNull(message, "Error message cannot be null");
    }

    public static Forbidden insufficientPermissions(String permission) {
        return new Forbidden(
                "FORBIDDEN", "Insufficient permissions to perform this action", permission);
    }

    public static Forbidden resourceNotAccessible() {
        return new Forbidden("FORBIDDEN", "You do not have access to this resource", null);
    }
}
