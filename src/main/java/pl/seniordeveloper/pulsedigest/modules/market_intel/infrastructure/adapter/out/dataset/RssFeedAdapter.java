package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RssItem;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ReportProperties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
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

    private final ReportProperties reportProperties;
    private RestClient restClient;

    @PostConstruct
    void init() {
        this.restClient = RestClient.builder()
                .defaultHeader("User-Agent", "PulseDigest/1.0 rss-reader")
                .defaultHeader("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml")
                .build();
    }

    public List<RssItem> fetchAll() {
        ReportProperties.RssProperties cfg = reportProperties.rss();
        List<RssItem> result = new ArrayList<>();
        for (ReportProperties.RssProperties.FeedConfig feed : cfg.feeds()) {
            result.addAll(fetchFeed(feed.url(), feed.name(), cfg.limit()));
        }
        log.info("RSS łącznie: {} itemów z {} feedów", result.size(), cfg.feeds().size());
        return result;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private List<RssItem> fetchFeed(String url, String feedName, int limit) {
        try {
            String xml = restClient.get().uri(url).retrieve().body(String.class);
            if (xml == null || xml.isBlank()) {
                log.warn("Pusty feed: {}", feedName);
                return List.of();
            }
            List<RssItem> items = parseXml(xml, feedName, limit);
            log.info("Feed [{}]: {} itemów", feedName, items.size());
            return items;
        } catch (Exception e) {
            log.warn("Błąd pobierania feeda [{}] {}: {}", feedName, url, e.getMessage());
            return List.of();
        }
    }

    private List<RssItem> parseXml(String xml, String feedName, int limit) throws Exception {
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

        ZonedDateTime cutoff = ZonedDateTime.now().minusHours(24);
        List<RssItem> items = new ArrayList<>();
        for (int i = 0; i < nodes.getLength() && items.size() < limit; i++) {
            Element el = (Element) nodes.item(i);
            String title = text(el, "title");
            String url = isAtom ? atomLink(el) : text(el, "link");
            String desc = text(el, isAtom ? "summary" : "description");
            String dateRaw = isAtom ? firstOf(el, "updated", "published") : text(el, "pubDate");

            if (title.isBlank() || url.isBlank()) {
                continue;
            }
            if (!isWithin24h(dateRaw, cutoff)) {
                continue;
            }
            items.add(new RssItem(title, url, truncate(desc, 300), feedName));
        }
        return items;
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
     * Returns true when the item's date is within the last 24 h, or when the date is missing/unparseable.
     */
    private boolean isWithin24h(String dateRaw, ZonedDateTime cutoff) {
        if (dateRaw == null || dateRaw.isBlank()) {
            return true;
        }
        try {
            ZonedDateTime parsed = ZonedDateTime.parse(dateRaw, DateTimeFormatter.ISO_DATE_TIME);
            return parsed.isAfter(cutoff);
        } catch (DateTimeParseException ignored) {
            // ignored
        }
        try {
            ZonedDateTime parsed = ZonedDateTime.parse(dateRaw.trim(), RFC_822);
            return parsed.isAfter(cutoff);
        } catch (DateTimeParseException ignored) {
            // ignored
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
