package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TechDemandPulseTest {

    private static final List<String> PRIORITY = List.of("java", "spring", "python");

    @Test
    void computesShareAndRanksByShareDescending() {
        Map<String, Integer> current = Map.of("python", 50, "go", 25, "java", 5);

        TechDemandSignal signal = TechDemandPulse.assemble(
                "url", new MonthMentions("June 2026", current, 100), null, PRIORITY, 1, 8);

        assertThat(signal.entries()).extracting(TechDemandEntry::name).containsExactly("python", "go", "java");
        assertThat(signal.entries().get(0).share()).isCloseTo(0.50, within(0.001));
        assertThat(signal.entries().get(1).share()).isCloseTo(0.25, within(0.001));
    }

    @Test
    void computesMonthOverMonthDeltaInPercentagePoints() {
        Map<String, Integer> current = Map.of("rust", 20);   // 20/100 = 20%
        Map<String, Integer> previous = Map.of("rust", 8);   // 8/50  = 16%

        TechDemandSignal signal = TechDemandPulse.assemble(
                "url", new MonthMentions("June 2026", current, 100),
                new MonthMentions("May 2026", previous, 50), PRIORITY, 1, 8);

        assertThat(signal.previousMonthLabel()).isEqualTo("May 2026");
        assertThat(signal.entries().get(0).deltaPp()).isCloseTo(4.0, within(0.001)); // 20% - 16%
    }

    @Test
    void leavesDeltaNullWhenNoPreviousThread() {
        TechDemandSignal signal = TechDemandPulse.assemble(
                "url", new MonthMentions("June 2026", Map.of("go", 10), 100), null, PRIORITY, 1, 8);

        assertThat(signal.previousMonthLabel()).isNull();
        assertThat(signal.entries().get(0).deltaPp()).isNull();
    }

    @Test
    void stackLineIncludesPriorityTokensEvenWhenBelowRankingOrAbsent() {
        Map<String, Integer> current = Map.of("python", 40, "go", 30); // java/spring absent

        TechDemandSignal signal = TechDemandPulse.assemble(
                "url", new MonthMentions("June 2026", current, 100), null, PRIORITY, 5, 8);

        // java & spring don't clear minMentions for the ranking, but still appear in the stack line.
        assertThat(signal.entries()).extracting(TechDemandEntry::name).doesNotContain("java", "spring");
        assertThat(signal.stackEntries()).extracting(TechDemandEntry::name)
                .containsExactly("java", "spring", "python");
        assertThat(stackShare(signal, "java")).isCloseTo(0.0, within(0.001));
        assertThat(stackShare(signal, "python")).isCloseTo(0.40, within(0.001));
    }

    @Test
    void appliesMinMentionsAndTopNToRanking() {
        Map<String, Integer> current = Map.of("a", 9, "b", 8, "c", 7, "d", 1);

        TechDemandSignal signal = TechDemandPulse.assemble(
                "url", new MonthMentions("m", current, 100), null, List.of(), 5, 2);

        assertThat(signal.entries()).extracting(TechDemandEntry::name).containsExactly("a", "b");
    }

    private static double stackShare(TechDemandSignal signal, String name) {
        return signal.stackEntries().stream()
                .filter(e -> e.name().equals(name)).map(TechDemandEntry::share).findFirst().orElse(-1.0);
    }
}
