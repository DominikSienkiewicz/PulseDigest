package pl.seniordeveloper.pulsedigest.modules.market_intel.application;

import pl.seniordeveloper.pulsedigest.testing.TestModels;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DependencyCoordinate;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DependencyProfile;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SecurityAdvisory;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StackMatchServiceTest {

    private final StackMatchService service = new StackMatchService();

    @Test
    void anAdvisoryNamingAnOwnedCoordinateAffectsTheStack() {
        var affecting = service.advisoryUrlsAffectingStack(profile("org.springframework:spring-web"),
                List.of(advisory("GHSA-1", "https://github.com/advisories/GHSA-1",
                        List.of("org.springframework:spring-web"))));

        assertThat(affecting).containsExactly("https://github.com/advisories/GHSA-1");
    }

    @Test
    void anAdvisoryAboutSomethingTheReaderDoesNotShipDoesNot() {
        var affecting = service.advisoryUrlsAffectingStack(profile("org.springframework:spring-web"),
                List.of(advisory("GHSA-2", "https://github.com/advisories/GHSA-2",
                        List.of("com.hazelcast:hazelcast"))));

        assertThat(affecting).isEmpty();
    }

    /**
     * The guard against the failure mode the entry names: a marker reading "affects your stack" has
     * to be right. {@code org.springframework:spring-webmvc} is a different artifact from
     * {@code spring-web}, and a prefix or substring match would claim otherwise.
     */
    @Test
    void matchingIsOnExactCoordinatesAndNotOnPrefixesOrKeywords() {
        var profile = profile("org.springframework:spring-web");

        assertThat(service.advisoryUrlsAffectingStack(profile, List.of(advisory("GHSA-3", "https://x/3", List.of("org.springframework:spring-webmvc")))))
                .as("a longer artifact id is a different artifact")
                .isEmpty();
        assertThat(service.advisoryUrlsAffectingStack(profile, List.of(advisory("GHSA-4", "https://x/4", List.of("org.other:spring-web")))))
                .as("the same artifact id under a different group is a different artifact")
                .isEmpty();
    }

    @Test
    void anAdvisoryNamingSeveralPackagesMatchesIfAnyOfThemIsOwned() {
        var affecting = service.advisoryUrlsAffectingStack(profile("org.postgresql:postgresql"),
                List.of(advisory("GHSA-5", "https://x/5",
                        List.of("com.unrelated:thing", "org.postgresql:postgresql"))));

        assertThat(affecting).containsExactly("https://x/5");
    }

    @Test
    void withoutAProfileNothingIsClaimedAtAll() {
        var advisories = List.of(advisory("GHSA-6", "https://x/6", List.of("org.postgresql:postgresql")));

        assertThat(service.advisoryUrlsAffectingStack(DependencyProfile.empty(), advisories)).isEmpty();
        assertThat(service.advisoryUrlsAffectingStack(null, advisories)).isEmpty();
    }

    /** Older payloads carry no package list; they must not match everything or crash. */
    @Test
    void anAdvisoryWithNoCapturedPackagesMatchesNothing() {
        var affecting = service.advisoryUrlsAffectingStack(profile("org.postgresql:postgresql"),
                List.of(TestModels.securityAdvisory("GHSA-7", "s", "HIGH", LocalDateTime.now(),
                        List.of("maven"), "https://x/7")));

        assertThat(affecting).isEmpty();
    }

    private static DependencyProfile profile(String key) {
        String[] parts = key.split(":");
        return new DependencyProfile(List.of(new DependencyCoordinate(parts[0], parts[1], "1.0.0", "test")), true, List.of("test"));
    }

    private static SecurityAdvisory advisory(String id, String url, List<String> packages) {
        return new SecurityAdvisory(id, "summary", "HIGH", LocalDateTime.now(), List.of("maven"), url, packages);
    }
}
