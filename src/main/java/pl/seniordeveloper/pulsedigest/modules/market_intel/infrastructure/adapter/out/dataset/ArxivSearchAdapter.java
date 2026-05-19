package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.http.ExternalRestClients;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchPaper;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ArxivProperties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Adapter that fetches and filters research papers from the arXiv API.
 */
@Slf4j
@Service
public class ArxivSearchAdapter {

    private static final String BASE_URL = "https://export.arxiv.org";

    private final ArxivProperties properties;
    private RestClient restClient;

    @Autowired
    public ArxivSearchAdapter(ArxivProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void init() {
        this.restClient = ExternalRestClients.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("User-Agent", "PulseDigest/1.0 arxiv-reader")
                .build();
    }

    public List<ResearchPaper> fetchLatestPapers() {
        String categoryQuery = buildCategoryQuery();
        // HTTP errors propagate so MarketResearchService.fetchSource marks the source FAILED.
        String xml = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/query")
                        .queryParam("search_query", categoryQuery)
                        .queryParam("start", 0)
                        .queryParam("max_results", properties.maxResults())
                        .queryParam("sortBy", "submittedDate")
                        .queryParam("sortOrder", "descending")
                        .build())
                .retrieve()
                .body(String.class);
        if (xml == null || xml.isBlank()) {
            log.warn("arXiv returned empty response");
            return List.of();
        }
        List<ResearchPaper> papers = parseFeed(xml, properties.lookbackHours());
        log.info("arXiv: {} papers after filtering (query={})", papers.size(), categoryQuery);
        return papers;
    }

    List<ResearchPaper> parseFeed(String xml, int lookbackHours) {
        Set<String> keywords = Arrays.stream(properties.keywords().split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
        LocalDateTime cutoff = LocalDateTime.now().minusHours(lookbackHours);

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xml)));
            doc.getDocumentElement().normalize();

            NodeList entries = doc.getElementsByTagName("entry");
            List<ResearchPaper> result = new ArrayList<>();

            for (int i = 0; i < entries.getLength(); i++) {
                Element entry = (Element) entries.item(i);
                parseEntry(entry, keywords, cutoff).ifPresent(result::add);
            }
            return result;
        } catch (Exception e) {
            log.warn("arXiv Atom parse failed: {}", e.getMessage());
            return List.of();
        }
    }

    private Optional<ResearchPaper> parseEntry(Element entry, Set<String> keywords, LocalDateTime cutoff) {
        String idUrl = text(entry, "id");
        String title = text(entry, "title").replaceAll("\\s+", " ").trim();
        String summary = text(entry, "summary").replaceAll("\\s+", " ").trim();
        String published = text(entry, "published");

        if (idUrl.isBlank() || title.isBlank() || published.isBlank()) {
            return Optional.empty();
        }

        LocalDateTime publishedAt;
        try {
            publishedAt = LocalDateTime.parse(published, DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception e) {
            return Optional.empty();
        }

        if (publishedAt.isBefore(cutoff)) {
            return Optional.empty();
        }

        String lowerTitle = title.toLowerCase();
        String lowerSummary = summary.toLowerCase();
        boolean matchesKeyword = keywords.stream()
                .anyMatch(kw -> lowerTitle.contains(kw) || lowerSummary.contains(kw));
        if (!matchesKeyword) {
            return Optional.empty();
        }

        String arxivId = extractArxivId(idUrl);
        String url = "https://arxiv.org/abs/" + arxivId;

        List<String> authors = new ArrayList<>();
        NodeList authorNodes = entry.getElementsByTagName("author");
        for (int i = 0; i < authorNodes.getLength(); i++) {
            Element author = (Element) authorNodes.item(i);
            NodeList names = author.getElementsByTagName("name");
            if (names.getLength() > 0) {
                authors.add(names.item(0).getTextContent().trim());
            }
        }

        String primaryCategory = "cs.AI";
        NodeList categories = entry.getElementsByTagName("category");
        if (categories.getLength() > 0) {
            primaryCategory = ((Element) categories.item(0)).getAttribute("term");
        }

        String abstractText = summary.length() > 500 ? summary.substring(0, 497) + "..." : summary;

        return Optional.of(new ResearchPaper(
                arxivId, title, abstractText, List.copyOf(authors), url, primaryCategory, publishedAt));
    }

    private String text(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        if (nodes.getLength() == 0) {
            return "";
        }
        String content = nodes.item(0).getTextContent();
        return content != null ? content.trim() : "";
    }

    private String extractArxivId(String idUrl) {
        String[] parts = idUrl.split("/");
        String last = parts[parts.length - 1];
        return last.replaceAll("v\\d+$", "");
    }

    private String buildCategoryQuery() {
        return Arrays.stream(properties.categories().split(","))
                .map(c -> "cat:" + c.trim())
                .collect(Collectors.joining(" OR "));
    }
}
