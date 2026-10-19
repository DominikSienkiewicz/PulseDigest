import java.io.File

plugins {
    java
    id("org.springframework.boot") version "4.1.0-SNAPSHOT"
    id("io.spring.dependency-management") version "1.1.7"
    checkstyle
    jacoco
    id("org.sonarqube") version "7.4.0.8496"
}

group = "pl.seniordeveloper"
version = "0.0.1-SNAPSHOT"

// ── Java toolchain ─────────────────────────────────────────────────────────────
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(26)
    }
}

// No `--enable-preview`. It was here until 2026-10-18 with a comment blaming "sealed pattern
// matching in switch" — a feature finalised in Java 21, five releases before the 26 this targets.
// Measured before removing it: of 264 compiled classes, zero carried the preview marker
// (class-file minor version 65535), so the flag was ceremony in six places at once (compiler, test
// JVM, bootRun, Sonar, the CI run command, and the tech-stack table that called it "required").
// It also had the opposite effect to the one the flag usually implies: with nothing preview-marked
// the JAR is not locked to one JDK major, which a run of a 26-built JAR under Temurin 27 confirms.
tasks.withType<JavaCompile> {
    options.compilerArgs.add("-Xlint:deprecation")
}
tasks.withType<Test> {
    useJUnitPlatform()

    // The golden files and the mobile baseline are test INPUTS, not just files a test happens to
    // open. Without saying so, Gradle sees no change when only one of them moves and reports the
    // test task up-to-date — a green build that ran nothing, which is the most expensive kind of
    // false pass there is. (Found by breaking a layout on purpose and watching the run go green.)
    inputs.files(
        "docs/sample-digest.html",
        "docs/sample-digest-thin.html",
        "docs/sample-digest.txt",
        "docs/images/sample-digest-mobile.png",
        // Same reasoning, found the same way: ConfigurationDocsTest reads this file, so editing it
        // has to re-run the tests. Until 2026-10-19 it did not, and a documentation gate that can be
        // skipped by editing exactly the file it guards is not a gate.
        "docs/configuration.md",
        // FeedbackReceiverContractTest reads both, to hold the Java and Deno halves of the HMAC
        // agreement to one vector. CI has no Deno, so this is the only thing that catches drift.
        "receiver/index.ts",
        "receiver/index_test.ts",
    ).withPropertyName("goldenFiles").withPathSensitivity(PathSensitivity.RELATIVE)
    // A file collection rather than `inputs.dir()`, and the difference is not cosmetic: `inputs.dir()`
    // validates that the path exists and fails the task outright when it does not, and `.optional()`
    // does not rescue it — optional permits an ABSENT value, while `layout.buildDirectory.dir(...)`
    // always has one and only its target is missing. So `./gradlew test` without a rendered snapshot
    // died in task validation instead of skipping the comparison, which took the `sonarcloud` job red
    // from c2b40ad onwards (it never runs the renderer) and did the same on any machine without
    // Docker — the one case MobileRenderingRegressionTest documents itself as tolerating. A file
    // collection fingerprints a missing directory as empty, so the up-to-date behaviour the paragraph
    // above exists for is unchanged: re-rendering the snapshot still re-runs the tests.
    inputs.files(layout.buildDirectory.dir("mobile"))
        .withPropertyName("mobileSnapshot")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    // Opt-in re-recording of the email golden file (docs/sample-digest.html). Absent this flag the
    // property is unset and ReportEmailGoldenFileTest can only assert, never write — so no ordinary
    // `test` run, locally or in CI, can overwrite the expected document. Passing the flag rewrites the
    // file AND fails the run, so a re-record is always followed by a deliberate confirming run.
    if (project.hasProperty("update-golden")) {
        systemProperty("pulsedigest.golden.update", "true")
    }
}
// ── Repositories ──────────────────────────────────────────────────────────────
repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/snapshot") }
    maven { url = uri("https://repo.spring.io/milestone") }
}

// ── Dependency versions ───────────────────────────────────────────────────────
val archunitVersion = "1.5.0"
val instancioVersion = "5.6.0"
val testcontainersVersion = "1.21.4"
val flywayVersion = "13.3.0"
val wiremockVersion = "3.13.2"
val dotenvVersion = "5.1.0"

// Classpath for the flywayMigrate task only — the Flyway engine, its Postgres module and the driver.
// Kept off the app's runtime classpath on purpose: the app assumes the schema exists; migrating it is
// a discrete step. Run via our own JavaExec entrypoint rather than the Flyway Gradle plugin, which
// reaches for JavaPluginConvention — an API removed in Gradle 9.
val flywayCli: Configuration by configurations.creating

dependencies {

    // ── Core (no web server — batch job) ──────────────────────────────────────
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    // Micrometer core (no Spring Boot Actuator — batch app has no HTTP server to scrape).
    // Metrics are dumped to logs on shutdown via MetricsLogger.
    implementation("io.micrometer:micrometer-core")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    // ── HTTP client ───────────────────────────────────────────────────────────
    // RestClient is what every outbound adapter actually uses, so it is declared directly.
    // It arrived transitively until now, via a Spring AI starter the code never imported — and
    // that starter also put spring-webflux and reactor on the classpath, the stack ADR-0006
    // rejects and CLAUDE.md lists as forbidden. Declaring the blocking client alone makes the
    // prohibition structural: the reactive types are no longer resolvable, so the ArchUnit rule
    // that forbids them can no longer pass merely because nobody reached for them.
    implementation("org.springframework.boot:spring-boot-starter-restclient")

    // ── Compile-time only ─────────────────────────────────────────────────────
    compileOnly("org.projectlombok:lombok")
    // Flyway is used only by the FlywayMigrate entrypoint, never at app runtime.
    compileOnly("org.flywaydb:flyway-core:$flywayVersion")
    annotationProcessor("org.projectlombok:lombok")

    // ── Runtime ───────────────────────────────────────────────────────────────
    runtimeOnly("org.postgresql:postgresql")
    // Local dev: load .env at startup (parity with GH Actions secrets).
    // Boot-specific module: since 5.x the artifact is split per framework, and the plain
    // `spring-dotenv` variant resolves .env keys strictly — relaxed binding is Boot-only.
    // developmentOnly, because CI and the scheduled digest pass every secret as an env var.
    developmentOnly("me.paulschwarz:springboot4-dotenv:$dotenvVersion")

    // ── Tests ─────────────────────────────────────────────────────────────────
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("com.tngtech.archunit:archunit-junit5:$archunitVersion")
    testImplementation("org.instancio:instancio-junit:$instancioVersion")
    testImplementation("org.wiremock:wiremock-standalone:$wiremockVersion")
    testImplementation("org.testcontainers:postgresql:$testcontainersVersion")
    testImplementation("org.testcontainers:junit-jupiter:$testcontainersVersion")
    // ITs apply the same V1 migration to their Testcontainers Postgres, so every PR proves the
    // migration file applies cleanly to a fresh database before it ever reaches Supabase.
    testImplementation("org.flywaydb:flyway-core:$flywayVersion")
    testImplementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // ── flywayMigrate task classpath ──────────────────────────────────────────
    // Runtime classpath for the flywayMigrate task: the Flyway engine, its Postgres module and the
    // JDBC driver. (flyway-commandline is not consumable as a normal dependency — its POM pulls
    // unpublished flyway-experimental-* modules — so we run flyway-core through our own entrypoint.)
    flywayCli("org.flywaydb:flyway-core:$flywayVersion")
    flywayCli("org.flywaydb:flyway-database-postgresql:$flywayVersion")
    flywayCli("org.postgresql:postgresql")
    // FlywayMigrate reports through SLF4J like every other class here, but neither Flyway nor the JDBC
    // driver puts an SLF4J binding on this classpath — without one the migration step would run mute.
    flywayCli("org.springframework.boot:spring-boot-starter-logging")
}

// Applies pending migrations to the database via the FlywayMigrate entrypoint. Credentials come from
// the environment (FLYWAY_URL / FLYWAY_USER / FLYWAY_PASSWORD), inherited by the forked process, so no
// database coordinates live in the build file. A no-op once flyway_schema_history is current.
tasks.register<JavaExec>("flywayMigrate") {
    group = "flyway"
    description = "Apply pending Flyway migrations (credentials from FLYWAY_URL/USER/PASSWORD env)."
    dependsOn(tasks.named("classes"))
    classpath = flywayCli + sourceSets["main"].output
    mainClass.set("pl.seniordeveloper.pulsedigest.migration.FlywayMigrate")
}

// ── Checkstyle ────────────────────────────────────────────────────────────────
checkstyle {
    toolVersion = "10.21.2"
    configFile = file("config/checkstyle/checkstyle.xml")
    isIgnoreFailures = false
}

// ── JaCoCo ───────────────────────────────────────────────────────────────────
jacoco {
    toolVersion = "0.8.14"
}

val coverageExclusions = listOf(
    "**/*Application.class",
    "**/*Bootstrap.class",
    "**/*Command.class",
    "**/*Config.class",
    "**/*Dto.class",
    "**/*Dto\$*.class",
    "**/*Error.class",
    "**/*Properties.class",
    "**/*Query.class",
    "**/*View.class",
    "**/DigestRunner.class",
    "**/FlywayMigrate.class",
    "**/shared/error/*.class"
)

fun recordAndEnumCoverageExclusions(): List<String> {
    val sourceRoot = layout.projectDirectory.dir("src/main/java").asFile.toPath()
    return fileTree("src/main/java") {
        include("**/*.java")
    }.files.flatMap { sourceFile ->
        val relativeClassPath = sourceRoot
            .relativize(sourceFile.toPath())
            .toString()
            .removeSuffix(".java")
            .replace(File.separatorChar, '/')
        val topLevelType = sourceFile.nameWithoutExtension
        Regex("""\b(record|enum)\s+([A-Za-z_][A-Za-z0-9_]*)\b""")
            .findAll(sourceFile.readText())
            .map { match ->
                val typeName = match.groupValues[2]
                if (typeName == topLevelType) {
                    "$relativeClassPath.class"
                } else {
                    "$relativeClassPath\$$typeName.class"
                }
            }
            .toList()
    }
}

/**
 * The source-path twin of [recordAndEnumCoverageExclusions], for Sonar.
 *
 * JaCoCo excludes by CLASS, Sonar by SOURCE PATH, and that difference is not cosmetic: a record
 * nested inside a class has a class file of its own but no file of its own, so excluding it from
 * Sonar would mean excluding the enclosing class's real logic along with it. Only files whose
 * TOP-LEVEL type is a record or enum can be mirrored honestly — and those are exactly the ones
 * that sank the gate, because JaCoCo reports nothing for them while Sonar still parses their
 * lines as lines to cover and therefore scores every one of them at 0%.
 *
 * The detection is deliberately the same scan as the JaCoCo half, so the two lists cannot drift.
 */
fun recordAndEnumSourceExclusions(): List<String> {
    val sourceRoot = layout.projectDirectory.dir("src/main/java").asFile.toPath()
    return fileTree("src/main/java") {
        include("**/*.java")
    }.files.filter { sourceFile ->
        val topLevelType = sourceFile.nameWithoutExtension
        Regex("""\b(record|enum)\s+([A-Za-z_][A-Za-z0-9_]*)\b""")
            .findAll(sourceFile.readText())
            .any { match -> match.groupValues[2] == topLevelType }
    }.map { sourceFile ->
        "**/" + sourceRoot.relativize(sourceFile.toPath()).toString().replace(File.separatorChar, '/')
    }.sorted()
}

val structuralCoverageExclusions = coverageExclusions + recordAndEnumCoverageExclusions()

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    classDirectories.setFrom(files(classDirectories.files.map {
        fileTree(it) {
            exclude(structuralCoverageExclusions)
        }
    }))
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    violationRules {
        rule {
            limit {
                minimum = "0.80".toBigDecimal()
            }
        }
    }
    classDirectories.setFrom(files(classDirectories.files.map {
        fileTree(it) {
            exclude(structuralCoverageExclusions)
        }
    }))
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

// ── SonarCloud ─────────────────────────────────────────────────────────────────
//
// CI-based analysis (run via `./gradlew test jacocoTestReport sonar` in the
// `sonarcloud` job in .github/workflows/digest.yml). The Gradle plugin auto-detects
// sources, tests, compiled bytecode and libraries; we only pin the project identity,
// the JaCoCo report path and the coverage exclusions.
//
// Sonar derives its own "lines to cover" from the source parser (NOT from JaCoCo), so
// the structural exclusions used by JaCoCo above must be declared again here — otherwise
// records/enums/DTOs/config would count as uncovered and sink the "coverage on new code"
// gate.
//
// Until 2026-10-19 this list mirrored only `coverageExclusions` — the STATIC half — and the
// sentence above was a statement of intent the build did not keep. The dynamic half was never
// mirrored, so all 48 record and enum files carried by the report scored 0% in Sonar while
// being absent from JaCoCo entirely: 349 of 816 uncovered lines, and the whole of the gap
// between JaCoCo's passing 80% and SonarCloud's failing one. Both halves are mirrored now
// (see `recordAndEnumSourceExclusions`), so the two tools finally measure the same denominator.
// The 80% threshold itself is untouched.
val sonarCoverageExclusions = listOf(
    "**/*Application.java",
    "**/*Bootstrap.java",
    "**/*Command.java",
    "**/*Config.java",
    "**/*Dto.java",
    "**/*Error.java",
    "**/*Properties.java",
    "**/*Query.java",
    "**/*View.java",
    "**/DigestRunner.java",
    "**/migration/FlywayMigrate.java",
    "**/shared/error/**"
)

sonar {
    properties {
        property("sonar.projectKey", "DominikSienkiewicz_PulseDigest")
        property("sonar.organization", "dominiksienkiewicz")
        property("sonar.host.url", "https://sonarcloud.io")
        // Written by the jacocoTestReport task (default location); run it before `sonar`.
        property("sonar.coverage.jacoco.xmlReportPaths", "build/reports/jacoco/test/jacocoTestReport.xml")
        property(
            "sonar.coverage.exclusions",
            (sonarCoverageExclusions + recordAndEnumSourceExclusions()).joinToString(",")
        )

        // ── Blokada zależności: świadomie nieobecna, nie zapomniana ───────────────────
        //
        // Dwie reguły żądają `gradle.lockfile` albo `gradle/verification-metadata.xml`.
        // W tym projekcie żadnego z nich nie da się utrzymać uczciwie: build stoi CELOWO
        // na wersji przedpremierowej — Spring Boot 4.1.0-SNAPSHOT — a plik blokady przypina
        // wersję rozwiązaną, podczas gdy SNAPSHOT z definicji zmienia się pod tą samą nazwą.
        // `.github/dependabot.yml` wyklucza tę zależność dokładnie z tego powodu.
        //
        // Do 2026-10-17 wyciszenie opierało się na DWÓCH SNAPSHOT-ach; drugim było Spring AI
        // 2.0.0-SNAPSHOT, zadeklarowane i nigdy nieimportowane, więc połowa uzasadnienia tej
        // reguły była pozorna. Po jego usunięciu wyciszenie stoi na jednym SNAPSHOCIE, który
        // naprawdę jest nośny.
        //
        // Drugi koszt jest operacyjny: każde podbicie Dependabota wymagałoby regeneracji
        // pliku blokady, a pozycja "domknąć PR-y Dependabota" i tak wraca cyklicznie.
        //
        // Wyciszenie jest zapisane TUTAJ, a nie oznaczone jako "Accepted" w interfejsie
        // SonarCloud, świadomie: decyzja przechodzi wtedy przez przegląd kodu, jest
        // wersjonowana i niesie powód obok siebie. Cena jest realna i warto ją znać —
        // zgłoszenia znikają z Sonara całkowicie, także przyszłe wystąpienia tych reguł
        // w tym pliku, więc nikt ich już tam nie zobaczy.
        //
        // DO PONOWNEGO ROZWAŻENIA, gdy projekt zejdzie ze SNAPSHOT-ów na wydania stabilne:
        // wtedy blokada zależności staje się wykonalna i to wyciszenie powinno zniknąć.
        property("sonar.issue.ignore.multicriteria", "blokadaZaleznosci,weryfikacjaZaleznosci,hasloUslugiCi")
        property("sonar.issue.ignore.multicriteria.blokadaZaleznosci.ruleKey", "text:S8569")
        property("sonar.issue.ignore.multicriteria.blokadaZaleznosci.resourceKey", "**/build.gradle.kts")
        // C2. Ten wzorzec był `**/*` — wyciszenie całego repozytorium dla jednej reguły, bo
        // zgłoszenie bywa wystawiane na komponencie PROJEKTU, nie na pliku, a tego nie da się
        // sprawdzić inaczej niż kolejną analizą. Argument był prawdziwy; wniosek nie. Z zakresem
        // ogólnorepozytoryjnym nie dowiemy się NIGDY, czy jest potrzebny, bo wyciszenie usuwa
        // dowód. Zawężone do plików builda, czyli jedynego miejsca, którego ta reguła dotyczy.
        //
        // WARUNEK WYCOFANIA: jeśli następna analiza SonarCloud znów pokaże kotlin:S6474, hipoteza
        // o komponencie projektu się potwierdziła — wtedy wracamy do `**/*` i dopisujemy tutaj, że
        // sprawdzono empirycznie. Wyciszenie oparte na dowodzie jest czymś innym niż wyciszenie
        // oparte na ostrożności, nawet jeśli wygląda tak samo.
        property("sonar.issue.ignore.multicriteria.weryfikacjaZaleznosci.ruleKey", "kotlin:S6474")
        property("sonar.issue.ignore.multicriteria.weryfikacjaZaleznosci.resourceKey", "**/*.gradle.kts")

        // ── Hasło kontenera usługowego Postgresa w CI ────────────────────────────────
        //
        // yaml:S2068 wskazuje `POSTGRES_PASSWORD: pulsedigest` i `FLYWAY_PASSWORD: pulsedigest`
        // w `.github/workflows/`. To nie jest sekret i nie da się go nim zrobić: kontener
        // usługowy GitHub Actions startuje ZANIM wykona się pierwszy krok joba, więc nie można
        // zasilić go wartością wygenerowaną w kroku ani wyjściem innego kroku. Baza żyje tyle,
        // co job, słucha wyłącznie na loopbacku runnera i nigdy nie zawiera danych.
        //
        // Wyciszenie jest zapisane TUTAJ, a nie oznaczone jako "Safe" w interfejsie SonarCloud,
        // z tego samego powodu co dwa wyciszenia powyżej: decyzja przechodzi wtedy przez przegląd
        // kodu, jest wersjonowana i niesie powód obok siebie.
        //
        // WARUNEK WYCOFANIA: gdyby którykolwiek workflow zaczął łączyć się tymi zmiennymi do bazy
        // przeżywającej joba — wtedy to JEST sekret i wyciszenie musi zniknąć razem z literałem.
        property("sonar.issue.ignore.multicriteria.hasloUslugiCi.ruleKey", "yaml:S2068")
        property("sonar.issue.ignore.multicriteria.hasloUslugiCi.resourceKey", "**/.github/workflows/*.yml")
    }
}

tasks.bootJar {
    archiveFileName.set("app.jar")
}

// ── N46: mutation testing on the scoring core, and nowhere else ───────────────────────────────
//
// The audit warns against gates for their own sake and against raising JaCoCo, and it is right:
// 80% over ~90 types is not this project's weakness. But the scoring arithmetic is pure,
// deterministic, central, and the one place where a test that asserts nothing would hide a real
// bug — a signal ranked one tier too low changes what the reader sees and nothing fails. Mutation
// coverage answers there what line coverage cannot: not "was this line executed" but "would anyone
// notice if it were wrong".
//
// PIT is invoked directly rather than through info.solidsoft.pitest, which is the usual way: that
// plugin's latest release (1.15.0) fails on Gradle 9 — it reads `reporting.baseDir`, removed in
// Gradle 9 — and there is no newer release. A JavaExec task is a dozen lines and depends on
// nothing that has to catch up with the build.
//
// Run it on purpose:  ./gradlew pitest
//
// NOT attached to `check`, and no mutation threshold: the moment a mutation score becomes a gate it
// becomes a number to game, and tests written to move it are worse than tests written to catch
// bugs. Read the report; do not grade it.
val pitest: Configuration by configurations.creating

dependencies {
    pitest("org.pitest:pitest-command-line:1.30.1")
    pitest("org.pitest:pitest-junit5-plugin:1.2.3")
}

tasks.register<JavaExec>("pitest") {
    group = "verification"
    description = "Mutation coverage over the scoring core (N46). Not part of `check`."
    dependsOn(tasks.testClasses)
    mainClass.set("org.pitest.mutationtest.commandline.MutationCoverageReport")
    classpath = pitest + sourceSets.test.get().runtimeClasspath

    val reportDir = layout.buildDirectory.dir("reports/pitest").get().asFile
    val mainClasses = sourceSets.main.get().output.classesDirs
    val testClassesDirs = sourceSets.test.get().output.classesDirs

    argumentProviders.add(CommandLineArgumentProvider {
        listOf(
            "--reportDir", reportDir.absolutePath,
            "--sourceDirs", "src/main/java",
            "--outputFormats", "HTML,XML",
            "--timestampedReports", "false",
            "--threads", "2",
            // Scoped to the arithmetic. Running PIT over adapters whose logic is "call HTTP, parse
            // JSON" would spend minutes to learn nothing.
            "--targetClasses", listOf(
                "pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.*",
                "pl.seniordeveloper.pulsedigest.modules.market_intel.application.SignalScoringService",
                "pl.seniordeveloper.pulsedigest.modules.market_intel.application.CalibrationService",
                "pl.seniordeveloper.pulsedigest.modules.market_intel.application.PredictionScoreboardService",
                "pl.seniordeveloper.pulsedigest.modules.market_intel.application.ReadingTimeService",
                "pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.*",
            ).joinToString(","),
            "--targetTests", listOf(
                "pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.*Test",
                "pl.seniordeveloper.pulsedigest.modules.market_intel.application.*Test",
            ).joinToString(","),
            "--excludedTestClasses", "*IT",
            "--classPath", (mainClasses + testClassesDirs).joinToString(",") { it.absolutePath },
        )
    })
}
