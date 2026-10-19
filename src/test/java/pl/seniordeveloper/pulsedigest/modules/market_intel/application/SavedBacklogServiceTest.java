package pl.seniordeveloper.pulsedigest.modules.market_intel.application;

import pl.seniordeveloper.pulsedigest.testing.TestModels;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.SavedBacklogPolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PastEdition;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SavedBacklog;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SavedItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Signal;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SignalRank;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** N05 follow-up. The shelf, and the two things it must never pretend to know. */
class SavedBacklogServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-19T08:00:00Z");

    @Test
    void namesASavedItemFromTheEditionItWasSavedOutOf() {
        // The feedback row carries a URL and nothing else; without this the reminder is a bare link.
        var service = service(5);

        SavedBacklog shelf = service.assemble(List.of(saved("https://example.com/jep", 4)),
                List.of(edition(item("JEP 533 Value Classes", "https://example.com/jep"))));

        assertThat(shelf.shown()).extracting(SavedItem::title).containsExactly("JEP 533 Value Classes");
    }

    @Test
    void fallsBackToTheUrlWhenNoStoredEditionStillNamesIt() {
        // An edition that has aged out of the history window must not make the reminder disappear:
        // the reader saved it, and a silent drop is worse than an ugly label.
        var service = service(5);

        SavedBacklog shelf = service.assemble(List.of(saved("https://example.com/gone", 20)), List.of());

        assertThat(shelf.shown()).hasSize(1);
        assertThat(shelf.shown().getFirst().title()).isNull();
        assertThat(shelf.shown().getFirst().label())
                .as("the scheme is noise in a line the reader scans for a subject")
                .isEqualTo("example.com/gone");
    }

    @Test
    void prefersTheMostRecentWordingWhenTwoEditionsCarriedTheSameUrl() {
        var service = service(5);

        SavedBacklog shelf = service.assemble(List.of(saved("https://example.com/story", 2)),
                List.of(edition(NOW.minus(Duration.ofDays(10)), item("Old wording", "https://example.com/story")),
                        edition(NOW.minus(Duration.ofDays(2)), item("New wording", "https://example.com/story"))));

        assertThat(shelf.shown().getFirst().title()).isEqualTo("New wording");
    }

    @Test
    void capsWhatItShowsButReportsHowManyThereReallyAre() {
        // A section that truncates in silence teaches the reader to trust a number that is wrong.
        var service = service(2);

        SavedBacklog shelf = service.assemble(List.of(saved("https://example.com/1", 9), saved("https://example.com/2", 8),
                        saved("https://example.com/3", 7), saved("https://example.com/4", 6)),
                List.of());

        assertThat(shelf.shown()).hasSize(2);
        assertThat(shelf.total()).isEqualTo(4);
        assertThat(shelf.remaining()).isEqualTo(2);
    }

    @Test
    void keepsTheOrderItWasGiven() {
        // The adapter returns earliest save first; what has been waiting longest is the reminder
        // most worth making, so the service must not quietly re-sort it.
        var service = service(5);

        SavedBacklog shelf = service.assemble(List.of(saved("https://example.com/oldest", 20), saved("https://example.com/newest", 1)),
                List.of());

        assertThat(shelf.shown()).extracting(SavedItem::url)
                .containsExactly("https://example.com/oldest", "https://example.com/newest");
    }

    @Test
    void staysEmptyWhenTheShelfIsOffOrNothingIsSaved() {
        var disabled = new SavedBacklogService(new SavedBacklogPolicy(false, 5, 30));

        assertThat(disabled.assemble(List.of(saved("https://example.com/x", 1)), List.of()).isEmpty()).isTrue();
        assertThat(service(5).assemble(List.of(), List.of()).isEmpty()).isTrue();
        assertThat(service(5).assemble(null, null).isEmpty()).isTrue();
    }

    @Test
    void countsWaitingInWholeDaysFromTheSaveItself() {
        SavedItem item = saved("https://example.com/x", 3);

        assertThat(item.daysWaiting(java.time.LocalDate.of(2026, 10, 19))).isEqualTo(3);
    }

    private static SavedBacklogService service(int maxItems) {
        return new SavedBacklogService(new SavedBacklogPolicy(true, maxItems, 30));
    }

    private static SavedItem saved(String url, int daysAgo) {
        return new SavedItem(null, url, "Hacker News", "Java/JVM", NOW.minus(Duration.ofDays(daysAgo)));
    }

    private static PastEdition edition(DigestItem... items) {
        return edition(NOW.minus(Duration.ofDays(5)), items);
    }

    private static PastEdition edition(Instant at, DigestItem... items) {
        return TestModels.pastEdition(at, List.of(items).stream().map(SavedBacklogServiceTest::signal).toList());
    }

    private static DigestItem item(String title, String url) {
        return TestModels.digestItem(title, url, "Hacker News", "Java/JVM", "NEWS", 8, null, "summary", "why");
    }

    private static Signal signal(DigestItem item) {
        return TestModels.signal(item, SignalRank.STRONG, item.score(), List.of());
    }
}
