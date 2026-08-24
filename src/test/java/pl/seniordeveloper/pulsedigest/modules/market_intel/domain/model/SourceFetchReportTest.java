package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SourceFetchReportTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void keepsTheDerivedQuotaFlagOutOfTheSerializedPayload() throws Exception {
        SourceFetchReport report = SourceFetchReport.failed("Twitter", 120L, "429 Too Many Requests");

        String json = objectMapper.writeValueAsString(report);

        assertThat(json).doesNotContain("quotaExhausted");
    }

    @Test
    void survivesAJsonRoundTrip() throws Exception {
        SourceFetchReport report = SourceFetchReport.failed("Twitter", 120L, "429 Too Many Requests");

        SourceFetchReport restored =
                objectMapper.readValue(objectMapper.writeValueAsString(report), SourceFetchReport.class);

        assertThat(restored).isEqualTo(report);
        assertThat(restored.isQuotaExhausted()).isTrue();
    }

    @Test
    void readsLegacyPayloadThatStillCarriesTheDerivedQuotaFlag() throws Exception {
        String legacyJson = """
                {
                  "sourceName": "Twitter",
                  "status": "FAILED",
                  "itemCount": 0,
                  "durationMillis": 120,
                  "errorMessage": "429 Too Many Requests",
                  "quotaExhausted": true
                }
                """;

        SourceFetchReport restored = objectMapper.readValue(legacyJson, SourceFetchReport.class);

        assertThat(restored.sourceName()).isEqualTo("Twitter");
        assertThat(restored.status()).isEqualTo(SourceFetchStatus.FAILED);
        assertThat(restored.errorMessage()).isEqualTo("429 Too Many Requests");
    }
}
