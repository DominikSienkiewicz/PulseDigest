package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import java.time.LocalDateTime;

/**
 * Library/package trend fetched from the Libraries.io API.
 */
public record PackageTrend(
        String name,
        String platform,
        String description,
        long stars,
        int dependentProjects,
        String url,
        LocalDateTime latestReleaseAt
) {}
