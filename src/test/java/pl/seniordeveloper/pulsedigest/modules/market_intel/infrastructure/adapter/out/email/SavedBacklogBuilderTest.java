package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SavedBacklog;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SavedItem;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * N05 follow-up. Pinned here against a fixed {@code today} rather than in the golden file, for the
 * same reason as the end-of-life section: "odłożone 4 dni temu" is relative to the day it renders.
 */
class SavedBacklogBuilderTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);

    @Test
    void rendersNothingWhenTheShelfIsEmpty() {
        assertThat(SavedBacklogBuilder.buildSavedBacklogSection(SavedBacklog.empty(), TODAY)).isEmpty();
        assertThat(SavedBacklogBuilder.buildSavedBacklogSection(null, TODAY)).isEmpty();
    }

    @Test
    void showsWhatWasSavedAndHowLongItHasWaited() {
        String html = render(List.of(saved("JEP 533 Value Classes", "https://example.com/jep", 4)), 1);

        assertThat(html)
                .contains("JEP 533 Value Classes")
                .contains("https://example.com/jep")
                .contains("Hacker News")
                .contains("od&#322;o&#380;one 4 dni temu");
    }

    @Test
    void countsOneDayInTheSingularAndTodayAsToday() {
        assertThat(render(List.of(saved("A", "https://example.com/a", 1)), 1))
                .contains("od&#322;o&#380;one 1 dzie&#324;");
        assertThat(render(List.of(saved("B", "https://example.com/b", 0)), 1))
                .contains("od&#322;o&#380;one dzi&#347;");
    }

    @Test
    void saysHowManyMoreAreOnTheShelfWhenItShowsOnlySome() {
        String html = render(List.of(saved("A", "https://example.com/a", 9)), 6);

        assertThat(html).contains("i jeszcze 5 na p&oacute;&#322;ce");
    }

    @Test
    void doesNotPromiseMoreWhenItIsShowingEverything() {
        assertThat(render(List.of(saved("A", "https://example.com/a", 2)), 1))
                .doesNotContain("na p&oacute;&#322;ce.");
    }

    @Test
    void doesNotClaimToKnowWhetherTheReaderReadIt() {
        // The whole wording decision in one assertion: there is no read tracking anywhere in this
        // system, so a section headed "nieprzeczytane" would be stating something it cannot know.
        String html = render(List.of(saved("A", "https://example.com/a", 2)), 1);

        assertThat(html)
                .doesNotContain("nieprzeczytane")
                .contains("Zapisane na p&oacute;&#378;niej")
                .contains("czy je przeczyta&#322;e&#347;, tego digest nie wie");
    }

    @Test
    void fallsBackToTheUrlWhenTheTitleIsUnknown() {
        String html = render(List.of(new SavedItem(null, "https://example.com/unnamed", "RSS/InfoQ", null,
                Instant.parse("2026-10-15T08:00:00Z"))), 1);

        assertThat(html)
                .as("the link still points at the full URL")
                .contains("href=\"https://example.com/unnamed\"")
                .as("but the visible label drops the scheme")
                .contains(">example.com/unnamed</a>");
    }

    @Test
    void escapesWhatItWasGiven() {
        String html = render(List.of(saved("<script>x</script>", "https://example.com/x", 1)), 1);

        assertThat(html).doesNotContain("<script>").contains("&lt;script&gt;");
    }

    private static String render(List<SavedItem> shown, int total) {
        return SavedBacklogBuilder.buildSavedBacklogSection(new SavedBacklog(shown, total), TODAY);
    }

    private static SavedItem saved(String title, String url, int daysAgo) {
        return new SavedItem(title, url, "Hacker News", "Java/JVM",
                TODAY.minusDays(daysAgo).atTime(8, 0).toInstant(java.time.ZoneOffset.UTC));
    }
}
