package pl.seniordeveloper.pulsedigest.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

final class ArchitectureRulesTest {

    @Test
    void domainIsFreeOfSpring() {
        DOMAIN_IS_FREE_OF_SPRING.check(importedClasses());
    }

    @Test
    void domainDoesNotDependOnOuterLayers() {
        DOMAIN_DOES_NOT_DEPEND_ON_OUTER_LAYERS.check(importedClasses());
    }

    @Test
    void applicationDoesNotDependOnModuleInfrastructure() {
        APPLICATION_DOES_NOT_DEPEND_ON_MODULE_INFRASTRUCTURE.check(importedClasses());
    }

    @Test
    void applicationDoesNotDependOnInfrastructureConfig() {
        APPLICATION_DOES_NOT_DEPEND_ON_INFRASTRUCTURE_CONFIG.check(importedClasses());
    }

    @Test
    void reactiveStackIsNotUsed() {
        REACTIVE_STACK_IS_NOT_USED.check(importedClasses());
    }

    @Test
    void forbiddenLombokAnnotationsAreNotUsed() {
        FORBIDDEN_LOMBOK_ANNOTATIONS_ARE_NOT_USED.check(productionClasses());
    }

    private JavaClasses importedClasses() {
        return new ClassFileImporter().importPackages("pl.seniordeveloper.pulsedigest");
    }

    private JavaClasses productionClasses() {
        return new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("pl.seniordeveloper.pulsedigest");
    }

    private static final ArchRule DOMAIN_IS_FREE_OF_SPRING = noClasses()
            .that().resideInAPackage("..modules..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.annotation..")
            .because("domain model and ports must stay framework-free");

    private static final ArchRule DOMAIN_DOES_NOT_DEPEND_ON_OUTER_LAYERS = noClasses()
            .that().resideInAPackage("..modules..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..modules..application..",
                    "..modules..infrastructure..")
            .because("dependencies must point domain <- application <- infrastructure");

    private static final ArchRule APPLICATION_DOES_NOT_DEPEND_ON_MODULE_INFRASTRUCTURE = noClasses()
            .that().resideInAPackage("..modules..application..")
            .should().dependOnClassesThat().resideInAPackage("..modules..infrastructure..")
            .because("use cases must depend on ports, not adapters");

    /**
     * Application services consume infrastructure-bound config (e.g. {@code ReportProperties}) only
     * via narrow value objects (policies) constructed in {@code infrastructure/config}. Importing
     * {@code shared/infrastructure/config/*} from application code would re-couple the use case to
     * the YAML schema and break test ergonomics.
     */
    private static final ArchRule APPLICATION_DOES_NOT_DEPEND_ON_INFRASTRUCTURE_CONFIG = noClasses()
            .that().resideInAPackage("..modules..application..")
            .should().dependOnClassesThat().resideInAPackage("..shared.infrastructure.config..")
            .because("application services must depend on narrow policies, not raw @ConfigurationProperties");

    private static final ArchRule REACTIVE_STACK_IS_NOT_USED = noClasses()
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework.web.reactive..",
                    "reactor..")
            .because("the project uses blocking I/O on virtual threads, not WebFlux");

    /**
     * Mirrors the FORBIDDEN list in CLAUDE.md → prefer records or explicit constructors.
     * {@code @Slf4j}, {@code @RequiredArgsConstructor}, and {@code @Builder} stay allowed.
     */
    private static final ArchRule FORBIDDEN_LOMBOK_ANNOTATIONS_ARE_NOT_USED = noClasses()
            .should().dependOnClassesThat().haveFullyQualifiedName("lombok.Data")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("lombok.experimental.UtilityClass")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("lombok.SneakyThrows")
            .because("CLAUDE.md forbids @Data, @UtilityClass, @SneakyThrows — prefer records or explicit code");

}
