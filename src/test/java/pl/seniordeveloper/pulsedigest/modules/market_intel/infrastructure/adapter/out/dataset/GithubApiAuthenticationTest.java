package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.CncfLandscapeProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.GithubApiProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.GithubProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.GithubReleasesProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.OpenJdkProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.SecurityAdvisoriesProperties;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every source on {@code api.github.com} shares one rate limit, so every one of them must carry the
 * token: unauthenticated, the whole family gets 60 requests an hour per IP — shared, on a hosted
 * runner, with every other tenant of that address — and the edition opens with the "exhausted
 * limits" banner naming "GitHub API (token)".
 *
 * <p>The adapters build their production client in {@code init()}; only its transport is swapped
 * here, so the headers asserted are the ones that client would send.
 */
class GithubApiAuthenticationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final GithubApiProperties WITH_TOKEN = new GithubApiProperties("test-token");
    private static final GithubApiProperties WITHOUT_TOKEN = new GithubApiProperties("");

    static Stream<GithubSource<?>> githubSources() {
        return Stream.of(
                new GithubSource<>("GitHub Search",
                        api -> new GithubSearchAdapter(MAPPER, new GithubProperties("topic:ai", 3, 5), api),
                        GithubSearchAdapter::init,
                        GithubSearchAdapter::fetchTrendingRepos,
                        "{\"items\":[]}"),
                new GithubSource<>("GitHub Releases",
                        api -> new GithubReleasesAdapter(
                                new GithubReleasesProperties(List.of("spring-projects/spring-boot"), 104), MAPPER, api),
                        GithubReleasesAdapter::init,
                        GithubReleasesAdapter::fetchLatestReleases,
                        "[]"),
                new GithubSource<>("Security Advisories",
                        api -> new SecurityAdvisoryAdapter(new SecurityAdvisoriesProperties(
                                "https://api.github.com/advisories", 20, 104, List.of("HIGH"), List.of("maven")), MAPPER, api),
                        SecurityAdvisoryAdapter::init,
                        SecurityAdvisoryAdapter::fetchSecurityAdvisories,
                        "[]"),
                new GithubSource<>("OpenJDK JEP",
                        api -> new OpenJdkJepAdapter(new OpenJdkProperties(
                                "https://api.github.com/repos/openjdk/jdk/commits", 10, List.of("Candidate")), MAPPER, api),
                        OpenJdkJepAdapter::init,
                        OpenJdkJepAdapter::fetchJepUpdates,
                        "[]"),
                new GithubSource<>("CNCF Landscape",
                        api -> new CncfLandscapeAdapter(new CncfLandscapeProperties(
                                "https://api.github.com/repos/cncf/landscape/commits", 7, List.of("sandbox")), MAPPER, api),
                        CncfLandscapeAdapter::init,
                        CncfLandscapeAdapter::fetchCncfLandscapeChanges,
                        "[]"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("githubSources")
    void sendsTheConfiguredTokenAsBearerAuthorization(GithubSource<?> source) throws ReflectiveOperationException {
        assertThat(source.authorizationHeadersSentWith(WITH_TOKEN)).containsExactly("Bearer test-token");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("githubSources")
    void sendsNoAuthorizationHeaderWithoutAToken(GithubSource<?> source) throws ReflectiveOperationException {
        // A bare "Bearer " would turn every GitHub source into a 401 instead of the unauthenticated limit.
        assertThat(source.authorizationHeadersSentWith(WITHOUT_TOKEN)).containsExactly((String) null);
    }

    private record GithubSource<A>(
            String name,
            Function<GithubApiProperties, A> create,
            Consumer<A> init,
            Consumer<A> fetch,
            String emptyBody) {

        /** One entry per request the fetch made: that request's {@code Authorization} value, or null. */
        List<String> authorizationHeadersSentWith(GithubApiProperties api) throws ReflectiveOperationException {
            A adapter = create.apply(api);
            init.accept(adapter);
            List<String> sent = new ArrayList<>();
            Field restClient = adapter.getClass().getDeclaredField("restClient");
            restClient.setAccessible(true);
            RestClient production = (RestClient) restClient.get(adapter);
            restClient.set(adapter, production.mutate()
                    .requestInterceptor((request, body, execution) -> {
                        sent.add(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
                        MockClientHttpResponse response =
                                new MockClientHttpResponse(emptyBody.getBytes(StandardCharsets.UTF_8), HttpStatus.OK);
                        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                        return response;
                    })
                    .build());
            fetch.accept(adapter);
            return sent;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
