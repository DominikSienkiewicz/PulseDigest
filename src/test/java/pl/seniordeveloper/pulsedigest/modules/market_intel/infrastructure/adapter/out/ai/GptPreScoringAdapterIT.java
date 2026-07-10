package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PreScoringCandidate;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.InterestProfileProperties;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class GptPreScoringAdapterIT {

    private WireMockServer wireMock;
    private GptPreScoringAdapter adapter;

    private static final List<PreScoringCandidate> CANDIDATES = List.of(
            new PreScoringCandidate("https://example.com/a", "Spring Boot 4.2", "GitHub Releases", 0),
            new PreScoringCandidate("https://example.com/b", "Gardening tips", "RSS/Blog", 0));

    @BeforeEach
    void setUp() throws Exception {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        adapter = new GptPreScoringAdapter(new ObjectMapper(),
                new InterestProfileProperties("Test Persona", List.of("java")));
        Field field = GptPreScoringAdapter.class.getDeclaredField("openAiClient");
        field.setAccessible(true);
        field.set(adapter, RestClient.builder().baseUrl("http://localhost:" + wireMock.port()).build());
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void mapsIndexedScoresBackOntoCandidateUrls() throws Exception {
        stubContent("{\"scores\":[{\"i\":0,\"s\":9},{\"i\":1,\"s\":1}]}");

        Map<String, Integer> scores = adapter.score(CANDIDATES);

        assertThat(scores).containsEntry("https://example.com/a", 9).containsEntry("https://example.com/b", 1);
    }

    @Test
    void clampsOutOfRangeScoresAndIgnoresIndicesThatDoNotExist() throws Exception {
        stubContent("{\"scores\":[{\"i\":0,\"s\":99},{\"i\":7,\"s\":5},{\"i\":1,\"s\":-3}]}");

        Map<String, Integer> scores = adapter.score(CANDIDATES);

        assertThat(scores).hasSize(2)
                .containsEntry("https://example.com/a", 10)
                .containsEntry("https://example.com/b", 0);
    }

    @Test
    void returnsNoOpinionWhenTheTriageCallFails() {
        wireMock.stubFor(post(urlPathEqualTo("/chat/completions")).willReturn(aResponse().withStatus(500)));

        assertThat(adapter.score(CANDIDATES)).isEmpty();
    }

    @Test
    void returnsNoOpinionForAnEmptyCandidateList() {
        assertThat(adapter.score(List.of())).isEmpty();
        wireMock.verify(0, com.github.tomakehurst.wiremock.client.WireMock
                .postRequestedFor(urlPathEqualTo("/chat/completions")));
    }

    private void stubContent(String content) throws Exception {
        String body = new ObjectMapper().writeValueAsString(Map.of(
                "choices", List.of(Map.of("message", Map.of("content", content)))));
        wireMock.stubFor(post(urlPathEqualTo("/chat/completions"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }
}
