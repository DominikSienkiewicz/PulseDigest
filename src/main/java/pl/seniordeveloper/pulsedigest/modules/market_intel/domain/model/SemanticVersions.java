package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import java.util.regex.Pattern;

/**
 * Compares two release numbers well enough to answer "is this one newer?".
 *
 * <p>Not a semver implementation and not trying to be: build metadata, pre-release precedence and
 * the long tail of versioning conventions are not the question here. The question is whether a
 * registry's current release is ahead of the version a build file pins, and dotted numeric segments
 * answer that for the artifacts a JVM project actually depends on. Anything the numeric comparison
 * cannot order is reported as "not newer", because a false upgrade nag is worse than a missed one.
 */
public final class SemanticVersions {

    private static final Pattern SEPARATOR = Pattern.compile("[._\\-+]");

    private SemanticVersions() {
    }

    /** Whether {@code candidate} is strictly newer than {@code current}. */
    public static boolean isNewer(String candidate, String current) {
        if (candidate == null || current == null || candidate.isBlank() || current.isBlank()) {
            return false;
        }
        int[] left = numericSegments(candidate);
        int[] right = numericSegments(current);
        if (left.length == 0 || right.length == 0) {
            return false;
        }
        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            int a = i < left.length ? left[i] : 0;
            int b = i < right.length ? right[i] : 0;
            if (a != b) {
                return a > b;
            }
        }
        return false;
    }

    /**
     * Leading numeric segments only. {@code 33.4.0-jre} reads as 33.4.0 and {@code 3.5.0.RELEASE} as
     * 3.5.0 — the qualifier is dropped rather than ranked, which is exactly the precision this is
     * willing to claim.
     */
    private static int[] numericSegments(String version) {
        String[] parts = SEPARATOR.split(version.strip());
        int count = 0;
        int[] segments = new int[parts.length];
        for (String part : parts) {
            if (part.isEmpty() || !part.chars().allMatch(Character::isDigit)) {
                break;
            }
            segments[count++] = Integer.parseInt(part);
        }
        int[] trimmed = new int[count];
        System.arraycopy(segments, 0, trimmed, 0, count);
        return trimmed;
    }
}
