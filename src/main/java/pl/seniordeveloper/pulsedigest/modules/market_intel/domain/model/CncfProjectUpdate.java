package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import java.time.LocalDateTime;

/**
 * Project status change detected from the CNCF Landscape GitHub repository.
 */
public record CncfProjectUpdate(
        String projectName,
        String category,
        String status,
        String description,
        String url,
        LocalDateTime updatedAt
) {}
