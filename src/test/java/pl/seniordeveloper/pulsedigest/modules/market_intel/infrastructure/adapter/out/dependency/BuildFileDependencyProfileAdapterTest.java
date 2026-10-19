package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dependency;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DependencyCoordinate;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DependencyProfile;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.DependencyProfileProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BuildFileDependencyProfileAdapterTest {

    @TempDir
    Path project;

    @Test
    void readsCoordinatesOutOfAGradleScript() throws IOException {
        Files.writeString(project.resolve("build.gradle.kts"), """
                dependencies {
                    implementation("org.springframework.boot:spring-boot-starter:3.5.0")
                    implementation("io.micrometer:micrometer-core")
                    testImplementation("org.assertj:assertj-core:3.27.3")
                }
                """);

        DependencyProfile profile = adapter(List.of(project.toString()), List.of()).load();

        assertThat(profile.fromBuildFiles()).isTrue();
        assertThat(profile.keys()).contains("org.springframework.boot:spring-boot-starter",
                "io.micrometer:micrometer-core",
                "org.assertj:assertj-core");
        assertThat(profile.byKey().get("org.springframework.boot:spring-boot-starter").pinnedVersion())
                .isEqualTo("3.5.0");
        assertThat(profile.byKey().get("io.micrometer:micrometer-core").pinnedVersion())
                .as("a coordinate without a version is still a dependency worth knowing about")
                .isEmpty();
    }

    /**
     * The parse refuses to guess a version it would have to interpolate. Reporting `${springVersion}`
     * as a pin would make the radar compare a literal against a release number; reporting no pin
     * keeps the artifact and drops only the claim the parse cannot support.
     */
    @Test
    void doesNotInventAVersionItWouldHaveToResolve() throws IOException {
        Files.writeString(project.resolve("build.gradle"), """
                implementation "org.postgresql:postgresql:${postgresVersion}"
                """);

        DependencyProfile profile = adapter(List.of(project.toString()), List.of()).load();

        assertThat(profile.byKey().get("org.postgresql:postgresql").pinnedVersion()).isEmpty();
    }

    @Test
    void readsCoordinatesOutOfAMavenPom() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <project><dependencies>
                  <dependency>
                    <groupId>com.google.guava</groupId>
                    <artifactId>guava</artifactId>
                    <version>33.4.0-jre</version>
                  </dependency>
                  <dependency>
                    <groupId>org.slf4j</groupId>
                    <artifactId>slf4j-api</artifactId>
                  </dependency>
                </dependencies></project>
                """);

        DependencyProfile profile = adapter(List.of(project.toString()), List.of()).load();

        assertThat(profile.keys()).containsExactlyInAnyOrder("com.google.guava:guava", "org.slf4j:slf4j-api");
        assertThat(profile.byKey().get("com.google.guava:guava").pinnedVersion()).isEqualTo("33.4.0-jre");
    }

    /**
     * The case every scheduled run takes: the reader's other checkouts do not exist in CI. The
     * profile must fall back to what configuration declares and must record that it did, because a
     * digest reporting a narrower stack without saying so is worse than one that admits the source.
     */
    @Test
    void degradesToTheDeclaredListWhenNoBuildFileIsReadable() {
        DependencyProfile profile = adapter(List.of("/nonexistent/project", project.resolve("absent").toString()),
                List.of("org.springframework.boot:spring-boot-starter:4.1.0", "org.postgresql:postgresql")).load();

        assertThat(profile.fromBuildFiles()).isFalse();
        assertThat(profile.scannedPaths()).isEmpty();
        assertThat(profile.keys()).containsExactlyInAnyOrder("org.springframework.boot:spring-boot-starter", "org.postgresql:postgresql");
    }

    @Test
    void anUnparseableDeclaredCoordinateIsDroppedRatherThanHalfUsed() {
        DependencyProfile profile = adapter(List.of(), List.of("not-a-coordinate", "org.slf4j:slf4j-api")).load();

        assertThat(profile.keys()).containsExactly("org.slf4j:slf4j-api");
    }

    @Test
    void disabledMeansNoProfileAtAll() {
        BuildFileDependencyProfileAdapter disabled = new BuildFileDependencyProfileAdapter(
                new DependencyProfileProperties(false, List.of(project.toString()), List.of()));

        assertThat(disabled.load().isEmpty()).isTrue();
    }

    /** A pre-release pin is a deliberate choice to run ahead of releases, not something to upgrade. */
    @Test
    void recognisesPreReleasePinsSoTheRadarCanSkipThem() {
        assertThat(new DependencyCoordinate("org.springframework.boot", "spring-boot-starter",
                "4.1.0-SNAPSHOT", "x").isPreRelease()).isTrue();
        assertThat(new DependencyCoordinate("org.postgresql", "postgresql", "42.7.4", "x")
                .isPreRelease()).isFalse();
    }

    private static BuildFileDependencyProfileAdapter adapter(List<String> paths, List<String> declared) {
        return new BuildFileDependencyProfileAdapter(new DependencyProfileProperties(true, paths, declared));
    }
}
