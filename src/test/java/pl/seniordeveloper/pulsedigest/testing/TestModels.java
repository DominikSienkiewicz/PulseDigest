package pl.seniordeveloper.pulsedigest.testing;

import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PastEdition;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PersistedReport;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportData;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ScoreBreakdown;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SecurityAdvisory;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Signal;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SignalRank;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceDomain;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.TrendRecurrence;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.TrendVelocity;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The short forms tests build domain objects with (F15 / K4).
 *
 * <p>These were convenience constructors on the records themselves. Eleven of the thirteen had no
 * production caller at all, which is the problem the audit named: they widened the API every
 * production class sees in order to spare test code a few arguments, and in doing so let tests
 * assert on shapes the pipeline cannot actually produce — a {@code Signal} with no breakdown, a
 * {@code ResearchResult} with no fetch reports — so a test could pass against an object no run will
 * ever build.
 *
 * <p>Moving them here keeps tests exactly as short as they were and takes the production API back to
 * the shapes production uses. The two that survived the cull did so because they have a production
 * caller; one more was replaced by a wither, which turned out to be fixing a real defect, since the
 * constructor it replaced silently dropped every component it did not name.
 *
 * <p>Every factory here fills the omitted components with the same values the deleted constructors
 * did, so no existing assertion changes meaning.
 */
public final class TestModels {

    private TestModels() {
    }

    /** A story item without a topic key — legacy editions and most fixtures. */
    public static DigestItem digestItem(String title, String url, String source, String category,
                                        String type, int score, Integer engagementScore,
                                        String summary, String whyItMatters) {
        return new DigestItem(title, url, source, category, type, score, engagementScore,
                summary, whyItMatters, null);
    }

    /** An edition stored before per-source fetch reports were read back, or one without them. */
    public static PastEdition pastEdition(Instant generatedAt, List<Signal> signals) {
        return new PastEdition(generatedAt, signals, List.of());
    }

    /** An edition stored before per-source fetch reports were persisted alongside it. */
    public static PersistedReport persistedReport(ReportData report, String jobId, Instant generatedAt,
                                                  int tweetCount, int hnCount, int githubCount) {
        return new PersistedReport(report, jobId, generatedAt, tweetCount, hnCount, githubCount, List.of());
    }

    /** The report as the synthesis model returns it, plus scored signals. */
    public static ReportData reportData(String emailPreview, String editorial, List<String> topInsights,
                                        List<DigestItem> items, List<Signal> signals) {
        return new ReportData(emailPreview, editorial, topInsights, items).withSignals(signals);
    }

    /** A breakdown from before the mute multiplier existed. */
    public static ScoreBreakdown scoreBreakdown(String sourceKey, double baseWeight, double effectiveWeight,
                                                int netSourceVotes, int engagementBonus,
                                                double categoryMultiplier, int netCategoryVotes,
                                                int crossSourceBonus) {
        return new ScoreBreakdown(sourceKey, baseWeight, effectiveWeight, netSourceVotes, engagementBonus,
                categoryMultiplier, netCategoryVotes, crossSourceBonus, 1.0);
    }

    /** An advisory without matched package coordinates. */
    public static SecurityAdvisory securityAdvisory(String ghsaId, String summary, String severity,
                                                    LocalDateTime publishedAt,
                                                    List<String> affectedEcosystems, String url) {
        return new SecurityAdvisory(ghsaId, summary, severity, publishedAt, affectedEcosystems, url, List.of());
    }

    /** A signal with neither recurrence, velocity nor breakdown. */
    public static Signal signal(DigestItem item, SignalRank rank, int signalScore,
                                List<SourceDomain> sourceDomains) {
        return new Signal(item, rank, signalScore, sourceDomains, null, null, null);
    }

    /** A signal carrying recurrence but no velocity. */
    public static Signal signal(DigestItem item, SignalRank rank, int signalScore,
                                List<SourceDomain> sourceDomains, TrendRecurrence recurrence) {
        return new Signal(item, rank, signalScore, sourceDomains, recurrence, null, null);
    }

    /** A signal carrying recurrence and velocity but no breakdown. */
    public static Signal signal(DigestItem item, SignalRank rank, int signalScore,
                                List<SourceDomain> sourceDomains, TrendRecurrence recurrence,
                                TrendVelocity velocity) {
        return new Signal(item, rank, signalScore, sourceDomains, recurrence, velocity, null);
    }
}
