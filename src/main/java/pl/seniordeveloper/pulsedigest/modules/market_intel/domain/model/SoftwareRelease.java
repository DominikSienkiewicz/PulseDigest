package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import java.time.LocalDateTime;

/**
 * Software release fetched from GitHub Releases API.
 */
public record SoftwareRelease(
        String repoFullName,
        String version,
        String releaseNotesExcerpt,
        String url,
        LocalDateTime releasedAt
) {}
