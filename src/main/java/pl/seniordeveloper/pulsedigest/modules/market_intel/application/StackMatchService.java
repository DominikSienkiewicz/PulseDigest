package pl.seniordeveloper.pulsedigest.modules.market_intel.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DependencyProfile;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SecurityAdvisory;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Decides which advisories touch dependencies the reader actually ships (N06).
 *
 * <p>"Critical CVE in a library you do not use" and "moderate CVE in one you ship" are different
 * emails, and the digest could not tell them apart: {@code InterestProfileProperties} encodes what
 * the reader is *interested* in, which is a topic, while this encodes what they *depend* on, which
 * is a fact.
 *
 * <p>Matching is on exact {@code group:artifact} coordinates — the form GitHub advisories use and
 * the form build files declare. Deliberately not fuzzy keyword matching against titles: a marker
 * reading "affects your stack" has to be right, and "spring" appearing in a headline is not evidence
 * that the reader ships the affected artifact. A missed match costs a marker; a wrong one costs the
 * marker's credibility everywhere else.
 */
@Slf4j
@Service
public class StackMatchService {

    /**
     * URLs of advisories naming at least one coordinate the reader depends on.
     *
     * <p>Keyed by URL because that is what survives synthesis: an advisory reaches the digest as a
     * {@code DigestItem} the model wrote, and its link is the only field that still identifies which
     * advisory it came from.
     */
    public Set<String> advisoryUrlsAffectingStack(DependencyProfile profile, List<SecurityAdvisory> advisories) {
        if (profile == null || profile.isEmpty() || advisories == null || advisories.isEmpty()) {
            return Set.of();
        }
        Set<String> owned = profile.keys();
        Set<String> affecting = advisories.stream()
                .filter(advisory -> advisory.affectedPackages().stream().anyMatch(owned::contains))
                .map(SecurityAdvisory::url)
                .filter(url -> url != null && !url.isBlank())
                .collect(Collectors.toSet());
        log.info("stack-match advisories={} affecting_your_stack={} owned_coordinates={}",
                advisories.size(), affecting.size(), owned.size());
        return affecting;
    }
}
