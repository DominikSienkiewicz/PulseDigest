package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.ai;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PreScoringTriageTest {

    private static Map<String, Object> item(String url, String source, int engagement) {
        return Map.of("url", url, "title", "t", "source", source, "engagement_score", engagement);
    }

    @Test
    void keepsTheHighestTriagedItemsUpToTheBudget() {
        List<Map<String, Object>> payload = List.of(
                item("https://a", "RSS/Blog", 0),
                item("https://b", "RSS/Blog", 0),
                item("https://c", "RSS/Blog", 0));
        Map<String, Integer> scores = Map.of("https://a", 9, "https://b", 2, "https://c", 7);

        List<Map<String, Object>> kept = PreScoringTriage.triage(payload, scores, 2);

        assertThat(kept).extracting(m -> m.get("url")).containsExactly("https://a", "https://c");
    }

    @Test
    void passesEverythingThroughWhenTheTriageModelHadNoOpinion() {
        // An empty score map means the mini call failed or was disabled. Dropping items on that
        // basis would silently shrink the digest because of an infrastructure hiccup.
        List<Map<String, Object>> payload = List.of(item("https://a", "RSS/Blog", 0), item("https://b", "RSS", 0));

        assertThat(PreScoringTriage.triage(payload, Map.of(), 1)).isSameAs(payload);
    }

    @Test
    void isANoopWhenThePayloadAlreadyFitsTheBudget() {
        List<Map<String, Object>> payload = List.of(item("https://a", "RSS/Blog", 0));

        assertThat(PreScoringTriage.triage(payload, Map.of("https://a", 1), 5)).isSameAs(payload);
    }

    @Test
    void aHighWeightSourceSurvivesEvenWhenTheTriageModelDismissesIt() {
        // GitHub Releases pre-scores 95 deterministically. A mini model that has never heard of the
        // project must not be able to cut it — that is exactly the niche item the reader wants.
        List<Map<String, Object>> payload = new ArrayList<>();
        payload.add(item("https://release", "GitHub Releases", 0));
        for (int i = 0; i < 5; i++) {
            payload.add(item("https://tweet-" + i, "Twitter/X", 0));
        }
        Map<String, Integer> scores = new java.util.HashMap<>();
        scores.put("https://release", 0);
        for (int i = 0; i < 5; i++) {
            scores.put("https://tweet-" + i, 10);
        }

        List<Map<String, Object>> kept = PreScoringTriage.triage(payload, scores, 3);

        assertThat(kept).extracting(m -> m.get("url")).contains("https://release");
        assertThat(kept).hasSize(3);
    }

    @Test
    void unscoredItemsAreTreatedAsNeutralRatherThanDropped() {
        List<Map<String, Object>> payload = List.of(
                item("https://scored", "RSS/Blog", 0),
                item("https://unscored", "RSS/Blog", 0),
                item("https://low", "RSS/Blog", 0));
        Map<String, Integer> scores = Map.of("https://scored", 9, "https://low", 1);

        List<Map<String, Object>> kept = PreScoringTriage.triage(payload, scores, 2);

        assertThat(kept).extracting(m -> m.get("url")).containsExactly("https://scored", "https://unscored");
    }
}
