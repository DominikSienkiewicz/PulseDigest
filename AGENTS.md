# AGENTS.md — PulseDigest Project Guidelines

This file contains PulseDigest-specific engineering rules. Global Codex working agreements live in
`~/.codex/AGENTS.md` and apply before this project file.

## Architecture Decision Records

| ADR                                                           | Decyzja                                 |
|---------------------------------------------------------------|-----------------------------------------|
| [ADR-0002](docs/adr/0002-hexagonal-architecture.md)        | Dlaczego Hexagonal Architecture         |
| [ADR-0006](docs/adr/0006-virtual-threads-over-reactive.md) | Dlaczego Virtual Threads, nie WebFlux   |
| [ADR-0007](docs/adr/0007-result-type-over-exceptions.md)   | Dlaczego Result&lt;T,E&gt;, nie wyjątki |

## Tech Stack (Non-Negotiable)

| Concern          | Technology                     | Version                          |
|------------------|--------------------------------|----------------------------------|
| Language         | Java                           | 26 (`--enable-preview` required) |
| Runtime features | Virtual Threads (Project Loom) | always enabled                   |
| Framework        | Spring Boot                    | 4.1.0-SNAPSHOT                   |
| AI               | Spring AI                      | 2.0.0-SNAPSHOT                   |
| Database         | Supabase (Postgres) via JDBC   | JdbcClient + JSONB payloads      |
| Build            | Gradle 9 (Kotlin DSL)          | —                                |

**FORBIDDEN tools / libraries:**

- Spring WebFlux / Reactive Stack (use Virtual Threads instead)
- `@Data`, `@UtilityClass` from Lombok (use records or explicit constructors)
- Any wildcard imports (`import java.util.*`)

---

## Module Structure (Hexagonal Architecture)

This is a **headless batch application** — no web server, no messaging. Persystencja raportów
w Supabase (Postgres) przez `JdbcClient`; payload trzymany jako pojedyncza kolumna `JSONB`.
Every business module lives under `src/main/java/.../modules/{context}/` and follows this layout:

```
modules/{context}/
│
├── domain/                              # Pure Java — NO Spring annotations allowed here
│   ├── model/                           # Domain models and Value Objects (use records for VOs)
│   └── port/
│       └── out/                         # Output port interfaces (implemented in infrastructure)
│
├── application/                         # Use cases — Spring @Service beans live here
│   ├── command/                         # Command handlers
│   ├── query/                           # Query handlers and view records
│   └── error/                           # Sealed module error interface
│
└── infrastructure/                      # Spring adapters — depends on application only
    ├── adapter/
    │   └── out/                         # Outbound adapters (AI, Twitter, HN, GitHub, file)
    └── config/                          # Module-specific @Configuration classes
```

---

## Naming Conventions

| Component        | Pattern                                                 | Example                       |
|------------------|---------------------------------------------------------|-------------------------------|
| Command input    | `{Action}Command`                                       | `GenerateMarketReportCommand` |
| Query input      | `{Action}Query`                                         | `GetMarketIntelStatusQuery`   |
| Use case impl    | `{Action}Service`                                       | `GenerateMarketReportService` |
| Module errors    | `{Module}Error`                                         | `MarketIntelError`            |
| Read projections | `{Entity}View`                                          | `MarketIntelStatusView`       |
| Tests            | `{Component}Test` (unit), `{Component}IT` (integration) | `MarketResearchServiceTest`   |

---

## Result Pattern (Mandatory for All Use Cases)

**NEVER** throw checked exceptions or return `Optional` from use cases. Use the `Result<T, E>` sealed type:

```java
// Successful result
return Result.success(report);

// Failed result
return Result.failure(new MarketIntelError.JobNotFound(jobId));
```

Callers use pattern matching to handle results:

```java
return switch (service.handle(query)) {
    case Result.Success(var report) -> process(report);
    case Result.Failure(MarketIntelError.JobNotFound e) -> handleNotFound(e);
    case Result.Failure(var error) -> handleGenericError(error);
};
```

---

## Error Hierarchy (Sealed Interfaces)

Each module defines ONE sealed error interface in `application/error/{Module}Error.java`:

```java
public interface MarketIntelError extends DomainError {

    default String code() { return "MARKET_INTEL_ERROR"; }

    record JobNotFound(String jobId) implements MarketIntelError {
        public String message() { return "Job not found: " + jobId; }
    }

    record ReportNotAvailable() implements MarketIntelError {
        public String message() { return "No report available yet"; }
    }
}
```

Rules:

- All error cases are `record`s inside the sealed interface.
- No inheritance chains — flat hierarchy only.

---

## Domain Model Rules

- **Value Objects** are always `record`s.
- **No Spring annotations in `domain/`** — zero framework coupling.
- Port interfaces in `domain/port/out/` — implementations live in `infrastructure/adapter/out/`.

---

## Testing Strategy

### Unit Tests (`{Component}Test.java`)

- Test domain logic and application services in isolation — no Spring context.
- Use Instancio for test data generation:
  ```java
  var result = Instancio.create(ResearchResult.class);
  ```

### Integration Tests (`{Component}IT.java`)

- HTTP-based adaptery: `@SpringBootTest` z WireMock.
- Persystencja: `@JdbcTest` + Testcontainers (`PostgreSQLContainer` z `@ServiceConnection`).
  IT używa izolowanego kontenera, NIGDY produkcyjnej Supabase — testy nie zaśmiecają prod bazy.

### Architecture Tests (ArchUnit)

ArchUnit enforces:

1. Layer dependency direction: `domain` ← `application` ← `infrastructure`.
2. No Spring annotations in `domain/`.

## Code Quality Tools

| Tool               | Purpose                  | Command                    |
|--------------------|--------------------------|----------------------------|
| Checkstyle 10.21.2 | Style enforcement        | `./gradlew check`          |
| JaCoCo             | Coverage (80% minimum)   | `./gradlew test`           |
| ArchUnit           | Architecture enforcement | runs with `./gradlew test` |

**Checkstyle limits:**

- Max file length: 500 lines.
- Max line length: 140 characters.
- One top-level class per file.
- No star imports, no unused imports.
- Javadoc required for public types and interfaces only.

**JaCoCo exclusions** (do not need coverage):
`*Dto`, `*View`, `*Command`, `*Query`, `*Error`, mappers, configuration classes, records, enums.

---

## Lombok Rules

Lombok is on the classpath but **restricted**:

| Annotation                 | Status                                 |
|----------------------------|----------------------------------------|
| `@Slf4j`                   | Allowed                                |
| `@RequiredArgsConstructor` | Allowed (sparingly)                    |
| `@Builder`                 | Allowed on application-layer DTOs only |
| `@Data`                    | **FORBIDDEN**                          |
| `@UtilityClass`            | **FORBIDDEN**                          |
| `@SneakyThrows`            | **FORBIDDEN**                          |

Prefer Java `record`s for immutable DTOs and Value Objects over any Lombok annotation.

---

## Concurrency

- All external I/O (Twitter, HackerNews, GitHub) runs via `CompletableFuture.supplyAsync()` on the `dataFetchExecutor` (
  Virtual Thread executor).
- Report generation runs on the `reportTaskExecutor` (ThreadPool, 2 threads — prevents concurrent LLM calls).
- Both executors are configured in `AsyncConfig`.

---

## Local Development

Aplikacja używa tej samej Supabase bazy lokalnie i w GitHub Actions — to gwarantuje parytet
"works on my machine == works in prod" oraz pozwala testować na realnych danych.

1. Skopiuj `.env.example` do `.env` i uzupełnij wszystkie zmienne (klucze API + Supabase).
2. `./gradlew bootRun` — `spring-dotenv` automatycznie ładuje `.env` przy starcie.
3. `.env` jest w `.gitignore` — nigdy nie commituj.

W GitHub Actions te same zmienne są wstrzykiwane jako `secrets` w workflow `digest.yml`.

**Testy IT** używają **Testcontainers Postgres**, NIE produkcyjnej Supabase — izolowane,
nie zaśmiecają prod bazy. Wymaga zainstalowanego Dockera lokalnie.

---

## Gradle Commands

```bash
./gradlew clean build          # Full build with all checks and tests
./gradlew check                # Checkstyle + tests + coverage
./gradlew test                 # Unit and integration tests
./gradlew compileJava          # Compile only (fast feedback)
./gradlew bootRun              # Run locally
./gradlew bootJar              # Build production JAR
```

---

## PulseDigest Documentation Checklist

Global Codex instructions require README/docs updates for externally visible changes.
For PulseDigest, `README.md` must be checked before finishing any significant change.

"Istotna zmiana" to:
- Nowy moduł, port, adapter, lub bounded context
- Nowa zewnętrzna zależność (DB, API, library w `build.gradle.kts`)
- Nowa zmienna środowiskowa lub konfiguracyjna w `application.yaml` / `.env.example`
- Zmiana w pipeline'ie (kolejność kroków, nowy boundary, modyfikacja "How it works")
- Nowe sekcje w mailu lub format outputu
- Nowy plik na classpath wymagany przy starcie (np. `schema.sql`)
- Nowy GitHub Actions secret lub krok w workflow
- Zmiana w komendach setup/run (np. nowy prerequisite jak Docker)

Sekcje README do sprawdzenia przy każdej zmianie:
- Lead paragraph (jednolinijkowy opis)
- Badges (jeśli dodano nową kluczową zależność)
- "How it works" diagram + numbered steps
- Tech stack
- Prerequisites
- `.env` keys
- GitHub Actions secrets list
- Configuration table
- Email anatomy (jeśli zmieniał się format)

If a change is purely internal, such as tests or refactoring with no API/UX/setup impact, say that
explicitly in the summary instead of changing README.
