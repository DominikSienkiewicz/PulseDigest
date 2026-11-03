package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.CallForPapers;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.CallForPapersProperties;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

/** N09. Against the real response shape of the conference dataset, not an invented one. */
class ConfsTechCallForPapersAdapterIT {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);

    private WireMockServer wireMock;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void readsOpenCallsAndSkipsTheOnesAlreadyClosed() {
        stub("""
                [
                  {"name":"Devnexus","city":"Atlanta","country":"U.S.A.","online":false,
                   "cfpUrl":"https://sessionize.com/devnexus","cfpEndDate":"2026-11-20"},
                  {"name":"Past Conf","city":"Berlin","country":"Germany","online":false,
                   "cfpUrl":"https://example.com/past","cfpEndDate":"2026-01-01"}
                ]
                """);

        List<CallForPapers> open = adapter(120).openCalls(TODAY);

        assertThat(open).extracting(CallForPapers::conference).containsExactly("Devnexus");
        assertThat(open.getFirst().location()).isEqualTo("Atlanta, U.S.A.");
    }

    @Test
    void skipsCallsBeyondTheHorizonSoTheSectionStaysActionable() {
        stub("""
                [{"name":"Far Future","cfpUrl":"https://example.com/far","cfpEndDate":"2027-06-01"}]
                """);

        assertThat(adapter(30).openCalls(TODAY)).isEmpty();
    }

    @Test
    void anEntryWithoutACfpUrlIsNotACallForPapers() {
        // The dataset carries conferences whose CFP is closed or was never opened; a deadline with
        // nowhere to submit is not something the reader can act on.
        stubAnd("""
                [{"name":"No Cfp","cfpEndDate":"2026-11-20"}]
                """);

        assertThat(adapterQuietly(120).openCalls(TODAY)).isEmpty();
    }

    @Test
    void aMissingTopicFileIsNormalRatherThanAFailure() {
        wireMock.stubFor(get(urlPathMatching("/.*")).willReturn(aResponse().withStatus(404)));

        assertThat(adapter(120).openCalls(TODAY)).isEmpty();
    }

    @Test
    void theSameConferenceInTwoTopicFilesIsListedOnce() {
        stub("""
                [{"name":"Devnexus","cfpUrl":"https://sessionize.com/devnexus","cfpEndDate":"2026-11-20"}]
                """);

        assertThat(adapter(120).openCalls(TODAY)).hasSize(1);
    }

    @Test
    void disabledMeansNoCallAtAll() {
        CallForPapersProperties off = new CallForPapersProperties(false,
                "http://localhost:" + wireMock.port(), List.of("java"), 120, 5);

        assertThat(build(off).openCalls(TODAY)).isEmpty();
        assertThat(wireMock.getAllServeEvents()).isEmpty();
    }

    private void stub(String body) {
        wireMock.stubFor(get(urlPathMatching("/.*\\.json")).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json").withBody(body)));
    }

    private void stubAnd(String body) {
        stub(body);
    }

    private ConfsTechCallForPapersAdapter adapter(int lookaheadDays) {
        return adapterQuietly(lookaheadDays);
    }

    private ConfsTechCallForPapersAdapter adapterQuietly(int lookaheadDays) {
        return build(new CallForPapersProperties(true, "http://localhost:" + wireMock.port(),
                List.of("java"), lookaheadDays, 5));
    }

    private ConfsTechCallForPapersAdapter build(CallForPapersProperties properties) {
        ConfsTechCallForPapersAdapter adapter =
                new ConfsTechCallForPapersAdapter(properties, new ObjectMapper());
        try {
            Field field = ConfsTechCallForPapersAdapter.class.getDeclaredField("client");
            field.setAccessible(true);
            field.set(adapter, RestClient.builder().baseUrl(properties.baseUrl()).build());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return adapter;
    }
}
