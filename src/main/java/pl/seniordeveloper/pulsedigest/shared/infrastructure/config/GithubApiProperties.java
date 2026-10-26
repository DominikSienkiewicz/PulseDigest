package pl.seniordeveloper.pulsedigest.shared.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpHeaders;

/**
 * Credentials for {@code api.github.com}, shared by every GitHub-backed source.
 *
 * <p>All of them draw on one rate limit. Unauthenticated it is 60 requests an hour per IP, which a
 * hosted GitHub Actions runner shares with every other tenant of that address; the workflow's own
 * {@code GITHUB_TOKEN} lifts it to 1,000 an hour per repository.
 *
 * @param token bearer token; blank means unauthenticated
 */
@ConfigurationProperties(prefix = "report.github-api")
public record GithubApiProperties(String token) {

    /** Adds the bearer token to {@code headers}; leaves them untouched when no token is configured. */
    public void authorize(HttpHeaders headers) {
        if (token != null && !token.isBlank()) {
            headers.setBearerAuth(token);
        }
    }
}
