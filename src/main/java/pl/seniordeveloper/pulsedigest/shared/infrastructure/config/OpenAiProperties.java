package pl.seniordeveloper.pulsedigest.shared.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The OpenAI credential shared by the three GPT adapters (synthesis, pre-scoring, reader-profile
 * distillation).
 *
 * <p>It lived under {@code spring.ai.openai.api-key} until 2026-10-18 — a namespace inherited from
 * a Spring AI starter that was declared, never imported, and removed with F4. Left there it was a
 * key under Spring Boot's reserved {@code spring.} prefix belonging to no starter and no
 * autoconfiguration, which reads as a binding someone else owns.
 *
 * <p>{@code @NotBlank} makes a missing key fail at startup rather than at the first LLM call. That
 * matches the other credentials ({@code twitter.bearer-token}, {@code report.email.*}) and matches
 * what CI already enforces: {@code digest.yml} aborts the run when {@code OPENAI_API_KEY} is unset,
 * so no environment that works today is affected. The environment variable name is unchanged.
 */
@ConfigurationProperties(prefix = "openai")
@Validated
public record OpenAiProperties(
        @NotBlank String apiKey
) {
}
