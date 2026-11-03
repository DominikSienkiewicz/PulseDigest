package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceProposal;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.InterestProfileProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.OpenAiProperties;
import pl.seniordeveloper.pulsedigest.testing.TestModels;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Source discovery (N28), which shipped with no test at all: which feeds or accounts the published
 * record keeps citing that this digest does not read.
 *
 * <p>This adapter proposes and changes nothing, so the risk it carries is not damage — it is a
 * proposal nobody can check. {@link SourceProposal#isSupported()} is what filters those out, and the
 * assertions below pin the adapter's half of that contract: the evidence the model returns is carried
 * through intact, a proposal without evidence still arrives (so the caller, not the adapter, decides
 * it is unusable), and every failure path yields no proposals rather than an unexplained one.
 */
class GptSourceDiscoveryAdapterIT {

    private static final List<DigestItem> BEST = List.of(
            TestModels.digestItem("Spring Boot 4.1", "https://example.com/a", "GitHub Releases",
                    "Java/JVM", "RELEASE", 9, 100, "summary", "why"),
            TestModels.digestItem("Virtual threads in anger", "https://example.com/b", "Hacker News",
                    "Java/JVM", "DISCUSSION", 8, 400, "summary", "why"));

    private WireMockServer wireMock;
    private GptSourceDiscoveryAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        adapter = new GptSourceDiscoveryAdapter(new OpenAiProperties("test-openai-key"),
                LlmTestSettings.properties(), LlmTestSettings.meter(), LlmTestSettings.ceiling(),
                new ObjectMapper(),
                new InterestProfileProperties("Test Persona", List.of("java")));
        Field field = GptSourceDiscoveryAdapter.class.getDeclaredField("openAiClient");
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
    void aProposalArrivesWithTheEvidenceThatJustifiesIt() {
        stubProposals("""
                {"proposals":[
                  {"name":"InfoQ Java","kind":"RSS","why":"cited by three published items",
                   "evidence":["https://example.com/a","https://example.com/b"]}
                ]}
                """);

        List<SourceProposal> proposals = adapter.propose(BEST);

        assertThat(proposals).singleElement().satisfies(p -> {
            assertThat(p.name()).isEqualTo("InfoQ Java");
            assertThat(p.kind()).isEqualTo("RSS");
            assertThat(p.why()).isEqualTo("cited by three published items");
            assertThat(p.evidence()).containsExactly("https://example.com/a", "https://example.com/b");
            assertThat(p.isSupported()).isTrue();
        });
    }

    @Test
    void aProposalWithNoEvidenceIsCarriedThroughAndLeftForTheCallerToRefuse() {
        // The adapter does not silently drop it: `isSupported()` is the one place that judgement
        // lives, and duplicating it here would let the two disagree later.
        stubProposals("""
                {"proposals":[{"name":"Someone's newsletter","kind":"RSS","why":"felt relevant"}]}
                """);

        assertThat(adapter.propose(BEST)).singleElement().satisfies(p -> {
            assertThat(p.evidence()).isEmpty();
            assertThat(p.isSupported())
                    .as("a proposal nobody can check is not worth printing — but that is the caller's call")
                    .isFalse();
        });
    }

    /**
     * Three payloads, one behaviour: nothing to propose is an answer, not a failure.
     *
     * <p>The empty list is the interesting one — "the record does not argue for a missing source" is
     * a successful answer to the question, not a broken call — and the other two reach the same place
     * by being unreadable rather than by being empty. The case name says which is which when one fails.
     */
    @ParameterizedTest(name = "{1}")
    @CsvSource(delimiter = '|', textBlock = """
            {"proposals":[]} | an empty proposal list is a valid answer
            {}               | a payload without the proposals field
            this is not json | content the parser cannot read
            """)
    void aPayloadThatProposesNothingYieldsNothing(String payload, String why) {
        stubProposals(payload);

        assertThat(adapter.propose(BEST)).as(why).isEmpty();
    }

    @Test
    void aFailedCallMeansNoProposalsRatherThanAFailedRun() {
        wireMock.stubFor(post(urlPathEqualTo("/chat/completions"))
                .willReturn(aResponse().withStatus(429).withBody("{\"error\":\"rate limited\"}")));

        assertThat(adapter.propose(BEST)).isEmpty();
    }

    @Test
    void nothingIsSentWhenThereIsNoPublishedRecordToReason() {
        assertThat(adapter.propose(List.of())).isEmpty();
        assertThat(adapter.propose(null)).isEmpty();
        wireMock.verify(0, postRequestedFor(urlPathEqualTo("/chat/completions")));
    }

    @Test
    void theReturnedListIsImmutable() {
        stubProposals("""
                {"proposals":[{"name":"InfoQ Java","kind":"RSS","why":"cited",
                               "evidence":["https://example.com/a"]}]}
                """);

        List<SourceProposal> proposals = adapter.propose(BEST);

        assertThat(proposals).isUnmodifiable();
    }

    private void stubProposals(String content) {
        wireMock.stubFor(post(urlPathEqualTo("/chat/completions"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(openAiEnvelope(content))));
    }

    private static String openAiEnvelope(String content) {
        try {
            return new ObjectMapper().writeValueAsString(Map.of(
                    "choices", List.of(Map.of("message", Map.of("content", content))),
                    "usage", Map.of("prompt_tokens", 200, "completion_tokens", 60)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
