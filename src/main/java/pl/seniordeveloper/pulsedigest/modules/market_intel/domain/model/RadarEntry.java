package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import java.time.LocalDateTime;

/**
 * Entry from the Thoughtworks Technology Radar (quarterly publication).
 */
public record RadarEntry(
        String name,
        String ring,
        String quadrant,
        String description,
        String url,
        LocalDateTime publishedAt
) {}
