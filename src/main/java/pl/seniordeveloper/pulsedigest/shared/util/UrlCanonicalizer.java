package pl.seniordeveloper.pulsedigest.shared.util;

import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Strips tracking parameters (utm_*, fbclid and friends) from URLs, for three reasons: the links
 * in the mail stay readable, deduplication by URL becomes possible at all, and the reader's click
 * does not carry somebody else's campaign tags onward.
 *
 * <p>Parameters that carry meaning in some domains are left alone — {@code ref} is a branch on
 * GitHub, not a referrer — because a false positive breaks the link, which is a worse failure than
 * a slightly untidy one. The list is therefore deliberately conservative.
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
     * The URL without its tracking parameters. A null, blank, malformed or query-less input comes
     * back exactly as it went in rather than throwing: canonicalization runs over every fetched
     * item, and one unparseable URL must not end a run.
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
