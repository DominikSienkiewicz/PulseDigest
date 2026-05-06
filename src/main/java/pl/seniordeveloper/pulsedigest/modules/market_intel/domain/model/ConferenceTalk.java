package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import java.time.LocalDateTime;

/**
 * Conference talk recording detected via YouTube Data API.
 */
public record ConferenceTalk(
        String title,
        String channelName,
        String conferenceName,
        String url,
        LocalDateTime publishedAt,
        long viewCount
) {}
