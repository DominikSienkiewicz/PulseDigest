package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
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
            super(new ObjectMapper());
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
