package pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sections switched off by configuration (N19).
 *
 * <p>One list rather than a boolean per section, because the project already carries a great deal of
 * configuration and F11's complaint was that the <em>wrong</em> things were configurable. A list
 * extends when a section is added without growing the surface every time.
 *
 * <p>The point is not customisation. The audit could not determine which sections are ever read, and
 * this is the cheap way to find out: turn one off for a few editions and see whether it is missed.
 *
 * @param disabled section keys, as {@code docs/digest-email.md} names them
 */
public record SectionsPolicy(Set<String> disabled) {

    public SectionsPolicy {
        disabled = disabled == null ? Set.of()
                : disabled.stream()
                        .filter(name -> name != null && !name.isBlank())
                        .map(name -> name.strip().toLowerCase(Locale.ROOT))
                        .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isEnabled(String section) {
        return section != null && !disabled.contains(section.strip().toLowerCase(Locale.ROOT));
    }
}
