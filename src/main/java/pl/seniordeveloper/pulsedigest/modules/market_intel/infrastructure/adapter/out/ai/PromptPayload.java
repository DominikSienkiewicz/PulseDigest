package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.ai;

import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PromptItemMeta;

import java.util.Map;

/**
 * The user prompt sent to the model together with the trusted metadata of every item it contains,
 * keyed by canonical URL. Both halves are produced in one pass so the map always describes exactly
 * the payload that was sent — a re-join against a differently-selected item set would be worthless.
 */
public record PromptPayload(String userPrompt, Map<String, PromptItemMeta> inputMeta) {
}
