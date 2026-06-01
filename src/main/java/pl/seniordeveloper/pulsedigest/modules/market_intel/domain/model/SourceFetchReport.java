package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

public record SourceFetchReport(
        String sourceName,
        SourceFetchStatus status,
        int itemCount,
        long durationMillis,
        String errorMessage
) {

    public static SourceFetchReport success(String sourceName, int itemCount, long durationMillis) {
        return new SourceFetchReport(sourceName, SourceFetchStatus.SUCCESS, itemCount, durationMillis, null);
    }

    public static SourceFetchReport failed(String sourceName, long durationMillis, String errorMessage) {
        return new SourceFetchReport(sourceName, SourceFetchStatus.FAILED, 0, durationMillis, errorMessage);
    }

    /**
     * Returns {@code true} when this source failed specifically because its API quota or rate
     * limit was exhausted (e.g. depleted credits, HTTP 429/402) — the case that warrants a
     * "top up this account" banner rather than a generic transient-failure warning.
     */
    public boolean isQuotaExhausted() {
        return status == SourceFetchStatus.FAILED && QuotaSignals.matches(errorMessage);
    }
}
