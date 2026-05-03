package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

public record RssItem(
        String title,
        String url,
        String description,
        String feedName
) {
}
