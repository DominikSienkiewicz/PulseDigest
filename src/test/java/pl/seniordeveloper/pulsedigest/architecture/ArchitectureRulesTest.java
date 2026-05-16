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

    @Test
    void marketIntelDoesNotDependOnTrendAnalyticsInternals() {
        MARKET_INTEL_DOES_NOT_DEPEND_ON_TREND_ANALYTICS_INTERNALS.check(importedClasses());
    }

    @Test
    void trendAnalyticsDoesNotDependOnMarketIntelApplicationOrInfrastructure() {
        TREND_ANALYTICS_DOES_NOT_DEPEND_ON_MARKET_INTEL_APPLICATION_OR_INFRASTRUCTURE.check(importedClasses());
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

    /**
     * Cross-module isolation: market_intel learns about trend_analytics only via the inverted
     * {@code ReportEnrichmentPort} (defined in market_intel/domain, implemented by
     * trend_analytics/infrastructure). Any direct import of trend_analytics application or
     * infrastructure types from market_intel would break that inversion.
     */
    private static final ArchRule MARKET_INTEL_DOES_NOT_DEPEND_ON_TREND_ANALYTICS_INTERNALS = noClasses()
            .that().resideInAPackage("..modules.market_intel..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..modules.trend_analytics.application..",
                    "..modules.trend_analytics.infrastructure..")
            .because("market_intel must only depend on trend_analytics via the domain port it owns");

    /**
     * Symmetric guard: trend_analytics consumes market_intel's domain (DigestItem, ReportData,
     * ReportEnrichmentPort) but must not reach into its application services or adapters.
     */
    private static final ArchRule TREND_ANALYTICS_DOES_NOT_DEPEND_ON_MARKET_INTEL_APPLICATION_OR_INFRASTRUCTURE =
            noClasses()
                    .that().resideInAPackage("..modules.trend_analytics..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..modules.market_intel.application..",
                            "..modules.market_intel.infrastructure..")
                    .because("cross-module wiring goes through market_intel's domain ports only");

}
