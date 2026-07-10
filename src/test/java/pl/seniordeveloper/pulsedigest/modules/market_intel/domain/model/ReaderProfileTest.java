package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReaderProfileTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 10);
    private static final Instant DISTILLED = Instant.parse("2026-07-06T06:00:00Z");

    private static ProfileHypothesis hypothesis(String statement, LocalDate observedAt) {
        return new ProfileHypothesis(statement, "12 głosów 👍 w kategorii Java/JVM", observedAt);
    }

    @Test
    void aHypothesisOlderThanItsTimeToLiveIsDropped() {
        // Tastes drift. A claim the reader last confirmed months ago must stop steering the digest.
        ReaderProfile profile = new ReaderProfile(DISTILLED, 40, List.of(
                hypothesis("Chce więcej Javy", LocalDate.of(2026, 7, 1)),
                hypothesis("Nie chce security", LocalDate.of(2026, 1, 1))));

        ReaderProfile active = profile.activeOn(TODAY, 60);

        assertThat(active.hypotheses()).extracting(ProfileHypothesis::statement)
                .containsExactly("Chce więcej Javy");
    }

    @Test
    void everyHypothesisCarriesTheEvidenceItWasDistilledFrom() {
        // A profile that cannot show its working is a horoscope, not a model.
        ProfileHypothesis h = hypothesis("Chce więcej Javy", TODAY);

        assertThat(h.evidence()).isNotBlank();
    }

    @Test
    void aProfileWhoseHypothesesAllExpiredIsEmpty() {
        ReaderProfile profile = new ReaderProfile(DISTILLED, 40,
                List.of(hypothesis("Stare", LocalDate.of(2025, 1, 1))));

        assertThat(profile.activeOn(TODAY, 60).isEmpty()).isTrue();
    }

    @Test
    void aProfileIsStaleOnceTheRefreshWindowHasPassed() {
        ReaderProfile profile = new ReaderProfile(DISTILLED, 40, List.of(hypothesis("X", TODAY)));

        assertThat(profile.isStaleOn(LocalDate.of(2026, 7, 13), 7)).isTrue();
        assertThat(profile.isStaleOn(LocalDate.of(2026, 7, 9), 7)).isFalse();
    }

    @Test
    void nullHypothesesDegradeToAnEmptyProfileRatherThanBlowingUp() {
        assertThat(new ReaderProfile(DISTILLED, 0, null).isEmpty()).isTrue();
    }
}
