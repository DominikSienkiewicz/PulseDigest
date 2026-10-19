package pl.seniordeveloper.pulsedigest.modules.market_intel.application;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.EolPolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.EolNotice;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * N08. Every case fixes {@code today} explicitly: a radar whose output depends on the calendar is a
 * test that passes until it quietly stops, which is the failure mode this suite exists to prevent.
 */
class EolRadarServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);
    private static final LocalDate CHECKED = LocalDate.of(2026, 10, 19);

    @Test
    void reportsADateThatHasAlreadyPassed() {
        var radar = radar(6, notice("Eclipse Temurin 26", 2026, 9, 22));

        assertThat(radar.due(TODAY)).extracting(EolNotice::name).containsExactly("Eclipse Temurin 26");
    }

    @Test
    void reportsADateInsideTheWarningWindow() {
        var radar = radar(6, notice("Spring Boot 4.0", 2026, 12, 31));

        assertThat(radar.due(TODAY)).hasSize(1);
    }

    @Test
    void staysSilentAboutADateBeyondTheWarningWindow() {
        // The whole point of a window: a 2031 LTS date is true, useless and therefore noise.
        var radar = radar(6, notice("Java 25 LTS", 2031, 9, 30));

        assertThat(radar.due(TODAY)).isEmpty();
    }

    @Test
    void putsWhatIsAlreadyUnsupportedBeforeWhatIsAboutToBe() {
        var radar = radar(6,
                notice("Spring Boot 4.0", 2026, 12, 31),
                notice("Eclipse Temurin 26", 2026, 9, 22),
                notice("Spring Boot 3.5", 2026, 6, 30));

        assertThat(radar.due(TODAY))
                .extracting(EolNotice::name)
                .containsExactly("Spring Boot 3.5", "Eclipse Temurin 26", "Spring Boot 4.0");
    }

    @Test
    void saysNothingAtAllWhenTheRadarIsDisabled() {
        var radar = new EolRadarService(new EolPolicy(false, 6, CHECKED,
                List.of(notice("Eclipse Temurin 26", 2026, 9, 22))));

        assertThat(radar.due(TODAY)).isEmpty();
    }

    @Test
    void treatsTheEndOfLifeDayItselfAsStillSupported() {
        // Support ends *on* that date, so the day it arrives is the last good day, not the first bad
        // one. Off by one here would tell a reader their runtime died a day early, every release.
        assertThat(notice("Spring Boot 4.0", 2026, 10, 19).isPast(TODAY)).isFalse();
        assertThat(notice("Spring Boot 4.0", 2026, 10, 18).isPast(TODAY)).isTrue();
    }

    private static EolRadarService radar(int warnWithinMonths, EolNotice... entries) {
        return new EolRadarService(new EolPolicy(true, warnWithinMonths, CHECKED, List.of(entries)));
    }

    private static EolNotice notice(String name, int year, int month, int day) {
        return new EolNotice(name, LocalDate.of(year, month, day), CHECKED);
    }
}
