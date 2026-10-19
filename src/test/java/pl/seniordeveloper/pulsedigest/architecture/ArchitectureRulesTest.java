package pl.seniordeveloper.pulsedigest.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.shared.result.Result;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
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
    void useCaseHandlersReturnResult() {
        USE_CASE_HANDLERS_RETURN_RESULT.check(importedClasses());
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
     * The gate for the project's most-cited decision (ADR-0007). Until this rule existed nothing checked
     * it: the ADR and CLAUDE.md were a promise kept alive by whoever remembered it.
     *
     * <p>It guards exactly one method today, {@code GenerateMarketReportService.handle}, and that is on
     * purpose: the value is prospective, the first use case added with a raw return type fails here
     * instead of in review. The selector is deliberately narrow. The other {@code *Service} classes in
     * {@code application/} are collaborators that return domain values, not use cases, so a rule over
     * every public method there would be wrong rather than merely strict.
     *
     * <p>{@code application.query} does not exist at the moment (removed when its only services turned
     * out to have no callers). That is harmless: a package selector that matches nothing is not an
     * error, and the rule still selects the command handler. {@code allowEmptyShould} is intentionally
     * not used, because a rule that selects nothing is a gate that cannot fire. If the handler is ever
     * renamed or moved out of these packages, this rule fails with "failed to check any classes"
     * instead of passing vacuously, which forces the selector to be updated along with the code.
     */
    private static final ArchRule USE_CASE_HANDLERS_RETURN_RESULT = methods()
            .that().haveName("handle")
            .and().areDeclaredInClassesThat().resideInAnyPackage("..application.command..", "..application.query..")
            .and().areDeclaredInClassesThat().haveSimpleNameEndingWith("Service")
            .should().haveRawReturnType(Result.class)
            .because("an expected failure must travel in the signature, where the compiler forces every caller "
                    + "to handle it, instead of surfacing as an exception or an empty Optional (ADR-0007)");

}
