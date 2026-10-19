package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import java.time.Instant;

/**
 * A stored edition, whole, for republishing (N31).
 *
 * <p>Distinct from {@link PastEdition}, which carries only the scored signals because that is all
 * trend memory and the yield ledger ever needed. An archive page needs the editorial, the insights
 * and the items — everything the payload already holds and nothing had asked for until now.
 *
 * @param jobId       the edition's id, and its stable URL slug
 * @param generatedAt when it was produced
 * @param report      the edition as it was delivered, before any depersonalising
 */
public record ArchivedEdition(String jobId, Instant generatedAt, ReportData report) {
}
