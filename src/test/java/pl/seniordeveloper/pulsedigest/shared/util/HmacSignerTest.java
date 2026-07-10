package pl.seniordeveloper.pulsedigest.shared.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HmacSignerTest {

    private static final String SECRET = "super-secret";

    @Test
    void theSameMessageAndSecretAlwaysProduceTheSameSignature() {
        assertThat(HmacSigner.sign("a|up|GitHub|2026-07-10", SECRET))
                .isEqualTo(HmacSigner.sign("a|up|GitHub|2026-07-10", SECRET));
    }

    @Test
    void flippingTheVoteChangesTheSignature() {
        // The whole point: a link's meaning cannot be edited without invalidating its signature.
        assertThat(HmacSigner.sign("a|up|GitHub|2026-07-10", SECRET))
                .isNotEqualTo(HmacSigner.sign("a|down|GitHub|2026-07-10", SECRET));
    }

    @Test
    void adifferentSecretProducesADifferentSignature() {
        assertThat(HmacSigner.sign("a|up|GitHub|2026-07-10", SECRET))
                .isNotEqualTo(HmacSigner.sign("a|up|GitHub|2026-07-10", "other-secret"));
    }

    @Test
    void signaturesAreUrlSafeAndUnpadded() {
        String signature = HmacSigner.sign("https://example.com/a?x=1&y=2|up|RSS/Java|2026-07-10", SECRET);

        assertThat(signature).matches("[A-Za-z0-9_-]+").doesNotContain("=");
    }

    @Test
    void aBlankSecretIsARefusalToSignRatherThanAWeakSignature() {
        assertThatThrownBy(() -> HmacSigner.sign("a|up|GitHub|2026-07-10", ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HmacSigner.sign("a|up|GitHub|2026-07-10", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
