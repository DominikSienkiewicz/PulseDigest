package pl.seniordeveloper.pulsedigest.testing;

import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.CncfProjectUpdate;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ConferenceTalk;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.GithubRepo;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.HackerNewsPost;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.HuggingFaceModel;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.JepUpdate;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.LabAnnouncement;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ProductHuntPost;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RadarEntry;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RawCounts;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RedditPost;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchPaper;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchResult;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RssItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SecurityAdvisory;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SocialPost;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SoftwareRelease;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceFetchReport;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Tweet;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Builds a {@link ResearchResult} for a test without naming the sixteen it does not care about.
 *
 * <p>Replaces the two convenience constructors the F15 cull removed, and the call sites that used
 * them: a test that wanted one Hacker News post used to spell out fourteen {@code List.of()} and
 * five bare integers, which is unreadable and — being twenty-one positional arguments, five of them
 * the same type — was also the exact hazard N48 names.
 */
public final class ResearchResults {

    private ResearchResults() {
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Nothing fetched from anywhere. */
    public static ResearchResult empty() {
        return builder().build();
    }

    public static final class Builder {

        private List<Tweet> tweets = List.of();
        private List<HackerNewsPost> hackerNews = List.of();
        private List<GithubRepo> github = List.of();
        private List<RssItem> rss = List.of();
        private List<RedditPost> reddit = List.of();
        private List<ResearchPaper> papers = List.of();
        private List<SoftwareRelease> releases = List.of();
        private List<HuggingFaceModel> huggingFace = List.of();
        private List<ProductHuntPost> productHunt = List.of();
        private List<SecurityAdvisory> advisories = List.of();
        private List<JepUpdate> jep = List.of();
        private List<CncfProjectUpdate> cncf = List.of();
        private List<RadarEntry> radar = List.of();
        private List<ConferenceTalk> talks = List.of();
        private List<LabAnnouncement> labAnnouncements = List.of();
        private List<SocialPost> social = List.of();
        private LocalDateTime collectedAt = LocalDateTime.parse("2026-05-14T10:00:00");
        private RawCounts rawCounts = RawCounts.NONE;
        private List<SourceFetchReport> fetchReports = List.of();

        private Builder() {
        }

        public Builder tweets(List<Tweet> value) {
            this.tweets = value;
            return this;
        }

        public Builder hackerNews(List<HackerNewsPost> value) {
            this.hackerNews = value;
            return this;
        }

        public Builder github(List<GithubRepo> value) {
            this.github = value;
            return this;
        }

        public Builder rss(List<RssItem> value) {
            this.rss = value;
            return this;
        }

        public Builder reddit(List<RedditPost> value) {
            this.reddit = value;
            return this;
        }

        public Builder papers(List<ResearchPaper> value) {
            this.papers = value;
            return this;
        }

        public Builder releases(List<SoftwareRelease> value) {
            this.releases = value;
            return this;
        }

        public Builder huggingFace(List<HuggingFaceModel> value) {
            this.huggingFace = value;
            return this;
        }

        public Builder productHunt(List<ProductHuntPost> value) {
            this.productHunt = value;
            return this;
        }

        public Builder advisories(List<SecurityAdvisory> value) {
            this.advisories = value;
            return this;
        }

        public Builder jep(List<JepUpdate> value) {
            this.jep = value;
            return this;
        }

        public Builder cncf(List<CncfProjectUpdate> value) {
            this.cncf = value;
            return this;
        }

        public Builder radar(List<RadarEntry> value) {
            this.radar = value;
            return this;
        }

        public Builder talks(List<ConferenceTalk> value) {
            this.talks = value;
            return this;
        }

        public Builder labAnnouncements(List<LabAnnouncement> value) {
            this.labAnnouncements = value;
            return this;
        }

        public Builder social(List<SocialPost> value) {
            this.social = value;
            return this;
        }

        public Builder collectedAt(LocalDateTime value) {
            this.collectedAt = value;
            return this;
        }

        public Builder rawCounts(RawCounts value) {
            this.rawCounts = value;
            return this;
        }

        public Builder fetchReports(List<SourceFetchReport> value) {
            this.fetchReports = value;
            return this;
        }

        public ResearchResult build() {
            return new ResearchResult(tweets, hackerNews, github, rss, reddit, papers, releases,
                    huggingFace, productHunt, advisories, jep, cncf, radar, talks, labAnnouncements,
                    social, collectedAt, rawCounts, fetchReports);
        }
    }
}
