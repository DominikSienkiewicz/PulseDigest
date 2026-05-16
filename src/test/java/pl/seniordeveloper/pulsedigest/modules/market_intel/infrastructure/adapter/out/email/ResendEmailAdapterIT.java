package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.EmailDeliveryReceipt;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportData;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchResult;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.EmailProperties;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResendEmailAdapterIT {

    private WireMockServer wireMock;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
    }

    @AfterEach
    void tearDown() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @Test
    void sendPostsEmailPayloadAndReturnsReceipt() throws Exception {
        wireMock.stubFor(post(urlPathEqualTo("/emails"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"email-1\"}")));

        ResendEmailAdapter adapter = adapter(emailProperties("resend-key", "from@example.com", "to@example.com"));

        EmailDeliveryReceipt receipt = adapter.send(report(), emptyResearch());

        assertThat(receipt.provider()).isEqualTo("resend");
        assertThat(receipt.responseBody()).contains("email-1");
        wireMock.verify(postRequestedFor(urlPathEqualTo("/emails"))
                .withHeader("Authorization", containing("Bearer resend-key"))
                .withRequestBody(containing("\"from\":\"from@example.com\""))
                .withRequestBody(containing("\"to\":[\"to@example.com\"]"))
                .withRequestBody(containing("Daily Digest"))
                .withRequestBody(containing("Item")));
    }

    @Test
    void sendFailsFastWhenConfigIsMissingAndWrapsProviderFailure() throws Exception {
        ResendEmailAdapter missingConfig = adapter(emailProperties("", "from@example.com", "to@example.com"));
        assertThatThrownBy(() -> missingConfig.send(report(), emptyResearch()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Email not configured");

        wireMock.stubFor(post(urlPathEqualTo("/emails"))
                .willReturn(aResponse().withStatus(500).withBody("boom")));
        ResendEmailAdapter providerFailure = adapter(emailProperties("resend-key", "from@example.com", "to@example.com"));

        assertThatThrownBy(() -> providerFailure.send(report(), emptyResearch()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to send email via Resend");
    }

    private ResendEmailAdapter adapter(EmailProperties emailProperties) throws Exception {
        ResendEmailAdapter adapter = new ResendEmailAdapter(new ObjectMapper(), new ReportEmailBuilder(), emailProperties);
        Field field = ResendEmailAdapter.class.getDeclaredField("resendClient");
        field.setAccessible(true);
        field.set(adapter, RestClient.builder()
                .baseUrl("http://localhost:" + wireMock.port())
                .defaultHeader("Authorization", "Bearer " + emailProperties.resendApiKey())
                .build());
        return adapter;
    }

    private static EmailProperties emailProperties(String apiKey, String from, String to) {
        return new EmailProperties(apiKey, from, to);
    }

    private static ReportData report() {
        return new ReportData(
                "Preview",
                "Editorial",
                List.of("Insight"),
                List.of(new DigestItem(
                        "Item",
                        "https://example.com",
                        "GitHub",
                        "Java",
                        "RELEASE",
                        9,
                        100,
                        "Summary")));
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
}
