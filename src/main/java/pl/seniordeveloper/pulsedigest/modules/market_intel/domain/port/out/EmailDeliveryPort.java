package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out;

import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportData;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.EmailDeliveryReceipt;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.QuotaAlert;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchResult;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Retrospective;

/**
 * Output port for delivering generated reports.
 */
public interface EmailDeliveryPort {
    EmailDeliveryReceipt send(ReportData report, ResearchResult research);

    /**
     * Sends a standalone alert email when the digest could not be produced at all (e.g. the LLM
     * provider ran out of credits, or every data source was rate-limited), so the recipient still
     * learns which account to top up.
     */
    EmailDeliveryReceipt sendQuotaAlert(QuotaAlert alert);

    /**
     * Sends the quarterly retrospective (N20). A separate method rather than another shape of
     * {@code send}: it carries no research, no scoring and no feedback links, and pretending it is
     * an edition would mean passing three arguments that have no meaning for it.
     */
    EmailDeliveryReceipt sendRetrospective(Retrospective retrospective);
}
