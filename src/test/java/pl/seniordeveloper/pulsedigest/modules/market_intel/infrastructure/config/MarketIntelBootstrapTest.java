package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.config;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.MarketIntelJobTracker;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PersistedReport;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.ReportStoragePort;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;

class MarketIntelBootstrapTest {

    @Test
    void bootsWhenTheArchivedReportCannotBeRead() {
        MarketIntelBootstrap bootstrap = new MarketIntelBootstrap(new MarketIntelJobTracker(), unreadableStorage());

        assertThatCode(bootstrap::init).doesNotThrowAnyException();
    }

    private static ReportStoragePort unreadableStorage() {
        return new ReportStoragePort() {

            @Override
            public void save(PersistedReport report) {
                throw new UnsupportedOperationException("not used by this test");
            }

            @Override
            public Optional<PersistedReport> getLatest() {
                throw new IllegalStateException("Corrupted report payload in DB");
            }
        };
    }
}
