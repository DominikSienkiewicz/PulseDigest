package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dependency;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DependencyCoordinate;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DependencyProfile;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DependencyRelease;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SemanticVersions;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.ReleaseRadarPort;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.http.ExternalRestClients;

import java.util.ArrayList;
import java.util.List;

/**
 * Asks Maven Central for the current release of each coordinate the reader pins.
 *
 * <p>One search call per artifact, which is why the profile's size matters more than its contents:
 * a reader declaring two hundred dependencies would turn a digest run into two hundred HTTP calls.
 * A per-run cap bounds that, and a coordinate past the cap is skipped with a warning rather than
 * silently dropped.
 *
 * <p>Pre-release pins are skipped by construction. Central only indexes releases, so comparing a
 * {@code -SNAPSHOT} or {@code -RC} pin against its latest release reports a *lower* number as an
 * upgrade — this project's own {@code 4.1.0-SNAPSHOT} Spring Boot pin against Central's 3.5.x is the
 * worked example. Running ahead of releases is a decision, not a staleness to nag about.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class MavenCentralReleaseRadarAdapter implements ReleaseRadarPort {

    private static final String SEARCH_PATH = "/solrsearch/select";
    private static final int MAX_LOOKUPS_PER_RUN = 60;

    @Value("${report.dependency-profile.registry-url:https://search.maven.org}")
    private String registryUrl;

    private final ObjectMapper objectMapper;
    private RestClient client;

    @PostConstruct
    void init() {
        this.client = ExternalRestClients.builder()
                .baseUrl(registryUrl)
                .defaultHeader("Accept", "application/json")
                .defaultHeader("User-Agent", "PulseDigest/1.0")
                .build();
    }

    @Override
    public List<DependencyRelease> outdated(DependencyProfile profile) {
        if (profile == null || profile.isEmpty()) {
            return List.of();
        }
        List<DependencyRelease> behind = new ArrayList<>();
        int lookups = 0;
        for (DependencyCoordinate coordinate : profile.coordinates()) {
            if (coordinate.pinnedVersion().isBlank() || coordinate.isPreRelease()) {
                continue;
            }
            if (++lookups > MAX_LOOKUPS_PER_RUN) {
                log.warn("release-radar stopped at {} lookups — {} coordinate(s) not checked this run",
                        MAX_LOOKUPS_PER_RUN, profile.coordinates().size() - lookups + 1);
                break;
            }
            latestVersionOf(coordinate)
                    .filter(latest -> SemanticVersions.isNewer(latest, coordinate.pinnedVersion()))
                    .ifPresent(latest -> behind.add(new DependencyRelease(coordinate, latest)));
        }
        log.info("release-radar checked={} behind={} from_build_files={}",
                lookups, behind.size(), profile.fromBuildFiles());
        return List.copyOf(behind);
    }

    private java.util.Optional<String> latestVersionOf(DependencyCoordinate coordinate) {
        try {
            String uri = UriComponentsBuilder.fromPath(SEARCH_PATH)
                    .queryParam("q", "g:" + coordinate.groupId() + " AND a:" + coordinate.artifactId())
                    .queryParam("rows", 1)
                    .queryParam("wt", "json")
                    .build()
                    .toUriString();
            String body = client.get().uri(uri).retrieve().body(String.class);
            if (body == null || body.isBlank()) {
                return java.util.Optional.empty();
            }
            JsonNode docs = objectMapper.readTree(body).path("response").path("docs");
            if (!docs.isArray() || docs.isEmpty()) {
                return java.util.Optional.empty();
            }
            String latest = docs.get(0).path("latestVersion").asText("");
            return latest.isBlank() ? java.util.Optional.empty() : java.util.Optional.of(latest);
        } catch (Exception e) {
            // One unreachable artifact must not cost the digest its other radar lines.
            log.warn("release-radar lookup failed for {}: {}", coordinate.key(), e.getMessage());
            return java.util.Optional.empty();
        }
    }
}
