package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

/**
 * Aggregate of all raw data collected from external intelligence sources.
 */
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
        int rawTweetsCount,
        int rawHackerNewsCount,
        int rawGithubCount,
        int rawRssCount,
        int rawRedditCount,
        List<SourceFetchReport> sourceFetchReports,
        TechDemandSignal techDemand
) {

    public ResearchResult {
        sourceFetchReports = sourceFetchReports != null ? List.copyOf(sourceFetchReports) : List.of();
        labAnnouncements = labAnnouncements != null ? List.copyOf(labAnnouncements) : List.of();
        socialPosts = socialPosts != null ? List.copyOf(socialPosts) : List.of();
    }

    public ResearchResult(
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
            LocalDateTime collectedAt,
            int rawTweetsCount,
            int rawHackerNewsCount,
            int rawGithubCount,
            int rawRssCount,
            int rawRedditCount
    ) {
        this(tweets, hackerNewsPosts, githubRepos, rssItems, redditPosts,
                papers, releases, huggingFaceModels, productHuntPosts, securityAdvisories,
                jepUpdates, cncfProjectUpdates, radarEntries, conferenceTalks, List.of(), List.of(), collectedAt,
                rawTweetsCount, rawHackerNewsCount, rawGithubCount, rawRssCount, rawRedditCount, List.of(), null);
    }

    public ResearchResult(
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
            LocalDateTime collectedAt,
            int rawTweetsCount,
            int rawHackerNewsCount,
            int rawGithubCount,
            int rawRssCount,
            int rawRedditCount,
            List<SourceFetchReport> sourceFetchReports
    ) {
        this(tweets, hackerNewsPosts, githubRepos, rssItems, redditPosts,
                papers, releases, huggingFaceModels, productHuntPosts, securityAdvisories,
                jepUpdates, cncfProjectUpdates, radarEntries, conferenceTalks, List.of(), List.of(), collectedAt,
                rawTweetsCount, rawHackerNewsCount, rawGithubCount, rawRssCount, rawRedditCount,
                sourceFetchReports, null);
    }

    /** Copy of this result with the tech-demand pulse attached (other fields unchanged). */
    public ResearchResult withTechDemand(TechDemandSignal newTechDemand) {
        return new ResearchResult(
                tweets, hackerNewsPosts, githubRepos, rssItems, redditPosts,
                papers, releases, huggingFaceModels, productHuntPosts, securityAdvisories,
                jepUpdates, cncfProjectUpdates, radarEntries, conferenceTalks, labAnnouncements, socialPosts,
                collectedAt, rawTweetsCount, rawHackerNewsCount, rawGithubCount, rawRssCount, rawRedditCount,
                sourceFetchReports, newTechDemand);
    }

    public boolean isEmpty() {
        return tweets.isEmpty() && hackerNewsPosts.isEmpty() && githubRepos.isEmpty()
                && rssItems.isEmpty() && redditPosts.isEmpty()
                && papers.isEmpty() && releases.isEmpty()
                && huggingFaceModels.isEmpty() && productHuntPosts.isEmpty()
                && securityAdvisories.isEmpty() && jepUpdates.isEmpty()
                && cncfProjectUpdates.isEmpty() && radarEntries.isEmpty()
                && conferenceTalks.isEmpty()
                && labAnnouncements.isEmpty()
                && socialPosts.isEmpty();
    }

    public int rawTotalCount() {
        return rawTweetsCount + rawHackerNewsCount + rawGithubCount
                + rawRssCount + rawRedditCount + papers.size() + releases.size()
                + huggingFaceModels.size() + productHuntPosts.size() + securityAdvisories.size()
                + jepUpdates.size()
                + cncfProjectUpdates.size() + radarEntries.size() + conferenceTalks.size()
                + labAnnouncements.size() + socialPosts.size();
    }

    public int activeSourceCount() {
        return (int) Stream.of(
                        tweets, hackerNewsPosts, githubRepos, rssItems, redditPosts, papers, releases,
                        huggingFaceModels, productHuntPosts, securityAdvisories, jepUpdates,
                        cncfProjectUpdates, radarEntries, conferenceTalks, labAnnouncements, socialPosts)
                .filter(list -> !list.isEmpty())
                .count();
    }

    public String summary() {
        return ("Zebrano: %d tweetów, %d HN, %d GH, %d RSS, %d Reddit, %d papers, %d releases,"
                + " %d HF models, %d ProductHunt, %d security advisories,"
                + " %d JEP, %d CNCF, %d Radar, %d Talks,"
                + " %d Lab announcements, %d Social"
                + " — łącznie %d itemów, o %s")
                .formatted(rawTweetsCount, rawHackerNewsCount, rawGithubCount,
                        rawRssCount, rawRedditCount, papers.size(), releases.size(),
                        huggingFaceModels.size(), productHuntPosts.size(), securityAdvisories.size(),
                        jepUpdates.size(),
                        cncfProjectUpdates.size(), radarEntries.size(), conferenceTalks.size(),
                        labAnnouncements.size(), socialPosts.size(),
                        rawTotalCount(),
                        collectedAt);
    }
}
