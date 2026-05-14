import java.io.File

plugins {
    java
    id("org.springframework.boot") version "4.1.0-SNAPSHOT"
    id("io.spring.dependency-management") version "1.1.7"
    checkstyle
    jacoco
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
val archunitVersion = "1.4.2"
val instancioVersion = "5.4.0"
val testcontainersVersion = "1.20.4"

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
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    runtimeOnly("org.postgresql:postgresql")

    // ── Local dev: load .env at startup (parity with GH Actions secrets) ─────
    implementation("me.paulschwarz:spring-dotenv:4.0.0")

    // ── AI (Spring AI OpenAI) ─────────────────────────────────────────────────
    implementation("org.springframework.ai:spring-ai-starter-model-openai:$springAiVersion")

    // ── Lombok ────────────────────────────────────────────────────────────────
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // ── Tests ─────────────────────────────────────────────────────────────────
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("com.tngtech.archunit:archunit-junit5:$archunitVersion")
    testImplementation("org.instancio:instancio-junit:$instancioVersion")
    testImplementation("org.wiremock:wiremock-standalone:3.13.0")
    testImplementation("org.testcontainers:postgresql:$testcontainersVersion")
    testImplementation("org.testcontainers:junit-jupiter:$testcontainersVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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

tasks.bootJar {
    archiveFileName.set("app.jar")
}
