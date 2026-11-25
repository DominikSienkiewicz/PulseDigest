package pl.seniordeveloper.pulsedigest.architecture;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The scheduled run's exit code has to reach the job, or every failure path wired to the job is dead.
 *
 * <p>{@code Run digest} pipes the application into {@code tee}, so the log survives as an artifact. A
 * step without {@code shell:} runs on GitHub's Linux runners as {@code bash -e {0}}, with no
 * {@code pipefail}: the step takes {@code tee}'s exit code, and a run that exited 1 left the job green.
 * {@code Notify on failure} and {@code Upload digest log on failure} never fired, and {@code archive}
 * ran after a failed digest. An explicit {@code shell: bash} runs as
 * {@code bash --noprofile --norc -eo pipefail {0}}, so the application's exit code fails the step.
 */
class DigestWorkflowTest {

    private static final Path WORKFLOW = Path.of(".github", "workflows", "digest.yml");

    @Test
    void theRunDigestStepFailsTheJobWhenTheApplicationFails() {
        Map<?, ?> step = step("digest", "Run digest");

        assertThat(step.get("run"))
                .as("the guarded step still pipes the application into tee")
                .asString()
                .contains("| tee");
        assertThat(step.get("shell"))
                .as("without an explicit `shell: bash`, GitHub runs `bash -e` with no pipefail, so tee's "
                        + "exit code replaces the application's and a failed run stays green")
                .isEqualTo("bash");
    }

    private static Map<?, ?> step(String job, String name) {
        Object workflow = new Yaml().load(read(repositoryRoot().resolve(WORKFLOW)));
        if (!(workflow instanceof Map<?, ?> root
                && root.get("jobs") instanceof Map<?, ?> jobs
                && jobs.get(job) instanceof Map<?, ?> definition
                && definition.get("steps") instanceof List<?> steps)) {
            throw new IllegalStateException(WORKFLOW + " has no steps for job '" + job + "'");
        }
        return steps.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .filter(candidate -> name.equals(candidate.get("name")))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No step '" + name + "' in job '" + job + "'"));
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
