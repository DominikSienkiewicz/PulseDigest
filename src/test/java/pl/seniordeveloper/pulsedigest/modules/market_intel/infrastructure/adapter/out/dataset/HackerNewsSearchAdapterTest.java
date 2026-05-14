package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ReportProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.AsyncConfig;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

class HackerNewsSearchAdapterTest {

    private static final String HITS_JSON = """
            {
              "hits": [
                {"title":"AI breakthrough","url":"https://example.com/ai","points":30,"objectID":"1"},
                {"title":"Java 26 released","url":"https://example.com/java","points":15,"objectID":"2"},
                {"title":"Spam post","url":"https://example.com/spam","points":3,"objectID":"3"},
                {"title":"HN discussion","url":null,"points":50,"objectID":"4"}
              ]
            }
            """;

    private HackerNewsSearchAdapter adapter;

    @BeforeEach
    void setUp() {
        ReportProperties.HackerNewsProperties props =
                new ReportProperties.HackerNewsProperties(
                        "https://hn.algolia.com/api/v1/search",
                        List.of("ai", "java"),
                        15,
                        25
                );
        ReportProperties reportProperties = new ReportProperties(
                60, 30, null, null, props, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null
        );
        adapter = new HackerNewsSearchAdapter(new ObjectMapper(), reportProperties);
        adapter.init();
    }

    @Test
    void parsesHitsFromJsonResponse() {
        List<HackerNewsSearchAdapter.HnHit> hits = adapter.parseResponse(HITS_JSON);

        assertThat(hits).hasSize(4);
        assertThat(hits.get(0).title()).isEqualTo("AI breakthrough");
        assertThat(hits.get(0).points()).isEqualTo(30);
    }

    @Test
    void returnsEmptyListForMalformedJson() {
        List<HackerNewsSearchAdapter.HnHit> hits = adapter.parseResponse("NOT JSON");
        assertThat(hits).isEmpty();
    }

    @Test
    void returnsEmptyListForEmptyHits() {
        List<HackerNewsSearchAdapter.HnHit> hits =
                adapter.parseResponse("{\"hits\":null}");
        assertThat(hits).isEmpty();
    }

    @Test
    void productionConstructorIsExplicitlyAutowired() {
        boolean hasAutowiredProductionConstructor = Arrays.stream(HackerNewsSearchAdapter.class.getDeclaredConstructors())
                .anyMatch(constructor ->
                        constructor.isAnnotationPresent(Autowired.class)
                                && constructor.getParameterCount() == 3);

        assertThat(hasAutowiredProductionConstructor).isTrue();
    }

    @Test
    void springContextCreatesAdapterWithProductionConstructor() {
        new ApplicationContextRunner()
                .withUserConfiguration(HackerNewsAdapterContext.class)
                .run(context -> assertThat(context).hasSingleBean(HackerNewsSearchAdapter.class));
    }

    @Configuration
    @Import(HackerNewsSearchAdapter.class)
    static class HackerNewsAdapterContext {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        ReportProperties reportProperties() {
            ReportProperties.HackerNewsProperties props =
                    new ReportProperties.HackerNewsProperties(
                            "https://hn.algolia.com/api/v1/search",
                            List.of("ai", "java"),
                            15,
                            25
                    );
            return new ReportProperties(
                    60, 30, null, null, props, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null, null, null
            );
        }

        @Bean(name = AsyncConfig.DATA_FETCH_EXECUTOR)
        Executor dataFetchExecutor() {
            return Runnable::run;
        }
    }
}
