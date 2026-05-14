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
}
