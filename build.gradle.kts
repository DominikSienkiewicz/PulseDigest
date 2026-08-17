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

// --enable-preview required for sealed pattern matching in switch
tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("--enable-preview", "-Xlint:preview"))
}
tasks.withType<Test> {
    jvmArgs("--enable-preview")
    useJUnitPlatform()
}
tasks.withType<JavaExec> {
    jvmArgs("--enable-preview")
}

// ── Repositories ──────────────────────────────────────────────────────────────
repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/snapshot") }
    maven { url = uri("https://repo.spring.io/milestone") }
}

// ── Dependency versions ───────────────────────────────────────────────────────
val springAiVersion = "2.0.0-SNAPSHOT"
val archunitVersion = "1.5.0"
val instancioVersion = "5.6.0"
val testcontainersVersion = "1.21.4"
val flywayVersion = "11.1.0"

// Classpath for the flywayMigrate task only — the Flyway engine, its Postgres module and the driver.
// Kept off the app's runtime classpath on purpose: the app assumes the schema exists; migrating it is
// a discrete step. Run via our own JavaExec entrypoint rather than the Flyway Gradle plugin, which
// reaches for JavaPluginConvention — an API removed in Gradle 9.
val flywayCli: Configuration by configurations.creating

// ── BOM imports ───────────────────────────────────────────────────────────────
dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:$springAiVersion")
    }
}

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
    runtimeOnly("org.postgresql:postgresql")

    // ── Local dev: load .env at startup (parity with GH Actions secrets) ─────
    // Boot-specific module: since 5.x the artifact is split per framework, and the plain
    // `spring-dotenv` variant resolves .env keys strictly — relaxed binding is Boot-only.
    // developmentOnly, because CI and the scheduled digest pass every secret as an env var.
    developmentOnly("me.paulschwarz:springboot4-dotenv:5.1.0")

    // ── AI (Spring AI OpenAI) ─────────────────────────────────────────────────
    implementation("org.springframework.ai:spring-ai-starter-model-openai:$springAiVersion")

    // ── Lombok ────────────────────────────────────────────────────────────────
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // ── Flyway (compileOnly: used only by the FlywayMigrate entrypoint, never at app runtime) ──
    compileOnly("org.flywaydb:flyway-core:$flywayVersion")

    // ── Tests ─────────────────────────────────────────────────────────────────
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("com.tngtech.archunit:archunit-junit5:$archunitVersion")
    testImplementation("org.instancio:instancio-junit:$instancioVersion")
    testImplementation("org.wiremock:wiremock-standalone:3.13.2")
    testImplementation("org.testcontainers:postgresql:$testcontainersVersion")
    testImplementation("org.testcontainers:junit-jupiter:$testcontainersVersion")
    // ITs apply the same V1 migration to their Testcontainers Postgres, so every PR proves the
    // migration file applies cleanly to a fresh database before it ever reaches Supabase.
    testImplementation("org.flywaydb:flyway-core:$flywayVersion")
    testImplementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

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
// gate. These globs mirror `coverageExclusions` (the static half) on the `.java` sources.
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
        // Java 26 + --enable-preview (sealed pattern matching in switch): the analyzer
        // must parse preview syntax or it silently skips those files.
        property("sonar.java.enablePreview", "true")
        // Written by the jacocoTestReport task (default location); run it before `sonar`.
        property("sonar.coverage.jacoco.xmlReportPaths", "build/reports/jacoco/test/jacocoTestReport.xml")
        property("sonar.coverage.exclusions", sonarCoverageExclusions.joinToString(","))
    }
}

tasks.bootJar {
    archiveFileName.set("app.jar")
}
