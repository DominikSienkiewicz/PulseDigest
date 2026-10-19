package pl.seniordeveloper.pulsedigest.modules.market_intel.application;

import pl.seniordeveloper.pulsedigest.testing.TestModels;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.ScoringPolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Signal;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SignalRank;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceDomain;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SignalScoringServiceTest {

    private final SignalScoringService service = new SignalScoringService(ScoringPolicy.defaults());

    private static DigestItem item(String source, String category, int engagement) {
        return TestModels.digestItem(
                "title", "http://example.com/" + Math.abs(source.hashCode()),
                source, category, "TYPE", 5, engagement, "summary", null);
    }

    /** Item carrying an explicit LLM-assigned topic key — the real correlation unit. */
    private static DigestItem topicItem(String source, String category, String topicKey) {
        return new DigestItem(
                "title", "http://example.com/" + Math.abs((source + topicKey).hashCode()),
                source, category, "TYPE", 5, 0, "summary", null, topicKey);
    }

    /**
     * F2. The README promises "the same topic surfacing in Science + Code + Business = 🔴 Critical
     * Trend", {@code how-it-works.md} says a story on 3+ domains "is promoted to 🔴 CRITICAL", and the
     * predictive radar calls a two-domain story "one independent confirmation short of 🔴". All three
     * describe corroboration as the thing the badge means. The score threshold cannot deliver that:
     * three weak sources clear 3 domains while adding up to 80 points.
     *
     * <p>Red before the fix: these three score 80, 90 and 120, so only the last was CRITICAL and the
     * story was split across three different ranks despite being one confirmed story.
     */
    @Test
    void aStoryConfirmedAcrossThreeDomainsIsCriticalEvenWhenItScoresLow() {
        List<Signal> result = service.score(List.of(topicItem("Security Advisories", "Security", "mcp"),
                topicItem("Twitter/X", "Community", "mcp"),
                topicItem("Hugging Face", "Research", "mcp")));

        assertThat(result).allMatch(s -> s.rank() == SignalRank.CRITICAL);
        assertThat(result).anyMatch(s -> s.signalScore() < 101);
    }

    /**
     * The other half of F2, and the one the audit calls out: a single popular source reaching the
     * score threshold used to earn 🔴, which made the badge a measure of popularity rather than of
     * confirmation. {@code GitHub Releases} at 0.95 plus an engagement bonus clears 101 on its own.
     *
     * <p>Red before the fix: this was CRITICAL on one domain.
     */
    @Test
    void aSinglePopularSourceIsNeverCriticalHoweverHighItScores() {
        List<Signal> result = service.score(List.of(item("GitHub Releases", "Releases", 10_000)));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().signalScore()).isGreaterThan(101);
        assertThat(result.getFirst().sourceDomains()).hasSize(1);
        assertThat(result.getFirst().rank()).isEqualTo(SignalRank.STRONG);
    }

    /**
     * N30. The one experiment F2 leaves open: does corroboration across *two* independent domains
     * mean enough to be 🔴, or does it take three? With the threshold compiled in, trying it meant a
     * recompile, which is how an experiment stops happening.
     */
    @Test
    void loweringTheCrossSourceThresholdMakesTwoDomainsEnoughForCritical() {
        List<DigestItem> twoDomains = List.of(topicItem("arXiv/cs.AI", "Research", "mcp"),
                topicItem("GitHub", "Open Source", "mcp"));

        assertThat(service.score(twoDomains))
                .as("three domains required by default")
                .noneMatch(s -> s.rank() == SignalRank.CRITICAL);

        SignalScoringService lenient = new SignalScoringService(
                new ScoringPolicy(2, 50, 50, 1_000, 100, 60, java.util.Map.of(), java.util.Set.of(), 1.0));

        assertThat(lenient.score(twoDomains)).allMatch(s -> s.rank() == SignalRank.CRITICAL);
    }

    /**
     * The other half of F2's problem, now testable from configuration: {@code SourceDomain.from}
     * collapses RSS, Reddit and every unrecognised label into {@code SOCIAL}, so a story on three of
     * them counts as one domain. Moving one elsewhere makes the same three items corroborate.
     */
    @Test
    void aDomainOverrideCanSplitSourcesThatOtherwiseCollapseIntoSocial() {
        List<DigestItem> allSocialByDefault = List.of(topicItem("Reddit/r/java", "Community", "mcp"),
                topicItem("RSS/The Register", "Press", "mcp"),
                topicItem("Mastodon", "Community", "mcp"));

        assertThat(service.score(allSocialByDefault))
                .as("all three resolve to SOCIAL, so this is one domain and not corroboration")
                .noneMatch(s -> s.rank() == SignalRank.CRITICAL);

        SignalScoringService split = new SignalScoringService(new ScoringPolicy(3, 50, 50, 1_000, 100, 60,
                java.util.Map.of("Reddit", SourceDomain.CODE, "RSS", SourceDomain.BUSINESS),
                java.util.Set.of(), 1.0));

        assertThat(split.score(allSocialByDefault)).allMatch(s -> s.rank() == SignalRank.CRITICAL);
    }

    /** An override must not win over a more specific one just because of map ordering. */
    @Test
    void theLongestMatchingOverridePrefixWins() {
        ScoringPolicy policy = new ScoringPolicy(3, 50, 50, 1_000, 100, 60,
                java.util.Map.of("RSS", SourceDomain.BUSINESS, "RSS/The Register", SourceDomain.SECURITY),
                java.util.Set.of(), 1.0);

        assertThat(policy.domainOf("RSS/The Register")).isEqualTo(SourceDomain.SECURITY);
        assertThat(policy.domainOf("RSS/InfoQ")).isEqualTo(SourceDomain.BUSINESS);
        assertThat(policy.domainOf("Hacker News"))
                .as("an unmatched label still falls through to the built-in mapping")
                .isEqualTo(SourceDomain.BUSINESS);
    }

    /**
     * N13. An explicit mute lowers the score — the reader said they do not want this.
     */
    @Test
    void anExplicitlyMutedTopicScoresLower() {
        SignalScoringService muting = muted("crypto");

        int unmuted = service.score(List.of(item("Hacker News", "Crypto", 0))).getFirst().signalScore();
        int withMute = muting.score(List.of(item("Hacker News", "Crypto", 0))).getFirst().signalScore();

        assertThat(withMute).isLessThan(unmuted);
        assertThat(muting.score(List.of(item("Hacker News", "Java/JVM", 0))).getFirst().signalScore())
                .as("an unmuted topic is untouched")
                .isEqualTo(service.score(List.of(item("Hacker News", "Java/JVM", 0))).getFirst().signalScore());
    }

    /**
     * The anti-bubble guard, and the whole reason a mute is a preference rather than a filter: a
     * muted topic that three independent domains confirm still reaches 🔴. Before F2 this needed a
     * rule — never scale the cross-source bonus — and it is now structural, because rank comes from
     * the domain count and not from points. Without this, a muted category could never earn the
     * votes that would revive it.
     */
    @Test
    void aMutedTopicThatThreeDomainsConfirmStillReachesCritical() {
        List<Signal> result = muted("crypto").score(List.of(topicItem("arXiv/cs.AI", "Crypto", "stablecoin-settlement"),
                topicItem("GitHub", "Crypto", "stablecoin-settlement"),
                topicItem("Hacker News", "Crypto", "stablecoin-settlement")));

        assertThat(result).allMatch(s -> s.rank() == SignalRank.CRITICAL);
    }

    /** Matched against the topic key too, so a mute works wherever the model put the subject. */
    @Test
    void aMuteMatchesTheTopicKeyAndNotOnlyTheCategory() {
        List<Signal> result = muted("stablecoin").score(List.of(topicItem("Hacker News", "Finance", "stablecoin-settlement")));

        assertThat(result.getFirst().breakdown().wasMuted()).isTrue();
    }

    private static SignalScoringService muted(String topic) {
        return new SignalScoringService(new ScoringPolicy(3, 50, 50, 1_000, 100, 60,
                java.util.Map.of(), java.util.Set.of(topic), 0.5));
    }

    @Test
    void crossSourceBonusGroupsByTopicKeyAcrossDifferentCategories() {
        // One story ("mcp") confirmed by a paper, a repo and a discussion — the thing 🔴 promises.
        List<Signal> result = service.score(List.of(topicItem("arXiv/cs.AI", "Research", "mcp"),
                topicItem("GitHub", "Open Source", "mcp"),
                topicItem("Hacker News", "Community", "mcp")));

        assertThat(result).allMatch(s -> s.rank() == SignalRank.CRITICAL);
    }

    @Test
    void sameBroadCategoryWithDistinctTopicsNoLongerEarnsTheBonus() {
        // Three unrelated stories that merely share the umbrella category "AI/LLM". Grouping by
        // category made this CRITICAL — it measured category diversity, not confirmation.
        List<Signal> result = service.score(List.of(
                topicItem("arXiv/cs.AI", "AI/LLM", "mcp"),
                topicItem("GitHub", "AI/LLM", "gemini-3"),
                topicItem("Hacker News", "AI/LLM", "llama-5")));

        assertThat(result).noneMatch(s -> s.rank() == SignalRank.CRITICAL);
    }

    @Test
    void topicKeyMatchingIsCaseInsensitive() {
        List<Signal> result = service.score(List.of(
                topicItem("arXiv/cs.AI", "Research", "MCP"),
                topicItem("GitHub", "Open Source", "mcp"),
                topicItem("Product Hunt", "Other", "Mcp")));

        assertThat(result).allMatch(s -> s.rank() == SignalRank.CRITICAL);
    }

    @Test
    void blankTopicKeyFallsBackToCategoryGrouping() {
        // Graceful degradation: a model that omits topic_key must not silently disable 🔴 entirely.
        List<Signal> result = service.score(List.of(
                topicItem("arXiv/cs.AI", "llm", "  "),
                topicItem("GitHub", "llm", null),
                topicItem("Hacker News", "llm", "")));

        assertThat(result).allMatch(s -> s.rank() == SignalRank.CRITICAL);
    }

    // --- C7: every scored signal explains itself ---

    @Test
    void everySignalCarriesTheBreakdownOfItsOwnScore() {
        List<Signal> result = service.score(
                List.of(item("Hacker News", "Java/JVM", 12_000)), Map.of("Hacker News", -2), Map.of("java/jvm", 3));

        assertThat(result.get(0).breakdown()).satisfies(b -> {
            assertThat(b.sourceKey()).isEqualTo("Hacker News");
            assertThat(b.baseWeight()).isCloseTo(0.80, within(0.001));
            assertThat(b.netSourceVotes()).isEqualTo(-2);
            assertThat(b.effectiveWeight()).isCloseTo(0.70, within(0.001));
            assertThat(b.engagementBonus()).isEqualTo(12);
            assertThat(b.netCategoryVotes()).isEqualTo(3);
            assertThat(b.categoryMultiplier()).isCloseTo(1.06, within(0.001));
            assertThat(b.crossSourceBonus()).isZero();
        });
    }

    @Test
    void theBreakdownAddsUpToTheScoreThatWasActuallyAssigned() {
        // The explanation must reconstruct the number, or it is a story about a different digest.
        List<Signal> result = service.score(
                List.of(item("Hacker News", "Java/JVM", 12_000)), Map.of(), Map.of("java/jvm", 3));
        Signal signal = result.get(0);

        var b = signal.breakdown();
        int reconstructed = (int) Math.round((b.baseScore() + b.engagementBonus()) * b.categoryMultiplier())
                + b.crossSourceBonus();

        assertThat(reconstructed).isEqualTo(signal.signalScore());
    }

    @Test
    void theBreakdownRecordsTheCrossSourceBonusWhenThreeDomainsConfirmedTheStory() {
        List<Signal> result = service.score(List.of(topicItem("arXiv/cs.AI", "Research", "mcp"),
                topicItem("GitHub", "Open Source", "mcp"),
                topicItem("Hacker News", "Community", "mcp")));

        assertThat(result).allSatisfy(s -> assertThat(s.breakdown().wasCrossSourceConfirmed()).isTrue());
    }

    // --- C13: per-category preference multiplier ---

    @Test
    void aDownVotedCategoryLosesScoreOnItsCredibilityAndEngagement() {
        // arXiv base 70, category "Research" at −5 net votes → multiplier 0.90 → 63.
        List<Signal> result = service.score(
                List.of(item("arXiv/cs.AI", "Research", 0)), Map.of(), Map.of("research", -5));

        assertThat(result.get(0).signalScore()).isEqualTo(63);
    }

    @Test
    void anUpVotedCategoryGainsScore() {
        // Hacker News base 80, category "Java/JVM" at +3 → multiplier 1.06 → 85 (rounded).
        List<Signal> result = service.score(
                List.of(item("Hacker News", "Java/JVM", 0)), Map.of(), Map.of("java/jvm", 3));

        assertThat(result.get(0).signalScore()).isEqualTo(85);
    }

    @Test
    void theCrossSourceBonusIsImmuneToCategoryPreference() {
        // The escape hatch against the positive feedback loop: a muted category can still break
        // through as a Critical Trend when three independent domains confirm one story. Corroboration
        // is evidence, not taste — so the +50 is added after the multiplier, never scaled by it.
        List<Signal> muted = service.score(List.of(topicItem("arXiv/cs.AI", "Research", "mcp"),
                topicItem("GitHub", "Research", "mcp"),
                topicItem("Hacker News", "Research", "mcp")), Map.of(), Map.of("research", -100));

        assertThat(muted).allMatch(s -> s.rank() == SignalRank.CRITICAL);
    }

    @Test
    void noCategoryVotesScoreIdenticallyToTheTwoArgOverload() {
        List<Signal> withEmpty = service.score(List.of(item("Hacker News", "Java", 0)), Map.of(), Map.of());
        List<Signal> without = service.score(List.of(item("Hacker News", "Java", 0)));

        assertThat(withEmpty.get(0).signalScore()).isEqualTo(without.get(0).signalScore());
    }

    @Test
    void anItemWithoutACategoryIsUnaffectedByCategoryVotes() {
        List<Signal> result = service.score(
                List.of(item("Hacker News", null, 0)), Map.of(), Map.of("research", -100));

        assertThat(result.get(0).signalScore()).isEqualTo(80);
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
        // arXiv weight=0.70 → base=70 + min(50, 999_999/1000)=50 = 120.
        // The cap is this test's subject and is unchanged. The rank expectation is not: a single
        // source on one domain is STRONG at any score since 2026-10-18 (F2), where this previously
        // read CRITICAL — which is precisely the behaviour the fix removes, not a weakened assertion.
        List<Signal> result = service.score(List.of(item("arXiv/cs.AI", "AI", 999_999)));
        assertThat(result.get(0).signalScore()).isEqualTo(120);
        assertThat(result.get(0).rank()).isEqualTo(SignalRank.STRONG);
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
        DigestItem itemWithNull = TestModels.digestItem(
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
        DigestItem noCategory = TestModels.digestItem(
                "title", "http://example.com", "arXiv/cs.AI", null, "TYPE", 5, 0, "summary", null);
        List<Signal> result = service.score(List.of(noCategory));
        // arXiv base=70, no cross-source bonus because category is null
        assertThat(result.get(0).signalScore()).isEqualTo(70);
        assertThat(result.get(0).sourceDomains()).isEmpty();
    }
}
