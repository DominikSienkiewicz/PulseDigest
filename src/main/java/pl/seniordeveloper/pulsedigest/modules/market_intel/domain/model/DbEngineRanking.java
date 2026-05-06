package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import java.time.LocalDateTime;

/**
 * Database ranking movement from DB-Engines.com (monthly cadence).
 */
public record DbEngineRanking(
        String dbName,
        int rank,
        int rankChange,
        double score,
        double scoreChange,
        String url,
        LocalDateTime updatedAt
) {}
