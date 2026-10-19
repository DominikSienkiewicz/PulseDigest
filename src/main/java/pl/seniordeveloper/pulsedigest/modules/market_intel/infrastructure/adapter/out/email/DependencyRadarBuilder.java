package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DependencyRelease;

import java.util.List;

import static pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email
        .EmailFormatting.escapeHtml;

/**
 * The reader's own outdated dependencies (N07).
 *
 * <p>Rendered deterministically from a registry lookup, never through the model: "you pin 3.5.0 and
 * 3.5.3 is out" is a fact with one correct phrasing, and handing it to an LLM to summarise would
 * risk it being reworded into something approximate.
 *
 * <p>Empty when nothing is behind — and empty is the normal state for a reader who keeps up, so the
 * section has to disappear rather than announce that it found nothing.
 */
final class DependencyRadarBuilder {

    private DependencyRadarBuilder() {
    }

    static String buildDependencyRadarSection(List<DependencyRelease> releases) {
        if (releases == null || releases.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"padding:20px 28px;background:#f5f3ff;"
                + "border-bottom:1px solid #ddd6fe;border-left:4px solid #7c3aed\">");
        sb.append("<h2 style=\"color:#5b21b6;font-size:15px;margin:0 0 4px\">")
                .append("&#128230; Twoje zale&#380;no&#347;ci &mdash; nowsze wydania</h2>");
        sb.append("<p style=\"color:#6d28d9;font-size:11px;margin:0 0 10px\">")
                .append("Z plik&oacute;w build Twoich projekt&oacute;w, nie z listy obserwowanych repozytori&oacute;w.</p>");
        sb.append("<ul style=\"margin:0;padding:0;list-style:none\">");
        for (DependencyRelease release : releases) {
            sb.append("<li style=\"margin-bottom:8px;color:#4c1d95;font-size:14px\">")
                    .append("<span style=\"font-weight:600\">")
                    .append(escapeHtml(release.coordinate().key())).append("</span>")
                    .append("<div style=\"color:#6d28d9;font-size:12px;margin-top:2px\">")
                    .append("pinujesz <code>").append(escapeHtml(release.coordinate().pinnedVersion()))
                    .append("</code> &middot; dost&#281;pne <code>")
                    .append(escapeHtml(release.latestVersion())).append("</code>")
                    .append(originNote(release))
                    .append("</div></li>");
        }
        sb.append("</ul></div>");
        return sb.toString();
    }

    /** Names the build file a pin came from, so the reader knows which project to touch. */
    private static String originNote(DependencyRelease release) {
        String origin = release.coordinate().origin();
        if (origin == null || origin.isBlank() || "configuration".equals(origin)) {
            return "";
        }
        return " &middot; " + escapeHtml(origin);
    }
}
