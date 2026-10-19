package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportData;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchResult;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Signal;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ThinWeekProperties;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import static pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email
        .PlainTextFormatting.block;
import static pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email
        .PlainTextFormatting.heading;
import static pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email
        .PlainTextFormatting.wrap;

/**
 * The digest as text, for the {@code text/plain} half of a {@code multipart/alternative} send (N14).
 *
 * <p>Three reasons it exists, in the order they matter. A message with no text part is a spam-filter
 * signal, so this is deliverability before it is anything else. It is the only version a terminal
 * client, a screen reader on a text-only setting or a watch notification can render faithfully. And
 * writing it is an editorial check: content that needs the colour-coding to make sense did not have
 * a shape to begin with.
 *
 * <p>Deliberately not a transcription of the HTML. The feedback links are left out — three URLs per
 * item would triple the length of the part whose whole virtue is being scannable — and the footer
 * says so rather than letting a text reader wonder where the thumbs went.
 *
 * <p>Held to the same section order as {@link ReportEmailBuilder} by the golden file, so the two
 * cannot drift apart silently.
 */
@Component
@RequiredArgsConstructor
public class PlainTextDigestBuilder {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.of("pl", "PL"));
    private static final String INDENT = "   ";

    private final ThinWeekProperties thinWeekProperties;

    public String buildText(ReportData report, ResearchResult research) {
        String today = LocalDate.now(ZoneOffset.UTC).format(DATE_FMT);
        List<DigestItem> items = report != null && report.items() != null ? report.items() : List.of();
        List<Signal> signals = report != null && report.signals() != null ? report.signals() : List.of();
        DigestLayout layout = DigestLayout.of(items, signals);

        if (isThinWeek(report, layout)) {
            return thinWeekText(today, layout, items);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(heading("PULSEDIGEST \u2014 " + today, '='));
        if (report != null && report.readingBudget() != null && report.readingBudget().isReportable()) {
            sb.append(report.readingBudget().items()).append(" pozycji \u00b7 oko\u0142o ")
                    .append(report.readingBudget().minutes()).append(" min czytania (szacunek)\n");
        }
        appendParagraph(sb, report != null ? report.editorial() : null);
        appendStories(sb, "KRYTYCZNE TRENDY \u2014 potwierdzenie w 3+ typach \u017ar\u00f3de\u0142",
                layout.criticalTrends());
        appendStories(sb, "MUST-KNOW", layout.mustKnow());
        appendInsights(sb, report);
        appendStories(sb, "DEALS & TOOLS", layout.deals());
        appendStories(sb, "TOP PICKS", layout.topPicks());
        appendStories(sb, "SIGNALS", layout.signals());
        sb.append(footer(items.size(), research, report));
        return sb.toString();
    }

    /**
     * The three-line note, in text. An edition with nothing above the bar has to be as short here as
     * it is in HTML, or the text part quietly reintroduces the padding N03 removed.
     */
    private String thinWeekText(String today, DigestLayout layout, List<DigestItem> items) {
        StringBuilder sb = new StringBuilder();
        sb.append(heading("PULSEDIGEST \u2014 " + today, '='));
        sb.append("\nCicho w tym tygodniu \u2014 nic nie zas\u0142uguje na Twoj\u0105 uwag\u0119.\n");
        sb.append("Powy\u017cej progu Must-know: ").append(layout.storiesAboveTheBar())
                .append(" z wymaganych ").append(thinWeekProperties.minItems()).append(".\n");
        items.stream()
                .max(java.util.Comparator.comparingInt(DigestItem::score))
                .ifPresentOrElse(
                        closest -> sb.append("\nNajbli\u017cej by\u0142o:\n")
                                .append(story(1, new DigestStory(closest, List.of(closest), null, false))),
                        () -> sb.append("\n\u017badne \u017ar\u00f3d\u0142o nie zwr\u00f3ci\u0142o nic,"
                                + " co da\u0142oby si\u0119 zmierzy\u0107.\n"));
        return sb.toString();
    }

    private void appendParagraph(StringBuilder sb, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        sb.append('\n').append(wrap(text, "")).append('\n');
    }

    private void appendInsights(StringBuilder sb, ReportData report) {
        List<String> insights = report != null && report.topInsights() != null ? report.topInsights() : List.of();
        if (insights.isEmpty()) {
            return;
        }
        sb.append('\n').append(heading("TOP INSIGHTS DNIA", '-'));
        for (String insight : insights) {
            sb.append("\n* ").append(wrap(insight, "  "));
        }
        sb.append('\n');
    }

    private void appendStories(StringBuilder sb, String title, List<DigestStory> stories) {
        if (stories == null || stories.isEmpty()) {
            return;
        }
        sb.append('\n').append(heading(title, '-'));
        int index = 1;
        for (DigestStory story : stories) {
            sb.append(story(index++, story));
        }
    }

    /**
     * One story. The score is spelled out rather than implied by which section it is in, because in
     * text there is no colour and no background to imply it with (N16's rule, applied here too).
     */
    private String story(int index, DigestStory story) {
        DigestItem lead = story.lead();
        StringBuilder sb = new StringBuilder();
        sb.append('\n').append(index).append(". ").append(wrap(safe(lead.title()), INDENT)).append('\n');
        sb.append(INDENT).append(lead.score()).append("/10");
        if (lead.source() != null && !lead.source().isBlank()) {
            sb.append(" \u00b7 ").append(lead.source());
        }
        sb.append('\n');
        appendIndented(sb, lead.summary());
        appendIndented(sb, lead.whyItMatters());
        if (lead.url() != null && !lead.url().isBlank()) {
            sb.append(INDENT).append(lead.url()).append('\n');
        }
        appendCorroborations(sb, story, lead);
        return sb.toString();
    }

    /**
     * The confirming sources, each with its own link. The HTML links every one of them, so printing
     * only their names here would quietly give a text reader a less evidenced digest than an HTML
     * one — and "which sources carried this" is the claim the 🔴 badge rests on.
     */
    private void appendCorroborations(StringBuilder sb, DigestStory story, DigestItem lead) {
        List<String> sources = story.sources();
        if (sources.size() <= 1) {
            return;
        }
        sb.append(block("Potwierdzone w: " + String.join(" + ", sources), INDENT)).append('\n');
        for (DigestItem corroboration : story.corroborations()) {
            if (corroboration.url() == null || corroboration.url().equals(lead.url())) {
                continue;
            }
            sb.append(INDENT).append("- ").append(safe(corroboration.source()))
                    .append(": ").append(corroboration.url()).append('\n');
        }
    }

    private void appendIndented(StringBuilder sb, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        sb.append(block(text, INDENT)).append('\n');
    }

    private String footer(int selectedCount, ResearchResult research, ReportData report) {
        int rawTotal = research != null ? research.rawTotalCount() : 0;
        int sources = research != null ? research.activeSourceCount() : 0;
        return "\n" + "-".repeat(PlainTextFormatting.WIDTH) + "\n"
                + "Wybrano " + selectedCount + " z " + rawTotal + " item\u00f3w \u00b7 "
                + sources + " \u017ar\u00f3de\u0142 \u00b7 okno pn/czw\n"
                + silentSourcesLine(report)
                + "Oceny \ud83d\udc4d/\ud83d\udc4e i \u201ezapisz\u201d dzia\u0142aj\u0105 w wersji HTML "
                + "tej wiadomo\u015bci.\n"
                + "Wygenerowano przez GPT-4o \u00b7 PulseDigest\n";
    }

    /** The same warning the HTML footer carries, so a text reader is not told less (N27). */
    private String silentSourcesLine(ReportData report) {
        if (report == null || report.silentSources().isEmpty()) {
            return "";
        }
        String names = report.silentSources().stream()
                .map(pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceIntake::source)
                .collect(java.util.stream.Collectors.joining(", "));
        return "Milcz\u0105ce \u017ar\u00f3d\u0142a: " + names
                + " \u2014 odpowiadaj\u0105, ale nie wnosz\u0105 nic od kilku wyda\u0144.\n";
    }

    private boolean isThinWeek(ReportData report, DigestLayout layout) {
        return report != null && thinWeekProperties.enabled()
                && layout.storiesAboveTheBar() < thinWeekProperties.minItems();
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }
}
