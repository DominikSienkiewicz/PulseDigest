package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Signal;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SignalRank;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Decides, once per edition, which section each story belongs to — so no story is printed twice.
 *
 * <p>Every section used to re-filter the same item list against its own threshold, which meant one
 * item could satisfy four of them at once. In a measured edition fifteen items produced twenty-seven
 * renderings and the lead story appeared in four consecutive blocks. Selection happens here now, in
 * priority order, and each builder renders the list it is handed instead of deciding membership for
 * itself.
 *
 * <p>Priority is <em>Critical trends → Must-know → Deals &amp; Tools → Top picks → Signals</em>, which
 * is also the order the sections appear in the document, so the reader never meets a story later than
 * the first place it could have appeared. Deals &amp; Tools therefore means "things you can adopt that
 * you have not already read about above", and the tables mean "everything else that cleared the bar".
 */
record DigestLayout(
        List<DigestStory> criticalTrends,
        List<DigestStory> mustKnow,
        List<DigestStory> deals,
        List<DigestStory> topPicks,
        List<DigestStory> signals
) {

    static final int MUST_KNOW_THRESHOLD = 7;
    private static final int MUST_KNOW_LIMIT = 5;
    private static final int DEALS_THRESHOLD = 6;
    private static final int DEALS_LIMIT = 5;
    private static final int TOP_PICK_THRESHOLD = 8;
    private static final int SIGNAL_THRESHOLD = 6;

    /** Item types that belong in "Deals &amp; Tools" — things the reader can adopt or claim now. */
    private static final Set<String> DEAL_TYPES = Set.of("LAUNCH", "RELEASE", "FEATURE", "PROMOTION");

    DigestLayout {
        criticalTrends = List.copyOf(criticalTrends);
        mustKnow = List.copyOf(mustKnow);
        deals = List.copyOf(deals);
        topPicks = List.copyOf(topPicks);
        signals = List.copyOf(signals);
    }

    /**
     * Every item URL this layout will print, folded-in members included.
     *
     * <p>The weekly recap filters against this. It has to carry the members and not just the leads:
     * a recap line names an item by its own URL, so reporting leads alone would let a story that was
     * folded into another entry reappear below as if it were new.
     */
    Set<String> renderedUrls() {
        return Stream.of(criticalTrends, mustKnow, deals, topPicks, signals)
                .flatMap(List::stream)
                .flatMap(story -> story.members().stream())
                .map(DigestItem::url)
                .collect(Collectors.toSet());
    }

    static DigestLayout of(List<DigestItem> items, List<Signal> signals) {
        Map<String, Signal> signalByUrl = signals.stream()
                .collect(Collectors.toMap(signal -> signal.item().url(), signal -> signal, (first, second) -> first));
        List<DigestStory> stories = groupIntoStories(items, signalByUrl);
        Set<String> criticalKeys = criticalStoryKeys(signals);
        Set<String> claimed = new HashSet<>();
        List<DigestStory> ranked = byScoreDesc(stories);

        List<DigestStory> criticals = claim(claimed, ranked,
                story -> criticalKeys.contains(storyKey(story.lead())), Integer.MAX_VALUE);
        List<DigestStory> mustKnow = claim(claimed, ranked,
                story -> story.score() >= MUST_KNOW_THRESHOLD, MUST_KNOW_LIMIT);
        List<DigestStory> deals = claim(claimed, ranked, DigestLayout::isDeal, DEALS_LIMIT);
        // The tables keep the model's own ordering rather than re-sorting: they index what is left,
        // and the reader already met the ranked material above.
        List<DigestStory> topPicks = claim(claimed, stories,
                story -> story.score() >= TOP_PICK_THRESHOLD, Integer.MAX_VALUE);
        List<DigestStory> tail = claim(claimed, stories,
                story -> story.score() >= SIGNAL_THRESHOLD, Integer.MAX_VALUE);

        return new DigestLayout(criticals, mustKnow, deals, topPicks, tail);
    }

    /**
     * Groups items into stories by {@code topic_key}, keeping the highest-scoring member as the lead.
     *
     * <p>Deliberately not {@link DigestItem#correlationKey()}, which falls back to the category when a
     * topic key is missing. That fallback is right for <em>measuring</em> cross-source confirmation —
     * a degraded model response still correlates something — but wrong for <em>merging</em> what the
     * reader sees, because it would hide two unrelated "AI/LLM" stories behind one entry. Without a
     * topic key an item is its own story.
     */
    private static List<DigestStory> groupIntoStories(List<DigestItem> items, Map<String, Signal> signalByUrl) {
        Map<String, List<DigestItem>> grouped = new LinkedHashMap<>();
        for (DigestItem item : items) {
            grouped.computeIfAbsent(storyKey(item), key -> new ArrayList<>()).add(item);
        }
        return grouped.values().stream().map(members -> toStory(members, signalByUrl)).toList();
    }

    private static String storyKey(DigestItem item) {
        String topicKey = item.topicKey();
        return topicKey != null && !topicKey.isBlank()
                ? "topic:" + topicKey.strip().toLowerCase(Locale.ROOT)
                : "url:" + item.url();
    }

    private static DigestStory toStory(List<DigestItem> members, Map<String, Signal> signalByUrl) {
        List<DigestItem> ranked = new ArrayList<>(members);
        // A stable sort means equal-scoring members keep the order the model emitted them in.
        ranked.sort(Comparator.comparingInt(DigestItem::score).reversed()
                .thenComparing(item -> signalScore(item, signalByUrl), Comparator.reverseOrder()));
        DigestItem lead = ranked.getFirst();
        return new DigestStory(lead, members, signalByUrl.get(lead.url()));
    }

    private static int signalScore(DigestItem item, Map<String, Signal> signalByUrl) {
        Signal signal = signalByUrl.get(item.url());
        return signal != null ? signal.signalScore() : 0;
    }

    /** A story is critical when ANY of its items reached CRITICAL, not only the one that leads it. */
    private static Set<String> criticalStoryKeys(List<Signal> signals) {
        return signals.stream()
                .filter(signal -> signal.rank() == SignalRank.CRITICAL)
                .map(signal -> storyKey(signal.item()))
                .collect(Collectors.toSet());
    }

    private static boolean isDeal(DigestStory story) {
        String type = story.lead().type();
        return story.score() >= DEALS_THRESHOLD && type != null && DEAL_TYPES.contains(type);
    }

    /** Takes every story the predicate accepts that no earlier section already took, up to {@code limit}. */
    private static List<DigestStory> claim(Set<String> claimedKeys, List<DigestStory> order,
                                           Predicate<DigestStory> belongsHere, int limit) {
        List<DigestStory> section = new ArrayList<>();
        for (DigestStory story : order) {
            if (section.size() >= limit) {
                break;
            }
            String key = storyKey(story.lead());
            if (!claimedKeys.contains(key) && belongsHere.test(story)) {
                section.add(story);
                claimedKeys.add(key);
            }
        }
        return section;
    }

    private static List<DigestStory> byScoreDesc(List<DigestStory> stories) {
        List<DigestStory> sorted = new ArrayList<>(stories);
        sorted.sort(Comparator.comparingInt(DigestStory::score).reversed());
        return sorted;
    }
}
