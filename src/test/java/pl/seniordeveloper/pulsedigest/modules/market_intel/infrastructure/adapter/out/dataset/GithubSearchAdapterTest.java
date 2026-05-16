package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.GithubRepo;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.GithubProperties;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GithubSearchAdapterTest {

    @Test
    void fetchTrendingReposMapsApiResponseAndFallbackFields() throws Exception {
        GithubSearchAdapter adapter = adapter("language:java", 3, """
                {
                  "items": [
                    {
                      "full_name": "spring/project",
                      "description": "Framework",
                      "stargazers_count": 100,
                      "html_url": "https://github.com/spring/project"
                    },
                    {
                      "full_name": null,
                      "description": null,
                      "stargazers_count": null,
                      "html_url": null
                    }
                  ]
                }
                """);

        List<GithubRepo> repos = adapter.fetchTrendingRepos();

        assertThat(repos).hasSize(2);
        assertThat(repos.getFirst().name()).isEqualTo("spring/project");
        assertThat(repos.getFirst().stars()).isEqualTo(100);
        assertThat(repos.get(1).name()).isEqualTo("Brak nazwy");
        assertThat(repos.get(1).description()).isEmpty();
        assertThat(repos.get(1).url()).isEmpty();
    }

    @Test
    void fetchTrendingReposReturnsEmptyForBlankQueryEmptyItemsAndErrors() throws Exception {
        assertThat(adapter("", 3, "{\"items\":[]}").fetchTrendingRepos()).isEmpty();
        assertThat(adapter("language:java", 3, "{\"items\":[]}").fetchTrendingRepos()).isEmpty();
        assertThat(adapter("language:java", 3, "NOT JSON").fetchTrendingRepos()).isEmpty();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static GithubSearchAdapter adapter(String query, int limit, String responseBody) throws Exception {
        GithubProperties props = new GithubProperties(query, limit);
        GithubSearchAdapter adapter = new GithubSearchAdapter(new ObjectMapper(), props);

        Field propsField = GithubSearchAdapter.class.getDeclaredField("props");
        propsField.setAccessible(true);
        propsField.set(adapter, props);

        RestClient restClient = mock(RestClient.class);
        RestClient.RequestHeadersUriSpec uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
        when(restClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(any(URI.class))).thenReturn(uriSpec);
        when(uriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenReturn(responseBody);

        Field restClientField = GithubSearchAdapter.class.getDeclaredField("restClient");
        restClientField.setAccessible(true);
        restClientField.set(adapter, restClient);
        return adapter;
    }
}
