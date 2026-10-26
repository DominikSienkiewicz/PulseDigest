package pl.seniordeveloper.pulsedigest.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C3. Documentation drift, caught by the build instead of by diligence.
 *
 * <p>{@code CLAUDE.md} asked for the README to be updated after every significant change. That rule
 * measures the wrong artefact — a key can be added, shipped and forgotten while the README is
 * perfectly accurate about everything else — and it relies on remembering, in a project that is
 * structural about everything else it cares about.
 *
 * <p>So this checks the one thing a reader is most likely to be misled by: a setting that exists and
 * is documented nowhere. It is not a general documentation test and does not pretend to be.
 *
 * <p>The keys are derived from the {@code @ConfigurationProperties} records, not from
 * {@code application.yaml}. That distinction is the whole reason this test is worth having: a key
 * can carry a {@code @DefaultValue} in code and never appear in the yaml at all, so checking
 * against the yaml would both miss real settings and report documented ones as stale.
 */
class ConfigurationDocsTest {

    /** The prefixes docs/configuration.md undertakes to document key by key. */
    private static final Set<String> DOCUMENTED_PREFIXES = Set.of("report", "llm");

    /**
     * Settings deliberately absent from the per-key table, with the reason. Each entry is a
     * decision rather than an oversight, which is the difference this list exists to record.
     */
    private static final Map<String, String> NOT_DOCUMENTED = Map.of(
            "report.eol.entries", "a data table, documented as a block rather than per row",
            "report.scoring.domain-overrides", "a map, documented as a block",
            "report.scoring.muted-topics", "a topic list, documented as a block",
            "report.dependency-profile.coordinates", "a coordinate list, documented as a block",
            "report.dependency-profile.project-paths", "a path list, documented as a block",
            "llm.pricing", "a per-model map, documented as a block with a worked example",
            "report.email.resend-api-key", "a credential; documented with the other secrets in running-locally.md",
            "report.email.from", "a credential; documented with the other secrets in running-locally.md",
            "report.email.to", "a credential; documented with the other secrets in running-locally.md");

    @Test
    void everySettingAppearsInTheConfigurationDocs() {
        String docs = read(repositoryRoot().resolve(Path.of("docs", "configuration.md")));

        List<String> undocumented = new ArrayList<>();
        for (String key : settings()) {
            if (DOCUMENTED_PREFIXES.stream().noneMatch(p -> key.startsWith(p + "."))) {
                continue;
            }
            if (NOT_DOCUMENTED.containsKey(key) || mentions(docs, key)) {
                continue;
            }
            undocumented.add(key);
        }

        assertThat(undocumented)
                .as("settings missing from docs/configuration.md — add a table row, or add the key "
                        + "to NOT_DOCUMENTED with the reason it does not need one")
                .isEmpty();
    }

    @Test
    void theDocsDoNotDescribeSettingsThatNoLongerExist() {
        // The direction nobody checks: a removed key leaves its row behind, and a documented
        // setting that does nothing is worse than an undocumented one — the reader has no reason
        // to doubt it.
        Set<String> actual = settings();
        String docs = read(repositoryRoot().resolve(Path.of("docs", "configuration.md")));

        List<String> stale = new ArrayList<>();
        for (String documented : documentedKeys(docs)) {
            if (isStale(documented, actual)) {
                stale.add(documented);
            }
        }

        assertThat(stale)
                .as("docs/configuration.md documents settings that no @ConfigurationProperties "
                        + "record declares")
                .isEmpty();
    }

    /**
     * The forms a table row might mean. The table writes most rows without their {@code report.}
     * prefix — {@code `social.limit`}, not {@code `report.social.limit`} — so the first version of
     * this check, which compared the literal text and skipped anything outside the documented
     * prefixes, was skipping almost every row it existed to examine. That is how
     * {@code technology-radar.lookback-months} stayed in the table for a day after the key was
     * deleted, with this test green and {@code CLAUDE.md} claiming that could not happen.
     */
    private static List<String> candidateKeys(String documented) {
        List<String> candidates = new ArrayList<>();
        candidates.add(documented);
        DOCUMENTED_PREFIXES.forEach(prefix -> candidates.add(prefix + "." + documented));
        return candidates;
    }

    /**
     * A row is stale when nothing answers to any of its forms <em>and</em> the group it sits in is
     * still declared. The second half is what keeps the check usable: a row about one of Spring's own
     * settings has no group here and is simply not this test's business, while a row under a group
     * this project does declare is a leaf that was removed and left its documentation behind.
     */
    private static boolean isStale(String documented, Set<String> actual) {
        List<String> candidates = candidateKeys(documented);
        if (candidates.stream().anyMatch(c -> declared(c, actual) || NOT_DOCUMENTED.containsKey(c))) {
            return false;
        }
        return candidates.stream().anyMatch(c -> groupIsDeclared(c, actual));
    }

    /** Something declares this exact key, the family it stands for, or a leaf beneath it. */
    private static boolean declared(String candidate, Set<String> actual) {
        String prefix = candidate.endsWith(".*")
                ? candidate.substring(0, candidate.length() - 1)
                : candidate + ".";
        return actual.contains(candidate)
                || actual.stream().anyMatch(key -> key.startsWith(prefix))
                || actual.stream().anyMatch(key -> candidate.startsWith(key + "."));
    }

    /** The key's parent group has other declared leaves — so this project owns the row. */
    private static boolean groupIsDeclared(String candidate, Set<String> actual) {
        int lastDot = candidate.lastIndexOf('.');
        if (lastDot < 0) {
            return false;
        }
        String group = candidate.substring(0, lastDot + 1);
        return actual.stream().anyMatch(key -> key.startsWith(group));
    }

    /**
     * A key counts as documented when the table names it in full, or names its last segment with a
     * leading dot — the form the table uses when two related keys share one row
     * ({@code `report.scoring.strong-threshold` / `.moderate-threshold`}).
     */
    private static boolean mentions(String docs, String key) {
        String lastSegment = key.substring(key.lastIndexOf('.'));
        String withoutReportPrefix = key.startsWith("report.") ? key.substring("report.".length()) : key;
        return docs.contains("`" + key + "`")
                || docs.contains("`" + withoutReportPrefix + "`")
                || docs.contains("`" + lastSegment + "`")
                || documentsTheFamily(docs, key);
    }

    /**
     * A source adapter's settings are documented as a family — {@code `report.hacker-news.*`} — not
     * one row per base URL. Sixteen sources times four plumbing keys would bury the dozen settings a
     * reader actually tunes, and `application.yaml` carries the per-value commentary anyway.
     *
     * <p>A family row still has to exist, so a source added with no documentation at all fails here.
     */
    private static boolean documentsTheFamily(String docs, String key) {
        String[] parts = key.split("\\.");
        return parts.length > 2 && docs.contains("`" + parts[0] + "." + parts[1] + ".*`");
    }

    /**
     * Every leaf setting the application declares, as a dotted, kebab-cased key — from the
     * {@code @ConfigurationProperties} records, plus the handful injected with {@code @Value}.
     *
     * <p>The {@code @Value} sweep is not thoroughness for its own sake: two real settings
     * ({@code report.rendered-edition-path} and the registry URL) are declared that way and nowhere
     * else, so a check that only knew about the records would have called their documentation stale.
     */
    private static Set<String> settings() {
        JavaClasses classes = new ClassFileImporter().importPackages("pl.seniordeveloper.pulsedigest");
        Set<String> keys = new LinkedHashSet<>();
        classes.stream()
                .filter(c -> c.isAnnotatedWith(ConfigurationProperties.class))
                .map(c -> c.reflect())
                .forEach(type -> collect(type.getAnnotation(ConfigurationProperties.class).prefix(), type, keys));
        keys.addAll(valueAnnotatedKeys());
        return keys;
    }

    /** Keys named in {@code @Value("${…}")}, read from the sources rather than from bytecode. */
    private static Set<String> valueAnnotatedKeys() {
        java.util.regex.Pattern placeholder =
                java.util.regex.Pattern.compile("@Value\\(\"\\$\\{([a-z0-9.-]+)");
        Set<String> keys = new LinkedHashSet<>();
        try (java.util.stream.Stream<Path> files = Files.walk(repositoryRoot().resolve("src/main/java"))) {
            files.filter(f -> f.toString().endsWith(".java")).forEach(f -> {
                java.util.regex.Matcher m = placeholder.matcher(read(f));
                while (m.find()) {
                    keys.add(m.group(1));
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return keys;
    }

    private static void collect(String prefix, Class<?> type, Set<String> into) {
        if (!type.isRecord()) {
            return;
        }
        for (RecordComponent component : type.getRecordComponents()) {
            String key = prefix + "." + kebab(component.getName());
            if (component.getType().isRecord() && !isValueLike(component.getType())) {
                collect(key, component.getType(), into);
            } else {
                into.add(key);
            }
        }
    }

    /**
     * A nested record that is a <em>value</em> rather than a group of settings — its components are
     * not keys of their own. Decided by package: anything outside the configuration package is a
     * domain type that happens to be configurable.
     */
    private static boolean isValueLike(Class<?> type) {
        return !type.getPackageName().endsWith(".infrastructure.config");
    }

    private static String kebab(String camel) {
        return camel.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT);
    }

    private static List<String> documentedKeys(String docs) {
        return docs.lines()
                .filter(line -> line.startsWith("| `"))
                .map(line -> line.substring(3, Math.max(3, line.indexOf('`', 3))))
                .map(String::trim)
                .filter(key -> !key.isBlank() && !key.contains("<"))
                .toList();
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
