package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.http.ExternalRestClients;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RssItem;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.RssProperties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Optional;
import java.util.List;
import java.util.Locale;

@Slf4j
@RequiredArgsConstructor
@Service
public class RssFeedAdapter {

    /**
     * RFC 822 as used by most RSS feeds (e.g. "Mon, 28 Apr 2026 12:00:00 +0000" or "...GMT").
     */
    private static final DateTimeFormatter RFC_822 =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH);

    private final RssProperties cfg;
    private RestClient restClient;

    @PostConstruct
    void init() {
        this.restClient = ExternalRestClients.builder()
                .defaultHeader("User-Agent", "PulseDigest/1.0 rss-reader")
                .defaultHeader("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml")
                .build();
    }

    public List<RssItem> fetchAll() {
        List<RssItem> result = new ArrayList<>();
        int failed = 0;
        Exception lastError = null;
        for (RssProperties.FeedConfig feed : cfg.feeds()) {
            try {
                result.addAll(fetchFeed(feed.url(), feed.name(), cfg.limit()));
            } catch (Exception e) {
                failed++;
                lastError = e;
                log.warn("Błąd pobierania feeda [{}] {}: {}", feed.name(), feed.url(), e.getMessage());
            }
        }
        if (failed > 0 && failed == cfg.feeds().size()) {
            throw new IllegalStateException(
                    "All " + failed + " RSS feeds failed; last error: "
                            + (lastError != null ? lastError.getMessage() : "unknown"),
                    lastError);
        }
        log.info("RSS łącznie: {} itemów z {} feedów ({} udanych)",
                result.size(), cfg.feeds().size(), cfg.feeds().size() - failed);
        return result;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private List<RssItem> fetchFeed(String url, String feedName, int limit)
            throws ParserConfigurationException, SAXException, IOException {
        // HTTP errors propagate; caller aggregates per-feed failures.
        String xml = restClient.get().uri(url).retrieve().body(String.class);
        if (xml == null || xml.isBlank()) {
            log.warn("Pusty feed: {}", feedName);
            return List.of();
        }
        List<RssItem> items = parseXml(xml, feedName, limit);
        log.info("Feed [{}]: {} itemów", feedName, items.size());
        return items;
    }

    private List<RssItem> parseXml(String xml, String feedName, int limit)
            throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // XXE prevention
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setExpandEntityReferences(false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new InputSource(new StringReader(xml)));
        doc.getDocumentElement().normalize();

        // Try RSS <item>, fall back to Atom <entry>
        NodeList nodes = doc.getElementsByTagName("item");
        boolean isAtom = nodes.getLength() == 0;
        if (isAtom) {
            nodes = doc.getElementsByTagName("entry");
        }

        int lookbackHours = cfg.lookbackHours() > 0 ? cfg.lookbackHours() : 24;
        ZonedDateTime cutoff = ZonedDateTime.now(ZoneOffset.UTC).minusHours(lookbackHours);
        List<RssItem> items = new ArrayList<>();
        for (int i = 0; i < nodes.getLength() && items.size() < limit; i++) {
            Element el = (Element) nodes.item(i);
            parseItem(el, isAtom, cutoff, feedName).ifPresent(items::add);
        }
        return items;
    }

    private Optional<RssItem> parseItem(Element el, boolean isAtom, ZonedDateTime cutoff, String feedName) {
        String title = text(el, "title");
        String url = isAtom ? atomLink(el) : text(el, "link");
        String desc = text(el, isAtom ? "summary" : "description");
        String dateRaw = isAtom ? firstOf(el, "updated", "published") : text(el, "pubDate");
        if (title.isBlank() || url.isBlank()) {
            return Optional.empty();
        }
        if (!isWithinWindow(dateRaw, cutoff)) {
            return Optional.empty();
        }
        return Optional.of(new RssItem(title, url, truncate(desc, 300), feedName));
    }

    private String text(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        if (nodes.getLength() == 0) {
            return "";
        }
        String content = nodes.item(0).getTextContent();
        return content != null ? content.trim() : "";
    }

    /**
     * Atom <link> stores URL in href attribute, not text content.
     */
    private String atomLink(Element entry) {
        NodeList links = entry.getElementsByTagName("link");
        for (int i = 0; i < links.getLength(); i++) {
            Element link = (Element) links.item(i);
            String rel = link.getAttribute("rel");
            String href = link.getAttribute("href");
            if (!href.isBlank() && (rel.isBlank() || rel.equals("alternate"))) {
                return href;
            }
        }
        return "";
    }

    private String firstOf(Element parent, String... tags) {
        for (String tag : tags) {
            String val = text(parent, tag);
            if (!val.isBlank()) {
                return val;
            }
        }
        return "";
    }

    /**
     * Returns true when the item's date is after the cutoff (within the configured lookback window),
     * or when the date is missing/unparseable.
     */
    private boolean isWithinWindow(String dateRaw, ZonedDateTime cutoff) {
        if (dateRaw == null || dateRaw.isBlank()) {
            return true;
        }
        try {
            ZonedDateTime parsed = ZonedDateTime.parse(dateRaw, DateTimeFormatter.ISO_DATE_TIME);
            return parsed.isAfter(cutoff);
        } catch (DateTimeParseException _) {
            // unparseable in this format → fall through to the next parse attempt
        }
        try {
            ZonedDateTime parsed = ZonedDateTime.parse(dateRaw.trim(), RFC_822);
            return parsed.isAfter(cutoff);
        } catch (DateTimeParseException _) {
            // unparseable in this format → fall through to the next parse attempt
        }
        return true;
    }

    private String truncate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s != null ? s : "";
        }
        return s.substring(0, max);
    }
}
