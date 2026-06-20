package pl.seniordeveloper.pulsedigest.shared.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.util.List;

@ConfigurationProperties(prefix = "twitter")
@Validated
public record TwitterProperties(
        @NotBlank
        String bearerToken,
        @NotEmpty
        List<@NotBlank String> queries,
        @NotEmpty
        List<@NotBlank String> accounts,
        // Hard ceiling on X API search calls per run (config-drift backstop). The intended intake is
        // ceil(accounts/8) + queries + 1 Anthropic call; this cap guarantees a sufficient sufit even
        // if the account/query lists grow. Calls beyond the budget short-circuit to an empty result.
        @DefaultValue("10") @Positive
        int maxCallsPerRun,
        // Server-side engagement floor (`min_faves:N`) appended to every search query when > 0. Trims
        // low-engagement noise BEFORE it consumes read quota. Kept OFF (0) by default: the `min_faves`
        // operator is not available on every X API access tier, and an unsupported operator fails the
        // whole search. Enable only after confirming your tier supports it.
        @DefaultValue("0") @Min(0)
        int minFaves
) {
}
