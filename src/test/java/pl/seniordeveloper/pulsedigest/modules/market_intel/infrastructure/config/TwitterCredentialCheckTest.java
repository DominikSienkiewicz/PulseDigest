package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.config;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.SourceDisablePolicy;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.TwitterProperties;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * K7. Disabling X must cost nothing, and enabling it must still fail early.
 *
 * <p>{@code twitter.bearer-token} stopped being {@code @NotBlank} when X was switched off, because
 * requiring the project's one paid credential for a source nobody fetches would have made
 * "disabled" cost exactly what "enabled" cost. The fail-fast property it provided moved to the
 * bootstrap, and this is what keeps it there.
 */
class TwitterCredentialCheckTest {

    private final MarketIntelBootstrap bootstrap = new MarketIntelBootstrap();

    @Test
    void aDisabledXNeedsNoCredential() {
        assertThatCode(() -> bootstrap
                .twitterCredentialCheck(new SourceDisablePolicy(SourceDisablePolicy.TWITTER_SOURCES),
                        twitter(""))
                .afterPropertiesSet())
                .doesNotThrowAnyException();
    }

    @Test
    void anEnabledXWithoutACredentialRefusesToStart() {
        // The failure this replaces would have happened at the first search, after the run had
        // already paid for a dozen other fetches.
        var credentialCheck = bootstrap
                .twitterCredentialCheck(new SourceDisablePolicy(Set.of("Twitter/X topic")), twitter(""));
        assertThatThrownBy(credentialCheck::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TWITTER_BEARER_TOKEN")
                .hasMessageContaining("report.sources.disabled");
    }

    @Test
    void anEnabledXWithACredentialStartsNormally() {
        assertThatCode(() -> bootstrap
                .twitterCredentialCheck(new SourceDisablePolicy(Set.of()), twitter("a-real-token"))
                .afterPropertiesSet())
                .doesNotThrowAnyException();
    }

    private static TwitterProperties twitter(String bearerToken) {
        return new TwitterProperties(bearerToken, List.of("q"), List.of("a"), 10, 0);
    }
}
