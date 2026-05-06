package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import java.time.LocalDateTime;

/**
 * JEP status change detected from OpenJDK commit history.
 */
public record JepUpdate(
        String jepId,
        String title,
        String status,
        LocalDateTime updatedAt,
        String url
) {}
