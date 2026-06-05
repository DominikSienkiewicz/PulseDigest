package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.QuotaAlert;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AlertEmailBuilderTest {

    private final AlertEmailBuilder builder = new AlertEmailBuilder();

    @Test
    void buildHtmlNamesAccountsToTopUp() {
        QuotaAlert alert = new QuotaAlert(List.of("OpenAI (model LLM)", "Twitter/X API"), "429 Too Many Requests");

        String html = builder.buildHtml(alert);

        assertThat(html)
                .contains("Digest nie powstał")
                .contains("Doładuj")
                .contains("OpenAI (model LLM)")
                .contains("Twitter/X API");
        assertThat(builder.buildSubject()).contains("nie powstał");
    }

    @Test
    void buildHtmlRendersGenericFailureWhenNoAccounts() {
        QuotaAlert alert = new QuotaAlert(List.of(), "synthesis blew up: NPE in mapper");

        String html = builder.buildHtml(alert);

        assertThat(html)
                .contains("Digest nie powstał")
                .contains("synthesis blew up: NPE in mapper")
                .doesNotContain("Doładuj");
    }
}
