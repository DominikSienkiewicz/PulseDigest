package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import java.time.LocalDateTime;

/**
 * Official announcement scraped from an AI lab's own blog or newsroom
 * (Anthropic, OpenAI, Google Gemini). Highest-signal source for model / product
 * news — fed to the digest with elevated weight.
 */
public record LabAnnouncement(
        String title,
        String url,
        String summary,
        String source,
        LocalDateTime publishedAt
) {
}
