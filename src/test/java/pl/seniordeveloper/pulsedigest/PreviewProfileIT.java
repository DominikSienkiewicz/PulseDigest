package pl.seniordeveloper.pulsedigest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.EditionDeliveryPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.EditionInputPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.EmailDeliveryPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.FeedbackPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.PublishedUrlsPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.ReaderProfilePort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.ReportHistoryPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.ReportStoragePort;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F12's last part: the production context boots under the {@code preview} profile with one credential
 * and no database at all.
 *
 * <p>This is the claim the mode makes, so it is the claim that is tested. Note what the test does
 * <em>not</em> have: no {@code @Testcontainers}, no Postgres, no Resend key, no Supabase URL — only
 * {@code openai.api-key}, because a preview makes real model calls and that is the one cost it cannot
 * avoid. If the context needed anything else, this test could not start.
 *
 * <p>What is deliberately not covered here: an end-to-end preview run. It would fetch today's real
 * sources and spend real money at OpenAI, which no test may do. The pipeline between synthesis and
 * rendering is the same code the committed golden files pin, and the ports it writes through are the
 * ones asserted below; what remains unverified is only the live run itself.
 */
@ActiveProfiles("preview")
@SpringBootTest(properties = "openai.api-key=preview-test-key")
class PreviewProfileIT {

    @MockitoBean
    private DigestRunner digestRunner;

    @Autowired
    private EmailDeliveryPort emailPort;

    @Autowired
    private ReportStoragePort storagePort;

    @Autowired
    private FeedbackPort feedbackPort;

    @Autowired
    private ReportHistoryPort historyPort;

    @Autowired
    private EditionInputPort editionInputPort;

    @Autowired
    private EditionDeliveryPort editionDeliveryPort;

    @Autowired
    private ReaderProfilePort readerProfilePort;

    @Autowired
    private PublishedUrlsPort publishedUrlsPort;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private org.springframework.core.env.Environment environment;

    @Test
    void nothingThatCouldSendOrStoreIsWiredIn() {
        // Named by class rather than by behaviour on purpose: the point is which implementation the
        // pipeline got, and a preview that accidentally injected the Resend adapter would satisfy any
        // behavioural assertion right up to the moment it mailed a real reader.
        assertThat(emailPort.getClass().getName()).contains("PreviewConfig");
        assertThat(storagePort.getClass().getName()).doesNotContain("Supabase");
        assertThat(editionInputPort.getClass().getName()).doesNotContain("Supabase");
        assertThat(editionDeliveryPort.getClass().getName()).doesNotContain("Supabase");
    }

    @Test
    void theReadSidesAnswerAsAFirstRunWould() {
        assertThat(feedbackPort.netVotesBySource(30)).isEmpty();
        assertThat(feedbackPort.savedItems(30)).isEmpty();
        assertThat(feedbackPort.upvotedUrls(30)).isEmpty();
        assertThat(historyPort.recentEditions(30)).isEmpty();
        assertThat(readerProfilePort.latest()).isEmpty();
        assertThat(publishedUrlsPort.recentlyPublishedUrls(30)).isEmpty();
    }

    @Test
    void theDoubleSendGuardGrantsTheClaimBecauseThereIsNoInboxToProtect() {
        assertThat(editionDeliveryPort.deliveredAt(java.time.LocalDate.now())).isEmpty();
        assertThat(editionDeliveryPort.claim(java.time.LocalDate.now(), "preview")).isTrue();
    }

    /**
     * The DataSource bean exists — the Supabase adapters are still constructed and need one — and the
     * profile's URL is the one that won. That second half is the interesting one: the real URL comes
     * from {@code ${SUPABASE_DB_URL}} in {@code application.yaml}, and a profile-specific value
     * outranks it, so a developer with real Supabase credentials in {@code .env} still gets a preview
     * that points nowhere. Hikari connects lazily, so nothing dials it and the context starts with no
     * database running at all.
     */
    @Test
    void theDataSourceBeanExistsAndTheProfilesUrlWins() {
        assertThat(dataSource).isNotNull();
        assertThat(environment.getProperty("spring.datasource.url")).contains("preview.invalid");
    }
}
