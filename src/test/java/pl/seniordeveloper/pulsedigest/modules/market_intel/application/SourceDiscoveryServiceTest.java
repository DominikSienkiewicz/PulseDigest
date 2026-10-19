package pl.seniordeveloper.pulsedigest.modules.market_intel.application;

import pl.seniordeveloper.pulsedigest.domain.TestHistory;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PastEdition;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SignalRank;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceProposal;
import pl.seniordeveloper.pulsedigest.testing.TestModels;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** N28. Evidence proposes sources; a human still makes the edit. */
class SourceDiscoveryServiceTest {

    @Test
    void proposalsWithoutEvidenceAreDropped() {
        // The rule that keeps this from becoming suggestion spam: a proposal nobody can check is
        // indistinguishable from a guess, and one guess is enough to get the feature switched off.
        var service = service(List.of(
                new SourceProposal("Dobre źródło", "rss", "bo tak", List.of("https://example.com/1")),
                new SourceProposal("Bez dowodów", "rss", "bo tak", List.of())));

        assertThat(service.discover(90)).extracting(SourceProposal::name).containsExactly("Dobre źródło");
    }

    @Test
    void neverMoreThanThree() {
        var service = service(List.of(proposal(1), proposal(2), proposal(3), proposal(4), proposal(5)));

        assertThat(service.discover(90)).hasSize(3);
    }

    @Test
    void anEmptyArchiveProposesNothingRatherThanGuessing() {
        var service = new SourceDiscoveryService(new TestHistory(List.of()),
                items -> {
                    throw new AssertionError("the model must not be asked without evidence");
                },
                new SourceYieldService());

        assertThat(service.discover(90)).isEmpty();
    }

    @Test
    void anArchiveWithNoHighRankedItemsHasNothingToArgueFrom() {
        PastEdition weakOnly = new PastEdition(Instant.parse("2026-09-01T08:00:00Z"),
                List.of(TestModels.signal(TestModels.digestItem("W", "https://example.com/w", "RSS",
                        "Java/JVM", "NEWS", 5, null, "s", "w"), SignalRank.WEAK, 20, List.of())),
                List.of());
        var service = new SourceDiscoveryService(new TestHistory(List.of(weakOnly)),
                items -> {
                    throw new AssertionError("the model must not be asked without evidence");
                },
                new SourceYieldService());

        assertThat(service.discover(90)).isEmpty();
    }

    private static SourceDiscoveryService service(List<SourceProposal> proposals) {
        PastEdition edition = new PastEdition(Instant.parse("2026-09-01T08:00:00Z"),
                List.of(TestModels.signal(TestModels.digestItem("Good", "https://example.com/good",
                        "Hacker News", "Java/JVM", "NEWS", 9, null, "s", "w"),
                        SignalRank.CRITICAL, 160, List.of())),
                List.of());
        return new SourceDiscoveryService(new TestHistory(List.of(edition)), items -> proposals,
                new SourceYieldService());
    }

    private static SourceProposal proposal(int n) {
        return new SourceProposal("Źródło " + n, "rss", "bo tak", List.of("https://example.com/" + n));
    }
}
