package pl.seniordeveloper.pulsedigest.modules.market_intel.application;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Signal;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SignalRank;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceDomain;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SignalScoringServiceTest {

    private final SignalScoringService service = new SignalScoringService();

    private static DigestItem item(String source, String category, int engagement) {
        return new DigestItem(
                "title", "http://example.com/" + Math.abs(source.hashCode()),
                source, category, "TYPE", 5, engagement, "summary", null);
    }

    @Test
    void arxivWithoutCrossSourceScoresModerate() {
        // arXiv weight=0.70 → base=70, engagement bonus=0, crossSource=0 → 70 → MODERATE
        List<Signal> result = service.score(List.of(item("arXiv/cs.AI", "AI", 0)));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).rank()).isEqualTo(SignalRank.MODERATE);
        assertThat(result.get(0).signalScore()).isEqualTo(70);
    }

    @Test
    void githubReleasesWithoutCrossSourceScoresModerate() {
        // GitHub Releases weight=0.95 → round(0.95*100)=95, engagement=0, crossSource=0 → 95 → MODERATE
        List<Signal> result = service.score(List.of(item("GitHub Releases", "Spring", 0)));
        assertThat(result.get(0).rank()).isEqualTo(SignalRank.MODERATE);
        assertThat(result.get(0).signalScore()).isEqualTo(95);
    }

    @Test
    void threeDistinctDomainsPromoteAllItemsToCritical() {
        // "llm" category → SCIENCE(arXiv), CODE(GitHub), BUSINESS(HN) → 3 domains → +50 bonus
        // arXiv:   70 + 0 + 50 = 120 → CRITICAL
        // GitHub:  85 + 0 + 50 = 135 → CRITICAL
        // HN:      80 + 0 + 50 = 130 → CRITICAL
        DigestItem paper = item("arXiv/cs.AI",  "llm", 0);
        DigestItem repo  = item("GitHub",       "llm", 0);
        DigestItem hn    = item("Hacker News",  "llm", 0);
        List<Signal> result = service.score(List.of(paper, repo, hn));
        assertThat(result).allMatch(s -> s.rank() == SignalRank.CRITICAL);
        assertThat(result.get(0).sourceDomains())
                .containsExactlyInAnyOrder(SourceDomain.BUSINESS, SourceDomain.CODE, SourceDomain.SCIENCE);
    }

    @Test
    void twoDistinctDomainsDoNotTriggerCrossSourceBonus() {
        // Only SCIENCE + CODE — need 3 distinct
        DigestItem paper = item("arXiv/cs.AI", "Quantum", 0);
        DigestItem repo  = item("GitHub",      "quantum", 0);  // category normalized case-insensitive
        List<Signal> result = service.score(List.of(paper, repo));
        assertThat(result).noneMatch(s -> s.rank() == SignalRank.CRITICAL);
    }

    @Test
    void categoryMatchingIsCaseInsensitive() {
        // "LLM" and "llm" must group into the same category
        DigestItem paper   = item("arXiv/cs.AI", "LLM",  0);
        DigestItem repo    = item("GitHub",      "llm",  0);
        DigestItem product = item("Product Hunt", "LLM", 0);
        List<Signal> result = service.score(List.of(paper, repo, product));
        // SCIENCE + CODE + BUSINESS → 3 domains → CRITICAL
        assertThat(result).allMatch(s -> s.rank() == SignalRank.CRITICAL);
    }

    @Test
    void twitterWithLowEngagementScoresWeak() {
        // Twitter/X weight=0.40 → base=40, engagement(500/1000=0), crossSource=0 → 40 → WEAK
        List<Signal> result = service.score(List.of(item("Twitter/X", "misc", 500)));
        assertThat(result.get(0).rank()).isEqualTo(SignalRank.WEAK);
        assertThat(result.get(0).signalScore()).isEqualTo(40);
    }

    @Test
    void engagementBonusCappedAt50Points() {
        // arXiv weight=0.70 → base=70 + min(50, 999_999/1000)=50 = 120 → CRITICAL
        List<Signal> result = service.score(List.of(item("arXiv/cs.AI", "AI", 999_999)));
        assertThat(result.get(0).signalScore()).isEqualTo(120);
        assertThat(result.get(0).rank()).isEqualTo(SignalRank.CRITICAL);
    }

    @Test
    void engagementBonusUsesIntegerDivision() {
        // HN weight=0.80 → base=80, engagement=999/1000=0 (int division) → 80 → MODERATE
        List<Signal> result = service.score(List.of(item("Hacker News", "Java", 999)));
        assertThat(result.get(0).signalScore()).isEqualTo(80);
        assertThat(result.get(0).rank()).isEqualTo(SignalRank.MODERATE);
    }

    @Test
    void nullEngagementScoreTreatedAsZero() {
        DigestItem itemWithNull = new DigestItem(
                "title", "http://example.com", "Hacker News", "AI", "TYPE", 5, null, "summary", null);
        List<Signal> result = service.score(List.of(itemWithNull));
        assertThat(result.get(0).signalScore()).isEqualTo(80);
        assertThat(result.get(0).rank()).isEqualTo(SignalRank.MODERATE);
    }

    @Test
    void outputSortedByRankThenSignalScoreDescending() {
        // HN:      80 → MODERATE  (0.80 * 100 = 80)
        // arXiv:   70 → MODERATE  (0.70 * 100 = 70)
        // Reddit:  60 → MODERATE  (0.60 * 100 = 60)
        // Twitter: 40 → WEAK
        // Within MODERATE sorted by score desc; WEAK ranks last regardless of input order.
        DigestItem arxiv   = item("arXiv/cs.AI",    "A", 0);
        DigestItem reddit  = item("Reddit/r/java",  "B", 0);
        DigestItem hn      = item("Hacker News",    "C", 0);
        DigestItem twitter = item("Twitter/X",      "D", 0);
        List<Signal> result = service.score(List.of(twitter, reddit, arxiv, hn));
        assertThat(result.get(0).signalScore()).isEqualTo(80);          // HN, MODERATE
        assertThat(result.get(1).signalScore()).isEqualTo(70);          // arXiv, MODERATE
        assertThat(result.get(2).signalScore()).isEqualTo(60);          // Reddit, MODERATE
        assertThat(result.get(3).rank()).isEqualTo(SignalRank.WEAK);    // Twitter 40
    }

    @Test
    void emptyItemsReturnsEmptyList() {
        assertThat(service.score(List.of())).isEmpty();
    }

    // --- C6 feedback nudging: score(items, netVotesBySource) ---

    @Test
    void downvotedSourceIsDemotedByFeedbackNudge() {
        // Hacker News base 0.80 → 80 (MODERATE). Net −6 votes → −0.30 → 0.50 → 50 (WEAK).
        DigestItem hn = item("Hacker News", "Java", 0);
        List<Signal> result = service.score(List.of(hn), Map.of("Hacker News", -6));
        assertThat(result.get(0).signalScore()).isEqualTo(50);
        assertThat(result.get(0).rank()).isEqualTo(SignalRank.WEAK);
    }

    @Test
    void upvotedSourceRaisesBaseScore() {
        // arXiv base 0.70 → 70. Net +4 → +0.20 → 0.90 → 90 (still MODERATE, but higher within rank).
        DigestItem arxiv = item("arXiv/cs.AI", "AI", 0);
        List<Signal> result = service.score(List.of(arxiv), Map.of("arXiv/cs.AI", 4));
        assertThat(result.get(0).signalScore()).isEqualTo(90);
        assertThat(result.get(0).rank()).isEqualTo(SignalRank.MODERATE);
    }

    @Test
    void emptyNudgeMapMatchesUnnudgedScoring() {
        DigestItem hn = item("Hacker News", "Java", 0);
        List<Signal> nudged = service.score(List.of(hn), Map.of());
        List<Signal> plain = service.score(List.of(hn));
        assertThat(nudged.get(0).signalScore()).isEqualTo(plain.get(0).signalScore());
        assertThat(nudged.get(0).signalScore()).isEqualTo(80);
    }

    @Test
    void feedbackAggregatesAtBaseSourceAcrossLabels() {
        // Votes are recorded under high-cardinality labels (arXiv/cs.AI), but the nudge modifies the
        // base-source weight — so a down-vote under "arXiv/cs.AI" must also demote a sibling "arXiv/cs.LG".
        DigestItem otherLabel = item("arXiv/cs.LG", "AI", 0);
        List<Signal> result = service.score(List.of(otherLabel), Map.of("arXiv/cs.AI", -6));
        // arXiv base 0.70 − 0.30 = 0.40 → 40 (WEAK), despite the differing label.
        assertThat(result.get(0).signalScore()).isEqualTo(40);
        assertThat(result.get(0).rank()).isEqualTo(SignalRank.WEAK);
    }

    @Test
    void feedbackSumsVotesAcrossLabelsOfSameSource() {
        // Two labels of the same base source sum at source level: -3 + -3 = -6 → 0.70 − 0.30 = 0.40.
        DigestItem arxiv = item("arXiv/cs.CR", "AI", 0);
        List<Signal> result = service.score(List.of(arxiv), Map.of("arXiv/cs.AI", -3, "arXiv/cs.LG", -3));
        assertThat(result.get(0).signalScore()).isEqualTo(40);
    }

    @Test
    void nudgeOnlyAffectsTheVotedSource() {
        // Down-vote HN; arXiv (unvoted) keeps its base 70.
        DigestItem hn = item("Hacker News", "A", 0);
        DigestItem arxiv = item("arXiv/cs.AI", "B", 0);
        List<Signal> result = service.score(List.of(hn, arxiv), Map.of("Hacker News", -6));
        Signal arxivSignal = result.stream().filter(s -> s.item().source().startsWith("arXiv")).findFirst().orElseThrow();
        assertThat(arxivSignal.signalScore()).isEqualTo(70);
    }

    @Test
    void itemWithNullCategoryIsScoredWithoutCrossSourceBonus() {
        DigestItem noCategory = new DigestItem(
                "title", "http://example.com", "arXiv/cs.AI", null, "TYPE", 5, 0, "summary", null);
        List<Signal> result = service.score(List.of(noCategory));
        // arXiv base=70, no cross-source bonus because category is null
        assertThat(result.get(0).signalScore()).isEqualTo(70);
        assertThat(result.get(0).sourceDomains()).isEmpty();
    }
}
