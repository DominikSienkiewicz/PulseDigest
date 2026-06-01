package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

/**
 * Maps internal source names to the human-readable API account a recipient would top up.
 * Shared between the in-digest "exhausted limits" banner and the standalone quota-alert email
 * so both name accounts identically.
 */
public final class ApiAccounts {

    /** The LLM provider account — used when report synthesis itself fails on exhausted credits. */
    public static final String LLM = "OpenAI (model LLM)";

    private ApiAccounts() {
    }

    /** Returns the account/API label for a source name (as produced by the research pipeline). */
    public static String label(String sourceName) {
        if (sourceName == null) {
            return "Nieznane źródło";
        }
        if (sourceName.startsWith("Twitter")) {
            return "Twitter/X API";
        }
        if (sourceName.equals("NVD/CVE")) {
            return "NVD API (NIST)";
        }
        if (sourceName.equals("Security Advisories") || sourceName.startsWith("GitHub")
                || sourceName.equals("OpenJDK JEP") || sourceName.startsWith("CNCF")) {
            return "GitHub API (token)";
        }
        if (sourceName.equals("Conference Talks") || sourceName.startsWith("YouTube")) {
            return "YouTube Data API";
        }
        if (sourceName.equals("Product Hunt")) {
            return "Product Hunt API";
        }
        if (sourceName.equals("Libraries.io")) {
            return "Libraries.io API";
        }
        if (sourceName.equals("Hugging Face")) {
            return "Hugging Face API";
        }
        if (sourceName.startsWith("Reddit")) {
            return "Reddit API";
        }
        if (sourceName.startsWith("Hacker News")) {
            return "Hacker News (Algolia) API";
        }
        return sourceName;
    }
}
