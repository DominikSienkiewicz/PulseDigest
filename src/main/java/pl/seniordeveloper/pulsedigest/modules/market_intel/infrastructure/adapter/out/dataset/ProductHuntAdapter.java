package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ProductHuntPost;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ReportProperties;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Fetches recent product launches from the Product Hunt GraphQL API.
 * Degrades gracefully when no developer token is configured.
 */
@Slf4j
@Service
public class ProductHuntAdapter {

    private static final String GRAPHQL_QUERY =
            "query TopPosts($first: Int!) { "
                    + "posts(order: VOTES, first: $first) { "
                    + "edges { node { name tagline url votesCount createdAt "
                    + "topics { edges { node { name } } } } } } }";

    private final ReportProperties.ProductHuntProperties properties;
    private final ObjectMapper objectMapper;
    private RestClient restClient;

    @Autowired
    public ProductHuntAdapter(ReportProperties reportProperties, ObjectMapper objectMapper) {
        this(reportProperties.productHunt(), objectMapper);
    }

    ProductHuntAdapter(ReportProperties.ProductHuntProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void init() {
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("Accept", "application/json")
                .defaultHeader("User-Agent", "PulseDigest/1.0")
                .build();
    }

    public List<ProductHuntPost> fetchProductLaunches() {
        if (properties.developerToken() == null || properties.developerToken().isBlank()) {
            log.info("Product Hunt: brak developer tokenu, pomijam fetch");
            return List.of();
        }
        try {
            Map<String, Object> body = Map.of(
                    "query", GRAPHQL_QUERY,
                    "variables", Map.of("first", Math.max(properties.minVotes() > 0 ? 25 : 10, 25))
            );
            String json = restClient.post()
                    .uri("")
                    .header("Authorization", "Bearer " + properties.developerToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            if (json == null || json.isBlank()) {
                return List.of();
            }
            List<ProductHuntPost> posts = parsePosts(json);
            log.info("Product Hunt: {} launches", posts.size());
            return posts;
        } catch (Exception e) {
            log.warn("Product Hunt fetch failed: {}", e.getMessage());
            return List.of();
        }
    }

    List<ProductHuntPost> parsePosts(String json) {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(properties.lookbackHours());
        Set<String> relevantTopics = lowerSet(properties.relevantTopics());
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode edges = root.path("data").path("posts").path("edges");
            List<ProductHuntPost> result = new ArrayList<>();
            for (JsonNode edge : edges) {
                JsonNode node = edge.path("node");
                int votes = node.path("votesCount").asInt(0);
                if (votes < properties.minVotes()) {
                    continue;
                }
                LocalDateTime createdAt = parseTimestamp(node.path("createdAt").asText(""));
                if (createdAt == null || createdAt.isBefore(cutoff)) {
                    continue;
                }
                List<String> topics = extractTopics(node);
                if (!relevantTopics.isEmpty()
                        && topics.stream().map(t -> t.toLowerCase(Locale.ROOT))
                                .noneMatch(relevantTopics::contains)) {
                    continue;
                }
                String name = node.path("name").asText("");
                String tagline = node.path("tagline").asText("");
                String url = node.path("url").asText("");
                if (name.isBlank() || url.isBlank()) {
                    continue;
                }
                result.add(new ProductHuntPost(name, tagline, url, votes, topics, createdAt));
            }
            return result;
        } catch (Exception e) {
            log.warn("Product Hunt parse failed: {}", e.getMessage());
            return List.of();
        }
    }

    private List<String> extractTopics(JsonNode node) {
        List<String> topics = new ArrayList<>();
        for (JsonNode edge : node.path("topics").path("edges")) {
            String name = edge.path("node").path("name").asText("");
            if (!name.isBlank()) {
                topics.add(name);
            }
        }
        return topics;
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
