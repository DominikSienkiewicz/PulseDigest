package pl.seniordeveloper.pulsedigest.shared.error;

/**
 * Authentication error - identity not established.
 */
public record Unauthorized(String code, String message) implements DomainError {

    public Unauthorized {
        java.util.Objects.requireNonNull(code, "Error code cannot be null");
        java.util.Objects.requireNonNull(message, "Error message cannot be null");
    }

    public static Unauthorized invalidCredentials() {
        return new Unauthorized("UNAUTHORIZED", "Invalid credentials");
    }

    public static Unauthorized tokenExpired() {
        return new Unauthorized("TOKEN_EXPIRED", "Authentication token has expired");
    }

    public static Unauthorized tokenInvalid() {
        return new Unauthorized("TOKEN_INVALID", "Authentication token is invalid");
    }
}
