package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import java.util.ArrayList;
import java.util.List;

/**
 * Line discipline for the plain-text part (N14).
 *
 * <p>Wrapped at 78 columns rather than left to the client. A terminal reader is the one this part
 * exists for, and an unwrapped paragraph is what makes a text email look like a stripped HTML one.
 */
final class PlainTextFormatting {

    static final int WIDTH = 78;

    private PlainTextFormatting() {
    }

    /** A heading and its underline, in the convention a terminal reader already knows. */
    static String heading(String title, char rule) {
        return title + "\n" + String.valueOf(rule).repeat(Math.min(title.length(), WIDTH)) + "\n";
    }

    /** {@code text} wrapped to {@link #WIDTH}, every line after the first prefixed with {@code indent}. */
    static String wrap(String text, String indent) {
        if (text == null || text.isBlank()) {
            return "";
        }
        int width = Math.max(20, WIDTH - indent.length());
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.strip().split("\\s+")) {
            if (!line.isEmpty() && line.length() + 1 + word.length() > width) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (!line.isEmpty()) {
                line.append(' ');
            }
            line.append(word);
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return String.join("\n" + indent, lines);
    }

    /** {@code text} wrapped and indented on every line, including the first. */
    static String block(String text, String indent) {
        String wrapped = wrap(text, indent);
        return wrapped.isEmpty() ? "" : indent + wrapped;
    }
}
