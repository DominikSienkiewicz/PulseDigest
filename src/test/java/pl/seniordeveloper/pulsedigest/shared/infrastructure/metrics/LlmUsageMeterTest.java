package pl.seniordeveloper.pulsedigest.shared.infrastructure.metrics;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.LlmProperties;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LlmUsageMeterTest {

    private static final LlmProperties.Call ANY_CALL = new LlmProperties.Call("gpt-4o", "", 0.2, 1000);

    private SimpleMeterRegistry registry;

    @BeforeEach
    void addRegistry() {
        registry = new SimpleMeterRegistry();
        Metrics.addRegistry(registry);
    }

    @AfterEach
    void removeRegistry() {
        Metrics.removeRegistry(registry);
        registry.close();
    }

    @Test
    void attributesTokensToTheCallSiteAndModelThatSpentThem() {
        meter(Map.of()).recordCall("pre-scoring", "gpt-4o-mini", 1_200, 300);

        assertThat(counter("llm.tokens", "call", "pre-scoring", "model", "gpt-4o-mini", "kind", "prompt"))
                .isEqualTo(1_200.0);
        assertThat(counter("llm.tokens", "call", "pre-scoring", "model", "gpt-4o-mini", "kind", "completion"))
                .isEqualTo(300.0);
        assertThat(counter("llm.calls", "call", "pre-scoring", "model", "gpt-4o-mini")).isEqualTo(1.0);
    }

    /**
     * With no price list the run still reports tokens and reports nothing about money. The
     * alternative — a price baked into the repository — goes stale silently and turns an honest
     * "unpriced" into a confident wrong number.
     *
     * <p>The model name is unique to this test on purpose: {@code Metrics.globalRegistry} is a
     * composite that replays meters registered by earlier tests into a registry added later, so a
     * bare "no cost counters exist" assertion would read another test's series and fail on ordering.
     */
    @Test
    void anUnpricedModelIsMeteredInTokensAndNotInMoney() {
        meter(Map.of()).recordCall("synthesis", "model-without-a-price", 10_000, 2_000);

        assertThat(counter("llm.tokens", "call", "synthesis", "model", "model-without-a-price",
                "kind", "prompt")).isEqualTo(10_000.0);
        assertThat(registry.find("llm.cost.usd").tag("model", "model-without-a-price").counters())
                .as("no price configured means no cost series for that model, not a zero")
                .isEmpty();
    }

    @Test
    void aPricedModelAlsoAccumulatesCost() {
        // $2.50 per million prompt tokens, $10.00 per million completion tokens.
        meter(Map.of("gpt-4o", new LlmProperties.Price(2.50, 10.00)))
                .recordCall("synthesis", "gpt-4o", 400_000, 50_000);

        // 0.4M × 2.50 = 1.00 ; 0.05M × 10.00 = 0.50
        assertThat(counter("llm.cost.usd", "call", "synthesis", "model", "gpt-4o")).isEqualTo(1.50);
    }

    @Test
    void anAbsentUsageBlockIsNotCountedAsZero() {
        meter(Map.of()).recordCall("synthesis", "model-never-metered", null, null);

        assertThat(registry.find("llm.tokens").tag("model", "model-never-metered").counters()).isEmpty();
        assertThat(registry.find("llm.calls").tag("model", "model-never-metered").counters()).isEmpty();
    }

    private LlmUsageMeter meter(Map<String, LlmProperties.Price> pricing) {
        return new LlmUsageMeter(new LlmProperties(ANY_CALL, ANY_CALL, ANY_CALL, pricing, new LlmProperties.Budget(false, 0, 0)));
    }

    private double counter(String name, String... tags) {
        return registry.find(name).tags(tags).counter().count();
    }
}
