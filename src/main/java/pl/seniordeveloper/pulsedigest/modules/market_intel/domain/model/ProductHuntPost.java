package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Product launch fetched from the Product Hunt GraphQL API.
 */
public record ProductHuntPost(
        String name,
        String tagline,
        String url,
        int votesCount,
        List<String> topics,
        LocalDateTime createdAt
) {}
