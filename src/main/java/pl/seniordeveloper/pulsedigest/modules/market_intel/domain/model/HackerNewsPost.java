package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

public record HackerNewsPost(
        String title,
        String url,
        int points
) {
    public String toPromptString() {
        return "[%d pkt] %s (Link: %s)".formatted(points, title, url);
    }
}
