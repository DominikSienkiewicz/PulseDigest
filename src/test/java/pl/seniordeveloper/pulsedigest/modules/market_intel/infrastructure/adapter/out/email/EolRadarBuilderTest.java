package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.EolNotice;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * N08. The section is not in either golden file on purpose: its wording is relative to today
 * ("za 2 miesiące"), so a recorded copy would go stale on a date nobody chose. It is pinned here
 * instead, against a fixed {@code today}.
 */
class EolRadarBuilderTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);

    @Test
    void rendersNothingWhenNothingIsDue() {
        assertThat(EolRadarBuilder.buildEolRadarSection(List.of(), TODAY)).isEmpty();
        assertThat(EolRadarBuilder.buildEolRadarSection(null, TODAY)).isEmpty();
    }

    @Test
    void saysSupportHasEndedAndHowLongAgo() {
        String html = EolRadarBuilder.buildEolRadarSection(List.of(notice("Eclipse Temurin 26", LocalDate.of(2026, 9, 22))), TODAY);

        assertThat(html)
                .contains("Eclipse Temurin 26")
                .contains("wsparcie skończyło się 22.09.2026")
                .contains("brak łatek bezpieczeństwa");
    }

    @Test
    void countsTheMonthsAheadInTheFormPolishActuallyUses() {
        assertThat(line(LocalDate.of(2026, 12, 31))).contains("za 2 miesiące");
        assertThat(line(LocalDate.of(2026, 11, 20))).contains("za 1 miesiąc");
        assertThat(line(LocalDate.of(2027, 4, 20))).contains("za 6 miesięcy");
        assertThat(line(LocalDate.of(2026, 10, 28))).contains("w tym miesiącu");
    }

    @Test
    void alwaysShowsWhenTheTableWasLastChecked() {
        // A reader cannot judge an end-of-life claim without knowing when the claim was last verified.
        String html = EolRadarBuilder.buildEolRadarSection(List.of(notice("Spring Boot 4.0", LocalDate.of(2026, 12, 31))), TODAY);

        assertThat(html).contains("Tabela sprawdzona 19.10.2026.");
    }

    @Test
    void shoutsWhenTheTableItselfIsOutOfDate() {
        // The failure this feature is designed around: the dates are hand-kept, so the day comes when
        // nobody has looked at them in a year. The reader has to see that, not inherit the confidence.
        EolNotice stale = new EolNotice("PostgreSQL 16", LocalDate.of(2028, 11, 9),
                LocalDate.of(2025, 1, 10));

        String html = EolRadarBuilder.buildEolRadarSection(List.of(stale), TODAY);

        assertThat(html)
                .contains("Tabela sprawdzona 10.01.2025")
                .contains("daty mogły się zmienić");
    }

    @Test
    void datesTheTableByItsOldestEntryWhenEntriesDisagree() {
        // Mixed provenance has to report the weakest link, not the most flattering one.
        String html = EolRadarBuilder.buildEolRadarSection(
                List.of(new EolNotice("Spring Boot 4.0", LocalDate.of(2026, 12, 31), LocalDate.of(2026, 10, 19)),
                new EolNotice("Spring Boot 3.5", LocalDate.of(2026, 6, 30), LocalDate.of(2024, 3, 1))), TODAY);

        assertThat(html).contains("Tabela sprawdzona 01.03.2024").contains("daty mogły się zmienić");
    }

    @Test
    void escapesTheNameItWasGiven() {
        String html = EolRadarBuilder.buildEolRadarSection(List.of(notice("<script>x</script>", LocalDate.of(2026, 12, 31))), TODAY);

        assertThat(html).doesNotContain("<script>").contains("&lt;script&gt;");
    }

    private static String line(LocalDate endOfLife) {
        return EolRadarBuilder.buildEolRadarSection(List.of(notice("X", endOfLife)), TODAY);
    }

    private static EolNotice notice(String name, LocalDate endOfLife) {
        return new EolNotice(name, endOfLife, TODAY);
    }
}
