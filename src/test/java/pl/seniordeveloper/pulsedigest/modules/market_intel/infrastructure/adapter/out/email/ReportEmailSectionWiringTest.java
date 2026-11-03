package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.SectionsPolicy;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.EolNotice;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportData;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SavedBacklog;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SavedItem;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.FeedbackProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ThinWeekProperties;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two sections the golden file cannot hold.
 *
 * <p>Both the end-of-life radar (N08) and the saved shelf (N05) word themselves relative to the day
 * they render — "za 2 miesiące", "odłożone 4 dni temu" — so neither can live in a committed
 * byte-for-byte copy of a rendered edition without going stale on a date nobody chose. Their wording
 * is pinned by their own builders' tests against a fixed clock.
 *
 * <p>That leaves one hole those tests do not cover: whether {@code ReportEmailBuilder} still calls
 * them at all. A whole-document diff would have caught a dropped section; two sections outside it
 * would not. This closes exactly that gap, and asserts on headings only — the wording belongs to the
 * builders' own tests, and duplicating it here would make a harmless rewording fail twice.
 */
class ReportEmailSectionWiringTest {

    private final DigestEditionFixture fixture = DigestEditionFixture.load();

    @Test
    void anEditionCarryingAnEndOfLifeDateRendersTheEndOfLifeSection() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        ReportData report = fixture.report().withEolNotices(List.of(new EolNotice("Eclipse Temurin 26", today.minusMonths(1), today)));

        assertThat(builder().buildHtml(report, fixture.research())).contains("Koniec wsparcia");
    }

    @Test
    void anEditionCarryingASavedShelfRendersTheSavedSection() {
        ReportData report = fixture.report().withSavedBacklog(new SavedBacklog(List.of(new SavedItem("Something kept", "https://example.com/kept", "Hacker News", "Java/JVM",
                        Instant.now().minusSeconds(86_400))), 1));

        assertThat(builder().buildHtml(report, fixture.research()))
                .contains("Zapisane na p&oacute;&#378;niej")
                .contains("Something kept");
    }

    @Test
    void anEditionCarryingNeitherRendersNeither() {
        // Both sections must vanish rather than announce that they found nothing — the normal state
        // for a reader whose stack is current and whose shelf is clear.
        String html = builder().buildHtml(fixture.report(), fixture.research());

        assertThat(html).doesNotContain("Koniec wsparcia").doesNotContain("Zapisane na");
    }

    private ReportEmailBuilder builder() {
        return new ReportEmailBuilder(new FeedbackProperties(true, 30, fixture.feedbackReceiverUrl(), ""),
                new ThinWeekProperties(true, 3),
                new SectionsPolicy(java.util.Set.of()));
    }
}
