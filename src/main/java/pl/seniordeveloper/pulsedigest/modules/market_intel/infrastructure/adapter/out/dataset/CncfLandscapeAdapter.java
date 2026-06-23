package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.http.ExternalRestClients;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.CncfProjectUpdate;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.CncfLandscapeProperties;

import java.time.ZoneOffset;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Optional;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects CNCF project additions and status changes by monitoring the cncf/landscape GitHub repository.
 */
@Slf4j
@Service
public class CncfLandscapeAdapter {

    private static final Pattern STATUS_PATTERN = Pattern.compile(
            "\\b(sandbox|incubating|graduated|archived)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PROJECT_PATTERN = Pattern.compile(
            "^(?:Add|Update|Move|Promote)\\s++(?:the\\s++)?(?:CNCF\\s++)?(?:project\\s++)?([\\w.\\-]+)",
            Pattern.CASE_INSENSITIVE);

    private final CncfLandscapeProperties properties;
    private final ObjectMapper objectMapper;
    private RestClient restClient;

    @Autowired
    public CncfLandscapeAdapter(CncfLandscapeProperties properties, ObjectMapper objectMapper) {
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

    public List<CncfProjectUpdate> fetchCncfLandscapeChanges() {
        // HTTP errors propagate so MarketResearchService.fetchSource marks the source FAILED.
        String since = ZonedDateTime.now(ZoneOffset.UTC)
                .minusDays(properties.lookbackDays())
                .format(DateTimeFormatter.ISO_INSTANT);
        String json = restClient.get()
                .uri(uri -> uri
                        .queryParam("since", since)
                        .queryParam("path", "landscape.yml")
                        .queryParam("per_page", 100)
                        .build())
                .retrieve()
                .body(String.class);
        if (json == null || json.isBlank()) {
            return List.of();
        }
        List<CncfProjectUpdate> changes = parseLandscapeChanges(json);
        log.info("CNCF Landscape: {} changes (lookback={}d)", changes.size(), properties.lookbackDays());
        return changes;
    }

    List<CncfProjectUpdate> parseLandscapeChanges(String json) {
        Set<String> relevantStatuses = lowerSet(properties.relevantStatuses());
        try {
            JsonNode arr = objectMapper.readTree(json);
            List<CncfProjectUpdate> result = new ArrayList<>();
            for (JsonNode commit : arr) {
                parseCommit(commit, relevantStatuses).ifPresent(result::add);
            }
            return result;
        } catch (Exception e) {
            log.warn("CNCF Landscape parse failed: {}", e.getMessage());
            return List.of();
        }
    }

    private Optional<CncfProjectUpdate> parseCommit(JsonNode commit, Set<String> relevantStatuses) {
        String message = commit.path("commit").path("message").asText("");
        Matcher statusMatcher = STATUS_PATTERN.matcher(message);
        if (!statusMatcher.find()) {
            return Optional.empty();
        }
        String status = statusMatcher.group(1).toLowerCase(java.util.Locale.ROOT);
        if (!relevantStatuses.isEmpty() && !relevantStatuses.contains(status)) {
            return Optional.empty();
        }
        String projectName = extractProjectName(message);
        String category = extractCategory(message);
        String url = "https://landscape.cncf.io/?item=" + projectName.replace(' ', '-').toLowerCase(java.util.Locale.ROOT);
        LocalDateTime date = parseTimestamp(
                commit.path("commit").path("committer").path("date").asText(""));
        return Optional.of(new CncfProjectUpdate(projectName, category,
                status.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + status.substring(1),
                message.split("\n")[0].trim(), url, date));
    }

    private String extractProjectName(String message) {
        Matcher m = PROJECT_PATTERN.matcher(message);
        if (m.find()) {
            return m.group(1);
        }
        String firstLine = message.split("\n")[0].trim();
        String name = firstLine.replaceAll("^(?:Add|Update|Move|Promote)\\s++(?:the\\s++)?(?:CNCF\\s++)?(?:project\\s++)?", "");
        name = name.replaceAll("\\bto\\s++(?:sandbox|incubating|graduated|archived).*$", "").trim();
        if (name.length() > 60) {
            name = name.substring(0, 60);
        }
        return name.isBlank() ? "Unknown CNCF Project" : name;
    }

    private String extractCategory(String message) {
        if (message.toLowerCase(java.util.Locale.ROOT).contains("observability")) {
            return "Observability";
        }
        if (message.toLowerCase(java.util.Locale.ROOT).contains("service mesh")) {
            return "Service Mesh";
        }
        if (message.toLowerCase(java.util.Locale.ROOT).contains("ci") || message.toLowerCase(java.util.Locale.ROOT).contains("cd")) {
            return "CI/CD";
        }
        if (message.toLowerCase(java.util.Locale.ROOT).contains("storage")) {
            return "Storage";
        }
        return "Platform";
    }

    private LocalDateTime parseTimestamp(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw, DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception _) {
            return null;
        }
    }

    private Set<String> lowerSet(List<String> values) {
        Set<String> out = new HashSet<>();
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                out.add(v.toLowerCase(java.util.Locale.ROOT));
            }
        }
        return out;
    }
}
