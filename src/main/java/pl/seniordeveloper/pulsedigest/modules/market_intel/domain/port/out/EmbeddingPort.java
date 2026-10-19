package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out;

import java.util.List;

/** Turns text into vectors, for finding the same story told three ways (N21). */
public interface EmbeddingPort {

    /**
     * Embeds every text in one call.
     *
     * <p>Batched deliberately: the catalogue priced this feature at "an embedding call per item",
     * and the provider takes an array, so an edition costs one request rather than a hundred. That
     * is the cost mitigation that actually matters here — a content-hash cache saves little when
     * cross-edition dedup has already removed the repeats.
     *
     * @return one vector per input, in the same order; empty when the call fails, because a
     *         clustering pass that silently used partial vectors would merge the wrong stories
     */
    List<float[]> embed(List<String> texts);
}
