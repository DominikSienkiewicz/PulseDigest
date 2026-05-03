package pl.seniordeveloper.pulsedigest.shared.error;

import java.util.Map;

/**
 * Business rule violation - domain invariant broken.
 */
public record BusinessRuleViolation(
        String code, String message, String ruleName, Map<String, Object> context)
        implements DomainError {

    public BusinessRuleViolation {
        java.util.Objects.requireNonNull(code, "Error code cannot be null");
        java.util.Objects.requireNonNull(message, "Error message cannot be null");
        java.util.Objects.requireNonNull(ruleName, "Rule name cannot be null");
        context = context != null ? Map.copyOf(context) : Map.of();
    }

    public static BusinessRuleViolation of(String ruleName, String message) {
        return new BusinessRuleViolation("BUSINESS_RULE_VIOLATION", message, ruleName, Map.of());
    }

    public static BusinessRuleViolation of(
            String ruleName, String message, Map<String, Object> context) {
        return new BusinessRuleViolation("BUSINESS_RULE_VIOLATION", message, ruleName, context);
    }
}
