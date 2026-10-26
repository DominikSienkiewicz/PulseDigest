package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ApiAccounts;
import lombok.extern.slf4j.Slf4j;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RadarAccuracy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReaderProfile;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportData;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchResult;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Signal;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceFetchReport;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceFetchStatus;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.SectionsPolicy;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.FeedbackProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ThinWeekProperties;

import java.time.ZoneOffset;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email.EmailFormatting.escapeHtml;
import static pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email.EmailFormatting.safeHref;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReportEmailBuilder {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.of("pl", "PL"));

    private static final int SUBJECT_PREVIEW_BUDGET = 70;

    /**
     * Dark-mode handling (N15).
     *
     * <p>Most mail clients default to dark now, and a client that finds a light document with no
     * opinion about colour schemes will <em>invert it itself</em> — machine-inverted `#f9fafb`
     * sections with dark text come out as something nobody designed and several things nobody can
     * read. Declaring {@code color-scheme} is what stops the automatic inversion; the media query is
     * what replaces it with a deliberate palette.
     *
     * <p>One class on the card, not a class per section, and the overrides are therefore blunt: in
     * dark mode every panel collapses to one background. The section tinting that carries meaning in
     * light mode survives as the coloured {@code border-left} on each panel, which these rules do not
     * touch — and since N16 the rank is written out as a word anyway, so nothing here was ever
     * carried by the background alone.
     *
     * <p>{@code !important} because the document is styled inline, which is the only thing mail
     * clients reliably honour; a cascade without it would lose to every one of those attributes.
     *
     * <p>The three attribute selectors map the light palette's muted tiers onto dark ones rather
     * than flattening everything to one foreground. Without them the first version of this block
     * left every summary and metadata line at its light-mode grey on a dark panel — a muddy
     * red-brown at roughly 3.7:1, which is the exact unreadable result the feature exists to
     * prevent. It was visible in the rendered sample and in nothing else, which is why N15's
     * "done when" asks for a screenshot rather than a passing test.
     */
    private static final String DARK_MODE_CSS = "<style>"
            + ":root{color-scheme:light dark;supported-color-schemes:light dark}"
            + "@media (prefers-color-scheme:dark){"
            + "body{background:#0b1220 !important}"
            + ".pd-card,.pd-card div,.pd-card td,.pd-card th,.pd-card table{"
            + "background-color:#111827 !important;background-image:none !important}"
            + ".pd-card,.pd-card p,.pd-card li,.pd-card div,.pd-card span,.pd-card h1,.pd-card h2,"
            + ".pd-card strong,.pd-card td,.pd-card th,.pd-card code{color:#e5e7eb !important}"
            + ".pd-card [style*=\"color:#4b5563\"]{color:#cbd5e1 !important}"
            + ".pd-card [style*=\"color:#6b7280\"]{color:#9ca3af !important}"
            + ".pd-card [style*=\"color:#64748b\"]{color:#94a3b8 !important}"
            + ".pd-card a{color:#93c5fd !important}"
            + ".pd-card th{border-bottom-color:#374151 !important}"
            + ".pd-card td{border-bottom-color:#1f2937 !important}"
            + "}</style>";

    private static final String CLOSE_DIV = "</div>";
    private static final String CLOSE_LIST_DIV = "</ul></div>";
    private static final String CLOSE_LI = "</li>";
    private static final String MIDDOT = " &middot; ";

    private final FeedbackProperties feedbackProperties;
    private final ThinWeekProperties thinWeekProperties;
    private final SectionsPolicy sectionsPolicy;

    /**
     * Subject line for one edition. The reader should be able to decide "now or tonight?" from the
     * inbox list alone, so the subject leads with a marker earned by the report's own signals and
     * then reuses {@code email_preview} — text the model already writes for the preheader.
     *
     * <p>Every rule here is deterministic: no model call, no clickbait, and the same report always
     * yields the same subject. A {@code null} report, or one whose preview is blank, degrades to the
     * plain dated subject rather than shipping an empty marker line.
     */
    public String buildSubject(ReportData report) {
        String today = LocalDate.now(ZoneOffset.UTC).format(DATE_FMT);
        if (isThinWeek(report)) {
            return "📡 PulseDigest — cicho w tym tygodniu · " + today;
        }
        if (report == null || report.emailPreview() == null || report.emailPreview().isBlank()) {
            return "📡 PulseDigest " + today;
        }
        return subjectMarker(report) + " " + truncateOnWordBoundary(report.emailPreview().strip())
                + " · " + today;
    }

    private static String subjectMarker(ReportData report) {
        List<Signal> signals = report.signals() != null ? report.signals() : List.of();
        if (signals.stream().anyMatch(Signal::isCriticalTrend)) {
            return "🔴";
        }
        List<DigestItem> items = report.items() != null ? report.items() : List.of();
        if (items.stream().anyMatch(i -> i.score() >= DigestLayout.MUST_KNOW_THRESHOLD)) {
            return "⚡";
        }
        return "📡";
    }

    // Gmail and Apple Mail cut the subject around 70–80 chars; cutting mid-word reads as a bug, so
    // we back off to the last space before the budget and mark the elision.
    private static String truncateOnWordBoundary(String preview) {
        if (preview.length() <= SUBJECT_PREVIEW_BUDGET) {
            return preview;
        }
        String clipped = preview.substring(0, SUBJECT_PREVIEW_BUDGET);
        int lastSpace = clipped.lastIndexOf(' ');
        if (lastSpace > 0) {
            clipped = clipped.substring(0, lastSpace);
        }
        return stripTrailingSeparators(clipped) + "…";
    }

    // A `$`-anchored regex is retried from every index, so trimming the tail with one costs O(n²)
    // on a separator-heavy string. Walking backwards once says the same thing in linear time.
    private static String stripTrailingSeparators(String text) {
        int end = text.length();
        while (end > 0 && isSubjectSeparator(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end);
    }

    private static boolean isSubjectSeparator(char character) {
        return Character.isWhitespace(character) || character == ',' || character == ';' || character == '·';
    }

    public String buildHtml(ReportData report, ResearchResult research) {
        String today = LocalDate.now(ZoneOffset.UTC).format(DATE_FMT);
        // ISO date identifies the edition in every feedback link, so the receiver can enforce one
        // vote per item per edition — a scanner prefetching the same link cannot amplify it.
        String edition = LocalDate.now(ZoneOffset.UTC).toString();
        String preheader = report.emailPreview() != null && !report.emailPreview().isBlank()
                ? report.emailPreview()
                : "Twój digest tech news z ostatnich kilku dni";
        List<String> insights = report.topInsights() != null ? report.topInsights() : List.of();
        List<DigestItem> items = report.items() != null ? report.items() : List.of();
        List<Signal> signals = report.signals() != null ? report.signals() : List.of();
        String editorial = report.editorial();

        // Membership is decided once, here, so no story is printed in two sections.
        DigestLayout layout = DigestLayout.of(items, signals, report.stackAffectingUrls());

        int aboveTheBar = layout.storiesAboveTheBar();
        boolean thin = thinWeekProperties.enabled() && aboveTheBar < thinWeekProperties.minItems();
        // Logged on every run, enabled or not: the threshold is a calibration and this is the only
        // evidence that accrues for it.
        log.info("thin-week above_the_bar={} min_items={} enabled={} applies={}",
                aboveTheBar, thinWeekProperties.minItems(), thinWeekProperties.enabled(), thin);
        if (thin) {
            return buildThinWeekHtml(today, preheader, layout, items);
        }

        List<SourceFetchReport> exhausted = research != null
                ? research.sourceFetchReports().stream().filter(SourceFetchReport::isQuotaExhausted).toList()
                : List.of();

        return "<!DOCTYPE html>"
                + "<html lang=\"pl\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + DARK_MODE_CSS
                + "<title>PulseDigest " + today + "</title></head>"
                + "<body style=\"font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',"
                + "sans-serif;background:#f9fafb;margin:0;padding:20px\">"
                + buildPreheader(preheader)
                + "<div class=\"pd-card\" style=\"max-width:720px;margin:0 auto;background:#fff;"
                + "border-radius:12px;overflow:hidden;box-shadow:0 1px 3px rgba(0,0,0,.1)\">"
                + buildHeader(today)
                + on(DigestSections.READING_BUDGET, () -> buildReadingBudget(report.readingBudget()))
                + buildQuotaBanner(exhausted)
                + buildEditorialSection(editorial)
                + on(DigestSections.CRITICAL_TRENDS, () -> buildCriticalTrendsSection(layout.criticalTrends()))
                + on(DigestSections.MUST_KNOW, () -> DigestHighlightBuilder.buildMustKnowSection(
                        layout.mustKnow(), feedbackProperties, edition, explainsScores()))
                + on(DigestSections.INSIGHTS, () -> buildInsightsSection(insights))
                + on(DigestSections.DEALS, () -> DigestHighlightBuilder.buildDealsAndToolsSection(
                        layout.deals(), feedbackProperties, edition))
                + on(DigestSections.WEEKLY_RECAP, () -> WeeklyRecapBuilder.buildWeeklyRecapSection(
                        report.weeklyRecap(), layout.renderedUrls()))
                + on(DigestSections.DEPENDENCY_RADAR, () -> DependencyRadarBuilder
                        .buildDependencyRadarSection(report.dependencyReleases()))
                + on(DigestSections.EOL, () -> EolRadarBuilder.buildEolRadarSection(
                        report.eolNotices(), LocalDate.now(ZoneOffset.UTC)))
                + on(DigestSections.CALL_FOR_PAPERS, () -> CallForPapersBuilder
                        .buildCallForPapersSection(report.callsForPapers(), LocalDate.now(ZoneOffset.UTC)))
                + on(DigestSections.SAVED, () -> SavedBacklogBuilder.buildSavedBacklogSection(
                        report.savedBacklog(), LocalDate.now(ZoneOffset.UTC)))
                + on(DigestSections.TABLES, () -> DigestTableBuilder.buildItemsSection(
                        layout.topPicks(), layout.signals(), feedbackProperties, edition,
                        explainsScores()))
                + buildFooter(items.size(), research, report.radarAccuracy(), report.readerProfile(), signals,
                        report.calibration(), report.silentSources())
                + "</div></body></html>";
    }

    /**
     * Whether this report would be sent as a thin week. Shares the one threshold with
     * {@link #buildHtml}: a subject promising a digest over a body that says nothing happened would
     * be the padding this feature exists to remove.
     */
    private boolean isThinWeek(ReportData report) {
        if (report == null || !thinWeekProperties.enabled()) {
            return false;
        }
        List<DigestItem> items = report.items() != null ? report.items() : List.of();
        List<Signal> signals = report.signals() != null ? report.signals() : List.of();
        return DigestLayout.of(items, signals).storiesAboveTheBar() < thinWeekProperties.minItems();
    }

    /**
     * Three lines instead of a document: what happened, and the one thing that came closest.
     *
     * <p>No tables, no recap, no score explanations. An edition with nothing above the
     * bar has nothing those sections would be about, and rendering them anyway is exactly the padding
     * the thesis forbids. The runner-up carries a feedback link, because "this was close" is the one
     * judgement a reader can usefully correct.
     */
    private String buildThinWeekHtml(String today, String preheader, DigestLayout layout,
                                     List<DigestItem> items) {
        String edition = LocalDate.now(ZoneOffset.UTC).toString();
        DigestItem closest = items.stream()
                .max(Comparator.comparingInt(DigestItem::score))
                .orElse(null);

        String runnerUp = closest == null
                ? "<p style=\"color:#6b7280;font-size:14px;margin:0\">"
                + "Żadne źródło nie zwróciło nic, co dałoby się zmierzyć.</p>"
                : "<p style=\"color:#374151;font-size:15px;margin:0\">&rarr; "
                + "<a href=\"" + safeHref(closest.url()) + "\" style=\"color:#1d4ed8;"
                + "font-weight:600;text-decoration:none\">" + escapeHtml(closest.title()) + "</a>"
                + FeedbackLinkBuilder.render(closest.url(), closest.source(), closest.category(),
                        edition, feedbackProperties)
                + "<span style=\"color:#6b7280;font-size:12px\"> &middot; "
                + escapeHtml(closest.source()) + "</span></p>";

        return "<!DOCTYPE html>"
                + "<html lang=\"pl\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + DARK_MODE_CSS
                + "<title>PulseDigest " + today + "</title></head>"
                + "<body style=\"margin:0;padding:24px;background:#f1f5f9;"
                + "font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif\">"
                + buildPreheader(preheader)
                + "<div class=\"pd-card\" style=\"max-width:720px;margin:0 auto;background:#fff;"
                + "border-radius:12px;overflow:hidden;box-shadow:0 1px 3px rgba(0,0,0,.1)\">"
                + buildHeader(today)
                + "<div style=\"padding:28px\">"
                + "<h2 style=\"color:#1e293b;font-size:17px;margin:0 0 10px\">"
                + "Cicho w tym tygodniu</h2>"
                + "<p style=\"color:#6b7280;font-size:14px;margin:0 0 14px;line-height:1.6\">"
                + "Nic nie zasługuje na Twoją uwagę &mdash; " + layout.storiesAboveTheBar()
                + " z " + items.size() + " pozycji przeszło próg. Najbliżej było:</p>"
                + runnerUp
                + "</div></div></body></html>";
    }

    // Sections

    private String buildPreheader(String preview) {
        return "<div style=\"display:none!important;max-height:0;overflow:hidden;"
                + "mso-hide:all;font-size:1px;line-height:1px;opacity:0;color:transparent\">"
                + escapeHtml(preview)
                + CLOSE_DIV;
    }

    /**
     * What this edition asks for, before the reader has scrolled anything (N04).
     *
     * <p>Worded as an estimate because it is one: "około" and a figure rounded to five minutes. A
     * precise-looking total would be believed, and this is derived from item types and summary
     * lengths rather than from the articles themselves.
     */
    private String buildReadingBudget(
            pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReadingBudget budget) {
        if (budget == null || !budget.isReportable()) {
            return "";
        }
        return "<div style=\"padding:10px 28px;background:#f1f5f9;border-bottom:1px solid #e2e8f0;"
                + "color:#475569;font-size:12px\">"
                + budget.items() + " pozycji &middot; oko&#322;o " + budget.minutes()
                + " min czytania <span style=\"color:#64748b\">(szacunek z typu i d&#322;ugo&#347;ci "
                + "opisu, nie z tre&#347;ci artyku&#322;&oacute;w)</span></div>";
    }

    /**
     * Renders a section, or nothing when configuration switched it off (N19).
     *
     * <p>A supplier rather than a string, so a disabled section costs nothing to skip — the layout
     * work behind it is not done either.
     */
    private String on(String section, java.util.function.Supplier<String> render) {
        return sectionsPolicy.isEnabled(section) ? render.get() : "";
    }

    /** Whether the per-item score explanation lines are rendered at all. */
    private boolean explainsScores() {
        return sectionsPolicy.isEnabled(DigestSections.SCORE_EXPLANATIONS);
    }

    private String buildHeader(String today) {
        return "<div style=\"background:#1e293b;padding:24px 28px\">"
                + "<h1 style=\"color:#fff;margin:0;font-size:22px\">&#128225; PulseDigest</h1>"
                + "<p style=\"color:#94a3b8;margin:4px 0 0;font-size:14px\">"
                + today + " &middot; powered by GPT-4o</p>"
                + CLOSE_DIV;
    }

    // Top-of-digest banner: names every API account whose quota/rate limit was exhausted this run,
    // so the recipient knows which one to top up to get full results. Renders nothing when all good.
    private String buildQuotaBanner(List<SourceFetchReport> exhausted) {
        if (exhausted == null || exhausted.isEmpty()) {
            return "";
        }
        List<String> accounts = exhausted.stream()
                .map(r -> accountLabel(r.sourceName()))
                .distinct()
                .toList();
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"padding:16px 28px;background:#fffbeb;"
                + "border-bottom:1px solid #fde68a;border-left:4px solid #d97706\">");
        sb.append("<h2 style=\"color:#92400e;font-size:15px;margin:0 0 6px\">")
                .append("&#9888;&#65039; Wyczerpane limity API &mdash; doładuj konto, by mieć pełne wyniki</h2>");
        sb.append("<p style=\"margin:0 0 8px;color:#92400e;font-size:13px;line-height:1.5\">")
                .append("Poniższe źródła nie zwróciły danych w tym wydaniu z powodu wyczerpanego limitu/kredytów. ")
                .append("Digest jest niepełny do czasu doładowania.</p>");
        sb.append("<ul style=\"margin:0;padding-left:20px;color:#78350f;font-size:13px;line-height:1.7\">");
        for (String account : accounts) {
            sb.append("<li><strong>").append(escapeHtml(account)).append("</strong></li>");
        }
        sb.append(CLOSE_LIST_DIV);
        return sb.toString();
    }

    // Maps an internal source name to a human-readable "account to top up" label.
    private String accountLabel(String sourceName) {
        return ApiAccounts.label(sourceName);
    }

    private String buildEditorialSection(String editorial) {
        if (editorial == null || editorial.isBlank()) {
            return "";
        }
        return "<div style=\"padding:22px 28px;background:#fff;"
                + "border-bottom:1px solid #f1f5f9\">"
                + "<p style=\"margin:0;color:#0f172a;font-size:15px;line-height:1.65;"
                + "font-style:italic\">"
                + escapeHtml(editorial)
                + "</p></div>";
    }

    private String buildInsightsSection(List<String> insights) {
        if (insights.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"padding:20px 28px;background:#eff6ff;"
                + "border-bottom:1px solid #dbeafe\">");
        sb.append("<h2 style=\"color:#1e40af;font-size:15px;margin:0 0 10px\">"
                + "&#128273; Top insights dnia</h2>");
        sb.append("<ul style=\"margin:0;padding-left:20px;color:#1e3a8a;"
                + "font-size:14px;line-height:1.7\">");
        for (String insight : insights) {
            sb.append("<li style=\"margin-bottom:6px\">").append(escapeHtml(insight))
                    .append(CLOSE_LI);
        }
        sb.append(CLOSE_LIST_DIV);
        return sb.toString();
    }

    private String buildCriticalTrendsSection(List<DigestStory> stories) {
        if (stories.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"padding:20px 28px;background:#fff1f2;"
                + "border-bottom:1px solid #fecdd3;border-left:4px solid #dc2626\">");
        sb.append("<h2 style=\"color:#b91c1c;font-size:15px;margin:0 0 10px\">")
                .append("&#128308; Krytyczne trendy &mdash; potwierdzenie w 3+ typach &#378;r&oacute;de&#322;</h2>");
        sb.append("<ul style=\"margin:0;padding:0;list-style:none\">");
        for (DigestStory story : stories) {
            DigestItem it = story.lead();
            Signal signal = story.signal();
            String domainLabel = signal == null || signal.sourceDomains().isEmpty() ? ""
                    : " <span style=\"color:#9f1239;font-size:11px;font-weight:600\">"
                    + "[" + signal.sourceDomains().stream()
                            .map(d -> escapeHtml(d.name()))
                            .collect(Collectors.joining(MIDDOT))
                    + "]</span>";
            String summaryPart = (it.summary() != null && !it.summary().isBlank())
                    ? "<div style=\"color:#991b1b;font-size:12px;margin-top:3px\">"
                    + escapeHtml(it.summary()) + " &mdash; " + escapeHtml(it.source()) + CLOSE_DIV
                    : "<div style=\"color:#991b1b;font-size:12px;margin-top:3px\">"
                    + escapeHtml(it.source()) + CLOSE_DIV;
            sb.append("<li style=\"margin-bottom:10px\">")
                    .append("<a href=\"").append(safeHref(it.url()))
                    .append("\" style=\"color:#b91c1c;font-weight:600;text-decoration:none;font-size:14px\">")
                    .append(escapeHtml(it.title())).append("</a>")
                    .append(domainLabel)
                    .append(StackMarkerBuilder.buildStackMarker(story))
                    .append(summaryPart)
                    .append(EmailFormatting.confirmationLine(story.corroborations(), "#9f1239"))
                    .append(TrendBadgeBuilder.buildRecurrenceBadge(signal))
                    .append(explainsScores() ? ScoreExplanationBuilder.buildWhyYouSeeThis(signal) : "")
                    .append(CLOSE_LI);
        }
        sb.append(CLOSE_LIST_DIV);
        return sb.toString();
    }

    private String buildFooter(int selectedCount, ResearchResult research, RadarAccuracy radarAccuracy,
                               ReaderProfile readerProfile, List<Signal> signals,
                               pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Calibration
                                       calibration,
                               List<pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceIntake>
                                       silentSources) {
        int rawTotal = research != null ? research.rawTotalCount() : 0;
        int sources = research != null ? research.activeSourceCount() : 0;
        long failedSources = research != null
                ? research.sourceFetchReports().stream().filter(r -> r.status() == SourceFetchStatus.FAILED).count()
                : 0;
        String sourceHealth = failedSources > 0 ? MIDDOT + failedSources + " źródła z ostrzeżeniem" : "";
        return "<div style=\"padding:16px 28px;background:#f9fafb;text-align:center;"
                + "color:#6b7280;font-size:12px\">"
                + on(DigestSections.FOOTER_INTAKE, () -> "Wybrano " + selectedCount + " z " + rawTotal
                        + " itemów &middot; " + sources + " &#378;róde&#322;" + sourceHealth
                        + " &middot; okno pn/czw")
                + TrendBadgeBuilder.buildRadarAccuracyLine(radarAccuracy)
                + TrendBadgeBuilder.buildCalibrationLine(calibration)
                + TrendBadgeBuilder.buildSilentSourcesLine(silentSources)
                + "<br>Wygenerowano przez GPT-4o &middot; PulseDigest"
                // Gated by the same toggle as the per-item explanations, and for the same reason:
                // it prints the reader's own voting history. Found by the archive's leak test —
                // disabling score-explanations silenced the inline lines and left this one shouting.
                + (explainsScores() ? ScoreExplanationBuilder.buildVotesInAction(signals) : "")
                + TrendBadgeBuilder.buildReaderModelLine(readerProfile)
                + CLOSE_DIV;
    }
}
