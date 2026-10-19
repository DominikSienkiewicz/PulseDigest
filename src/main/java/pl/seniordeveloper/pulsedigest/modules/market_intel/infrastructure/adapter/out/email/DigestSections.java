package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

/**
 * The section keys {@code report.sections.disabled} accepts (N19).
 *
 * <p>Constants rather than free strings so a typo in configuration fails a test rather than silently
 * disabling nothing — the worst outcome for a switch whose whole purpose is finding out whether a
 * section is missed.
 */
public final class DigestSections {

    static final String CRITICAL_TRENDS = "critical-trends";
    static final String MUST_KNOW = "must-know";
    static final String INSIGHTS = "insights";
    static final String DEALS = "deals";
    static final String WEEKLY_RECAP = "weekly-recap";
    static final String WATCHLIST = "watchlist";
    static final String DEPENDENCY_RADAR = "dependency-radar";
    static final String EOL = "eol";
    static final String CALL_FOR_PAPERS = "call-for-papers";
    static final String SAVED = "saved";
    static final String TABLES = "tables";
    static final String READING_BUDGET = "reading-budget";
    static final String SCORE_EXPLANATIONS = "score-explanations";
    /**
     * The footer's "selected N of M items · K sources" line. A toggle because the archive renders
     * editions read back from storage, where the research that produced those numbers was never
     * kept — and a footer confidently reporting "0 źródeł" is worse than one that omits the line.
     */
    public static final String FOOTER_INTAKE = "footer-intake";

    /** Every key, for the test that keeps configuration from naming a section that does not exist. */
    static final java.util.Set<String> ALL = java.util.Set.of(
            CRITICAL_TRENDS, MUST_KNOW, INSIGHTS, DEALS, WEEKLY_RECAP, WATCHLIST,
            DEPENDENCY_RADAR, EOL, CALL_FOR_PAPERS, SAVED, TABLES, READING_BUDGET,
            SCORE_EXPLANATIONS, FOOTER_INTAKE);

    private DigestSections() {
    }
}
