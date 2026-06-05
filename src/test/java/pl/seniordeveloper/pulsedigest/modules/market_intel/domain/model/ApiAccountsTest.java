package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ApiAccountsTest {

    @ParameterizedTest
    @CsvSource({
            "Twitter/X topic, Twitter/X API",
            "Twitter/X influencer, Twitter/X API",
            "NVD/CVE, NVD API (NIST)",
            "Security Advisories, GitHub API (token)",
            "GitHub Releases, GitHub API (token)",
            "OpenJDK JEP, GitHub API (token)",
            "CNCF Landscape, GitHub API (token)",
            "Conference Talks, YouTube Data API",
            "Product Hunt, Product Hunt API",
            "Libraries.io, Libraries.io API",
            "Hugging Face, Hugging Face API",
            "Reddit/r/java, Reddit API",
            "Hacker News, Hacker News (Algolia) API"
    })
    void mapsSourceNameToTopUpAccount(String source, String expected) {
        assertThat(ApiAccounts.label(source)).isEqualTo(expected);
    }

    @Test
    void returnsPlaceholderForNullSource() {
        assertThat(ApiAccounts.label(null)).isEqualTo("Nieznane źródło");
    }

    @Test
    void passesThroughUnknownSourceName() {
        assertThat(ApiAccounts.label("DB-Engines")).isEqualTo("DB-Engines");
    }
}
