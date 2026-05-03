package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out;

import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportData;

public interface EmailDeliveryPort {
    void send(ReportData report);
}
