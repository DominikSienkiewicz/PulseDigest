package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TechDemandAggregatorTest {

    private static final List<String> VOCAB =
            List.of("java", "kotlin", "spring", "go", "react", "kubernetes");

    @Test
    void countsOnePostAsOneMentionRegardlessOfRepeats() {
        List<String> posts = List.of(
                "We use Java, Java, and more Java with Spring Boot",
                "Kotlin shop with Spring",
                "Backend role: Spring and Kubernetes");

        Map<String, Integer> counts = TechDemandAggregator.countMentions(posts, VOCAB);

        assertThat(counts).containsEntry("java", 1).containsEntry("spring", 3)
                .containsEntry("kotlin", 1).containsEntry("kubernetes", 1);
    }

    @Test
    void shortTokensRespectWordBoundaries() {
        List<String> posts = List.of(
                "We love Go and Golang is not the same token",
                "google is not golang",
                "React frontend, not reactionary");

        Map<String, Integer> counts = TechDemandAggregator.countMentions(posts, VOCAB);

        assertThat(counts).containsEntry("go", 1).containsEntry("react", 1);
    }

    @Test
    void isCaseInsensitive() {
        Map<String, Integer> counts = TechDemandAggregator.countMentions(List.of("KUBERNETES on AWS"), VOCAB);
        assertThat(counts).containsEntry("kubernetes", 1);
    }

    @Test
    void omitsTokensWithZeroMentions() {
        Map<String, Integer> counts = TechDemandAggregator.countMentions(List.of("Java only"), VOCAB);
        assertThat(counts).containsOnlyKeys("java");
    }

    @Test
    void handlesEmptyInputs() {
        assertThat(TechDemandAggregator.countMentions(List.of(), VOCAB)).isEmpty();
        assertThat(TechDemandAggregator.countMentions(List.of("Java"), List.of())).isEmpty();
        assertThat(TechDemandAggregator.countMentions(null, VOCAB)).isEmpty();
    }
}
