package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.WatchlistHit;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.WatchlistScan;

import static pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email
        .EmailFormatting.escapeHtml;

/**
 * Renders the 🎯 radar block: one line per watched technology, including the ones nothing was said
 * about. Extracted from {@code ReportEmailBuilder} to keep that class under the 500-line budget.
 */
final class WatchlistBuilder {

    private WatchlistBuilder() {
    }

    static String buildWatchlistSection(WatchlistScan scan) {
        if (scan == null || scan.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"padding:20px 28px;background:#f7fee7;border-bottom:1px solid #d9f99d\">");
        sb.append("<h2 style=\"color:#3f6212;font-size:15px;margin:0 0 10px\">")
                .append("&#127919; Twój radar</h2>");
        sb.append("<div style=\"font-size:13px;color:#365314;line-height:1.7\">");
        for (WatchlistHit hit : scan.hits()) {
            sb.append(hit.mentions() == 0
                            ? "<span style=\"color:#84cc16\">&#9675;</span> "
                            : "<span style=\"color:#4d7c0f;font-weight:700\">&#9679;</span> ")
                    .append(escapeHtml(hit.keyword()))
                    .append(": ")
                    .append(mentionsLabel(hit.mentions()))
                    .append("<br>");
        }
        sb.append("</div></div>");
        return sb.toString();
    }

    private static String mentionsLabel(int mentions) {
        if (mentions == 0) {
            return "<span style=\"color:#65a30d\">0 wzmianek</span>";
        }
        return mentions + (mentions == 1 ? " wzmianka" : " wzmianek");
    }
}
