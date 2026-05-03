package pl.seniordeveloper.pulsedigest.modules.market_intel.application.query;

import java.time.Instant;

/**
 * Read-model projection of a report job — decouples API contract from domain model.
 */
public record MarketIntelStatusView(
        String jobId,
        String status,
        String error,
        Instant createdAt,
        Instant completedAt
) {
}
