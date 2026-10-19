package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.archive;

import org.springframework.stereotype.Component;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ArchivedEdition;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * An Atom feed of the archive (N32).
 *
 * <p>Atom rather than RSS: dates are unambiguous (RFC 3339 instead of RSS's RFC 822 variants),
 * {@code id} is specified as an IRI rather than left to convention, and content type is explicit.
 * For a feed generated once and read by machines, the format with fewer things to get subtly wrong
 * is the right one.
 *
 * <p>One entry per edition, not per item. The digest's unit is the edition — an entry per story
 * would turn a twice-weekly feed into a firehose and lose the one thing the digest adds, which is
 * the selection.
 *
 * <p>Escaping is deliberately XML's, not HTML's: a feed is XML, and reusing the email's escaper here
 * would leave {@code '} and {@code "} wrong in attributes.
 */
@Component
public class AtomFeedBuilder {

    private static final String HREF_CLOSE = "\"/>\n";

    private static final DateTimeFormatter RFC3339 = DateTimeFormatter.ISO_INSTANT;
    private static final int SUMMARY_ITEMS = 5;

    public String build(List<ArchivedEdition> editions, String siteUrl) {
        String base = siteUrl.endsWith("/") ? siteUrl : siteUrl + "/";
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
                .append("<feed xmlns=\"http://www.w3.org/2005/Atom\" xml:lang=\"pl\">\n")
                .append("  <title>PulseDigest</title>\n")
                .append("  <subtitle>Dwa razy w tygodniu: sygnał zamiast szumu</subtitle>\n")
                .append("  <id>").append(xml(base)).append("</id>\n")
                .append("  <link rel=\"alternate\" type=\"text/html\" href=\"")
                .append(xml(base)).append(HREF_CLOSE)
                .append("  <link rel=\"self\" type=\"application/atom+xml\" href=\"")
                .append(xml(base + "feed.xml")).append(HREF_CLOSE)
                .append("  <updated>").append(updated(editions)).append("</updated>\n");

        for (ArchivedEdition edition : editions) {
            String url = base + ArchiveSiteBuilder.slug(edition) + ".html";
            sb.append("  <entry>\n")
                    .append("    <title>PulseDigest — ")
                    .append(xml(ArchiveSiteBuilder.day(edition).toString())).append("</title>\n")
                    // The page URL, not the job id: an id must be stable and resolvable, and the
                    // date-based page is both.
                    .append("    <id>").append(xml(url)).append("</id>\n")
                    .append("    <link rel=\"alternate\" type=\"text/html\" href=\"")
                    .append(xml(url)).append(HREF_CLOSE)
                    .append("    <updated>").append(RFC3339.format(edition.generatedAt()))
                    .append("</updated>\n")
                    .append("    <summary type=\"text\">").append(xml(summary(edition)))
                    .append("</summary>\n")
                    .append("  </entry>\n");
        }
        return sb.append("</feed>\n").toString();
    }

    /** The editorial where there is one, else the first few headlines — enough to decide to click. */
    private static String summary(ArchivedEdition edition) {
        String editorial = edition.report().editorial();
        if (editorial != null && !editorial.isBlank()) {
            return editorial;
        }
        List<DigestItem> items = edition.report().items();
        if (items == null || items.isEmpty()) {
            return "";
        }
        return items.stream().limit(SUMMARY_ITEMS).map(DigestItem::title).reduce((a, b) -> a + " · " + b)
                .orElse("");
    }

    private static String updated(List<ArchivedEdition> editions) {
        return RFC3339.format(editions.isEmpty()
                ? java.time.Instant.now().atZone(ZoneOffset.UTC).toInstant()
                : editions.getFirst().generatedAt());
    }

    private static String xml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
