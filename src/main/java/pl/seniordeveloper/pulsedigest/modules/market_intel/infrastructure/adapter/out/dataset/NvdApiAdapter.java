package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.http.ExternalRestClients;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.NvdVulnerability;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.NvdProperties;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Fetches recent high/critical CVEs from the NIST National Vulnerability Database (NVD) API 2.0.
 */
@Slf4j
@Service
public class NvdApiAdapter {

    private final NvdProperties properties;
    private final ObjectMapper objectMapper;
    private RestClient restClient;

    @Autowired
    public NvdApiAdapter(NvdProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void init() {
        this.restClient = ExternalRestClients.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("Accept", "application/json")
                .defaultHeader("User-Agent", "PulseDigest/1.0")
                .build();
    }

    public List<NvdVulnerability> fetchNvdVulnerabilities() {
        // HTTP errors propagate so MarketResearchService.fetchSource marks the source FAILED.
        String json = restClient.get()
                .uri(uri -> uri
                        .queryParam("pubStartDate", ZonedDateTime.now()
                                .minusHours(properties.lookbackHours())
                                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")))
                        .queryParam("pubEndDate", ZonedDateTime.now()
                                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")))
                        .queryParam("resultsPerPage", properties.resultsPerPage())
                        .build())
                .retrieve()
                .body(String.class);
        if (json == null || json.isBlank()) {
            return List.of();
        }
        List<NvdVulnerability> vulns = parseVulnerabilities(json);
        log.info("NVD: {} CVEs fetched (limit={})", vulns.size(), properties.resultsPerPage());
        return vulns;
    }

    List<NvdVulnerability> parseVulnerabilities(String json) {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(properties.lookbackHours());
        Set<String> minSeverities = upperSet(properties.minSeverities());
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode vulns = root.path("vulnerabilities");
            List<NvdVulnerability> result = new ArrayList<>();
            for (JsonNode entry : vulns) {
                JsonNode cve = entry.path("cve");
                String cveId = cve.path("id").asText("");
                if (cveId.isBlank()) {
                    continue;
                }
                LocalDateTime publishedAt = parseTimestamp(cve.path("published").asText(""));
                if (publishedAt == null || publishedAt.isBefore(cutoff)) {
                    continue;
                }
                JsonNode metrics = cve.path("metrics").path("cvssMetricV31");
                if (!metrics.isArray() || metrics.isEmpty()) {
                    continue;
                }
                JsonNode cvssData = metrics.get(0).path("cvssData");
                String severity = cvssData.path("baseSeverity").asText("").toUpperCase(Locale.ROOT);
                double cvssScore = cvssData.path("baseScore").asDouble(0.0);
                if (!minSeverities.isEmpty() && !minSeverities.contains(severity)) {
                    continue;
                }
                String description = extractEnglishDescription(cve);
                List<String> products = extractProducts(cve);
                String url = "https://nvd.nist.gov/vuln/detail/" + cveId;
                result.add(new NvdVulnerability(cveId, description, cvssScore, severity,
                        publishedAt, products, url));
            }
            return result;
        } catch (Exception e) {
            log.warn("NVD parse failed: {}", e.getMessage());
            return List.of();
        }
    }

    private String extractEnglishDescription(JsonNode cve) {
        for (JsonNode desc : cve.path("descriptions")) {
            if ("en".equals(desc.path("lang").asText(""))) {
                return desc.path("value").asText("");
            }
        }
        return "";
    }

    private List<String> extractProducts(JsonNode cve) {
        List<String> products = new ArrayList<>();
        for (JsonNode config : cve.path("configurations")) {
            for (JsonNode node : config.path("nodes")) {
                for (JsonNode match : node.path("cpeMatch")) {
                    addProductFromCpe(match, products);
                    if (products.size() >= 5) {
                        return products;
                    }
                }
            }
        }
        return products;
    }

    private void addProductFromCpe(JsonNode cpeMatch, List<String> products) {
        String criteria = cpeMatch.path("criteria").asText("");
        if (!criteria.isBlank() && criteria.startsWith("cpe:2.3:")) {
            String[] parts = criteria.split(":");
            if (parts.length >= 5) {
                String product = parts[3] + ":" + parts[4];
                if (!products.contains(product)) {
                    products.add(product);
                }
            }
        }
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
}
