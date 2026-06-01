package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out;

import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportData;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.EmailDeliveryReceipt;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.QuotaAlert;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchResult;

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
}
