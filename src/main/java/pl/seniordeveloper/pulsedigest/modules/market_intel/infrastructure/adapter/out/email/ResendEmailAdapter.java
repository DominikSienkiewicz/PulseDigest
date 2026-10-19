package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.http.ExternalRestClients;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.EmailDeliveryReceipt;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.QuotaAlert;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportData;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchResult;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Retrospective;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.EmailDeliveryPort;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.EmailProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Service
public class ResendEmailAdapter implements EmailDeliveryPort {

    private static final String RESEND_BASE_URL = "https://api.resend.com";

    private final ObjectMapper objectMapper;
    private final ReportEmailBuilder emailBuilder;
    private final AlertEmailBuilder alertBuilder;
    private final PlainTextDigestBuilder plainTextBuilder;
    private final RetrospectiveEmailBuilder retrospectiveBuilder;
    private final EmailProperties cfg;
    private RestClient resendClient;

    @PostConstruct
    void init() {
        this.resendClient = ExternalRestClients.builder()
                .baseUrl(RESEND_BASE_URL)
                .defaultHeader("Authorization", "Bearer " + cfg.resendApiKey())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public EmailDeliveryReceipt send(ReportData report, ResearchResult research) {
        if (isBlank(cfg.resendApiKey()) || isBlank(cfg.from()) || isBlank(cfg.to())) {
            throw new IllegalStateException(
                    "Email not configured: RESEND_API_KEY, DIGEST_FROM_EMAIL and DIGEST_TO_EMAIL are required");
        }

        return send(emailBuilder.buildSubject(report), emailBuilder.buildHtml(report, research),
                plainTextBuilder.buildText(report, research));
    }

    @Override
    public EmailDeliveryReceipt sendQuotaAlert(QuotaAlert alert) {
        if (isBlank(cfg.resendApiKey()) || isBlank(cfg.from()) || isBlank(cfg.to())) {
            throw new IllegalStateException(
                    "Email not configured: RESEND_API_KEY, DIGEST_FROM_EMAIL and DIGEST_TO_EMAIL are required");
        }
        // The alert has no text part: it is six lines of markup whose whole job is to arrive, and a
        // second renderer for it would be two things to keep in step for no reader.
        return send(alertBuilder.buildSubject(), alertBuilder.buildHtml(alert), null);
    }

    /**
     * Sends one message. Passing both {@code html} and {@code text} makes Resend emit
     * {@code multipart/alternative} (N14): the client picks, and a message with no text part at all
     * is a spam-filter signal regardless of what the HTML says.
     */
    @Override
    public EmailDeliveryReceipt sendRetrospective(Retrospective retrospective) {
        if (isBlank(cfg.resendApiKey()) || isBlank(cfg.from()) || isBlank(cfg.to())) {
            throw new IllegalStateException(
                    "Email not configured: RESEND_API_KEY, DIGEST_FROM_EMAIL and DIGEST_TO_EMAIL are required");
        }
        return send(retrospectiveBuilder.buildSubject(retrospective),
                retrospectiveBuilder.buildHtml(retrospective), null);
    }

    private EmailDeliveryReceipt send(String subject, String html, String text) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>(Map.of(
                    "from", cfg.from(),
                    "to", List.of(cfg.to()),
                    "subject", subject,
                    "html", html
            ));
            if (text != null && !text.isBlank()) {
                payload.put("text", text);
            }
            String body = objectMapper.writeValueAsString(payload);

            String response = resendClient.post()
                    .uri("/emails")
                    .body(body)
                    .retrieve()
                    .body(String.class);

            log.info("Email sent to {} — response: {}", cfg.to(), response);
            return new EmailDeliveryReceipt("resend", response);

        } catch (Exception e) {
            throw new IllegalStateException("Failed to send email via Resend: " + e.getMessage(), e);
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
