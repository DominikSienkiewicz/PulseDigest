package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.http.ExternalRestClients;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SecurityAdvisory;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.SecurityAdvisoriesProperties;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Fetches recent high/critical security advisories from the GitHub Advisory Database (GHSA).
 */
@Slf4j
@Service
public class SecurityAdvisoryAdapter {

    private final SecurityAdvisoriesProperties properties;
    private final ObjectMapper objectMapper;
    private RestClient restClient;

    @Autowired
    public SecurityAdvisoryAdapter(SecurityAdvisoriesProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void init() {
        this.restClient = ExternalRestClients.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("User-Agent", "PulseDigest/1.0")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
    }

    public List<SecurityAdvisory> fetchSecurityAdvisories() {
        try {
            String json = restClient.get()
                    .uri(uri -> uri
                            .queryParam("per_page", properties.limit())
                            .queryParam("sort", "published")
                            .queryParam("direction", "desc")
                            .build())
                    .retrieve()
                    .body(String.class);
            if (json == null || json.isBlank()) {
                return List.of();
            }
            List<SecurityAdvisory> advisories = parseAdvisories(json);
            log.info("Security Advisories: {} (limit={})", advisories.size(), properties.limit());
            return advisories;
        } catch (Exception e) {
            log.warn("Security Advisories fetch failed: {}", e.getMessage());
            return List.of();
        }
    }

    List<SecurityAdvisory> parseAdvisories(String json) {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(properties.lookbackHours());
        Set<String> minSeverities = upperSet(properties.minSeverities());
        Set<String> relevantEcosystems = lowerSet(properties.relevantEcosystems());
        try {
            JsonNode arr = objectMapper.readTree(json);
            List<SecurityAdvisory> result = new ArrayList<>();
            for (JsonNode node : arr) {
                String severity = node.path("severity").asText("").toUpperCase(Locale.ROOT);
                if (!minSeverities.isEmpty() && !minSeverities.contains(severity)) {
                    continue;
                }
                LocalDateTime publishedAt = parseTimestamp(node.path("published_at").asText(""));
                if (publishedAt == null || publishedAt.isBefore(cutoff)) {
                    continue;
                }
                List<String> ecosystems = extractEcosystems(node);
                if (!relevantEcosystems.isEmpty()
                        && ecosystems.stream().noneMatch(relevantEcosystems::contains)) {
                    continue;
                }
                String ghsaId = node.path("ghsa_id").asText("");
                String summary = node.path("summary").asText("");
                String url = node.path("html_url").asText("");
                if (ghsaId.isBlank() || url.isBlank()) {
                    continue;
                }
                result.add(new SecurityAdvisory(ghsaId, summary, severity, publishedAt, ecosystems, url));
            }
            return result;
        } catch (Exception e) {
            log.warn("Security Advisories parse failed: {}", e.getMessage());
            return List.of();
        }
    }

    private List<String> extractEcosystems(JsonNode advisory) {
        List<String> ecosystems = new ArrayList<>();
        for (JsonNode vuln : advisory.path("vulnerabilities")) {
            String ecosystem = vuln.path("package").path("ecosystem").asText("").toLowerCase(Locale.ROOT);
            if (!ecosystem.isBlank() && !ecosystems.contains(ecosystem)) {
                ecosystems.add(ecosystem);
            }
        }
        return ecosystems;
    }

    private LocalDateTime parseTimestamp(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw, DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception e) {
            return null;
        }
    }

    private Set<String> upperSet(List<String> values) {
        Set<String> out = new HashSet<>();
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                out.add(v.toUpperCase(Locale.ROOT));
            }
        }
        return out;
    }

    private Set<String> lowerSet(List<String> values) {
        Set<String> out = new HashSet<>();
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                out.add(v.toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }
}
