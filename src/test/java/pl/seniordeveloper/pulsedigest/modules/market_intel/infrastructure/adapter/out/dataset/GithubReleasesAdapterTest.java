package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SoftwareRelease;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.GithubReleasesProperties;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GithubReleasesAdapterTest {

    private static final String RELEASES_JSON = """
            [
              {
                "tag_name": "v3.5.0",
                "name": "Spring Boot 3.5.0",
                "html_url": "https://github.com/spring-projects/spring-boot/releases/tag/v3.5.0",
                "body": "## What's New\\n- Virtual thread improvements\\n- Spring AI integration updates",
                "published_at": "2099-05-01T08:00:00Z",
                "prerelease": false,
                "draft": false
              },
              {
                "tag_name": "v3.5.0-RC1",
                "name": "Spring Boot 3.5.0-RC1",
                "html_url": "https://github.com/spring-projects/spring-boot/releases/tag/v3.5.0-RC1",
                "body": "Release candidate",
                "published_at": "2099-04-20T08:00:00Z",
                "prerelease": true,
                "draft": false
              },
              {
                "tag_name": "v3.4.0-DRAFT",
                "name": "Draft",
                "html_url": "https://github.com/spring-projects/spring-boot/releases/tag/draft",
                "body": "Draft release",
                "published_at": "2099-04-19T08:00:00Z",
                "prerelease": false,
                "draft": true
              }
            ]
            """;

    private GithubReleasesAdapter adapter;

    @BeforeEach
    void setUp() {
        GithubReleasesProperties props =
                new GithubReleasesProperties(
                        List.of("spring-projects/spring-boot"), 48);
        adapter = new GithubReleasesAdapter(props, new ObjectMapper());
    }

    @Test
    void parsesOnlyNonPrereleaseNonDraftReleases() {
        List<SoftwareRelease> releases = adapter.parseReleases(
                RELEASES_JSON, "spring-projects/spring-boot", 0);

        assertThat(releases).hasSize(1);
        SoftwareRelease release = releases.get(0);
        assertThat(release.repoFullName()).isEqualTo("spring-projects/spring-boot");
        assertThat(release.version()).isEqualTo("v3.5.0");
        assertThat(release.url()).isEqualTo("https://github.com/spring-projects/spring-boot/releases/tag/v3.5.0");
        assertThat(release.releaseNotesExcerpt()).contains("Virtual thread");
    }

    @Test
    void truncatesReleaseNotesTo300Chars() {
        String longBody = "x".repeat(400);
        String json = """
                [{"tag_name":"v1.0.0","name":"v1.0.0",
                  "html_url":"https://github.com/test/repo/releases/tag/v1.0.0",
                  "body":"%s","published_at":"2099-05-01T08:00:00Z",
                  "prerelease":false,"draft":false}]
                """.formatted(longBody);

        List<SoftwareRelease> releases = adapter.parseReleases(json, "test/repo", 0);

        assertThat(releases).hasSize(1);
        assertThat(releases.get(0).releaseNotesExcerpt().length()).isLessThanOrEqualTo(300);
    }

    @Test
    void excludesReleasesOlderThanLookbackWindow() {
        String json = """
                [{"tag_name":"v1.0.0","name":"v1.0.0",
                  "html_url":"https://github.com/test/repo/releases/tag/v1.0.0",
                  "body":"Old release","published_at":"2020-01-01T08:00:00Z",
                  "prerelease":false,"draft":false}]
                """;

        List<SoftwareRelease> releases = adapter.parseReleases(json, "test/repo", 24);

        assertThat(releases).isEmpty();
    }

    @Test
    void returnsEmptyListForMalformedJson() {
        List<SoftwareRelease> releases = adapter.parseReleases("NOT JSON", "test/repo", 0);
        assertThat(releases).isEmpty();
    }
}
