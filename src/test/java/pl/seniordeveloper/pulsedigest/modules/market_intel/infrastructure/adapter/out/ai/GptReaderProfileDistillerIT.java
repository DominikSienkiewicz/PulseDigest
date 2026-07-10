package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ProfileEvidence;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ProfileHypothesis;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReaderProfile;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.InterestProfileProperties;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class GptReaderProfileDistillerIT {

    private static final ProfileEvidence EVIDENCE = new ProfileEvidence(
            Map.of("java/jvm", 8, "research", -4), Map.of("arXiv", -4), List.of("A dull paper"));

    private WireMockServer wireMock;
    private GptReaderProfileDistiller distiller;

    @BeforeEach
    void setUp() throws Exception {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        distiller = new GptReaderProfileDistiller(new ObjectMapper().registerModule(new JavaTimeModule()),
                new InterestProfileProperties("Test Persona", List.of("java")));
        Field field = GptReaderProfileDistiller.class.getDeclaredField("openAiClient");
        field.setAccessible(true);
        field.set(distiller, RestClient.builder().baseUrl("http://localhost:" + wireMock.port()).build());
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void distilsDatedHypothesesFromTheReadersVotes() throws Exception {
        stubContent("""
                {"hypotheses":[
                  {"statement":"Chce więcej Javy","evidence":"+8 netto w java/jvm"},
                  {"statement":"Nie chce paperów","evidence":"−4 netto w research i arXiv"}
                ]}""");

        Optional<ReaderProfile> profile = distiller.distil(EVIDENCE);

        assertThat(profile).get().satisfies(p -> {
            assertThat(p.voteCount()).isEqualTo(16);
            assertThat(p.hypotheses()).extracting(ProfileHypothesis::statement)
                    .containsExactly("Chce więcej Javy", "Nie chce paperów");
            assertThat(p.hypotheses()).allSatisfy(h ->
                    assertThat(h.observedAt()).isEqualTo(LocalDate.now(ZoneOffset.UTC)));
        });
    }

    @Test
    void dropsAnyHypothesisTheModelCouldNotEvidence() {
        // A claim without evidence is a guess dressed as a model.
        stubContentQuietly("""
                {"hypotheses":[
                  {"statement":"Chce więcej Javy","evidence":"+8 netto w java/jvm"},
                  {"statement":"Lubi poranki","evidence":""},
                  {"statement":"Nienawidzi Rusta"}
                ]}""");

        assertThat(distiller.distil(EVIDENCE)).get()
                .satisfies(p -> assertThat(p.hypotheses()).hasSize(1));
    }

    @Test
    void capsTheNumberOfHypothesesSoTheAddendumStaysSmall() {
        StringBuilder sb = new StringBuilder("{\"hypotheses\":[");
        for (int i = 0; i < 9; i++) {
            sb.append(i > 0 ? "," : "")
                    .append("{\"statement\":\"H").append(i).append("\",\"evidence\":\"+1\"}");
        }
        stubContentQuietly(sb.append("]}").toString());

        assertThat(distiller.distil(EVIDENCE)).get()
                .satisfies(p -> assertThat(p.hypotheses()).hasSize(5));
    }

    @Test
    void anEmptyHypothesisListMeansKeepTheOldProfile() {
        stubContentQuietly("{\"hypotheses\":[]}");

        assertThat(distiller.distil(EVIDENCE)).isEmpty();
    }

    @Test
    void aFailedCallNeverWipesMonthsOfAccumulatedModel() {
        wireMock.stubFor(post(urlPathEqualTo("/chat/completions")).willReturn(aResponse().withStatus(500)));

        assertThat(distiller.distil(EVIDENCE)).isEmpty();
    }

    @Test
    void doesNotCallTheModelWhenThereIsNothingToDistil() {
        assertThat(distiller.distil(new ProfileEvidence(Map.of(), Map.of(), List.of()))).isEmpty();

        wireMock.verify(0, postRequestedFor(urlPathEqualTo("/chat/completions")));
    }

    private void stubContent(String content) throws Exception {
        stubContentQuietly(content);
    }

    private void stubContentQuietly(String content) {
        try {
            String body = new ObjectMapper().writeValueAsString(Map.of(
                    "choices", List.of(Map.of("message", Map.of("content", content)))));
            wireMock.stubFor(post(urlPathEqualTo("/chat/completions"))
                    .willReturn(aResponse().withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(body)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
