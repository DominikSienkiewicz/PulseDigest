package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Aggregate of all raw data collected from external intelligence sources.
 */
/*
 * Serialisation note (N24): the five derived accessors below are @JsonIgnore'd so a stored edition
 * input is exactly the components the prompt was built from. Jackson otherwise writes them as
 * `empty`, `summary`, `allTitles`, `rawTotalCount` and `activeSourceCount` — fields no constructor
 * can read back, which made the payload unreadable rather than merely larger.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ResearchResult(
        List<Tweet> tweets,
        List<HackerNewsPost> hackerNewsPosts,
        List<GithubRepo> githubRepos,
        List<RssItem> rssItems,
        List<RedditPost> redditPosts,
        List<ResearchPaper> papers,
        List<SoftwareRelease> releases,
        List<HuggingFaceModel> huggingFaceModels,
        List<ProductHuntPost> productHuntPosts,
        List<SecurityAdvisory> securityAdvisories,
        List<JepUpdate> jepUpdates,
        List<CncfProjectUpdate> cncfProjectUpdates,
        List<RadarEntry> radarEntries,
        List<ConferenceTalk> conferenceTalks,
        List<LabAnnouncement> labAnnouncements,
        List<SocialPost> socialPosts,
        LocalDateTime collectedAt,
        @JsonProperty("raw_counts") RawCounts rawCounts,
        List<SourceFetchReport> sourceFetchReports
) {

    public ResearchResult {
        // An edition stored before N48 carries the five flat counts this record no longer has, so it
        // deserializes with none. Degrading to zero keeps `--replay-prompt` working on those
        // editions; the counts only feed a footer line, and a wrong footer beats a crash.
        rawCounts = rawCounts != null ? rawCounts : RawCounts.NONE;
        sourceFetchReports = sourceFetchReports != null ? List.copyOf(sourceFetchReports) : List.of();
        labAnnouncements = labAnnouncements != null ? List.copyOf(labAnnouncements) : List.of();
        socialPosts = socialPosts != null ? List.copyOf(socialPosts) : List.of();
    }

    @JsonIgnore
    public boolean isEmpty() {
        return sections().stream().allMatch(section -> section.items().isEmpty());
    }

    /**
     * Every headline this run fetched, before any prompt budget was applied. The watchlist scans
     * these — scanning only the items that reached the digest would make "0 mentions" meaningless.
     */
    @JsonIgnore
    public List<String> allTitles() {
        return sections().stream()
                .flatMap(Section::titles)
                .filter(title -> title != null && !title.isBlank())
                .toList();
    }

    @JsonIgnore
    public int rawTotalCount() {
        return sections().stream().mapToInt(Section::rawCount).sum();
    }

    @JsonIgnore
    public int activeSourceCount() {
        return (int) sections().stream().filter(section -> !section.items().isEmpty()).count();
    }

    @JsonIgnore
    public String summary() {
        String counts = sections().stream()
                .map(section -> section.rawCount() + " " + section.label())
                .collect(Collectors.joining(", "));
        return "Zebrano: %s \u2014 \u0142\u0105cznie %d item\u00f3w, o %s"
                .formatted(counts, rawTotalCount(), collectedAt);
    }

    /**
     * One fetched source: what to call it, what it returned, and how to read a headline out of it.
     *
     * <p>The roster below is C1's concession made concrete. ADR-0002 argues that adding a source
     * should cost a deliberate edit, and that still holds — but it does not follow that the edit
     * should be repeated five times in one file. {@code isEmpty}, {@code activeSourceCount},
     * {@code allTitles}, {@code rawTotalCount} and {@code summary} each used to restate the same
     * sixteen lists, so a new source that was added to four of them and forgotten in the fifth
     * compiled perfectly and reported itself wrong.
     *
     * @param label    what {@link #summary()} calls this source
     * @param items    what it returned after filtering
     * @param title    a headline for one item, for the watchlist scan
     * @param rawCount what it returned <em>before</em> filtering — the same number for sources that
     *                 do not page, and {@link RawCounts} for the five that do
     */
    private record Section<T>(String label, List<T> items, Function<T, String> title, int rawCount) {

        static <T> Section<T> of(String label, List<T> items, Function<T, String> title) {
            return new Section<>(label, items, title, items.size());
        }

        static <T> Section<T> paged(String label, List<T> items, Function<T, String> title, int rawCount) {
            return new Section<>(label, items, title, rawCount);
        }

        /** Mapping happens inside, so callers can hold a {@code Section<?>} without capturing T. */
        Stream<String> titles() {
            return items.stream().map(title);
        }
    }

    /**
     * Every source this result carries, in the order {@link #summary()} prints them. The one place a
     * new source has to be registered for all five aggregations to see it.
     */
    private List<Section<?>> sections() {
        return List.of(
                Section.paged("tweetów", tweets, Tweet::text, rawCounts.tweets()),
                Section.paged("HN", hackerNewsPosts, HackerNewsPost::title, rawCounts.hackerNews()),
                Section.paged("GH", githubRepos,
                        r -> r.name() + " " + (r.description() != null ? r.description() : ""),
                        rawCounts.github()),
                Section.paged("RSS", rssItems, RssItem::title, rawCounts.rss()),
                Section.paged("Reddit", redditPosts, RedditPost::title, rawCounts.reddit()),
                Section.of("papers", papers, ResearchPaper::title),
                Section.of("releases", releases, r -> r.repoFullName() + " " + r.version()),
                Section.of("HF models", huggingFaceModels, HuggingFaceModel::id),
                Section.of("ProductHunt", productHuntPosts, ProductHuntPost::name),
                Section.of("security advisories", securityAdvisories,
                        a -> a.summary() != null ? a.summary() : a.ghsaId()),
                Section.of("JEP", jepUpdates, j -> j.title() != null ? j.title() : j.jepId()),
                Section.of("CNCF", cncfProjectUpdates, CncfProjectUpdate::projectName),
                Section.of("Radar", radarEntries, RadarEntry::name),
                Section.of("Talks", conferenceTalks, ConferenceTalk::title),
                Section.of("Lab announcements", labAnnouncements, LabAnnouncement::title),
                Section.of("Social", socialPosts, SocialPost::text));
    }
}
