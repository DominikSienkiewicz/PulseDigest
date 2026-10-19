package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.archive;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ArchivedEdition;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import static pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email
        .EmailFormatting.escapeHtml;

/**
 * The static pages around the editions: an index and a search page (N31, N33).
 *
 * <p>No framework and no build step. The archive is a handful of files served by GitHub Pages, and
 * a static site generator would be more machinery than the thing it generates — the whole site is
 * an index, one page per edition, a feed and a JSON file.
 *
 * <p>The search is client-side over that JSON for the same reason: an archive of a hundred editions
 * is a few hundred kilobytes, which the browser can filter faster than a round trip, and it needs no
 * service to stay up.
 */
@Component
@RequiredArgsConstructor
public class ArchiveSiteBuilder {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM.yyyy",
            Locale.of("pl", "PL"));

    private static final String STYLE = """
            :root{color-scheme:light dark;--bg:#f9fafb;--fg:#111827;--muted:#4b5563;--card:#fff;\
            --line:#e5e7eb;--link:#1d4ed8}
            @media (prefers-color-scheme:dark){:root{--bg:#0b1220;--fg:#e5e7eb;--muted:#9ca3af;\
            --card:#111827;--line:#1f2937;--link:#93c5fd}}
            *{box-sizing:border-box}body{margin:0;padding:24px 16px;background:var(--bg);color:var(--fg);\
            font:16px/1.6 -apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif}
            main{max-width:760px;margin:0 auto}h1{font-size:24px;margin:0 0 4px}
            .sub{color:var(--muted);font-size:14px;margin:0 0 24px}
            a{color:var(--link)}ul{list-style:none;padding:0;margin:0}
            li{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:14px 16px;\
            margin-bottom:10px}
            .date{color:var(--muted);font-size:13px}
            input{width:100%;padding:12px 14px;font-size:16px;border:1px solid var(--line);\
            border-radius:10px;background:var(--card);color:var(--fg);margin-bottom:16px}
            footer{color:var(--muted);font-size:13px;margin-top:32px;text-align:center}
            """;

    /** The landing page: every edition, newest first. */
    public String buildIndex(List<ArchivedEdition> editions) {
        StringBuilder sb = new StringBuilder(head("PulseDigest — archiwum"));
        sb.append("<h1>📡 PulseDigest</h1>")
                .append("<p class=\"sub\">Archiwum &middot; ")
                .append(editions.size()).append(' ').append(editionWord(editions.size()))
                .append(" &middot; ")
                .append("<a href=\"search.html\">szukaj</a> &middot; ")
                .append("<a href=\"feed.xml\">Atom</a></p><ul>");
        for (ArchivedEdition edition : editions) {
            LocalDate day = day(edition);
            sb.append("<li><a href=\"").append(slug(edition)).append(".html\">")
                    .append(day.format(DAY)).append("</a> ")
                    .append("<span class=\"date\">&middot; ")
                    .append(edition.report().items() == null ? 0 : edition.report().items().size())
                    .append(" pozycji</span><div class=\"date\">")
                    .append(escapeHtml(preview(edition))).append("</div></li>");
        }
        return sb.append("</ul>").append(footer()).toString();
    }

    /**
     * The search page. One input, one fetch of the index, filtering in the browser.
     *
     * <p>Deliberately plain: no dependency, no bundler, and it degrades to the index link when
     * JavaScript is off, because an archive that needs a framework to be read is not an archive.
     */
    public String buildSearchPage() {
        return head("PulseDigest — szukaj")
                + "<h1>Szukaj w archiwum</h1>"
                + "<p class=\"sub\"><a href=\"index.html\">&larr; wszystkie wydania</a></p>"
                + "<input id=\"q\" type=\"search\" placeholder=\"tytuł, źródło, "
                + "kategoria…\" autofocus>"
                + "<ul id=\"r\"></ul>"
                + "<script>"
                + "const r=document.getElementById('r'),q=document.getElementById('q');let idx=[];"
                + "fetch('search-index.json').then(x=>x.json()).then(d=>{idx=d;draw(idx.slice(0,40));});"
                + "function draw(items){r.innerHTML=items.map(i=>"
                + "`<li><a href=\"${i.u}\">${esc(i.t)}</a>"
                + "<div class=\"date\">${esc(i.s)} &middot; ${esc(i.c)} &middot; "
                + "<a href=\"${i.e}.html\">${i.d}</a></div></li>`).join('')"
                + "||'<li>Nic nie pasuje.</li>';}"
                + "function esc(s){return (s||'').replace(/[&<>\"]/g,c=>"
                + "({'&':'&amp;','<':'&lt;','>':'&gt;','\"':'&quot;'}[c]));}"
                + "q.addEventListener('input',()=>{const v=q.value.toLowerCase().trim();"
                + "draw(!v?idx.slice(0,40):idx.filter(i=>"
                + "(i.t+' '+i.s+' '+i.c).toLowerCase().includes(v)).slice(0,200));});"
                + "</script>"
                + footer();
    }

    /** Wraps one depersonalised edition in a page with a way back to the index. */
    public String buildEditionPage(ArchivedEdition edition, String editionHtml) {
        String nav = "<div style=\"max-width:720px;margin:0 auto 12px;font:14px/1.6 -apple-system,"
                + "BlinkMacSystemFont,'Segoe UI',sans-serif\">"
                + "<a href=\"index.html\" style=\"color:#1d4ed8\">&larr; archiwum PulseDigest</a>"
                + " &middot; <span style=\"color:#6b7280\">" + day(edition).format(DAY) + "</span></div>";
        // No marker attribute on <body>. An earlier version stamped data-edition="…" there, which
        // served nothing and tripped the archive's own leak test — the string it added contains
        // "edition=", which is exactly what that test scans for. A paranoid check is worth more than
        // a decorative attribute.
        return editionHtml.replace("<div class=\"pd-card\"", nav + "<div class=\"pd-card\"");
    }

    /** The flat index the search page filters: one row per item, small enough to ship whole. */
    public String buildSearchIndex(List<ArchivedEdition> editions) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (ArchivedEdition edition : editions) {
            List<DigestItem> items = edition.report().items();
            if (items == null) {
                continue;
            }
            for (DigestItem item : items) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append("{\"t\":").append(json(item.title()))
                        .append(",\"u\":").append(json(item.url()))
                        .append(",\"s\":").append(json(item.source()))
                        .append(",\"c\":").append(json(item.category()))
                        .append(",\"e\":").append(json(slug(edition)))
                        .append(",\"d\":").append(json(day(edition).format(DAY)))
                        .append('}');
            }
        }
        return sb.append(']').toString();
    }

    /** Polish needs three forms, and "3 wydań" on the landing page would be a small, visible error. */
    private static String editionWord(int count) {
        if (count == 1) {
            return "wydanie";
        }
        int lastTwo = count % 100;
        int last = count % 10;
        boolean few = last >= 2 && last <= 4 && (lastTwo < 12 || lastTwo > 14);
        return few ? "wydania" : "wydań";
    }

    static String slug(ArchivedEdition edition) {
        return day(edition).toString();
    }

    static LocalDate day(ArchivedEdition edition) {
        return edition.generatedAt().atZone(ZoneOffset.UTC).toLocalDate();
    }

    private static String preview(ArchivedEdition edition) {
        String preview = edition.report().emailPreview();
        if (preview != null && !preview.isBlank()) {
            return preview;
        }
        List<DigestItem> items = edition.report().items();
        return items == null || items.isEmpty() ? "" : items.getFirst().title();
    }

    private static String head(String title) {
        return "<!DOCTYPE html><html lang=\"pl\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>" + escapeHtml(title) + "</title>"
                + "<link rel=\"alternate\" type=\"application/atom+xml\" href=\"feed.xml\">"
                + "<style>" + STYLE + "</style></head><body><main>";
    }

    private static String footer() {
        return "<footer>Wydania bez linków zwrotnych i bez danych czytelnika. "
                + "<a href=\"https://github.com/DominikSienkiewicz/PulseDigest\">źródło</a>"
                + "</footer></main></body></html>";
    }

    /** Minimal JSON string escaping — the index is generated, never parsed back here. */
    private static String json(String value) {
        if (value == null) {
            return "\"\"";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n', '\r', '\t' -> sb.append(' ');
                default -> {
                    if (c < 0x20) {
                        sb.append(' ');
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
