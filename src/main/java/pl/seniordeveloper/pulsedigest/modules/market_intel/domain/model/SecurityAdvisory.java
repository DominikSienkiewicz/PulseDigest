package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Security advisory fetched from the GitHub Advisory Database (GHSA).
 *
 * <p>{@code affectedPackages} carries the coordinates the advisory names — {@code org.bytedeco:javacv}
 * and the like — which the list endpoint returns and this model discarded until 2026-10-18. Without
 * them "critical CVE in a library you do not use" and "moderate CVE in one you ship" are the same
 * line in the digest, and N06's whole point is that they are different emails.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SecurityAdvisory(
        String ghsaId,
        String summary,
        String severity,
        LocalDateTime publishedAt,
        List<String> affectedEcosystems,
        String url,
        List<String> affectedPackages
) {

    public SecurityAdvisory {
        affectedEcosystems = affectedEcosystems != null ? List.copyOf(affectedEcosystems) : List.of();
        affectedPackages = affectedPackages != null ? List.copyOf(affectedPackages) : List.of();
    }

}
