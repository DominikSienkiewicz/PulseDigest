package pl.seniordeveloper.pulsedigest.modules.market_intel.application;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.SelfCritiquePolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.CritiqueVerdict;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;
import pl.seniordeveloper.pulsedigest.testing.TestModels;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * N23. The catalogue asks for shadow mode first, and these pin the reason: a second model opinion
 * can be confidently wrong, so the edition has to survive one until the log says it can be trusted.
 */
class SelfCritiqueServiceTest {

    private static final List<DigestItem> FOUR = List.of(item(1), item(2), item(3), item(4));

    @Test
    void shadowModeChangesNothingHoweverMuchItDislikesTheEdition() {
        var service = new SelfCritiqueService(items -> cut(1, 2), new SelfCritiquePolicy(true, false));

        assertThat(service.apply(FOUR)).isEqualTo(FOUR);
    }

    @Test
    void enforcingModeRemovesExactlyWhatItNamed() {
        var service = new SelfCritiqueService(items -> cut(2), new SelfCritiquePolicy(true, true));

        assertThat(service.apply(FOUR)).extracting(DigestItem::url).containsExactly(
                url(1), url(3), url(4));
    }

    @Test
    void aCritiqueThatWouldGutTheEditionIsIgnoredEntirely() {
        // Cutting most of an edition is disagreement with it, not critique of it — far more likely a
        // confused model than a uniformly bad digest. Partially honouring it would invent a
        // judgement neither the model nor the rubric made.
        var service = new SelfCritiqueService(items -> cut(1, 2, 3), new SelfCritiquePolicy(true, true));

        assertThat(service.apply(FOUR)).isEqualTo(FOUR);
    }

    @Test
    void aFailedCallLeavesTheEditionAlone() {
        var service = new SelfCritiqueService(items -> CritiqueVerdict.NOTHING,
                new SelfCritiquePolicy(true, true));

        assertThat(service.apply(FOUR)).isEqualTo(FOUR);
    }

    @Test
    void theCallIsNotEvenMadeWhenTheCritiqueIsOff() {
        var service = new SelfCritiqueService(items -> {
            throw new AssertionError("the critique must not be called when disabled");
        }, new SelfCritiquePolicy(false, false));

        assertThat(service.apply(FOUR)).isEqualTo(FOUR);
    }

    private static CritiqueVerdict cut(int... indexes) {
        return new CritiqueVerdict(java.util.Arrays.stream(indexes)
                .mapToObj(i -> new CritiqueVerdict.Cut(url(i), "nuda"))
                .toList());
    }

    private static DigestItem item(int n) {
        return TestModels.digestItem("T" + n, url(n), "S", "Java/JVM", "NEWS", 8, null, "s", "w");
    }

    private static String url(int n) {
        return "https://example.com/" + n;
    }
}
