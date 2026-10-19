package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import org.instancio.Instancio;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * N58: what a seventeen-component record's copy-methods have to get right, and what nothing checked
 * before.
 *
 * <p>Every {@code withX} used to restate the whole seventeen-argument constructor call. Several
 * components are {@code List}s and {@code Set}s that the compiler accepts in each other's positions,
 * so one argument out of order produced a report with the wrong field replaced and no error
 * anywhere — and an eighteenth component meant getting eighteen such calls right. The copy now goes
 * through one private builder, and this test is what says the delegation is correct: for every
 * wither, the named component is replaced and <em>no other component moves</em>. It discovers the
 * withers reflectively, so a new one is covered the moment it is written.
 *
 * <p>The second thing it pins is the canonical constructor's normalisation. The builder deliberately
 * calls that constructor instead of assigning fields, because the normalisation lives there: five
 * components turn null into an empty collection and copy what they are given. A builder that
 * bypassed it would keep compiling, and the first symptom would be a {@code NullPointerException} or
 * a mutated collection somewhere far from here.
 */
class ReportDataWitherTest {

    @Test
    void everyWitherReplacesItsOwnComponentAndMovesNoOther() throws Exception {
        ReportData original = Instancio.create(ReportData.class);
        ReportData donor = Instancio.create(ReportData.class);

        assertThat(withersByComponent())
                .as("no single-argument wither was found — the reflective discovery below is broken, "
                        + "not the record")
                .isNotEmpty();

        for (Map.Entry<String, Method> wither : withersByComponent().entrySet()) {
            String replaced = wither.getKey();
            String name = wither.getValue().getName();
            ReportData copy = (ReportData) wither.getValue().invoke(original, read(donor, replaced));

            assertThat(read(copy, replaced))
                    .as("%s() must replace %s", name, replaced)
                    .isEqualTo(read(donor, replaced));
            assertUntouched(copy, original, replaced, name);
        }
    }

    @Test
    void theDerivedWithersAlsoMoveNoOtherComponent() {
        ReportData original = Instancio.create(ReportData.class);

        assertUntouched(original.withCanonicalizedUrls(), original, "items", "withCanonicalizedUrls");
        assertUntouched(original.withRejoinedMetadata(promptMetaFor(original)), original, "items",
                "withRejoinedMetadata");
    }

    @Test
    void aNullCollectionComesBackEmptyThroughAWither() {
        ReportData report = Instancio.create(ReportData.class);

        assertThat(report.withDependencyReleases(null).dependencyReleases()).isEmpty();
        assertThat(report.withStackAffectingUrls(null).stackAffectingUrls()).isEmpty();
        assertThat(report.withEolNotices(null).eolNotices()).isEmpty();
        assertThat(report.withSilentSources(null).silentSources()).isEmpty();
        assertThat(report.withCallsForPapers(null).callsForPapers()).isEmpty();
    }

    @Test
    void aMutableListHandedToAWitherIsCopiedNotAdopted() {
        List<EolNotice> caller = new ArrayList<>(List.of(Instancio.create(EolNotice.class)));
        ReportData report = Instancio.create(ReportData.class).withEolNotices(caller);

        caller.add(Instancio.create(EolNotice.class));

        assertThat(report.eolNotices()).hasSize(1);
        var notices = report.eolNotices();
        var extra = Instancio.create(EolNotice.class);
        assertThatThrownBy(() -> notices.add(extra))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void aMutableSetHandedToAWitherIsCopiedNotAdopted() {
        Set<String> caller = new HashSet<>(Set.of("https://example.com/a"));
        ReportData report = Instancio.create(ReportData.class).withStackAffectingUrls(caller);

        caller.add("https://example.com/b");

        assertThat(report.stackAffectingUrls()).containsExactly("https://example.com/a");
        var urls = report.stackAffectingUrls();
        assertThatThrownBy(() -> urls.add("https://example.com/c"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static void assertUntouched(ReportData copy, ReportData original, String replaced, String wither) {
        for (RecordComponent component : ReportData.class.getRecordComponents()) {
            if (component.getName().equals(replaced)) {
                continue;
            }
            assertThat(read(copy, component.getName()))
                    .as("%s() must not move %s", wither, component.getName())
                    .isEqualTo(read(original, component.getName()));
        }
    }

    private static Map<String, Method> withersByComponent() {
        Set<String> components = Arrays.stream(ReportData.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());
        return Arrays.stream(ReportData.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> method.getParameterCount() == 1)
                .filter(method -> method.getName().matches("^with[A-Z].*"))
                .filter(method -> components.contains(componentNameOf(method)))
                .collect(Collectors.toMap(ReportDataWitherTest::componentNameOf, method -> method,
                        (first, second) -> first, LinkedHashMap::new));
    }

    private static String componentNameOf(Method wither) {
        String suffix = wither.getName().substring("with".length());
        return Character.toLowerCase(suffix.charAt(0)) + suffix.substring(1);
    }

    private static Object read(ReportData report, String component) {
        try {
            return ReportData.class.getMethod(component).invoke(report);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("no accessor for component " + component, e);
        }
    }

    private static Map<String, PromptItemMeta> promptMetaFor(ReportData report) {
        return report.items().stream().collect(Collectors.toMap(DigestItem::url,
                item -> new PromptItemMeta(item.source(), item.engagementScore()),
                (first, second) -> first));
    }
}
