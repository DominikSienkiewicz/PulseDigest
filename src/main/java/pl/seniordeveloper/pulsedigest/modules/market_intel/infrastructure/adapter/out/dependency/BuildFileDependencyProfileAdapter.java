package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dependency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DependencyCoordinate;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DependencyProfile;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.DependencyProfilePort;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.DependencyProfileProperties;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Reads Maven coordinates out of the reader's build files, textually.
 *
 * <p>The parse is deliberately dumb: it matches coordinate-shaped strings and nothing else. No build
 * is executed, no version catalogue is resolved, no property is interpolated. A coordinate whose
 * version is a variable (`libs.versions.toml` reference, `${springVersion}`) is still reported with
 * an unknown pin — knowing the reader depends on an artifact is most of the value, and guessing the
 * resolved version by half-interpreting a build script is how a dumb parse becomes a wrong one.
 *
 * <p>Unreadable or absent paths are normal, not errors: in CI none of these directories exist, and
 * the profile degrades to the declared coordinates with {@code fromBuildFiles=false} so the digest
 * can say which list it used.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class BuildFileDependencyProfileAdapter implements DependencyProfilePort {

    private static final String CONFIGURATION_SCOPE = "configuration";

    /** `"group:artifact:version"` in a Gradle script, with the version optional. */
    private static final Pattern GRADLE_COORDINATE = Pattern.compile(
            "[\"']([a-zA-Z0-9_.\\-]+):([a-zA-Z0-9_.\\-]+)(?::([a-zA-Z0-9_.\\-${}]+))?[\"']");

    /** A Maven `<dependency>` block's three elements, in the order POMs conventionally write them. */
    private static final Pattern MAVEN_DEPENDENCY = Pattern.compile(
            "<groupId>\\s*([^<\\s]+)\\s*</groupId>\\s*<artifactId>\\s*([^<\\s]+)\\s*</artifactId>"
                    + "(?:\\s*<version>\\s*([^<\\s]+)\\s*</version>)?",
            Pattern.DOTALL);

    private static final List<String> BUILD_FILE_NAMES =
            List.of("build.gradle.kts", "build.gradle", "pom.xml");

    private static final int MAX_FILE_BYTES = 512 * 1024;

    private final DependencyProfileProperties properties;

    @Override
    public DependencyProfile load() {
        if (!properties.enabled()) {
            return DependencyProfile.empty();
        }
        Map<String, DependencyCoordinate> found = new LinkedHashMap<>();
        List<String> scanned = new ArrayList<>();

        for (String configured : properties.projectPaths()) {
            for (Path buildFile : buildFilesAt(configured)) {
                readCoordinates(buildFile).forEach(c -> found.putIfAbsent(c.key(), c));
                scanned.add(buildFile.toString());
            }
        }

        if (!scanned.isEmpty()) {
            log.info("dependency-profile source=build-files files={} coordinates={}",
                    scanned.size(), found.size());
            return new DependencyProfile(List.copyOf(found.values()), true, scanned);
        }

        List<DependencyCoordinate> declared = properties.coordinates().stream()
                .map(BuildFileDependencyProfileAdapter::parseDeclared)
                .filter(DependencyCoordinate::isComplete)
                .toList();
        log.info("dependency-profile source=configuration coordinates={} (no build file was readable"
                + " at {} configured path(s) — normal in CI)", declared.size(), properties.projectPaths().size());
        return new DependencyProfile(declared, false, List.of());
    }

    /** Resolves a configured path to the build files under it; a file path is taken as-is. */
    private List<Path> buildFilesAt(String configured) {
        if (configured == null || configured.isBlank()) {
            return List.of();
        }
        Path path = Path.of(configured.replaceFirst("^~", System.getProperty("user.home", "~")));
        if (Files.isRegularFile(path)) {
            return List.of(path);
        }
        if (!Files.isDirectory(path)) {
            log.debug("dependency-profile path not readable: {}", path);
            return List.of();
        }
        return BUILD_FILE_NAMES.stream().map(path::resolve).filter(Files::isRegularFile).toList();
    }

    private List<DependencyCoordinate> readCoordinates(Path buildFile) {
        String text;
        try {
            if (Files.size(buildFile) > MAX_FILE_BYTES) {
                log.warn("dependency-profile skipping oversized build file {}", buildFile);
                return List.of();
            }
            text = Files.readString(buildFile, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("dependency-profile could not read {}: {}", buildFile, e.getMessage());
            return List.of();
        }
        String origin = buildFile.toString();
        Pattern pattern = buildFile.getFileName().toString().equals("pom.xml")
                ? MAVEN_DEPENDENCY : GRADLE_COORDINATE;
        return matches(pattern, text, origin);
    }

    private static List<DependencyCoordinate> matches(Pattern pattern, String text, String origin) {
        List<DependencyCoordinate> found = new ArrayList<>();
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            String version = m.group(3) != null ? m.group(3) : "";
            DependencyCoordinate coordinate =
                    new DependencyCoordinate(m.group(1), m.group(2), unresolved(version) ? "" : version, origin);
            if (coordinate.isComplete() && coordinate.groupId().contains(".")) {
                found.add(coordinate);
            }
        }
        return found;
    }

    /** A version the parse refuses to guess at: a property reference rather than a number. */
    private static boolean unresolved(String version) {
        return version.contains("$") || version.contains("{");
    }

    private static DependencyCoordinate parseDeclared(String declared) {
        String[] parts = Stream.of(declared.split(":")).map(String::strip).toArray(String[]::new);
        return switch (parts.length) {
            case 2 -> new DependencyCoordinate(parts[0], parts[1], "", CONFIGURATION_SCOPE);
            case 3 -> new DependencyCoordinate(parts[0], parts[1], parts[2], CONFIGURATION_SCOPE);
            default -> new DependencyCoordinate("", "", "", CONFIGURATION_SCOPE);
        };
    }
}
