package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import pl.seniordeveloper.pulsedigest.shared.util.UrlCanonicalizer;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * One edition: the editorial lead, the day's top three insights, the scored items and the signals
 * carrying their cross-source assessment.
 *
 * <p>Every {@code withX} copies through the private {@code Builder} below and names only the
 * component it replaces. Until N58 each one restated the full seventeen-argument constructor call,
 * so an eighteenth component meant an eighteen-place edit, and an argument in the wrong position was
 * a silent bug rather than a compile error — several components are {@code List}s and {@code Set}s
 * the compiler will accept in each other's places. Adding a component now touches the record header,
 * the builder and the four-argument convenience constructor's delegation, and nothing else; the last
 * two both fail to compile until they are updated.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReportData(
        @JsonProperty("email_preview") String emailPreview,
        String editorial,
        @JsonProperty("top_insights") List<String> topInsights,
        List<DigestItem> items,
        List<Signal> signals,
        @JsonProperty("weekly_recap") WeeklyRecap weeklyRecap,
        @JsonProperty("radar_accuracy") RadarAccuracy radarAccuracy,
        @JsonProperty("reader_profile") ReaderProfile readerProfile,
        @JsonProperty("dependency_releases") List<DependencyRelease> dependencyReleases,
        @JsonProperty("stack_affecting_urls") Set<String> stackAffectingUrls,
        @JsonProperty("calibration") Calibration calibration,
        @JsonProperty("eol_notices") List<EolNotice> eolNotices,
        @JsonProperty("saved_backlog") SavedBacklog savedBacklog,
        @JsonProperty("silent_sources") List<SourceIntake> silentSources,
        @JsonProperty("reading_budget") ReadingBudget readingBudget,
        @JsonProperty("calls_for_papers") List<CallForPapers> callsForPapers,
        EditionProvenance provenance
) {

    public ReportData {
        dependencyReleases = dependencyReleases != null ? List.copyOf(dependencyReleases) : List.of();
        stackAffectingUrls = stackAffectingUrls != null ? Set.copyOf(stackAffectingUrls) : Set.of();
        eolNotices = eolNotices != null ? List.copyOf(eolNotices) : List.of();
        silentSources = silentSources != null ? List.copyOf(silentSources) : List.of();
        callsForPapers = callsForPapers != null ? List.copyOf(callsForPapers) : List.of();
    }

    /**
     * The shape the synthesis model returns: the four fields the LLM actually produces. Everything
     * else on this record is derived by the pipeline afterwards, so this is the one convenience
     * constructor that survived the F15 cull — it has a production caller, which is the whole test.
     *
     * <p>Its {@code this(...)} delegation is the one place besides the builder that still enumerates
     * every component, and it cannot be routed through the builder: {@code this(...)} has to be the
     * constructor's delegation and takes the components themselves, not an object carrying them. An
     * eighteenth component therefore needs a default added here too — but the compiler says so,
     * because the arity no longer matches.
     */
    public ReportData(String emailPreview, String editorial, List<String> topInsights, List<DigestItem> items) {
        this(emailPreview, editorial, topInsights, items, List.of(), null, null, null, List.of(),
                Set.of(), null, List.of(), null, List.of(), null, List.of(), null);
    }

    public ReportData withSignals(List<Signal> newSignals) {
        return toBuilder().signals(newSignals).build();
    }

    /** Copy of this report carrying the Thursday "week in signals" block. */
    public ReportData withWeeklyRecap(WeeklyRecap recap) {
        return toBuilder().weeklyRecap(recap).build();
    }

    /** Copy of this report carrying the predictive radar's published hit rate. */
    public ReportData withRadarAccuracy(RadarAccuracy accuracy) {
        return toBuilder().radarAccuracy(accuracy).build();
    }

    /** Copy of this report carrying the reader model that shaped it — shown in the footer. */
    public ReportData withReaderProfile(ReaderProfile profile) {
        return toBuilder().readerProfile(profile).build();
    }

    /**
     * Copy of this report carrying the reader's own outdated dependencies (N07).
     *
     * <p>Rendered deterministically, like the watchlist: these lines are a registry lookup against a
     * build file, not something the model should be asked to summarise or allowed to reword.
     */
    public ReportData withDependencyReleases(List<DependencyRelease> releases) {
        return toBuilder().dependencyReleases(releases).build();
    }

    /**
     * Copy of this report with a different editorial.
     *
     * <p>Replaced a five-argument constructor that the emergency-marker path used to rebuild the
     * report from parts. That constructor silently dropped every component it did not name — which
     * happened to be harmless where it was called, and would not have stayed harmless as the record
     * grew. A wither cannot drop anything.
     */
    public ReportData withEditorial(String newEditorial) {
        return toBuilder().editorial(newEditorial).build();
    }

    /** Copy of this report with a different item list — what the self-critique returns (N23). */
    public ReportData withItems(List<DigestItem> newItems) {
        return toBuilder().items(newItems).build();
    }

    /** Copy of this report carrying the conference deadlines still open (N09). */
    public ReportData withCallsForPapers(List<CallForPapers> calls) {
        return toBuilder().callsForPapers(calls).build();
    }

    /** Copy of this report naming the prompt and model that produced it (N26). */
    public ReportData withProvenance(EditionProvenance editionProvenance) {
        return toBuilder().provenance(editionProvenance).build();
    }

    /** Copy of this report carrying what it costs to read (N04). */
    public ReportData withReadingBudget(ReadingBudget budget) {
        return toBuilder().readingBudget(budget).build();
    }

    /** Copy of this report naming the sources that have gone quiet (N27). */
    public ReportData withSilentSources(List<SourceIntake> sources) {
        return toBuilder().silentSources(sources).build();
    }

    /** Copy of this report carrying what the reader saved and has not voted on since (N05). */
    public ReportData withSavedBacklog(SavedBacklog backlog) {
        return toBuilder().savedBacklog(backlog).build();
    }

    /** Copy of this report carrying the end-of-life dates due a line this edition (N08). */
    public ReportData withEolNotices(List<EolNotice> notices) {
        return toBuilder().eolNotices(notices).build();
    }

    /** Copy of this report carrying the 🔴 hit rate the reader's own votes produced (N12). */
    public ReportData withCalibration(Calibration newCalibration) {
        return toBuilder().calibration(newCalibration).build();
    }

    /**
     * Copy of this report knowing which of its items name a dependency the reader ships (N06).
     *
     * <p>A set of URLs rather than a flag on the item: an advisory reaches the digest as something
     * the model wrote, and its link is the only field that still says which advisory it was.
     */
    public ReportData withStackAffectingUrls(Set<String> urls) {
        return toBuilder().stackAffectingUrls(urls).build();
    }

    /**
     * Safety-net canonicalization after synthesis: strips tracking parameters from item URLs, in
     * case one reached the model with them attached. Returns {@code this} when there was nothing to
     * change, so an untouched report keeps its identity.
     */
    public ReportData withCanonicalizedUrls() {
        if (items == null || items.isEmpty()) {
            return this;
        }
        List<DigestItem> cleaned = items.stream()
                .map(i -> new DigestItem(
                        i.title(),
                        UrlCanonicalizer.canonicalize(i.url()),
                        i.source(), i.category(), i.type(),
                        i.score(), i.engagementScore(), i.summary(), i.whyItMatters(), i.topicKey()))
                .toList();
        return toBuilder().items(cleaned).build();
    }

    /**
     * Re-joins each item with the trusted metadata of the prompt item carrying the same canonical URL.
     * The model's echoed {@code source} and {@code engagement_score} are overwritten, and any item
     * whose URL was never in the prompt — a hallucination, or an exfiltration attempt riding on a
     * successful prompt injection — is dropped.
     */
    public ReportData withRejoinedMetadata(Map<String, PromptItemMeta> inputMeta) {
        if (items == null || items.isEmpty()) {
            return this;
        }
        List<DigestItem> rejoined = items.stream()
                .map(item -> rejoin(item, inputMeta))
                .filter(Objects::nonNull)
                .toList();
        return toBuilder().items(rejoined).build();
    }

    private static DigestItem rejoin(DigestItem item, Map<String, PromptItemMeta> inputMeta) {
        if (item.url() == null || item.url().isBlank()) {
            return null;
        }
        String canonical = UrlCanonicalizer.canonicalize(item.url());
        PromptItemMeta meta = inputMeta.get(canonical);
        if (meta == null) {
            return null;
        }
        return new DigestItem(item.title(), canonical, meta.source(), item.category(), item.type(),
                item.score(), meta.engagementScore(), item.summary(), item.whyItMatters(), item.topicKey());
    }

    private Builder toBuilder() {
        return new Builder(this);
    }

    /**
     * Mutable intermediate for the withers: the only place that still spells out the canonical
     * constructor's seventeen arguments in order.
     *
     * <p>{@code build()} goes through that constructor rather than assigning the record's fields, so
     * the compact constructor's {@code List.copyOf} and null-to-empty normalisation runs on every
     * copy and no mutable collection reaches a component. The class is private and so is
     * {@code toBuilder()}: the record's public API is seventeen components and the withers, and a
     * mutable intermediate never escapes to a caller.
     *
     * <p>Setters exist for the components a wither replaces. {@code emailPreview} and
     * {@code topInsights} come from the synthesis response and are never rewritten afterwards, so
     * they are copied but have no setter — a wither for either one adds its setter here.
     */
    private static final class Builder {

        private String emailPreview;
        private String editorial;
        private List<String> topInsights;
        private List<DigestItem> items;
        private List<Signal> signals;
        private WeeklyRecap weeklyRecap;
        private RadarAccuracy radarAccuracy;
        private ReaderProfile readerProfile;
        private List<DependencyRelease> dependencyReleases;
        private Set<String> stackAffectingUrls;
        private Calibration calibration;
        private List<EolNotice> eolNotices;
        private SavedBacklog savedBacklog;
        private List<SourceIntake> silentSources;
        private ReadingBudget readingBudget;
        private List<CallForPapers> callsForPapers;
        private EditionProvenance provenance;

        private Builder(ReportData source) {
            this.emailPreview = source.emailPreview();
            this.editorial = source.editorial();
            this.topInsights = source.topInsights();
            this.items = source.items();
            this.signals = source.signals();
            this.weeklyRecap = source.weeklyRecap();
            this.radarAccuracy = source.radarAccuracy();
            this.readerProfile = source.readerProfile();
            this.dependencyReleases = source.dependencyReleases();
            this.stackAffectingUrls = source.stackAffectingUrls();
            this.calibration = source.calibration();
            this.eolNotices = source.eolNotices();
            this.savedBacklog = source.savedBacklog();
            this.silentSources = source.silentSources();
            this.readingBudget = source.readingBudget();
            this.callsForPapers = source.callsForPapers();
            this.provenance = source.provenance();
        }

        private ReportData build() {
            return new ReportData(emailPreview, editorial, topInsights, items, signals, weeklyRecap,
                    radarAccuracy, readerProfile, dependencyReleases, stackAffectingUrls, calibration,
                    eolNotices, savedBacklog, silentSources, readingBudget, callsForPapers, provenance);
        }

        private Builder editorial(String value) {
            this.editorial = value;
            return this;
        }

        private Builder items(List<DigestItem> value) {
            this.items = value;
            return this;
        }

        private Builder signals(List<Signal> value) {
            this.signals = value;
            return this;
        }

        private Builder weeklyRecap(WeeklyRecap value) {
            this.weeklyRecap = value;
            return this;
        }

        private Builder radarAccuracy(RadarAccuracy value) {
            this.radarAccuracy = value;
            return this;
        }

        private Builder readerProfile(ReaderProfile value) {
            this.readerProfile = value;
            return this;
        }

        private Builder dependencyReleases(List<DependencyRelease> value) {
            this.dependencyReleases = value;
            return this;
        }

        private Builder stackAffectingUrls(Set<String> value) {
            this.stackAffectingUrls = value;
            return this;
        }

        private Builder calibration(Calibration value) {
            this.calibration = value;
            return this;
        }

        private Builder eolNotices(List<EolNotice> value) {
            this.eolNotices = value;
            return this;
        }

        private Builder savedBacklog(SavedBacklog value) {
            this.savedBacklog = value;
            return this;
        }

        private Builder silentSources(List<SourceIntake> value) {
            this.silentSources = value;
            return this;
        }

        private Builder readingBudget(ReadingBudget value) {
            this.readingBudget = value;
            return this;
        }

        private Builder callsForPapers(List<CallForPapers> value) {
            this.callsForPapers = value;
            return this;
        }

        private Builder provenance(EditionProvenance value) {
            this.provenance = value;
            return this;
        }
    }
}
