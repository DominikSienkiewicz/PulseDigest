package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ProductHuntPost;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ProductHuntProperties;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductHuntAdapterTest {

    private ProductHuntAdapter adapter;

    @BeforeEach
    void setUp() {
        ProductHuntProperties props =
                new ProductHuntProperties(
                        "https://api.producthunt.com/v2/api/graphql",
                        "test-token",
                        100,
                        24 * 365 * 80,
                        List.of("Artificial Intelligence", "Developer Tools"));
        adapter = new ProductHuntAdapter(props, new ObjectMapper());
    }

    @Test
    void parsesPostMatchingTopicAndVoteThreshold() {
        String recent = LocalDateTime.now().minusHours(2).format(DateTimeFormatter.ISO_DATE_TIME);
        String json = """
                {"data":{"posts":{"edges":[
                  {"node":{
                    "name":"AwesomeAI",
                    "tagline":"AI assistant for devs",
                    "url":"https://www.producthunt.com/posts/awesomeai",
                    "votesCount":250,
                    "createdAt":"%s",
                    "topics":{"edges":[{"node":{"name":"Artificial Intelligence"}}]}
                  }}
                ]}}}
                """.formatted(recent);

        List<ProductHuntPost> posts = adapter.parsePosts(json);

        assertThat(posts).hasSize(1);
        ProductHuntPost p = posts.get(0);
        assertThat(p.name()).isEqualTo("AwesomeAI");
        assertThat(p.votesCount()).isEqualTo(250);
        assertThat(p.topics()).contains("Artificial Intelligence");
    }

    @Test
    void filtersOutPostsBelowVoteThreshold() {
        String recent = LocalDateTime.now().minusHours(2).format(DateTimeFormatter.ISO_DATE_TIME);
        String json = """
                {"data":{"posts":{"edges":[
                  {"node":{
                    "name":"LowVotes",
                    "tagline":"Niche tool",
                    "url":"https://www.producthunt.com/posts/lowvotes",
                    "votesCount":5,
                    "createdAt":"%s",
                    "topics":{"edges":[{"node":{"name":"Artificial Intelligence"}}]}
                  }}
                ]}}}
                """.formatted(recent);

        List<ProductHuntPost> posts = adapter.parsePosts(json);

        assertThat(posts).isEmpty();
    }

    @Test
    void filtersOutPostsWithIrrelevantTopics() {
        String recent = LocalDateTime.now().minusHours(2).format(DateTimeFormatter.ISO_DATE_TIME);
        String json = """
                {"data":{"posts":{"edges":[
                  {"node":{
                    "name":"FashionApp",
                    "tagline":"Outfit picker",
                    "url":"https://www.producthunt.com/posts/fashionapp",
                    "votesCount":500,
                    "createdAt":"%s",
                    "topics":{"edges":[{"node":{"name":"Fashion"}}]}
                  }}
                ]}}}
                """.formatted(recent);

        List<ProductHuntPost> posts = adapter.parsePosts(json);

        assertThat(posts).isEmpty();
    }

    @Test
    void filtersOutPostsOutsideLookbackWindow() {
        ProductHuntProperties shortLookback =
                new ProductHuntProperties(
                        "https://api.producthunt.com/v2/api/graphql",
                        "test-token",
                        100,
                        1,
                        List.of("Artificial Intelligence"));
        ProductHuntAdapter shortAdapter = new ProductHuntAdapter(shortLookback, new ObjectMapper());

        String json = """
                {"data":{"posts":{"edges":[
                  {"node":{
                    "name":"OldLaunch",
                    "tagline":"Released years ago",
                    "url":"https://www.producthunt.com/posts/old",
                    "votesCount":1000,
                    "createdAt":"2020-01-01T00:00:00Z",
                    "topics":{"edges":[{"node":{"name":"Artificial Intelligence"}}]}
                  }}
                ]}}}
                """;

        List<ProductHuntPost> posts = shortAdapter.parsePosts(json);

        assertThat(posts).isEmpty();
    }

    @Test
    void degradeWhenDeveloperTokenMissing() {
        ProductHuntProperties noToken =
                new ProductHuntProperties(
                        "https://api.producthunt.com/v2/api/graphql",
                        "",
                        100,
                        24,
                        List.of("Artificial Intelligence"));
        ProductHuntAdapter noTokenAdapter = new ProductHuntAdapter(noToken, new ObjectMapper());

        List<ProductHuntPost> posts = noTokenAdapter.fetchProductLaunches();

        assertThat(posts).isEmpty();
    }

    @Test
    void returnsEmptyListForMalformedJson() {
        List<ProductHuntPost> posts = adapter.parsePosts("NOT JSON");
        assertThat(posts).isEmpty();
    }
}
