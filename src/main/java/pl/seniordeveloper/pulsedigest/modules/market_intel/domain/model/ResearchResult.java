package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import java.time.LocalDateTime;
import java.util.List;

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
        List<NvdVulnerability> nvdVulnerabilities,
        List<PackageTrend> packageTrends,
        List<JepUpdate> jepUpdates,
        List<CncfProjectUpdate> cncfProjectUpdates,
        List<RadarEntry> radarEntries,
        List<ConferenceTalk> conferenceTalks,
        List<DbEngineRanking> dbEngineRankings,
        List<LabAnnouncement> labAnnouncements,
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
            List<NvdVulnerability> nvdVulnerabilities,
            List<PackageTrend> packageTrends,
            List<JepUpdate> jepUpdates,
            List<CncfProjectUpdate> cncfProjectUpdates,
            List<RadarEntry> radarEntries,
            List<ConferenceTalk> conferenceTalks,
            List<DbEngineRanking> dbEngineRankings,
            LocalDateTime collectedAt,
            int rawTweetsCount,
            int rawHackerNewsCount,
            int rawGithubCount,
            int rawRssCount,
            int rawRedditCount
    ) {
        this(tweets, hackerNewsPosts, githubRepos, rssItems, redditPosts,
                papers, releases, huggingFaceModels, productHuntPosts, securityAdvisories,
                nvdVulnerabilities, packageTrends, jepUpdates, cncfProjectUpdates,
                radarEntries, conferenceTalks, dbEngineRankings, List.of(), collectedAt,
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
            List<NvdVulnerability> nvdVulnerabilities,
            List<PackageTrend> packageTrends,
            List<JepUpdate> jepUpdates,
            List<CncfProjectUpdate> cncfProjectUpdates,
            List<RadarEntry> radarEntries,
            List<ConferenceTalk> conferenceTalks,
            List<DbEngineRanking> dbEngineRankings,
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
                nvdVulnerabilities, packageTrends, jepUpdates, cncfProjectUpdates,
                radarEntries, conferenceTalks, dbEngineRankings, List.of(), collectedAt,
                rawTweetsCount, rawHackerNewsCount, rawGithubCount, rawRssCount, rawRedditCount,
                sourceFetchReports, null);
    }

    /** Copy of this result with the tech-demand pulse attached (other fields unchanged). */
    public ResearchResult withTechDemand(TechDemandSignal newTechDemand) {
        return new ResearchResult(
                tweets, hackerNewsPosts, githubRepos, rssItems, redditPosts,
                papers, releases, huggingFaceModels, productHuntPosts, securityAdvisories,
                nvdVulnerabilities, packageTrends, jepUpdates, cncfProjectUpdates,
                radarEntries, conferenceTalks, dbEngineRankings, labAnnouncements, collectedAt,
                rawTweetsCount, rawHackerNewsCount, rawGithubCount, rawRssCount, rawRedditCount,
                sourceFetchReports, newTechDemand);
    }

    public boolean isEmpty() {
        return tweets.isEmpty() && hackerNewsPosts.isEmpty() && githubRepos.isEmpty()
                && rssItems.isEmpty() && redditPosts.isEmpty()
                && papers.isEmpty() && releases.isEmpty()
                && huggingFaceModels.isEmpty() && productHuntPosts.isEmpty()
                && securityAdvisories.isEmpty() && nvdVulnerabilities.isEmpty()
                && packageTrends.isEmpty() && jepUpdates.isEmpty()
                && cncfProjectUpdates.isEmpty() && radarEntries.isEmpty()
                && conferenceTalks.isEmpty() && dbEngineRankings.isEmpty()
                && labAnnouncements.isEmpty();
    }

    public int rawTotalCount() {
        return rawTweetsCount + rawHackerNewsCount + rawGithubCount
                + rawRssCount + rawRedditCount + papers.size() + releases.size()
                + huggingFaceModels.size() + productHuntPosts.size() + securityAdvisories.size()
                + nvdVulnerabilities.size() + packageTrends.size() + jepUpdates.size()
                + cncfProjectUpdates.size() + radarEntries.size() + conferenceTalks.size()
                + dbEngineRankings.size() + labAnnouncements.size();
    }

    public int activeSourceCount() {
        int n = 0;
        if (!tweets.isEmpty()) {
            n++;
        }
        if (!hackerNewsPosts.isEmpty()) {
            n++;
        }
        if (!githubRepos.isEmpty()) {
            n++;
        }
        if (!rssItems.isEmpty()) {
            n++;
        }
        if (!redditPosts.isEmpty()) {
            n++;
        }
        if (!papers.isEmpty()) {
            n++;
        }
        if (!releases.isEmpty()) {
            n++;
        }
        if (!huggingFaceModels.isEmpty()) {
            n++;
        }
        if (!productHuntPosts.isEmpty()) {
            n++;
        }
        if (!securityAdvisories.isEmpty()) {
            n++;
        }
        if (!nvdVulnerabilities.isEmpty()) {
            n++;
        }
        if (!packageTrends.isEmpty()) {
            n++;
        }
        if (!jepUpdates.isEmpty()) {
            n++;
        }
        if (!cncfProjectUpdates.isEmpty()) {
            n++;
        }
        if (!radarEntries.isEmpty()) {
            n++;
        }
        if (!conferenceTalks.isEmpty()) {
            n++;
        }
        if (!dbEngineRankings.isEmpty()) {
            n++;
        }
        if (!labAnnouncements.isEmpty()) {
            n++;
        }
        return n;
    }

    public String summary() {
        return ("Zebrano: %d tweetów, %d HN, %d GH, %d RSS, %d Reddit, %d papers, %d releases,"
                + " %d HF models, %d ProductHunt, %d security advisories,"
                + " %d NVD, %d Libraries.io, %d JEP, %d CNCF, %d Radar, %d Talks, %d DB-Engines,"
                + " %d Lab announcements"
                + " — łącznie %d itemów, o %s")
                .formatted(rawTweetsCount, rawHackerNewsCount, rawGithubCount,
                        rawRssCount, rawRedditCount, papers.size(), releases.size(),
                        huggingFaceModels.size(), productHuntPosts.size(), securityAdvisories.size(),
                        nvdVulnerabilities.size(), packageTrends.size(), jepUpdates.size(),
                        cncfProjectUpdates.size(), radarEntries.size(), conferenceTalks.size(),
                        dbEngineRankings.size(), labAnnouncements.size(),
                        rawTotalCount(),
                        collectedAt);
    }
}
