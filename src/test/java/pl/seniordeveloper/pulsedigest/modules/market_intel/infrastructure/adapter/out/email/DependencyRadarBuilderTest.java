package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DependencyCoordinate;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DependencyRelease;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DependencyRadarBuilderTest {

    @Test
    void namesTheArtefactThePinAndWhatIsAvailable() {
        String html = DependencyRadarBuilder.buildDependencyRadarSection(
                List.of(new DependencyRelease(new DependencyCoordinate("org.springframework.boot",
                        "spring-boot-starter", "3.5.0", "/home/dev/api/build.gradle.kts"), "3.5.3")));

        assertThat(html)
                .contains("org.springframework.boot:spring-boot-starter")
                .contains("3.5.0")
                .contains("3.5.3")
                .as("the reader has to know which project to touch")
                .contains("/home/dev/api/build.gradle.kts");
    }

    /** Keeping up is the normal state, so the section disappears rather than reporting nothing. */
    @Test
    void rendersNothingWhenEveryDependencyIsCurrent() {
        assertThat(DependencyRadarBuilder.buildDependencyRadarSection(List.of())).isEmpty();
        assertThat(DependencyRadarBuilder.buildDependencyRadarSection(null)).isEmpty();
    }

    @Test
    void omitsTheOriginWhenTheCoordinateCameFromConfigurationRatherThanAProject() {
        String html = DependencyRadarBuilder.buildDependencyRadarSection(
                List.of(new DependencyRelease(new DependencyCoordinate("org.postgresql", "postgresql",
                        "42.7.3", "configuration"), "42.7.4")));

        assertThat(html).contains("org.postgresql:postgresql").doesNotContain("configuration");
    }
}
