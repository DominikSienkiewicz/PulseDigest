package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import java.time.LocalDateTime;

/**
 * Trending AI model fetched from the Hugging Face Hub API.
 */
public record HuggingFaceModel(
        String id,
        String author,
        long downloads,
        long likes,
        LocalDateTime lastModified,
        String pipelineTag,
        String url
) {}
