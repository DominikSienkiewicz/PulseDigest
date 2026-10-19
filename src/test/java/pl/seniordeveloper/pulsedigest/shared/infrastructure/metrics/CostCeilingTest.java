package pl.seniordeveloper.pulsedigest.shared.infrastructure.metrics;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.LlmProperties;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * N40. Each case gets its own registry, which is the reason {@link CostCeiling} takes one rather
 * than reading {@code Metrics.globalRegistry}: that static is JVM-wide, so counters written by every
 * other test in the suite leak into it and the ceiling sees a number nobody in this file wrote.
 *
 * <p>The ceiling reads the very counters {@link LlmUsageMeter} writes, so these drive it through
 * the meter rather than incrementing counters by hand — a test that wrote its own counters would
 * still pass after a rename that left the guard reading nothing.
 */
class CostCeilingTest {

    private static final LlmProperties.Call ANY_CALL = new LlmProperties.Call("gpt-4o", "", 0.2, 100);
    private static final Map<String, LlmProperties.Price> PRICED =
            Map.of("gpt-4o", new LlmProperties.Price(1_000.0, 1_000.0));

    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        Metrics.addRegistry(registry);
    }

    @AfterEach
    void tearDown() {
        Metrics.removeRegistry(registry);
        registry.close();
    }

    @Test
    void letsEveryCallThroughWhileThereIsHeadroom() {
        var ceiling = ceiling(new LlmProperties.Budget(true, 10_000, 0));
        spend(1_000, 500);

        assertThat(ceiling.allows("pre-scoring")).isTrue();
        ceiling.requireHeadroom("synthesis");
    }

    @Test
    void refusesOptionalCallsOnceTheTokenCeilingIsReached() {
        var ceiling = ceiling(new LlmProperties.Budget(true, 1_000, 0));
        spend(800, 300);

        assertThat(ceiling.tokensSpent()).isEqualTo(1_100);
        assertThat(ceiling.allows("pre-scoring")).isFalse();
    }

    @Test
    void failsTheRunWhenSynthesisWouldCrossTheCeiling() {
        // The one call with nothing to degrade to. The message has to carry both numbers, or the
        // alert it produces is a puzzle rather than a warning.
        var ceiling = ceiling(new LlmProperties.Budget(true, 1_000, 0));
        spend(2_000, 0);

        assertThatThrownBy(() -> ceiling.requireHeadroom("synthesis"))
                .isInstanceOf(BudgetExceededException.class)
                .hasMessageContaining("2000 tokens")
                .hasMessageContaining("max 1000")
                .hasMessageContaining("synthesis");
    }

    @Test
    void countsPromptAndCompletionTokensTogether() {
        // Billing does, so a ceiling that watched only one of them would be wrong by roughly half.
        var ceiling = ceiling(new LlmProperties.Budget(true, 1_000, 0));
        spend(600, 600);

        assertThat(ceiling.tokensSpent()).isEqualTo(1_200);
        assertThat(ceiling.allows("reader-profile")).isFalse();
    }

    @Test
    void addsUpSpendAcrossEveryCallSiteNotJustTheLastOne() {
        // The failure this exists for is many small calls, not one big one.
        var ceiling = ceiling(new LlmProperties.Budget(true, 1_000, 0));
        meter(Map.of()).recordCall("pre-scoring", "gpt-4o", 400, 100);
        meter(Map.of()).recordCall("reader-profile", "gpt-4o", 400, 200);

        assertThat(ceiling.tokensSpent()).isEqualTo(1_100);
        assertThat(ceiling.allows("pre-scoring")).isFalse();
    }

    @Test
    void entersTheMoneyCeilingOnlyOnceAPriceListExists() {
        // The shipped configuration has no price list on purpose, which is exactly why the token
        // ceiling is the primary one: a dollars-only guard would be inert where it is switched on.
        var unpriced = ceiling(new LlmProperties.Budget(true, 0, 0.01), Map.of());
        meter(Map.of()).recordCall("synthesis", "gpt-4o", 1_000_000, 1_000_000);

        assertThat(unpriced.usdSpent()).isZero();
        unpriced.requireHeadroom("synthesis");

        var priced = ceiling(new LlmProperties.Budget(true, 0, 0.01), PRICED);
        meter(PRICED).recordCall("synthesis", "gpt-4o", 1_000_000, 0);

        assertThat(priced.usdSpent()).isGreaterThan(0.01);
        assertThatThrownBy(() -> priced.requireHeadroom("synthesis"))
                .isInstanceOf(BudgetExceededException.class);
    }

    @Test
    void capsNothingWhenTheBudgetIsOffOrHasNoLimits() {
        spend(10_000_000, 10_000_000);

        assertThat(ceiling(new LlmProperties.Budget(false, 1, 0)).allows("pre-scoring")).isTrue();
        assertThat(ceiling(new LlmProperties.Budget(true, 0, 0)).allows("pre-scoring")).isTrue();
        ceiling(new LlmProperties.Budget(true, 0, 0)).requireHeadroom("synthesis");
    }

    private void spend(long prompt, long completion) {
        meter(Map.of()).recordCall("synthesis", "gpt-4o", prompt, completion);
    }

    private static LlmUsageMeter meter(Map<String, LlmProperties.Price> pricing) {
        return new LlmUsageMeter(properties(new LlmProperties.Budget(false, 0, 0), pricing));
    }

    private CostCeiling ceiling(LlmProperties.Budget budget) {
        return ceiling(budget, Map.of());
    }

    private CostCeiling ceiling(LlmProperties.Budget budget, Map<String, LlmProperties.Price> pricing) {
        return new CostCeiling(properties(budget, pricing), registry);
    }

    private static LlmProperties properties(LlmProperties.Budget budget,
                                            Map<String, LlmProperties.Price> pricing) {
        return new LlmProperties(ANY_CALL, ANY_CALL, ANY_CALL, pricing, budget);
    }
}
