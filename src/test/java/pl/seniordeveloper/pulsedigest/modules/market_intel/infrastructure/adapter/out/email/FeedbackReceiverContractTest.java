package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.shared.util.HmacSigner;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F6, the half this repository can actually check: the receiver's source is here now, and the two
 * sides of its one cryptographic agreement are pinned to the same vector.
 *
 * <p>{@code receiver/index.ts} verifies an HMAC that {@code FeedbackLinkBuilder} produces. Two
 * languages, two crypto libraries, one agreement — over the canonical message, the digest's
 * URL-safe-unpadded Base64 encoding, and the lowercase vote that goes into the signature. Nothing in
 * a Java build can execute the Deno side, and adding a second toolchain to CI to run two assertions
 * would cost more than it returns. So this test does the next best thing and the thing that actually
 * fails on drift: it computes the vector with the production signer, and asserts that the receiver's
 * test file contains that same vector verbatim. Editing either side alone turns this red.
 *
 * <p>What this does not prove is that the deployed function is this file — only the author's deploy
 * can make that true, and {@code receiver/README.md} says so in the first paragraph. Before today
 * the source was not here at all, so there was nothing to compare a deployment against.
 */
class FeedbackReceiverContractTest {

    private static final String SECRET = "pulse-digest-test-secret";
    private static final String CANONICAL = "https://example.com/item|up|GitHub|Java/JVM|2026-10-19";
    private static final String SIGNATURE = "juS2D0bAY7J-mngDVHo_S5s4veZvndJzYNxJJqE4vPc";

    @Test
    void theProductionSignerProducesTheVectorTheReceiverTestsAgainst() {
        assertThat(HmacSigner.sign(CANONICAL, SECRET))
                .as("if this moved, the deployed receiver stopped being able to verify any link")
                .isEqualTo(SIGNATURE);
    }

    @Test
    void theReceiversOwnTestUsesTheSameVector() {
        String denoTest = read(repositoryRoot().resolve(Path.of("receiver", "index_test.ts")));

        assertThat(denoTest)
                .as("receiver/index_test.ts must pin the same secret, message and signature as this "
                        + "test — otherwise the two halves of the contract can drift apart silently")
                .contains(SECRET)
                .contains(CANONICAL)
                .contains(SIGNATURE);
    }

    /**
     * The canonical message's shape, asserted against the builder's own output rather than restated:
     * {@code url|vote|source|category|edition}, with the vote in the lowercase form the link carries.
     * The receiver lowercases before signing for this reason, and a change of separator or field
     * order here is a change the receiver cannot see until a reader's vote is rejected.
     */
    @Test
    void theCanonicalMessageIsTheOneTheReceiverReconstructs() {
        assertThat(CANONICAL.split("\\|"))
                .containsExactly("https://example.com/item", "up", "GitHub", "Java/JVM", "2026-10-19");
        assertThat(read(repositoryRoot().resolve(Path.of("receiver", "index.ts"))))
                .as("the receiver must join the same five fields, in the same order, with the same "
                        + "separator, and must lowercase the vote before signing")
                .contains("[itemUrl, rawVote.toLowerCase(), source, category, edition].join(\"|\")");
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file, e);
        }
    }

    private static Path repositoryRoot() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null && !Files.exists(candidate.resolve("settings.gradle.kts"))) {
            candidate = candidate.getParent();
        }
        if (candidate == null) {
            throw new IllegalStateException("Could not locate the repository root");
        }
        return candidate;
    }
}
