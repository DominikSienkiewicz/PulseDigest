package pl.seniordeveloper.pulsedigest.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
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
    void reactiveStackIsNotUsed() {
        REACTIVE_STACK_IS_NOT_USED.check(importedClasses());
    }

    private JavaClasses importedClasses() {
        return new ClassFileImporter().importPackages("pl.seniordeveloper.pulsedigest");
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

    private static final ArchRule REACTIVE_STACK_IS_NOT_USED = noClasses()
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework.web.reactive..",
                    "reactor..")
            .because("the project uses blocking I/O on virtual threads, not WebFlux");

}
