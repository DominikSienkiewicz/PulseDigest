package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.MonthMentions;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.TechDemandEntry;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.TechDemandSignal;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.TechDemandHistoryPort;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.TechDemandProperties;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TechDemandAdapterIT {

    private WireMockServer wireMock;
    private TechDemandAdapter adapter;
    private InMemoryTechDemandHistory history;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        history = new InMemoryTechDemandHistory();
        adapter = new TechDemandAdapter(new ObjectMapper(), props(), history);
        adapter.init();
    }

    /** Stores snapshots per (month, vocabulary), like the Postgres adapter, without a container. */
    private static final class InMemoryTechDemandHistory implements TechDemandHistoryPort {
        private final java.util.Map<String, MonthMentions> byKey = new java.util.HashMap<>();

        @Override
        public Optional<MonthMentions> findByMonth(String monthLabel, String vocabularyVersion) {
            return Optional.ofNullable(byKey.get(monthLabel + "|" + vocabularyVersion));
        }

        @Override
        public void save(MonthMentions month, String vocabularyVersion) {
            byKey.put(month.label() + "|" + vocabularyVersion, month);
        }
    }

    @AfterEach
    void tearDown() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    private TechDemandProperties props() {
        return new TechDemandProperties(
                true,
                "http://localhost:" + wireMock.port(),
                7,
                1000,
                8,
                1,
                List.of("java", "kotlin", "spring", "kubernetes", "go"),
                List.of("java", "spring"));
    }

    @Test
    void aggregatesTechMentionsFromFreshThread() {
        long now = Instant.now().getEpochSecond();
        wireMock.stubFor(get(urlPathEqualTo("/search_by_date")).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withBody("{\"hits\":[{\"title\":\"Ask HN: Who is hiring? (June 2026)\","
                        + "\"objectID\":\"999\",\"created_at_i\":" + now + "}]}")));
        wireMock.stubFor(get(urlPathMatching("/items/.*")).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withBody("{\"children\":["
                        + "{\"text\":\"<p>We use Java and Kubernetes</p>\"},"
                        + "{\"text\":\"Spring shop, also Kubernetes\"},"
                        + "{\"text\":\"Kotlin backend\"}]}")));

        Optional<TechDemandSignal> result = adapter.fetchTechDemand();

        assertThat(result).isPresent();
        TechDemandSignal signal = result.get();
        assertThat(signal.monthLabel()).isEqualTo("June 2026");
        assertThat(signal.totalPostings()).isEqualTo(3);
        assertThat(signal.entries()).extracting("name", "mentions")
                .contains(org.assertj.core.api.Assertions.tuple("kubernetes", 2));
        assertThat(signal.previousMonthLabel()).isNull();
    }

    @Test
    void previousMonthIsReadFromHistoryInsteadOfRescrapingItsThread() {
        // The saving: the May thread's ~1000 comments were already counted when May was current.
        long now = Instant.now().getEpochSecond();
        long lastMonth = now - (30L * 86_400L);
        wireMock.stubFor(get(urlPathEqualTo("/search_by_date")).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withBody("{\"hits\":["
                        + "{\"title\":\"Ask HN: Who is hiring? (June 2026)\",\"objectID\":\"999\",\"created_at_i\":"
                        + now + "},"
                        + "{\"title\":\"Ask HN: Who is hiring? (May 2026)\",\"objectID\":\"888\",\"created_at_i\":"
                        + lastMonth + "}]}")));
        wireMock.stubFor(get(urlPathMatching("/items/.*")).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withBody("{\"children\":[{\"text\":\"Java shop\"},{\"text\":\"Java and Kubernetes\"}]}")));
        adapter.fetchTechDemand();          // first run records June and back-fills May
        wireMock.verify(1, com.github.tomakehurst.wiremock.client.WireMock
                .getRequestedFor(urlPathEqualTo("/items/888")));
        wireMock.resetRequests();

        adapter.fetchTechDemand();          // second run: May is already in history

        wireMock.verify(1, com.github.tomakehurst.wiremock.client.WireMock
                .getRequestedFor(urlPathEqualTo("/items/999")));
        wireMock.verify(0, com.github.tomakehurst.wiremock.client.WireMock
                .getRequestedFor(urlPathEqualTo("/items/888")));
    }

    @Test
    void computesMonthOverMonthDeltaFromPreviousThread() {
        long now = Instant.now().getEpochSecond();
        long lastMonth = now - (30L * 86_400L);
        wireMock.stubFor(get(urlPathEqualTo("/search_by_date")).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withBody("{\"hits\":["
                        + "{\"title\":\"Ask HN: Who is hiring? (June 2026)\",\"objectID\":\"999\",\"created_at_i\":"
                        + now + "},"
                        + "{\"title\":\"Ask HN: Who is hiring? (May 2026)\",\"objectID\":\"888\",\"created_at_i\":"
                        + lastMonth + "}]}")));
        // current: go in 2/2 posts (100%); previous: go in 1/2 posts (50%) -> delta +50pp
        wireMock.stubFor(get(urlPathEqualTo("/items/999")).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withBody("{\"children\":[{\"text\":\"Go backend\"},{\"text\":\"Go and Java\"}]}")));
        wireMock.stubFor(get(urlPathEqualTo("/items/888")).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withBody("{\"children\":[{\"text\":\"Go backend\"},{\"text\":\"Java only\"}]}")));

        TechDemandSignal signal = adapter.fetchTechDemand().orElseThrow();

        assertThat(signal.previousMonthLabel()).isEqualTo("May 2026");
        TechDemandEntry go = signal.entries().stream()
                .filter(e -> e.name().equals("go")).findFirst().orElseThrow();
        assertThat(go.deltaPp()).isCloseTo(50.0, org.assertj.core.api.Assertions.within(0.001));
    }

    @Test
    void returnsEmptyWhenThreadIsStale() {
        long staleEpoch = Instant.now().getEpochSecond() - (40L * 86_400L);
        wireMock.stubFor(get(urlPathEqualTo("/search_by_date")).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withBody("{\"hits\":[{\"title\":\"Ask HN: Who is hiring? (April 2026)\","
                        + "\"objectID\":\"888\",\"created_at_i\":" + staleEpoch + "}]}")));

        assertThat(adapter.fetchTechDemand()).isEmpty();
    }

    @Test
    void returnsEmptyWhenDisabled() {
        TechDemandProperties disabled = new TechDemandProperties(
                false, "http://localhost:" + wireMock.port(), 7, 1000, 8, 1, List.of("java"), List.of("java"));
        TechDemandAdapter off = new TechDemandAdapter(new ObjectMapper(), disabled, new InMemoryTechDemandHistory());
        off.init();

        assertThat(off.fetchTechDemand()).isEmpty();
    }

    @Test
    void propagatesQuotaErrorOn429() {
        wireMock.stubFor(get(urlPathEqualTo("/search_by_date")).willReturn(aResponse().withStatus(429)));

        assertThatThrownBy(() -> adapter.fetchTechDemand())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("QUOTA_EXHAUSTED");
    }
}
