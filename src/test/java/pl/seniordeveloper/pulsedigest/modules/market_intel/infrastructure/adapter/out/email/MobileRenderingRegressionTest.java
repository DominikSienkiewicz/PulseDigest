package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * N17. The digest is read on a phone, and the HTML golden file proves the markup did not change —
 * not that it still <em>looks</em> right. A CSS edit that keeps every byte the golden file checks
 * while pushing a table off the right edge at 375px passes every other test in this build.
 *
 * <h2>How to run it</h2>
 * <pre>
 * scripts/mobile-snapshot.sh            render the current digest for this test to compare
 * scripts/mobile-snapshot.sh --update   accept what it renders as the new baseline
 * </pre>
 * Without a rendered snapshot this test aborts rather than fails: the renderer is a pinned container,
 * and a developer without Docker should not be blocked by a test whose input they cannot produce. CI
 * runs the script first, so the comparison is never skipped where it matters.
 *
 * <h2>Why a pinned container renders both sides</h2>
 * A screenshot diff is only a test if both images come from the same renderer. Chrome on macOS and
 * Chrome on a CI runner disagree about font hinting on every glyph, so a baseline recorded on one
 * would fail on the other for reasons that have nothing to do with layout.
 *
 * <h2>Why a threshold and not an exact match</h2>
 * The same renderer on the same input is deterministic, so in principle this could demand equality.
 * It does not, because the failure that matters is a layout change — a shifted column, an overflowing
 * table, a section that collapsed — and all of those move thousands of pixels. A threshold this low
 * still catches every one of them while leaving room for emoji antialiasing to differ by a hair
 * between container runs, which is the kind of flake that gets a visual test deleted.
 */
class MobileRenderingRegressionTest {

    private static final Path BASELINE = Path.of("docs", "images", "sample-digest-mobile.png");
    private static final Path RENDERED = Path.of("build", "mobile", "sample-digest-mobile.png");

    /** Per-channel difference below which two pixels count as the same colour. */
    private static final int CHANNEL_TOLERANCE = 8;

    /** Fraction of differing pixels that fails the build. 0.1% of a 375×5600 image is ~2100 pixels. */
    private static final double MAX_DIFFERING_FRACTION = 0.001;

    /** Empty space that must sit below the document, proving the capture reached its end. */
    private static final int REQUIRED_TRAILING_MARGIN_PX = 50;

    @Test
    void theDigestStillLooksRightAtPhoneWidth() {
        Path rendered = resolve(RENDERED);
        Assumptions.assumeTrue(Files.exists(rendered),
                "No mobile snapshot to compare — run scripts/mobile-snapshot.sh first.");

        BufferedImage baseline = read(resolve(BASELINE));
        BufferedImage current = read(rendered);

        assertThat(current.getWidth()).as("rendered width").isEqualTo(baseline.getWidth());
        assertThat(current.getHeight())
                .as("rendered height — a different document height is a layout change by definition")
                .isEqualTo(baseline.getHeight());

        assertCapturedToTheBottom(current);

        long differing = countDifferingPixels(baseline, current);
        double fraction = (double) differing / (baseline.getWidth() * (long) baseline.getHeight());

        assertThat(fraction)
                .as("%.3f%% of pixels differ from docs/images/sample-digest-mobile.png — open both and "
                        + "look before re-recording; if the change is intended, "
                        + "scripts/mobile-snapshot.sh --update", fraction * 100)
                .isLessThanOrEqualTo(MAX_DIFFERING_FRACTION);
    }

    /**
     * A screenshot is a window, not a document: ask for one shorter than the page and the renderer
     * quietly gives you the top of it. The first baseline recorded here was 2400px of a 5269px
     * document, so every table in the digest sat below the frame and this test guarded nothing that
     * mattered. The last rows being empty page background is the evidence that the capture reached
     * the end — and the digest growing past the configured height now fails here, loudly, instead of
     * narrowing the test in silence.
     */
    private static void assertCapturedToTheBottom(BufferedImage image) {
        // Derived, not assumed: the colour below the document is whatever the renderer paints there
        // (Chromium uses its own base colour, not the body background — asserting on #f9fafb failed
        // against a perfectly good snapshot). What matters is that a band of it exists at all.
        int bottomColour = image.getRGB(0, image.getHeight() - 1);
        int lastContentRow = image.getHeight() - 1;
        while (lastContentRow >= 0 && rowIsUniform(image, lastContentRow, bottomColour)) {
            lastContentRow--;
        }

        assertThat(image.getHeight() - lastContentRow)
                .as("empty rows below the last content row — a snapshot shorter than the document "
                        + "silently stops guarding everything below the fold; raise HEIGHT in "
                        + "scripts/mobile-snapshot.sh and re-record")
                .isGreaterThanOrEqualTo(REQUIRED_TRAILING_MARGIN_PX);
    }

    private static boolean rowIsUniform(BufferedImage image, int y, int colour) {
        for (int x = 0; x < image.getWidth(); x++) {
            if (differs(image.getRGB(x, y), colour)) {
                return false;
            }
        }
        return true;
    }

    private static long countDifferingPixels(BufferedImage a, BufferedImage b) {
        long differing = 0;
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                if (differs(a.getRGB(x, y), b.getRGB(x, y))) {
                    differing++;
                }
            }
        }
        return differing;
    }

    private static boolean differs(int left, int right) {
        return Math.abs((left >> 16 & 0xff) - (right >> 16 & 0xff)) > CHANNEL_TOLERANCE
                || Math.abs((left >> 8 & 0xff) - (right >> 8 & 0xff)) > CHANNEL_TOLERANCE
                || Math.abs((left & 0xff) - (right & 0xff)) > CHANNEL_TOLERANCE;
    }

    private static BufferedImage read(Path file) {
        try {
            return ImageIO.read(file.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file, e);
        }
    }

    /** Resolved against the repository root, so the test does not depend on the runner's directory. */
    private static Path resolve(Path file) {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null && !Files.exists(candidate.resolve("settings.gradle.kts"))) {
            candidate = candidate.getParent();
        }
        if (candidate == null) {
            throw new IllegalStateException("Could not locate the repository root");
        }
        return candidate.resolve(file);
    }
}
