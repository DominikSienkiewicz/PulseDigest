package pl.seniordeveloper.pulsedigest.shared.error;

import java.util.List;

/**
 * Validation error with detailed field-level errors.
 */
public record ValidationFailed(String code, String message, List<FieldError> fieldErrors)
        implements DomainError {

    public ValidationFailed {
        java.util.Objects.requireNonNull(code, "Error code cannot be null");
        java.util.Objects.requireNonNull(message, "Error message cannot be null");
        fieldErrors = fieldErrors != null ? List.copyOf(fieldErrors) : List.of();
    }

    public static ValidationFailed of(String field, String message) {
        return new ValidationFailed(
                "VALIDATION_FAILED", "Validation failed", List.of(new FieldError(field, message)));
    }

    public static ValidationFailed of(List<FieldError> errors) {
        return new ValidationFailed(
                "VALIDATION_FAILED", "Validation failed for " + errors.size() + " field(s)", errors);
    }

    public static ValidationFailed single(String message) {
        return new ValidationFailed("VALIDATION_FAILED", message, List.of());
    }

    public record FieldError(String field, String message) {
        public FieldError {
            java.util.Objects.requireNonNull(field, "Field cannot be null");
            java.util.Objects.requireNonNull(message, "Message cannot be null");
        }
    }
}
