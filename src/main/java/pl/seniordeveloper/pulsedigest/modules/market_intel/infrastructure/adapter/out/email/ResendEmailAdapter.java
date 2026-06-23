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
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.EmailDeliveryPort;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.EmailProperties;

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

        return sendHtml(emailBuilder.buildSubject(), emailBuilder.buildHtml(report, research));
    }

    @Override
    public EmailDeliveryReceipt sendQuotaAlert(QuotaAlert alert) {
        if (isBlank(cfg.resendApiKey()) || isBlank(cfg.from()) || isBlank(cfg.to())) {
            throw new IllegalStateException(
                    "Email not configured: RESEND_API_KEY, DIGEST_FROM_EMAIL and DIGEST_TO_EMAIL are required");
        }
        return sendHtml(alertBuilder.buildSubject(), alertBuilder.buildHtml(alert));
    }

    private EmailDeliveryReceipt sendHtml(String subject, String html) {
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "from", cfg.from(),
                    "to", List.of(cfg.to()),
                    "subject", subject,
                    "html", html
            ));

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
