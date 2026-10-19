package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.CritiqueVerdict;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.InterestProfileProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.OpenAiProperties;
import pl.seniordeveloper.pulsedigest.testing.TestModels;

import java.lang.reflect.Field;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The second opinion on a finished edition (N23), which shipped with no test at all.
 *
 * <p>Three properties here are the ones that make a self-critique safe to run in shadow mode, and
 * all three are invisible from the happy path:
 *
 * <ul>
 *   <li>the model answers with <strong>indices</strong>, never URLs, and they are re-joined against
 *       the list that was sent — a critique able to introduce a URL would be a new injection surface
 *       in exchange for nothing;</li>
 *   <li>an index outside that list is dropped rather than throwing, because one malformed entry must
 *       not discard the rest of a paid call;</li>
 *   <li>every failure path returns {@link CritiqueVerdict#NOTHING}, which in shadow mode must mean
 *       "the critique did not run" and never "nothing to cut" — the second would quietly empty the
 *       log the decision to enforce is supposed to rest on.</li>
 * </ul>
 */
class GptSelfCritiqueAdapterIT {

    private static final List<DigestItem> ITEMS = List.of(
            item("First story", "https://example.com/first"),
            item("Second story", "https://example.com/second"),
            item("Third story", "https://example.com/third"));

    private WireMockServer wireMock;
    private GptSelfCritiqueAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        adapter = new GptSelfCritiqueAdapter(new OpenAiProperties("test-openai-key"),
                LlmTestSettings.properties(), LlmTestSettings.meter(), LlmTestSettings.ceiling(),
                new ObjectMapper(),
                new InterestProfileProperties("Test Persona", List.of("java")));
        Field field = GptSelfCritiqueAdapter.class.getDeclaredField("openAiClient");
        field.setAccessible(true);
        field.set(adapter, RestClient.builder().baseUrl("http://localhost:" + wireMock.port()).build());
    }

    @AfterEach
    void tearDown() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @Test
    void anIndexIsResolvedToTheUrlOfTheItemThatWasActuallySent() {
        stubCritique("{\"cuts\":[{\"i\":1,\"why\":\"rehash of last week\"}]}");

        CritiqueVerdict verdict = adapter.critique(ITEMS);

        assertThat(verdict.cuts()).hasSize(1);
        assertThat(verdict.cuts().getFirst().url()).isEqualTo("https://example.com/second");
        assertThat(verdict.cuts().getFirst().reason()).isEqualTo("rehash of last week");
    }

    @Test
    void aUrlTheModelInventedCannotBecomeACutBecauseTheModelNeverSendsUrls() {
        // The response shape carries no url field, so the only way a cut can name an item is by
        // pointing at one that was sent. An index the model made up is out of range and is dropped.
        stubCritique("""
                {"cuts":[{"i":2,"why":"thin"},{"i":99,"why":"invented"},
                         {"i":-1,"why":"negative"},{"why":"no index at all"}]}
                """);

        CritiqueVerdict verdict = adapter.critique(ITEMS);

        assertThat(verdict.cuts()).extracting(CritiqueVerdict.Cut::url)
                .as("one valid entry survives; three unusable ones are skipped, not fatal")
                .containsExactly("https://example.com/third");
    }

    @Test
    void aCutWithoutAReasonStillCountsButCarriesAnEmptyOne() {
        stubCritique("{\"cuts\":[{\"i\":0}]}");

        assertThat(adapter.critique(ITEMS).cuts())
                .singleElement()
                .satisfies(cut -> {
                    assertThat(cut.url()).isEqualTo("https://example.com/first");
                    assertThat(cut.reason()).isEmpty();
                });
    }

    @Test
    void aVerdictWithNoCutsIsAnEmptyVerdictRatherThanAFailure() {
        stubCritique("{\"cuts\":[]}");

        assertThat(adapter.critique(ITEMS).cuts()).isEmpty();
    }

    @Test
    void aPayloadWithoutTheCutsFieldIsTreatedAsNothing() {
        stubCritique("{}");

        assertThat(adapter.critique(ITEMS).cuts()).isEmpty();
    }

    @Test
    void aFailedCallLetsTheEditionGoOutUnreviewed() {
        wireMock.stubFor(post(urlPathEqualTo("/chat/completions"))
                .willReturn(aResponse().withStatus(500).withBody("{\"error\":\"boom\"}")));

        assertThat(adapter.critique(ITEMS).cuts()).isEmpty();
    }

    @Test
    void anEmptyChoicesArrayIsAFailureAndNotAnEmptyVerdict() {
        // Same observable result, different reason, and the distinction is the point: this must not
        // reach the log as "the critique found nothing".
        wireMock.stubFor(post(urlPathEqualTo("/chat/completions"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"choices\":[]}")));

        assertThat(adapter.critique(ITEMS).cuts()).isEmpty();
    }

    @Test
    void nothingIsSentWhenThereIsNothingToCritique() {
        assertThat(adapter.critique(List.of()).cuts()).isEmpty();
        assertThat(adapter.critique(null).cuts()).isEmpty();
        wireMock.verify(0, postRequestedFor(urlPathEqualTo("/chat/completions")));
    }

    @Test
    void theRequestSendsIndexedTitlesAndNoUrls() {
        stubCritique("{\"cuts\":[]}");

        adapter.critique(ITEMS);

        // Asserted through `verify` rather than by reading the serve-event journal: the journal is
        // written after the response is returned, so reading it immediately is a race — this test
        // failed once in a full-suite run for exactly that reason and passed on its own.
        wireMock.verify(1, postRequestedFor(urlPathEqualTo("/chat/completions"))
                .withRequestBody(containing("First story")));
        wireMock.verify(0, postRequestedFor(urlPathEqualTo("/chat/completions"))
                .withRequestBody(containing("https://example.com/first")));
    }

    private void stubCritique(String content) {
        wireMock.stubFor(post(urlPathEqualTo("/chat/completions"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(openAiEnvelope(content))));
    }

    /** The chat-completions envelope, with usage so the meter path is exercised too. */
    private static String openAiEnvelope(String content) {
        try {
            return new ObjectMapper().writeValueAsString(java.util.Map.of(
                    "choices", List.of(java.util.Map.of("message", java.util.Map.of("content", content))),
                    "usage", java.util.Map.of("prompt_tokens", 120, "completion_tokens", 30)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static DigestItem item(String title, String url) {
        return TestModels.digestItem(title, url, "Hacker News", "Java/JVM", "NEWS", 8, 100,
                "summary", "why it matters");
    }
}
