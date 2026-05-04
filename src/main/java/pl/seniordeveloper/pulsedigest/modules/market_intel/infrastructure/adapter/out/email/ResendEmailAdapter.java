package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportData;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchResult;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.EmailDeliveryPort;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ReportProperties;

import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Service
public class ResendEmailAdapter implements EmailDeliveryPort {

    private static final String RESEND_BASE_URL = "https://api.resend.com";

    private final ObjectMapper objectMapper;
    private final ReportEmailBuilder emailBuilder;
    private final ReportProperties reportProperties;
    private RestClient resendClient;

    @PostConstruct
    void init() {
        this.resendClient = RestClient.builder()
                .baseUrl(RESEND_BASE_URL)
                .defaultHeader("Authorization",
                        "Bearer " + reportProperties.email().resendApiKey())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public void send(ReportData report, ResearchResult research) {
        ReportProperties.EmailProperties cfg = reportProperties.email();
        if (isBlank(cfg.resendApiKey()) || isBlank(cfg.to())) {
            log.warn("Email not configured (RESEND_API_KEY or DIGEST_TO_EMAIL missing) — skipping");
            return;
        }

        String subject = emailBuilder.buildSubject(report);
        String html = emailBuilder.buildHtml(report, research);

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

        } catch (Exception e) {
            log.error("Failed to send email via Resend: {}", e.getMessage(), e);
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
