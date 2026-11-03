package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.SectionsPolicy;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.FeedbackProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ThinWeekProperties;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * N16. Accessibility asserted on the rendered document rather than reviewed once and hoped for.
 *
 * <p>Email HTML is where accessibility is normally abandoned entirely, which is exactly why these
 * are cheap to break: nothing else in the build has an opinion about a colour value or an unlabelled
 * link, and a reviewer reading a diff of inline styles will not catch either.
 */
class EmailAccessibilityTest {

    /** WCAG AA for body text. Everything in this document is body text — there is no large type. */
    private static final double MIN_CONTRAST = 4.5;

    /** The lightest background in the document; anything legible on it is legible on the tinted panels. */
    private static final String LIGHTEST_BACKGROUND = "ffffff";

    private static final Pattern FOREGROUND = Pattern.compile("color:#([0-9a-fA-F]{6})");

    private final DigestEditionFixture fixture = DigestEditionFixture.load();

    @Test
    void everyForegroundColourOnALightPanelMeetsWcagAa() {
        // The dark header is excluded by construction: it is light-on-dark, so measuring it against
        // white would report a failure that does not exist. Everything else sits on #fff or a tint
        // of it, so white is the worst case and the right one to measure against.
        String body = withoutDarkHeader(withoutDarkModeBlock(render()));

        Matcher matcher = FOREGROUND.matcher(body);
        Set<String> failing = new java.util.TreeSet<>();
        while (matcher.find()) {
            String colour = matcher.group(1).toLowerCase(java.util.Locale.ROOT);
            if (contrast(colour, LIGHTEST_BACKGROUND) < MIN_CONTRAST) {
                failing.add("#" + colour + " (%.2f:1)".formatted(contrast(colour, LIGHTEST_BACKGROUND)));
            }
        }

        assertThat(failing)
                .as("foreground colours below %.1f:1 on a light panel", MIN_CONTRAST)
                .isEmpty();
    }

    @Test
    void theRankIsReadableWithoutSeeingItsColour() {
        // 🔴 🟨 🟡 ⚪ differ only by colour. Before N16 that was the whole of the rank in the tables.
        String html = render();

        assertThat(html).contains("<strong>Słaby</strong>", "<strong>Umiarkowany</strong>")
                .contains("aria-label=\"Słaby\"");
    }

    @Test
    void everyFeedbackLinkHasAnAccessibleName() {
        // A link whose text is one emoji announces as "link, thumbs up sign" at best, "link" at worst.
        String html = render();

        assertThat(html).contains("aria-label=\"Więcej takich\"", "title=\"Więcej takich\"")
                .contains("aria-label=\"Mniej takich\"", "title=\"Mniej takich\"")
                .contains("title=\"Zapisz na później\"");
    }

    @Test
    void theDataTableDeclaresItsColumnHeaders() {
        // scope, not role="presentation": this table has real headers, and calling it presentational
        // would take the association away rather than add one.
        String html = render();

        assertThat(html.split("scope=\"col\"", -1).length - 1)
                .as("every column header is scoped")
                .isGreaterThanOrEqualTo(4);
    }

    @Test
    void theDocumentDeclaresItsColourSchemesSoNoClientInventsOne() {
        // N15. Without this a dark client machine-inverts the light palette into something nobody
        // designed — which is an accessibility failure before it is an aesthetic one.
        String html = render();

        assertThat(html)
                .contains("color-scheme:light dark")
                .contains("@media (prefers-color-scheme:dark)");
    }

    @Test
    void theHeadingStructureStartsAtH1AndSectionsAreH2() {
        String html = render();

        assertThat(html.split("<h1", -1).length - 1).as("exactly one h1").isEqualTo(1);
        assertThat(html).contains("<h2")
                .doesNotContain("<h3");
    }

    private String render() {
        return new ReportEmailBuilder(new FeedbackProperties(true, 30, fixture.feedbackReceiverUrl(), ""),
                new ThinWeekProperties(true, 3),
                new SectionsPolicy(java.util.Set.of()))
                .buildHtml(fixture.report(), fixture.research());
    }

    /**
     * The {@code <style>} block, whose colours are the dark-mode palette. Measuring those against
     * white would report failures for exactly the rules that exist to be read on #111827.
     */
    private static String withoutDarkModeBlock(String html) {
        int start = html.indexOf("<style>");
        int end = html.indexOf("</style>", start);
        return start < 0 || end < 0 ? html : html.substring(0, start) + html.substring(end);
    }

    /** Everything from the dark header's opening div to the end of that block. */
    private static String withoutDarkHeader(String html) {
        int start = html.indexOf("background:#1e293b");
        if (start < 0) {
            return html;
        }
        int end = html.indexOf("</h1>", start);
        end = end < 0 ? start : html.indexOf("</div>", end);
        return html.substring(0, start) + html.substring(end < 0 ? start : end);
    }

    /** WCAG relative-luminance contrast ratio between two six-digit hex colours. */
    private static double contrast(String a, String b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(String hex) {
        List<Double> channels = List.of(channel(Integer.parseInt(hex.substring(0, 2), 16)),
                channel(Integer.parseInt(hex.substring(2, 4), 16)),
                channel(Integer.parseInt(hex.substring(4, 6), 16)));
        return 0.2126 * channels.get(0) + 0.7152 * channels.get(1) + 0.0722 * channels.get(2);
    }

    private static double channel(int raw) {
        double v = raw / 255.0;
        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }
}
