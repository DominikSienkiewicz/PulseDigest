package pl.seniordeveloper.pulsedigest.shared.util;

import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Strip-uje parametry trackingowe (utm_*, fbclid, etc.) z URL-i, żeby:
 * (1) nie zaśmiecać linków w mailu, (2) umożliwić deduplikację po URL,
 * (3) nie propagować naszych UTM-ów do reklamodawców gdy user kliknie.
 *
 * <p>Nie strip-ujemy parametrów które mają semantyczne znaczenie w niektórych
 * domenach (np. {@code ref} jako branch na GitHub) — fałszywie pozytywne usunięcie
 * łamie linki, dlatego jesteśmy konserwatywni.
 */
public final class UrlCanonicalizer {

    private static final Set<String> EXACT_TRACKING_PARAMS = Set.of(
            "fbclid", "gclid", "gbraid", "wbraid",
            "mc_cid", "mc_eid",
            "igshid",
            "_ga", "_gl",
            "ref_src", "ref_url",
            "s"
    );

    private static final String UTM_PREFIX = "utm_";

    private UrlCanonicalizer() {
    }

    /**
     * Zwraca URL bez parametrów trackingowych. Dla {@code null}, blank, malformed lub URL bez
     * query — zwraca dokładnie to co weszło (graceful, nie wybucha).
     */
    public static String canonicalize(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        try {
            URI uri = URI.create(url);
            if (uri.getRawQuery() == null || uri.getRawQuery().isBlank()) {
                return url;
            }
            String stripped = stripTracking(uri.getRawQuery());
            return rebuild(uri, stripped);
        } catch (IllegalArgumentException _) {
            return url;
        }
    }

    private static String stripTracking(String rawQuery) {
        return Arrays.stream(rawQuery.split("&"))
                .filter(param -> !isTrackingParam(extractKey(param)))
                .collect(Collectors.joining("&"));
    }

    private static String extractKey(String param) {
        int eq = param.indexOf('=');
        return eq >= 0 ? param.substring(0, eq) : param;
    }

    private static boolean isTrackingParam(String key) {
        return key.startsWith(UTM_PREFIX) || EXACT_TRACKING_PARAMS.contains(key);
    }

    private static String rebuild(URI uri, String cleanQuery) {
        StringBuilder sb = new StringBuilder();
        if (uri.getScheme() != null) {
            sb.append(uri.getScheme()).append("://");
        }
        if (uri.getRawAuthority() != null) {
            sb.append(uri.getRawAuthority());
        }
        if (uri.getRawPath() != null) {
            sb.append(uri.getRawPath());
        }
        if (!cleanQuery.isEmpty()) {
            sb.append('?').append(cleanQuery);
        }
        if (uri.getRawFragment() != null) {
            sb.append('#').append(uri.getRawFragment());
        }
        return sb.toString();
    }
}
