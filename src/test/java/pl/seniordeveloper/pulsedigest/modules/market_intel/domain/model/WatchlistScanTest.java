package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WatchlistScanTest {

    @Test
    void reportsZeroMentionsExplicitlySoSilenceIsConfirmedRatherThanAmbiguous() {
        // The whole point: "Spring AI: 0 wzmianek" says nothing happened, not "it fell out of budget".
        WatchlistScan scan = WatchlistScan.of(
                List.of("Kubernetes 1.35 released"), List.of("kubernetes", "spring ai"));

        assertThat(scan.hits()).containsExactly(
                new WatchlistHit("kubernetes", 1),
                new WatchlistHit("spring ai", 0));
    }

    @Test
    void countsEachHeadlineOnceEvenWhenItRepeatsTheKeyword() {
        WatchlistScan scan = WatchlistScan.of(
                List.of("Quarkus, Quarkus, Quarkus", "Quarkus 4 ships"), List.of("quarkus"));

        assertThat(scan.hits()).containsExactly(new WatchlistHit("quarkus", 2));
    }

    @Test
    void respectsWordBoundariesSoGolangDoesNotCountAsGo() {
        WatchlistScan scan = WatchlistScan.of(List.of("Golang generics"), List.of("go"));

        assertThat(scan.hits()).containsExactly(new WatchlistHit("go", 0));
    }

    @Test
    void isEmptyWhenNoKeywordsAreConfigured() {
        assertThat(WatchlistScan.of(List.of("anything"), List.of()).isEmpty()).isTrue();
    }

    @Test
    void preservesTheConfiguredKeywordOrder() {
        WatchlistScan scan = WatchlistScan.of(List.of("spring ai rocks"), List.of("zzz", "spring ai", "aaa"));

        assertThat(scan.hits()).extracting(WatchlistHit::keyword).containsExactly("zzz", "spring ai", "aaa");
    }
}
