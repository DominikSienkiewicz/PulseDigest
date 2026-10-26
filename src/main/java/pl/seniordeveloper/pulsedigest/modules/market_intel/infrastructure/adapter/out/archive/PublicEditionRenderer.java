package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.archive;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.SectionsPolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportData;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchResult;
import pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email
        .ReportEmailBuilder;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.FeedbackProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ThinWeekProperties;

import java.util.List;
import java.util.Set;

/**
 * Renders an edition for publication: the news, with the reader removed (N31).
 *
 * <p>A published archive is the only irreversible thing in this project, and the thing that makes it
 * irreversible is not the HTML — it is everything the mail carries <em>about the person reading
 * it</em>. The delivered edition is personal in ways that are easy to miss: it knows what they
 * up-voted, what they bookmarked, which libraries they pin, what the model has inferred about them,
 * and it carries one signed URL per item.
 *
 * <p>So the public rendering is not a copy of the mail with a header removed. It is the same builder
 * handed a deliberately emptied report:
 *
 * <ul>
 *   <li><strong>No feedback links.</strong> A blank {@code receiverUrl} is the existing graceful
 *       path — {@code FeedbackLinkBuilder} renders nothing — so no {@code vote=}, no
 *       {@code edition=} and, critically, no {@code sig=}. The HMAC is computed over values that
 *       would be published beside it; a signed link in a public archive is a forgeable vote handed
 *       to anyone who reads it.</li>
 *   <li><strong>No reader model, calibration or saved shelf.</strong> These are statements about a
 *       person, not about the week.</li>
 *   <li><strong>No dependency radar, stack markers or end-of-life notices.</strong> All three
 *       describe the reader's own stack, which is not the public's business and is a small
 *       reconnaissance gift besides.</li>
 *   <li><strong>No score explanations.</strong> They print the reader's own voting history inline.</li>
 * </ul>
 *
 * <p>What is left is what a stranger could have read anywhere: the stories, the corroboration, the
 * editorial, the recap.
 */
@Component
@RequiredArgsConstructor
public class PublicEditionRenderer {

    /** Sections that describe the reader rather than the week. */
    private static final Set<String> PRIVATE_SECTIONS = Set.of(
            "score-explanations", "saved", "dependency-radar", "eol",
            // Not private — simply unknowable here. Editions are read back from storage, which
            // never kept the research that produced the intake counts, and a footer reporting
            // "0 źródeł" with confidence is worse than one that says nothing.
            pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email
                    .DigestSections.FOOTER_INTAKE);

    private final ThinWeekProperties thinWeekProperties;

    public String render(ReportData report, ResearchResult research) {
        return builder().buildHtml(depersonalise(report), research);
    }

    /**
     * Everything the published edition must not know. Done by emptying the report rather than by
     * filtering the HTML afterwards: a regex over rendered markup is a guess, and this is the one
     * place in the project where being wrong is not recoverable.
     */
    static ReportData depersonalise(ReportData report) {
        return report
                .withReaderProfile(null)
                .withCalibration(null)
                .withSavedBacklog(null)
                .withSilentSources(List.of())
                .withDependencyReleases(List.of())
                .withStackAffectingUrls(Set.of())
                .withEolNotices(List.of());
    }

    /**
     * A builder with no feedback receiver, which is what removes every signed link. The blank URL is
     * not a special case invented here — it is the documented degradation for an unconfigured
     * receiver, so this path is exercised by the same code every unconfigured run uses.
     */
    private ReportEmailBuilder builder() {
        return new ReportEmailBuilder(
                new FeedbackProperties(false, 30, "", ""),
                thinWeekProperties,
                new SectionsPolicy(PRIVATE_SECTIONS));
    }
}
