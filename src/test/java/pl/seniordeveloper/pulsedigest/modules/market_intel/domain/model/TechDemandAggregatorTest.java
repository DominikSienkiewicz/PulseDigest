package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;

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

        var result = TechDemandAggregator.aggregate(posts, VOCAB, 1, 10);

        // Java mentioned thrice in one post -> counts as 1; Spring appears in 3 posts -> 3.
        assertThat(entry(result, "java")).isEqualTo(1);
        assertThat(entry(result, "spring")).isEqualTo(3);
        assertThat(entry(result, "kotlin")).isEqualTo(1);
        assertThat(entry(result, "kubernetes")).isEqualTo(1);
    }

    @Test
    void rendersRankedByMentionsDescendingThenName() {
        List<String> posts = List.of(
                "Spring", "Spring", "Spring", "Java", "Java", "Kotlin");

        var result = TechDemandAggregator.aggregate(posts, VOCAB, 1, 10);

        assertThat(result).extracting(TechDemandEntry::name)
                .containsExactly("spring", "java", "kotlin");
    }

    @Test
    void shortTokensRespectWordBoundaries() {
        List<String> posts = List.of(
                "We love Go and Golang is not the same token",
                "google is not golang",
                "React frontend, not reactionary");

        var result = TechDemandAggregator.aggregate(posts, VOCAB, 1, 10);

        // "go" matches the standalone word in post 1 only (not "golang"/"google").
        assertThat(entry(result, "go")).isEqualTo(1);
        // "react" matches "React" but not "reactionary".
        assertThat(entry(result, "react")).isEqualTo(1);
    }

    @Test
    void isCaseInsensitive() {
        var result = TechDemandAggregator.aggregate(List.of("KUBERNETES on AWS"), VOCAB, 1, 10);
        assertThat(entry(result, "kubernetes")).isEqualTo(1);
    }

    @Test
    void dropsTechnologiesBelowMinMentions() {
        List<String> posts = List.of("Java", "Java", "Kotlin");

        var result = TechDemandAggregator.aggregate(posts, VOCAB, 2, 10);

        assertThat(result).extracting(TechDemandEntry::name).containsExactly("java");
    }

    @Test
    void keepsOnlyTopNTechnologies() {
        List<String> posts = List.of("Java Spring Kotlin go react kubernetes");

        var result = TechDemandAggregator.aggregate(posts, VOCAB, 1, 3);

        assertThat(result).hasSize(3);
    }

    @Test
    void handlesEmptyInputs() {
        assertThat(TechDemandAggregator.aggregate(List.of(), VOCAB, 1, 10)).isEmpty();
        assertThat(TechDemandAggregator.aggregate(List.of("Java"), List.of(), 1, 10)).isEmpty();
        assertThat(TechDemandAggregator.aggregate(null, VOCAB, 1, 10)).isEmpty();
    }

    private static int entry(List<TechDemandEntry> result, String name) {
        return result.stream()
                .filter(e -> e.name().equals(name))
                .map(TechDemandEntry::mentions)
                .findFirst()
                .orElse(0);
    }
}
