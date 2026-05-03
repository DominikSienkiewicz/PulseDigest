plugins {
    java
    id("org.springframework.boot") version "4.1.0-SNAPSHOT"
    id("io.spring.dependency-management") version "1.1.7"
    checkstyle
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
val archunitVersion = "1.4.0"
val instancioVersion = "5.4.0"

// ── BOM imports ───────────────────────────────────────────────────────────────
dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:$springAiVersion")
    }
}

dependencies {

    // ── Core (no web server — batch job) ──────────────────────────────────────
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // ── AI (Spring AI OpenAI) ─────────────────────────────────────────────────
    implementation("org.springframework.ai:spring-ai-starter-model-openai:$springAiVersion")

    // ── Lombok ────────────────────────────────────────────────────────────────
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // ── Tests ─────────────────────────────────────────────────────────────────
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("com.tngtech.archunit:archunit-junit5:$archunitVersion")
    testImplementation("org.instancio:instancio-junit:$instancioVersion")
    testImplementation("org.wiremock:wiremock-standalone:3.13.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// ── Checkstyle ────────────────────────────────────────────────────────────────
checkstyle {
    toolVersion = "10.21.2"
    configFile = file("config/checkstyle/checkstyle.xml")
    isIgnoreFailures = false
}

tasks.bootJar {
    archiveFileName.set("app.jar")
}
