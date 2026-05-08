package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

/**
 * Categorical domain type for an intelligence source.
 * Used for cross-source correlation: a topic appearing in 3+ distinct domains is a Critical Trend.
 */
public enum SourceDomain {
    SCIENCE, CODE, BUSINESS, SOCIAL, SECURITY;

    /** Maps the raw source string (as produced by ReportPromptBuilder) to its domain type. */
    public static SourceDomain from(String source) {
        if (source == null) {
            return SOCIAL;
        }
        if (source.startsWith("arXiv") || source.equals("Hugging Face")) {
            return SCIENCE;
        }
        if (source.equals("GitHub") || source.equals("GitHub Releases")
                || source.equals("OpenJDK JEP") || source.startsWith("CNCF")
                || source.equals("Libraries.io") || source.equals("DB-Engines")) {
            return CODE;
        }
        if (source.startsWith("Hacker News") || source.equals("Product Hunt")
                || source.equals("Tech Radar") || source.startsWith("YouTube")) {
            return BUSINESS;
        }
        if (source.equals("Security Advisories") || source.equals("NVD/CVE")) {
            return SECURITY;
        }
        return SOCIAL;
    }
}
