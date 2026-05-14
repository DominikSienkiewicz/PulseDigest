package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out;

import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportData;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.EmailDeliveryReceipt;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchResult;

/**
 * Output port for delivering generated reports.
 */
public interface EmailDeliveryPort {
    EmailDeliveryReceipt send(ReportData report, ResearchResult research);
}
