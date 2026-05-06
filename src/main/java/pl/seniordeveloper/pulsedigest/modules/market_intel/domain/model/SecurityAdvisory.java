package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Security advisory fetched from the GitHub Advisory Database (GHSA).
 */
public record SecurityAdvisory(
        String ghsaId,
        String summary,
        String severity,
        LocalDateTime publishedAt,
        List<String> affectedEcosystems,
        String url
) {}
