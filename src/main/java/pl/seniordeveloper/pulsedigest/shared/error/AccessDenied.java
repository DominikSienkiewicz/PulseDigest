package pl.seniordeveloper.pulsedigest.shared.error;

/**
 * Access denied error - action not permitted for the current user. More specific than Forbidden,
 * typically used when user is authenticated but lacks required permissions or scope.
 */
public record AccessDenied(String code, String message, String requiredPermission, String userId)
        implements DomainError {

    public AccessDenied {
        java.util.Objects.requireNonNull(code, "Error code cannot be null");
        java.util.Objects.requireNonNull(message, "Error message cannot be null");
    }

    public static AccessDenied insufficientPermissions(String permission) {
        return new AccessDenied(
                "ACCESS_DENIED", "Insufficient permissions to perform this action", permission, null);
    }

    public static AccessDenied resourceNotAccessible(String userId) {
        return new AccessDenied(
                "ACCESS_DENIED", "You do not have access to this resource", null, userId);
    }
}
