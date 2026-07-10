package pl.seniordeveloper.pulsedigest.modules.market_intel.application;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.ReaderProfilePolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ProfileEvidence;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ProfileHypothesis;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReaderProfile;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.ReaderProfileDistillerPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.ReaderProfilePort;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReaderProfileServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 10);
    private static final ReaderProfilePolicy POLICY = new ReaderProfilePolicy(true, 10, 7, 60);

    private final ReaderProfilePort store = mock(ReaderProfilePort.class);
    private final ReaderProfileDistillerPort distiller = mock(ReaderProfileDistillerPort.class);
    private final ReaderProfileService service = new ReaderProfileService(store, distiller, POLICY);

    private static ProfileEvidence evidence(int votes) {
        Map<String, Integer> categories = new java.util.HashMap<>();
        for (int i = 0; i < votes; i++) {
            categories.put("cat-" + i, 1);
        }
        return new ProfileEvidence(categories, Map.of(), List.of());
    }

    private static ReaderProfile profile(Instant distilledAt, LocalDate observedAt) {
        return new ReaderProfile(distilledAt, 20,
                List.of(new ProfileHypothesis("Chce Javy", "12 głosów", observedAt)));
    }

    @Test
    void doesNotDistilBeforeTheReaderHasVotedEnoughTimes() {
        // Nine clicks is not a reader model. Distilling a profile from noise is how drift starts.
        when(store.latest()).thenReturn(Optional.empty());

        service.refresh(TODAY, evidence(9));

        verify(distiller, never()).distil(any());
    }

    @Test
    void distilsOnceTheVoteThresholdIsCleared() {
        when(store.latest()).thenReturn(Optional.empty());
        when(distiller.distil(any())).thenReturn(Optional.of(profile(TODAY.atStartOfDay().toInstant(
                java.time.ZoneOffset.UTC), TODAY)));

        service.refresh(TODAY, evidence(10));

        verify(distiller).distil(any());
        verify(store).save(any());
    }

    @Test
    void doesNotRedistilAProfileThatIsStillFresh() {
        // One mini call per week, not per run.
        when(store.latest()).thenReturn(Optional.of(profile(Instant.parse("2026-07-08T06:00:00Z"), TODAY)));

        service.refresh(TODAY, evidence(50));

        verify(distiller, never()).distil(any());
    }

    @Test
    void redistilsOnceTheRefreshWindowHasPassed() {
        when(store.latest()).thenReturn(Optional.of(profile(Instant.parse("2026-07-01T06:00:00Z"), TODAY)));
        when(distiller.distil(any())).thenReturn(Optional.of(profile(Instant.now(), TODAY)));

        service.refresh(TODAY, evidence(50));

        verify(distiller).distil(any());
    }

    @Test
    void returnsTheStoredProfileWithExpiredHypothesesPruned() {
        when(store.latest()).thenReturn(Optional.of(profile(
                Instant.parse("2026-07-08T06:00:00Z"), LocalDate.of(2026, 1, 1))));

        Optional<ReaderProfile> active = service.refresh(TODAY, evidence(50));

        assertThat(active).get().satisfies(p -> assertThat(p.isEmpty()).isTrue());
    }

    @Test
    void keepsTheOldProfileWhenTheDistillerFails() {
        // A failed mini call must not wipe months of accumulated model.
        ReaderProfile existing = profile(Instant.parse("2026-07-01T06:00:00Z"), TODAY);
        when(store.latest()).thenReturn(Optional.of(existing));
        when(distiller.distil(any())).thenReturn(Optional.empty());

        Optional<ReaderProfile> active = service.refresh(TODAY, evidence(50));

        verify(store, never()).save(any());
        assertThat(active).get().satisfies(p -> assertThat(p.hypotheses()).hasSize(1));
    }

    @Test
    void isDisabledEntirelyByPolicy() {
        ReaderProfileService off = new ReaderProfileService(store, distiller,
                new ReaderProfilePolicy(false, 10, 7, 60));

        assertThat(off.refresh(TODAY, evidence(50))).isEmpty();
        verify(store, never()).latest();
    }

    @Test
    void aStorageFailureDegradesToNoProfileRatherThanALostRun() {
        when(store.latest()).thenThrow(new IllegalStateException("db down"));

        assertThat(service.refresh(TODAY, evidence(50))).isEmpty();
    }

    @Test
    void evidenceCountsEveryVoteItCarries() {
        ProfileEvidence e = new ProfileEvidence(
                Map.of("java/jvm", 5, "research", -3), Map.of("arXiv", -2), new ArrayList<>());

        assertThat(e.totalVotes()).isEqualTo(10);
    }
}
