package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RecapChange;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RecapEntry;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SignalRank;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.WeeklyRecap;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The recap is assembled from <em>this</em> edition's signals, so left unfiltered every line restates
 * a story printed above it. These tests pin the one distinction worth keeping.
 */
class WeeklyRecapBuilderTest {

    @Test
    void keepsAFadedLineEvenWhenTheStoryStillAppearsAboveIt() {
        // "Monday's 🔴 came to nothing" is the one thing no section above can say. A digest that only
        // ever reports things going up is a hype machine; this line is what makes the 🔴 credible.
        WeeklyRecap recap = new WeeklyRecap(List.of(
                entry("Faded story", "https://a", RecapChange.FADED, SignalRank.CRITICAL, SignalRank.WEAK)));

        String html = WeeklyRecapBuilder.buildWeeklyRecapSection(recap, Set.of("https://a"));

        assertThat(html).contains("Faded story").contains("wygas");
    }

    @Test
    void dropsAConfirmedLineWhoseStoryIsAlreadyPrintedAbove() {
        WeeklyRecap recap = new WeeklyRecap(List.of(
                entry("Still critical", "https://a", RecapChange.CONFIRMED,
                        SignalRank.CRITICAL, SignalRank.CRITICAL)));

        assertThat(WeeklyRecapBuilder.buildWeeklyRecapSection(recap, Set.of("https://a"))).isEmpty();
    }

    @Test
    void keepsAnEscalatedLineWhoseStoryNeverReachedTheMail() {
        // Scored too low to be rendered, but its rank climbed — the recap is the only place it shows.
        WeeklyRecap recap = new WeeklyRecap(List.of(
                entry("Quiet climber", "https://unseen", RecapChange.ESCALATED,
                        SignalRank.WEAK, SignalRank.MODERATE)));

        String html = WeeklyRecapBuilder.buildWeeklyRecapSection(recap, Set.of("https://a"));

        assertThat(html).contains("Quiet climber").contains("uros");
    }

    @Test
    void rendersNothingWhenEveryLineWasAlreadyPrintedAbove() {
        WeeklyRecap recap = new WeeklyRecap(List.of(
                entry("One", "https://a", RecapChange.CONFIRMED, SignalRank.CRITICAL, SignalRank.CRITICAL),
                entry("Two", "https://b", RecapChange.ESCALATED, SignalRank.WEAK, SignalRank.STRONG)));

        assertThat(WeeklyRecapBuilder.buildWeeklyRecapSection(recap, Set.of("https://a", "https://b")))
                .isEmpty();
    }

    @Test
    void rendersNothingWithoutARecap() {
        assertThat(WeeklyRecapBuilder.buildWeeklyRecapSection(null, Set.of())).isEmpty();
        assertThat(WeeklyRecapBuilder.buildWeeklyRecapSection(new WeeklyRecap(List.of()), Set.of()))
                .isEmpty();
    }

    private static RecapEntry entry(String title, String url, RecapChange change,
                                    SignalRank previous, SignalRank current) {
        return new RecapEntry(title, url, change, previous, current);
    }
}
