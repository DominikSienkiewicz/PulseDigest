package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.http.ExternalRestClients;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.JepUpdate;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ReportProperties;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects JEP status changes by monitoring OpenJDK commit messages via the GitHub API.
 */
@Slf4j
@Service
public class OpenJdkJepAdapter {

    private static final Pattern JEP_ID_PATTERN = Pattern.compile("JEP[-\\s](\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern STATUS_PATTERN = Pattern.compile(
            "\\b(Candidate|Proposed to Target|Integrated|Delivered|Closed|Withdrawn)\\b",
            Pattern.CASE_INSENSITIVE);

    private final ReportProperties.OpenJdkProperties properties;
    private final ObjectMapper objectMapper;
    private RestClient restClient;

    @Autowired
    public OpenJdkJepAdapter(ReportProperties reportProperties, ObjectMapper objectMapper) {
        this(reportProperties.openJdk(), objectMapper);
    }

    OpenJdkJepAdapter(ReportProperties.OpenJdkProperties properties, ObjectMapper objectMapper) {
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

    public List<JepUpdate> fetchJepUpdates() {
        try {
            String since = ZonedDateTime.now()
                    .minusDays(properties.lookbackDays())
                    .format(DateTimeFormatter.ISO_INSTANT);
            String json = restClient.get()
                    .uri(uri -> uri
                            .queryParam("since", since)
                            .queryParam("per_page", 100)
                            .build())
                    .retrieve()
                    .body(String.class);
            if (json == null || json.isBlank()) {
                return List.of();
            }
            List<JepUpdate> updates = parseJepUpdates(json);
            log.info("OpenJDK JEP: {} updates (lookback={}d)", updates.size(), properties.lookbackDays());
            return updates;
        } catch (Exception e) {
            log.warn("OpenJDK JEP fetch failed: {}", e.getMessage());
            return List.of();
        }
    }

    List<JepUpdate> parseJepUpdates(String json) {
        Set<String> relevantStatuses = lowerSet(properties.relevantStatuses());
        try {
            JsonNode arr = objectMapper.readTree(json);
            List<JepUpdate> result = new ArrayList<>();
            Set<String> seenJeps = new HashSet<>();
            for (JsonNode commit : arr) {
                String message = commit.path("commit").path("message").asText("");
                Matcher jepMatcher = JEP_ID_PATTERN.matcher(message);
                if (!jepMatcher.find()) {
                    continue;
                }
                String jepNumber = jepMatcher.group(1);
                String jepId = "JEP-" + jepNumber;
                if (seenJeps.contains(jepId)) {
                    continue;
                }
                Matcher statusMatcher = STATUS_PATTERN.matcher(message);
                if (!statusMatcher.find()) {
                    continue;
                }
                String status = statusMatcher.group(1);
                if (!relevantStatuses.isEmpty() && !relevantStatuses.contains(status.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                String titleLine = extractTitleLine(message, jepId);
                LocalDateTime date = parseTimestamp(
                        commit.path("commit").path("committer").path("date").asText(""));
                String url = "https://openjdk.org/jeps/" + jepNumber;
                seenJeps.add(jepId);
                result.add(new JepUpdate(jepId, titleLine, status, date, url));
            }
            return result;
        } catch (Exception e) {
            log.warn("OpenJDK JEP parse failed: {}", e.getMessage());
            return List.of();
        }
    }

    private String extractTitleLine(String message, String jepId) {
        for (String line : message.split("\n")) {
            if (line.contains(jepId)) {
                String title = line.substring(line.indexOf(jepId) + jepId.length()).trim();
                if (title.startsWith(":")) {
                    title = title.substring(1).trim();
                }
                if (title.length() > 120) {
                    title = title.substring(0, 120).trim();
                }
                if (!title.isBlank()) {
                    return title;
                }
            }
        }
        return jepId;
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
