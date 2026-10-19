package pl.seniordeveloper.pulsedigest.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F14, made checkable: Java comments are written in English
 * (<a href="../../../../../../../docs/adr/0001-polish-as-the-product-language.md">ADR-0001</a>).
 *
 * <p>The audit found both languages inside single files and inside single comments. A style rule
 * nobody can run is a style rule that decays, and this one decays invisibly — a Polish sentence in
 * a Javadoc block reads perfectly well to the person writing it.
 *
 * <p>The boundary the ADR draws is exactly what this checks: a comment may <em>quote</em> Polish,
 * because explaining a Polish user-facing string usually requires showing it. What it may not do is
 * be written in Polish. Quoted text is therefore removed before looking for Polish letters.
 */
class CommentLanguageTest {

    private static final Pattern POLISH = Pattern.compile("[ąćęłńóśźżĄĆĘŁŃÓŚŹŻ]");
    private static final Pattern QUOTED = Pattern.compile("\"[^\"]*\"|\\{@code [^}]*}|&[a-zA-Z#0-9]+;");

    @Test
    void javaCommentsAreWrittenInEnglish() {
        List<String> offenders = new ArrayList<>();
        for (Path file : sources()) {
            List<String> lines = read(file);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                String trimmed = line.strip();
                boolean isComment = trimmed.startsWith("//") || trimmed.startsWith("*")
                        || trimmed.startsWith("/*");
                if (isComment && POLISH.matcher(QUOTED.matcher(line).replaceAll("")).find()) {
                    offenders.add("%s:%d  %s".formatted(file.getFileName(), i + 1,
                            trimmed.substring(0, Math.min(90, trimmed.length()))));
                }
            }
        }

        assertThat(offenders)
                .as("Java comments written in Polish — ADR-0001 puts the product in Polish and the "
                        + "engineering in English; quoting a Polish string is fine, writing the "
                        + "comment in Polish is not")
                .isEmpty();
    }

    private static List<Path> sources() {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("settings.gradle.kts"))) {
            root = root.getParent();
        }
        if (root == null) {
            throw new IllegalStateException("Could not locate the repository root");
        }
        try (Stream<Path> files = Files.walk(root.resolve("src"))) {
            return files.filter(p -> p.toString().endsWith(".java")).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<String> read(Path file) {
        try {
            return Files.readAllLines(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file, e);
        }
    }
}
