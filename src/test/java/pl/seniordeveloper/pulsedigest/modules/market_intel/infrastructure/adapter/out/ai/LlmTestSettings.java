package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.ai;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.LlmProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.metrics.CostCeiling;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.metrics.LlmUsageMeter;

import java.util.Map;

/**
 * The model settings the AI adapter ITs run with — the production values, so a test exercises the
 * same request body a run sends, with no price list so nothing is attributed to money in a test.
 */
final class LlmTestSettings {

    private LlmTestSettings() {
    }

    static LlmProperties properties() {
        return new LlmProperties(new LlmProperties.Call("gpt-4o", "gpt-4o-mini", 0.25, 10_000),
                new LlmProperties.Call("gpt-4o-mini", "", 0.0, 4_000),
                new LlmProperties.Call("gpt-4o-mini", "", 0.2, 700),
                Map.of(),
                // No ceiling in adapter tests: the budget is CostCeilingTest's subject, and an
                // adapter test that silently skipped its own call would pass while proving nothing.
                new LlmProperties.Budget(false, 0, 0));
    }

    static LlmUsageMeter meter() {
        return new LlmUsageMeter(properties());
    }

    static CostCeiling ceiling() {
        return new CostCeiling(properties(), new SimpleMeterRegistry());
    }
}
