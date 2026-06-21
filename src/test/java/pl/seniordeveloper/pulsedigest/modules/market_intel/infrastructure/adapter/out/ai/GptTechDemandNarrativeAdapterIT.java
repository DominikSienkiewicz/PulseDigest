package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.TechDemandEntry;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.TechDemandSignal;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.InterestProfileProperties;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class GptTechDemandNarrativeAdapterIT {

    private WireMockServer wireMock;
    private GptTechDemandNarrativeAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        adapter = new GptTechDemandNarrativeAdapter(new ObjectMapper(),
                new InterestProfileProperties("Test-Persona-XYZ", List.of("java")));
        setOpenAiClient(adapter, "http://localhost:" + wireMock.port());
    }

    @AfterEach
    void tearDown() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @Test
    void narrateParsesAndTrimsNarrativeAndSendsRanking() throws Exception {
        wireMock.stubFor(post(urlPathEqualTo("/chat/completions"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(openAiResponse("{\"narrative\":\"  Kubernetes dominuje, Spring spada.  \"}"))));

        String narrative = adapter.narrate(sampleSignal());

        assertThat(narrative).isEqualTo("Kubernetes dominuje, Spring spada.");
        wireMock.verify(postRequestedFor(urlPathEqualTo("/chat/completions"))
                .withRequestBody(containing("kubernetes"))
                .withRequestBody(containing("Who is hiring"))
                .withRequestBody(containing("Test-Persona-XYZ")));   // persona injected from interest-profile
    }

    @Test
    void narrateReturnsEmptyOnEmptyChoicesGracefully() {
        wireMock.stubFor(post(urlPathEqualTo("/chat/completions"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"choices\":[]}")));

        assertThat(adapter.narrate(sampleSignal())).isEmpty();
    }

    @Test
    void narrateReturnsEmptyForNullSignalWithoutHttpCall() {
        assertThat(adapter.narrate(null)).isEmpty();

        wireMock.verify(0, postRequestedFor(urlPathEqualTo("/chat/completions")));
    }

    private static TechDemandSignal sampleSignal() {
        return new TechDemandSignal(
                "czerwiec 2026",
                "maj 2026",
                "https://news.ycombinator.com/item?id=999",
                120,
                null,
                List.of(
                        new TechDemandEntry("kubernetes", 38, 0.32, 4.0),
                        new TechDemandEntry("spring", 24, 0.20, -2.0)),
                List.of(new TechDemandEntry("java", 6, 0.05, null)));
    }

    private static String openAiResponse(String content) throws Exception {
        return new ObjectMapper().writeValueAsString(Map.of(
                "choices",
                List.of(Map.of("message", Map.of("content", content)))));
    }

    private static void setOpenAiClient(GptTechDemandNarrativeAdapter target, String baseUrl) throws Exception {
        Field field = GptTechDemandNarrativeAdapter.class.getDeclaredField("openAiClient");
        field.setAccessible(true);
        field.set(target, RestClient.builder().baseUrl(baseUrl).build());
    }
}
