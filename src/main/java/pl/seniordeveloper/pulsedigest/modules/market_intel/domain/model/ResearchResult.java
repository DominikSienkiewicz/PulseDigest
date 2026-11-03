package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Aggregate of all raw data collected from external intelligence sources.
 */
/*
 * Serialisation note (N24): the four derived accessors below are @JsonIgnore'd so a stored edition
 * input is exactly the components the prompt was built from. Jackson otherwise writes them as
 * `empty`, `summary`, `rawTotalCount` and `activeSourceCount` — fields no constructor can read back,
 * which made the payload unreadable rather than merely larger.
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
     * One fetched source: what to call it, and how much it returned.
     *
     * <p>The roster below is C1's concession made concrete. ADR-0002 argues that adding a source
     * should cost a deliberate edit, and that still holds — but it does not follow that the edit
     * should be repeated in every aggregation of one file. {@code isEmpty}, {@code activeSourceCount},
     * {@code rawTotalCount} and {@code summary} each used to restate the same sixteen lists, so a new
     * source that was added to some of them and forgotten in another compiled perfectly and reported
     * itself wrong.
     *
     * @param label    what {@link #summary()} calls this source
     * @param items    what it returned after filtering
     * @param rawCount what it returned <em>before</em> filtering — the same number for sources that
     *                 do not page, and {@link RawCounts} for the five that do
     */
    private record Section(String label, List<?> items, int rawCount) {

        static Section of(String label, List<?> items) {
            return new Section(label, items, items.size());
        }

        static Section paged(String label, List<?> items, int rawCount) {
            return new Section(label, items, rawCount);
        }
    }

    /**
     * Every source this result carries, in the order {@link #summary()} prints them. The one place a
     * new source has to be registered for all four aggregations to see it.
     */
    private List<Section> sections() {
        return List.of(
                Section.paged("tweetów", tweets, rawCounts.tweets()),
                Section.paged("HN", hackerNewsPosts, rawCounts.hackerNews()),
                Section.paged("GH", githubRepos, rawCounts.github()),
                Section.paged("RSS", rssItems, rawCounts.rss()),
                Section.paged("Reddit", redditPosts, rawCounts.reddit()),
                Section.of("papers", papers),
                Section.of("releases", releases),
                Section.of("HF models", huggingFaceModels),
                Section.of("ProductHunt", productHuntPosts),
                Section.of("security advisories", securityAdvisories),
                Section.of("JEP", jepUpdates),
                Section.of("CNCF", cncfProjectUpdates),
                Section.of("Radar", radarEntries),
                Section.of("Talks", conferenceTalks),
                Section.of("Lab announcements", labAnnouncements),
                Section.of("Social", socialPosts));
    }
}
