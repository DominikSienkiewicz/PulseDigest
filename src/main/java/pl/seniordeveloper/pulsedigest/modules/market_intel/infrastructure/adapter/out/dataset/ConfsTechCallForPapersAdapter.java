package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.CallForPapers;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.CallForPapersPort;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.CallForPapersProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.http.ExternalRestClients;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads open calls for papers from the conference dataset behind confs.tech (N09).
 *
 * <p>Chosen over scraping because it is already structured: one JSON file per topic per year, with
 * {@code cfpEndDate} and {@code cfpUrl} as fields rather than as prose on a landing page. A deadline
 * parsed out of free text and got wrong is worse than no deadline at all — the reader would plan
 * around it.
 *
 * <p>Two years are read, because a CFP open today routinely closes for an event in the next calendar
 * year, and a topic file missing entirely is normal rather than an error: not every topic exists for
 * every year.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ConfsTechCallForPapersAdapter implements CallForPapersPort {

    private final CallForPapersProperties properties;
    private final ObjectMapper objectMapper;
    private RestClient client;

    @PostConstruct
    void init() {
        this.client = ExternalRestClients.builder().baseUrl(properties.baseUrl()).build();
    }

    @Override
    public List<CallForPapers> openCalls(LocalDate today) {
        if (!properties.enabled()) {
            return List.of();
        }
        LocalDate horizon = today.plusDays(properties.lookaheadDays());
        Map<String, CallForPapers> byConference = new LinkedHashMap<>();

        for (int year = today.getYear(); year <= today.getYear() + 1; year++) {
            for (String topic : properties.topics()) {
                for (Conference conference : fetchTopic(year, topic)) {
                    CallForPapers call = toOpenCall(conference, today, horizon);
                    if (call != null) {
                        // The same conference appears in several topic files; first deadline wins,
                        // and they agree because they are the same record.
                        byConference.putIfAbsent(call.conference(), call);
                    }
                }
            }
        }

        List<CallForPapers> open = byConference.values().stream()
                .sorted(Comparator.comparing(CallForPapers::deadline))
                .limit(properties.limit())
                .toList();
        log.info("call-for-papers open={} horizon_days={} topics={}",
                open.size(), properties.lookaheadDays(), properties.topics().size());
        return open;
    }

    /** A missing topic file is normal; a broken one must not take the digest down with it. */
    private List<Conference> fetchTopic(int year, String topic) {
        try {
            String body = client.get().uri("/{year}/{topic}.json", year, topic).retrieve().body(String.class);
            if (body == null || body.isBlank()) {
                return List.of();
            }
            return List.of(objectMapper.readValue(body, Conference[].class));
        } catch (Exception e) {
            log.debug("No call-for-papers data for {}/{}: {}", year, topic, e.getMessage());
            return List.of();
        }
    }

    private static CallForPapers toOpenCall(Conference conference, LocalDate today, LocalDate horizon) {
        if (conference.cfpEndDate() == null || conference.cfpUrl() == null
                || conference.cfpUrl().isBlank() || conference.name() == null) {
            return null;
        }
        LocalDate deadline;
        try {
            deadline = LocalDate.parse(conference.cfpEndDate());
        } catch (Exception _) {
            return null;
        }
        if (deadline.isBefore(today) || deadline.isAfter(horizon)) {
            return null;
        }
        return new CallForPapers(conference.name(), conference.cfpUrl(), deadline, location(conference));
    }

    private static String location(Conference conference) {
        List<String> parts = new ArrayList<>();
        if (conference.city() != null && !conference.city().isBlank()) {
            parts.add(conference.city());
        }
        if (conference.country() != null && !conference.country().isBlank()) {
            parts.add(conference.country());
        }
        if (Boolean.TRUE.equals(conference.online())) {
            parts.add("online");
        }
        return String.join(", ", parts);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Conference(String name, String city, String country, Boolean online,
                              String cfpUrl, String cfpEndDate) {
    }
}
