package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.OpenAiProperties;

import java.lang.reflect.Field;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The embeddings call behind semantic clustering (N21), which shipped with no test at all.
 *
 * <p>Two of the behaviours below are the difference between clustering that works and clustering
 * that silently merges unrelated stories, and neither is visible from reading the happy path:
 * the adapter sorts the response by the {@code index} each vector carries rather than trusting the
 * order it arrives in, and it returns <em>no</em> vectors rather than a partial list whenever the
 * count does not match what was sent. Clustering on partial or misaligned vectors is the one outcome
 * worse than not clustering, and it would look like a working feature.
 */
class GptEmbeddingAdapterIT {

    private WireMockServer wireMock;
    private GptEmbeddingAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        adapter = new GptEmbeddingAdapter(new OpenAiProperties("test-openai-key"),
                LlmTestSettings.meter(), LlmTestSettings.ceiling(), new ObjectMapper());
        set(adapter, "model", "text-embedding-3-small");
        set(adapter, "openAiClient", RestClient.builder()
                .baseUrl("http://localhost:" + wireMock.port()).build());
    }

    @AfterEach
    void tearDown() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @Test
    void vectorsComeBackInTheOrderTheTextsWereSentRegardlessOfTheResponsesOwnOrder() {
        // The response carries an index per vector, and the API does not promise to return them in
        // order. A reordered response mapping stories to the wrong vectors is exactly the bug that
        // would merge unrelated articles, and nothing downstream could detect it.
        stubEmbeddings("""
                {"data":[
                  {"index":2,"embedding":[0.3,0.3]},
                  {"index":0,"embedding":[0.1,0.1]},
                  {"index":1,"embedding":[0.2,0.2]}
                ],"usage":{"prompt_tokens":12}}
                """);

        List<float[]> vectors = adapter.embed(List.of("first", "second", "third"));

        assertThat(vectors).hasSize(3);
        assertThat(vectors.get(0)[0]).isEqualTo(0.1f);
        assertThat(vectors.get(1)[0]).isEqualTo(0.2f);
        assertThat(vectors.get(2)[0]).isEqualTo(0.3f);
    }

    @Test
    void aShortResponseYieldsNoVectorsAtAllRatherThanAPartialList() {
        stubEmbeddings("""
                {"data":[{"index":0,"embedding":[0.1,0.1]}],"usage":{"prompt_tokens":4}}
                """);

        assertThat(adapter.embed(List.of("first", "second", "third")))
                .as("two stories silently clustered against one vector is worse than no clustering")
                .isEmpty();
    }

    @Test
    void aFailedCallLeavesTheEditionUnclusteredInsteadOfFailingTheRun() {
        wireMock.stubFor(post(urlPathEqualTo("/embeddings"))
                .willReturn(aResponse().withStatus(500).withBody("{\"error\":\"boom\"}")));

        assertThat(adapter.embed(List.of("first"))).isEmpty();
    }

    @Test
    void unparseableJsonIsTreatedAsNoVectorsRatherThanPropagating() {
        stubEmbeddings("not json at all");

        assertThat(adapter.embed(List.of("first"))).isEmpty();
    }

    @Test
    void noTextsMeansNoCallIsMadeAtAll() {
        assertThat(adapter.embed(List.of())).isEmpty();
        assertThat(adapter.embed(null)).isEmpty();
        assertThat(wireMock.getAllServeEvents()).isEmpty();
    }

    @Test
    void aResponseWithoutUsageStillYieldsItsVectors() {
        // Usage is optional in the response and the meter must not be the reason a good call is lost.
        stubEmbeddings("""
                {"data":[{"index":0,"embedding":[0.5,0.5]}]}
                """);

        assertThat(adapter.embed(List.of("only one"))).hasSize(1);
    }

    private void stubEmbeddings(String body) {
        wireMock.stubFor(post(urlPathEqualTo("/embeddings"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    private static void set(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
