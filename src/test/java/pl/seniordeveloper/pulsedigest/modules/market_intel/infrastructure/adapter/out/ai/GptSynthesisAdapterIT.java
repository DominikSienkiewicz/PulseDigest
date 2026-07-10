package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportData;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchResult;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GptSynthesisAdapterIT {

    private WireMockServer wireMock;
    private GptSynthesisAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        adapter = new GptSynthesisAdapter(new ObjectMapper(), new StubPromptBuilder());
        setOpenAiClient(adapter, "http://localhost:" + wireMock.port());
    }

    @AfterEach
    void tearDown() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @Test
    void synthesizePostsJsonModeRequestAndParsesReport() throws Exception {
        String reportContent = """
                {
                  "email_preview": "Preview",
                  "editorial": "Lead",
                  "top_insights": ["Insight"],
                  "items": [
                    {
                      "title": "Item",
                      "url": "https://example.com",
                      "source": "GitHub",
                      "category": "Java",
                      "type": "RELEASE",
                      "score": 9,
                      "engagement_score": 100,
                      "summary": "Summary"
                    }
                  ]
                }
                """;
        wireMock.stubFor(post(urlPathEqualTo("/chat/completions"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(openAiResponse(reportContent))));

        ReportData report = adapter.synthesize(emptyResearch());

        assertThat(report.emailPreview()).isEqualTo("Preview");
        assertThat(report.items()).hasSize(1);
        wireMock.verify(postRequestedFor(urlPathEqualTo("/chat/completions"))
                .withRequestBody(containing("\"response_format\":{\"type\":\"json_object\"}"))
                .withRequestBody(containing("\"content\":\"system prompt\""))
                .withRequestBody(containing("\"content\":\"user prompt\"")));
    }

    @Test
    void synthesizeWrapsEmptyChoicesAsLlmException() {
        wireMock.stubFor(post(urlPathEqualTo("/chat/completions"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"choices\":[]}")));

        assertThatThrownBy(() -> adapter.synthesize(emptyResearch()))
                .isInstanceOf(LlmSynthesisException.class)
                .hasMessageContaining("OpenAI synthesis failed");
    }

    @Test
    void synthesizeFailsClearlyWhenResponseTruncatedAtTokenCap() throws Exception {
        String truncated = new ObjectMapper().writeValueAsString(Map.of(
                "choices", List.of(Map.of(
                        "message", Map.of("content", "{\"items\":[{\"title\":\"half"),
                        "finish_reason", "length")),
                "usage", Map.of("prompt_tokens", 5000, "completion_tokens", 10000, "total_tokens", 15000)));
        wireMock.stubFor(post(urlPathEqualTo("/chat/completions"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(truncated)));

        assertThatThrownBy(() -> adapter.synthesize(emptyResearch()))
                .isInstanceOf(LlmSynthesisException.class)
                .hasMessageContaining("finish_reason=length");
    }

    @Test
    void synthesizeRetriesOnceAfterTransientServerErrorThenSucceeds() throws Exception {
        wireMock.stubFor(post(urlPathEqualTo("/chat/completions")).inScenario("transient")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("recovered"));
        wireMock.stubFor(post(urlPathEqualTo("/chat/completions")).inScenario("transient")
                .whenScenarioStateIs("recovered")
                .willReturn(jsonResponse(openAiResponse(reportJson()))));

        ReportData report = adapter.synthesize(emptyResearch());

        assertThat(report.emailPreview()).isEqualTo("Preview");
        assertThat(report.editorial())
                .as("a run recovered by retry is a normal digest — no emergency marker")
                .isEqualTo("Lead");
        wireMock.verify(2, postRequestedFor(urlPathEqualTo("/chat/completions"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("gpt-4o"))));
    }

    @Test
    void synthesizeFallsBackToMiniAndFlagsEmergencyDigestWhenPrimaryModelKeepsFailing() throws Exception {
        wireMock.stubFor(post(urlPathEqualTo("/chat/completions"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("gpt-4o")))
                .willReturn(aResponse().withStatus(503)));
        wireMock.stubFor(post(urlPathEqualTo("/chat/completions"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("gpt-4o-mini")))
                .willReturn(jsonResponse(openAiResponse(reportJson()))));

        ReportData report = adapter.synthesize(emptyResearch());

        assertThat(report.editorial())
                .as("the reader must see that this digest came from the fallback model")
                .contains("Digest awaryjny")
                .endsWith("Lead");
        wireMock.verify(2, postRequestedFor(urlPathEqualTo("/chat/completions"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("gpt-4o"))));
        wireMock.verify(1, postRequestedFor(urlPathEqualTo("/chat/completions"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("gpt-4o-mini"))));
    }

    @Test
    void synthesizeDoesNotRetryOrFallBackWhenOpenAiQuotaIsExhausted() {
        wireMock.stubFor(post(urlPathEqualTo("/chat/completions"))
                .willReturn(aResponse().withStatus(429)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":{\"code\":\"insufficient_quota\"}}")));

        assertThatThrownBy(() -> adapter.synthesize(emptyResearch()))
                .isInstanceOf(LlmQuotaException.class);

        wireMock.verify(1, postRequestedFor(urlPathEqualTo("/chat/completions")));
    }

    @Test
    void synthesizeRetriesWithFewerItemsWhenModelTruncatesAtTokenCap() throws Exception {
        wireMock.stubFor(post(urlPathEqualTo("/chat/completions")).inScenario("truncation")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(jsonResponse(truncatedOpenAiResponse()))
                .willSetStateTo("reduced"));
        wireMock.stubFor(post(urlPathEqualTo("/chat/completions")).inScenario("truncation")
                .whenScenarioStateIs("reduced")
                .willReturn(jsonResponse(openAiResponse(reportJson()))));

        ReportData report = adapter.synthesize(emptyResearch());

        assertThat(report.items()).hasSize(1);
        wireMock.verify(2, postRequestedFor(urlPathEqualTo("/chat/completions")));
        // First call carries the full-intake prompt (stubbed as "user prompt"); the retry goes through
        // the capped overload, which the stub does not override — so it renders the real payload.
        wireMock.verify(1, postRequestedFor(urlPathEqualTo("/chat/completions"))
                .withRequestBody(containing("\"content\":\"user prompt\"")));
        wireMock.verify(1, postRequestedFor(urlPathEqualTo("/chat/completions"))
                .withRequestBody(containing("Oto posty z ostatnich kilku dni")));
    }

    private static ResponseDefinitionBuilder jsonResponse(String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
    }

    private static String reportJson() {
        return """
                {
                  "email_preview": "Preview",
                  "editorial": "Lead",
                  "top_insights": ["Insight"],
                  "items": [
                    {
                      "title": "Item",
                      "url": "https://example.com",
                      "source": "GitHub",
                      "category": "Java",
                      "type": "RELEASE",
                      "score": 9,
                      "engagement_score": 100,
                      "summary": "Summary"
                    }
                  ]
                }
                """;
    }

    private static String truncatedOpenAiResponse() throws Exception {
        return new ObjectMapper().writeValueAsString(Map.of(
                "choices", List.of(Map.of(
                        "message", Map.of("content", "{\"items\":[{\"title\":\"half"),
                        "finish_reason", "length"))));
    }

    private static ResearchResult emptyResearch() {
        return new ResearchResult(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                LocalDateTime.parse("2026-05-14T10:00:00"),
                0,
                0,
                0,
                0,
                0
        );
    }

    private static String openAiResponse(String content) throws Exception {
        return new ObjectMapper().writeValueAsString(Map.of(
                "choices",
                List.of(Map.of("message", Map.of("content", content)))));
    }

    private static void setOpenAiClient(GptSynthesisAdapter target, String baseUrl) throws Exception {
        Field field = GptSynthesisAdapter.class.getDeclaredField("openAiClient");
        field.setAccessible(true);
        field.set(target, RestClient.builder().baseUrl(baseUrl).build());
    }

    private static final class StubPromptBuilder extends ReportPromptBuilder {

        private StubPromptBuilder() {
            super(new ObjectMapper(), lookbackDays -> java.util.Set.of(),
                    new pl.seniordeveloper.pulsedigest.shared.infrastructure.config.DedupProperties(false, 10),
                    new pl.seniordeveloper.pulsedigest.shared.infrastructure.config.InterestProfileProperties(
                            "Test Persona", java.util.List.of("java")),
                    noFeedback(),
                    new pl.seniordeveloper.pulsedigest.shared.infrastructure.config.FeedbackProperties(false, 30, ""));
        }

        private static pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.FeedbackPort noFeedback() {
            return new pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.FeedbackPort() {
                @Override
                public java.util.Set<String> downvotedUrls(int lookbackDays) {
                    return java.util.Set.of();
                }

                @Override
                public java.util.Map<String, Integer> netVotesBySource(int lookbackDays) {
                    return java.util.Map.of();
                }
            };
        }

        @Override
        public String buildSystemPrompt() {
            return "system prompt";
        }

        @Override
        public String buildUserPrompt(ResearchResult research) {
            return "user prompt";
        }
    }
}
