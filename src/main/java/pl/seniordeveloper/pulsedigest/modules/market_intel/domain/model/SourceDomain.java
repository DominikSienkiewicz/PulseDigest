package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import java.util.List;

/**
 * Categorical domain type for an intelligence source.
 * Used for cross-source correlation: a topic appearing in 3+ distinct domains is a Critical Trend.
 */
public enum SourceDomain {
    SCIENCE, CODE, BUSINESS, SOCIAL, SECURITY, LABS;

    /**
     * Prefixes of the AI-lab source labels configured under {@code report.lab-announcements.sources}.
     * A lab post is neither research nor code nor chatter — it is the primary announcement itself,
     * so it earns its own domain and strengthens the 3-domain rule rather than blurring it.
     */
    private static final List<String> LAB_PREFIXES = List.of("Anthropic", "Claude", "Google Gemini", "OpenAI");

    /** Maps the raw source string (as produced by ReportPromptBuilder) to its domain type. */
    public static SourceDomain from(String source) {
        if (source == null) {
            return SOCIAL;
        }
        if (source.equals("OpenJDK JEP") || source.equals("GitHub") || source.equals("GitHub Releases")
                || source.startsWith("CNCF")) {
            return CODE;
        }
        if (LAB_PREFIXES.stream().anyMatch(source::startsWith)) {
            return LABS;
        }
        if (source.startsWith("arXiv") || source.equals("Hugging Face")) {
            return SCIENCE;
        }
        if (source.startsWith("Hacker News") || source.equals("Product Hunt")
                || source.equals("Tech Radar") || source.startsWith("YouTube")) {
            return BUSINESS;
        }
        if (source.equals("Security Advisories")) {
            return SECURITY;
        }
        return SOCIAL;
    }
}
