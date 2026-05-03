package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

public record GithubRepo(
        String name,
        String description,
        int stars,
        String url
) {
    public String toPromptString() {
        String desc = (description != null && !description.isBlank()) ? " – " + description : "";
        return "[⭐ %d] %s%s (Link: %s)".formatted(stars, name, desc, url);
    }
}
