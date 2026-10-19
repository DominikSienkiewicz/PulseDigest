package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.SectionsPolicy;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.FeedbackProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ThinWeekProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.WatchlistProperties;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * N19. A section switched off has to disappear from the document and take nothing with it.
 *
 * <p>The purpose is not customisation. The audit could not determine which sections are ever read,
 * and turning one off for a few editions is the cheapest way to find out — which only works if the
 * rest of the digest still renders.
 */
class SectionTogglesTest {

    private final DigestEditionFixture fixture = DigestEditionFixture.load();

    @Test
    void everythingRendersWhenNothingIsDisabled() {
        String html = render(Set.of());

        assertThat(html).contains("Twój radar", "Top picks", "Dlaczego to widzisz");
    }

    @Test
    void aDisabledSectionIsAbsentAndTheRestSurvives() {
        String html = render(Set.of(DigestSections.WATCHLIST));

        assertThat(html).doesNotContain("Twój radar")
                .as("the rest of the document is untouched").contains("Top picks", "Must-know");
    }

    @Test
    void scoreExplanationsCanBeTurnedOffWithoutLosingTheItems() {
        // The line most likely to be noise to a reader who already trusts the ranking, and the one
        // the audit specifically wondered about.
        String html = render(Set.of(DigestSections.SCORE_EXPLANATIONS));

        assertThat(html).doesNotContain("Dlaczego to widzisz")
                .contains("Must-know", "Top picks");
    }

    @Test
    void disablingEverySectionStillProducesAValidDocument() {
        // The case that would break a naive implementation: no builder may throw on an empty digest,
        // and the shell has to close properly or the mail renders as raw markup.
        String html = render(DigestSections.ALL);

        assertThat(html).startsWith("<!DOCTYPE html>").endsWith("</div></body></html>")
                .contains("PulseDigest");
    }

    @Test
    void anUnknownKeyDisablesNothingRatherThanEverything() {
        String html = render(Set.of("watchlist-typo"));

        assertThat(html).contains("Twój radar");
    }

    @Test
    void everyKeyTheConstantsDeclareActuallySwitchSomethingOff() {
        // Guards the failure this feature is most likely to have: a key that looks supported,
        // changes nothing, and quietly makes the experiment report "the section was not missed".
        String full = render(Set.of());

        for (String section : DigestSections.ALL) {
            if (DigestSections.DEPENDENCY_RADAR.equals(section)) {
                // The one section with nothing to show in the fixture: the reader's own
                // dependencies come from build files that do not exist in a test.
                continue;
            }
            assertThat(render(Set.of(section)))
                    .as("disabling %s must change the document", section)
                    .isNotEqualTo(full);
        }
    }

    /**
     * The fixture with every optional section populated.
     *
     * <p>Without this, a key for a section the fixture happens to leave empty would look like it
     * works: the document is identical whether or not it is disabled, and the experiment the switch
     * exists for would report "nobody missed it" about a section that never rendered.
     */
    private pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportData populated() {
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneOffset.UTC);
        return fixture.report()
                .withEolNotices(java.util.List.of(
                        new pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.EolNotice(
                                "Eclipse Temurin 26", today.minusMonths(1), today)))
                .withCallsForPapers(java.util.List.of(
                        new pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.CallForPapers(
                                "Devnexus", "https://sessionize.com/devnexus", today.plusDays(20), "Atlanta")))
                .withSavedBacklog(new pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model
                        .SavedBacklog(java.util.List.of(
                        new pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SavedItem(
                                "Kept", "https://example.com/kept", "Hacker News", "Java/JVM",
                                java.time.Instant.now().minusSeconds(86_400))), 1))
                .withDependencyReleases(java.util.List.of())
                .withReadingBudget(new pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model
                        .ReadingBudget(7, 15));
    }

    private String render(Set<String> disabled) {
        return new ReportEmailBuilder(
                new FeedbackProperties(true, 30, fixture.feedbackReceiverUrl(), ""),
                new WatchlistProperties(true, fixture.watchlistTechnologies()),
                new ThinWeekProperties(true, 3),
                new SectionsPolicy(disabled))
                .buildHtml(populated(), fixture.research());
    }
}
