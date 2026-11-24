# AGENTS.md — PulseDigest Project Guidelines

This file contains PulseDigest-specific engineering rules. Global Codex working agreements live in
`~/.codex/AGENTS.md` and apply before this project file.

## Architecture Decision Records

The full index, with statuses, is [docs/adr/README.md](docs/adr/README.md).

| ADR                                                                | Decision                                                  |
|--------------------------------------------------------------------|-----------------------------------------------------------|
| [ADR-0001](docs/adr/0001-polish-as-the-product-language.md)        | Polish for the product, English for the engineering       |
| [ADR-0002](docs/adr/0002-hexagonal-architecture.md)                | Hexagonal architecture, enforced by ArchUnit              |
| [ADR-0003](docs/adr/0003-jsonb-payload-with-domain-records.md)     | One JSONB column, with the domain records as its schema   |
| [ADR-0004](docs/adr/0004-hand-rolled-openai-client.md)             | A hand-rolled OpenAI client, and no Spring AI             |
| [ADR-0005](docs/adr/0005-prompt-budget-allocator.md)               | Per-source caps and one hard total for the prompt         |
| [ADR-0006](docs/adr/0006-virtual-threads-over-reactive.md)         | Virtual threads instead of WebFlux                        |
| [ADR-0007](docs/adr/0007-result-type-over-exceptions.md)           | `Result<T, E>` in use cases instead of exceptions         |

## Tech Stack (Non-Negotiable)

| Concern          | Technology                     | Version                                              |
|------------------|--------------------------------|------------------------------------------------------|
| Language         | Java                           | 27 via the Gradle toolchain (Temurin locally, Oracle JDK in CI); no `--enable-preview` |
| Concurrency      | Virtual Threads (Project Loom) | `dataFetchExecutor` for all source fan-out           |
| Framework        | Spring Boot                    | 4.1.0-SNAPSHOT                                       |
| Database         | Supabase (Postgres) via JDBC   | JdbcClient + JSONB payloads, Flyway migrations       |
| Build            | Gradle 9 (Kotlin DSL)          | —                                                    |

**FORBIDDEN tools / libraries:**

- Spring WebFlux / Reactive Stack (use Virtual Threads instead)
- `@Data`, `@UtilityClass` and `@SneakyThrows` from Lombok (use records or explicit constructors)
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
│   ├── query/                           # Query handlers and view records (none today — see note)
│   ├── policy/                          # *Policy records built from configuration
│   └── error/                           # Sealed module error interface
│
└── infrastructure/                      # Spring adapters — depends on application only
    ├── adapter/
    │   └── out/                         # Outbound adapters: ai, dataset, email, persistence, archive, ...
    └── config/                          # Module-specific @Configuration classes
```

Every port in `domain/port/out/` has exactly one implementation (`PulseDigestContextIT` asserts it).
`@ConfigurationProperties` records live in `shared/infrastructure/config/` and never reach
`application/`: `MarketIntelBootstrap` turns them into the narrow `application/policy/` records.

`query/` is a convention for read-side use cases, not a claim that one exists: the package was
removed on 2026-10-18 because both services in it (`GetLatestMarketReportService`,
`GetMarketIntelStatusService`) had zero production callers — leftovers from a design that served
HTTP. A headless batch reads nothing back; add the package again only when something actually
queries.

---

## Naming Conventions

| Component            | Pattern                                                 | Example                          |
|----------------------|---------------------------------------------------------|----------------------------------|
| Command input        | `{Action}Command`                                       | `GenerateMarketReportCommand`    |
| Query input          | `{Action}Query`                                         | none today                       |
| Use case impl        | `{Action}Service`                                       | `GenerateMarketReportService`    |
| Module errors        | `{Module}Error`                                         | `MarketIntelError`               |
| Read projections     | `{Entity}View`                                          | none today                       |
| Output port          | `*Port` in `domain/port/out`                            | `ReportStoragePort`              |
| Adapter              | provider-prefixed `*Adapter`                            | `SupabaseReportStorageAdapter`   |
| Configuration value  | `*Policy` in `application/policy`                       | `SourceFloorPolicy`              |
| Bound configuration  | `*Properties` in `shared/infrastructure/config`         | `HackerNewsProperties`           |
| Renderer             | `*Builder`                                              | `ReportEmailBuilder`             |
| Tests                | `{Component}Test` (unit), `{Component}IT` (integration) | `MarketResearchServiceTest`      |

---

## Result Pattern (Mandatory for Use-Case Entry Points)

Every use-case entry point that `DigestRunner` dispatches returns the `Result<T, E>` sealed type:
the edition run, and the `--replay`, `--replay-prompt`, `--dry-run`, `--publish-archive`,
`--retrospective` and `--discover-sources` modes. An entry point **never** throws a checked exception
and **never** returns `Optional` to signal failure. Collaborators inside `application/` may return
plain domain values or `Optional`.

```java
// Successful result
return Result.success(jobId);

// Failed result
return Result.failure(new MarketIntelError.JobVanished(jobId));
```

Callers handle results with an exhaustive `switch` and match the specific error case, not a
catch-all `Result.Failure(var error)`, which discards the type information:

```java
return switch (service.handle(command)) {
    case Result.Success(var jobId) -> 0;
    case Result.Failure(MarketIntelError.PipelineFailed failed) -> 1;
    case Result.Failure(MarketIntelError.JobVanished vanished) -> 1;
};
```

The ArchUnit gate (`useCaseHandlersReturnResult`) checks only `handle` on a `*Service` in
`application.command` or `application.query`, so the rule above is wider than its gate. Not yet
compliant: `ReplayService` (`Optional`), `DryRunService.render`, `ArchivePublishService.publish`,
`RetrospectiveService.assemble`, `SourceDiscoveryService.discover`, and the catch-all match in
`DigestRunner.exitCode`.

Genuinely exceptional conditions (a dead socket, an exhausted quota) throw unchecked exceptions
defined in `infrastructure/`; `GenerateMarketReportProcessor` is the one boundary that maps them to
a terminal job status. Optional enrichments catch, log a WARN with the consequence, and return a
neutral value; a catch that swallows an HTTP failure calls `QuotaErrors.rethrowIfQuota(e)` first.

---

## Error Hierarchy (Sealed Interfaces)

Each module defines ONE sealed error interface in `application/error/{Module}Error.java`:

```java
public sealed interface MarketIntelError extends DomainError
        permits MarketIntelError.PipelineFailed, MarketIntelError.JobVanished {

    default String code() {
        return "MARKET_INTEL_ERROR";
    }

    record PipelineFailed(String jobId, ReportJobStatus status, String detail) implements MarketIntelError {
        public String message() { ... }
    }

    record JobVanished(String jobId) implements MarketIntelError {
        public String message() { ... }
    }
}
```

Rules:

- All error cases are `record`s inside the sealed interface.
- No inheritance chains — flat hierarchy only.
- Delete a case that becomes unreachable: an error type whose cases cannot occur is a gate that
  cannot fail.

---

## Domain Model Rules

- **Value Objects** are always `record`s.
- **No Spring annotations in `domain/`** — zero framework coupling.
- Port interfaces in `domain/port/out/` — implementations live in `infrastructure/adapter/out/`.

---

## Testing Strategy

Test classes are package-private, live in the class-under-test's package, and split by aspect as
`{Class}{Aspect}Test` before reaching 500 lines. Method names are camelCase behaviour sentences
(`keepsTheOldProfileWhenTheDistillerFails`): no `should` prefix, no `@DisplayName`, no `@Nested`.
Assertions use AssertJ only.

### Unit Tests (`{Component}Test.java`)

- Test domain logic and application services in isolation — no Spring context. Construct the class
  under test with `new`; replace ports with hand-written stubs; pass `Runnable::run` as the executor.
  Mockito only through static `mock()`/`when()`/`verify()`.
- Build test data with the shared factories (`testing/TestModels`, `ResearchResults`) or private
  static factories in the test, and never shapes the pipeline cannot produce. Instancio is for bulk
  or serialization round-trip tests:
  ```java
  Signal signal = TestModels.signal(item, SignalRank.STRONG, 100, List.of(SourceDomain.CODE));
  ```

### Integration Tests (`{Component}IT.java`)

- HTTP adapters: no Spring context. A `WireMockServer` on a dynamic port per test, the adapter
  built from its properties record, its private `RestClient` field replaced with one pointing at
  WireMock, and inline text-block stubs.
- Persystencja: Testcontainers (`PostgreSQLContainer`) + `JdbcClient` nad własnym `HikariDataSource`,
  schemat zakładany produkcyjnymi migracjami Flyway (`TestSchema`). IT używa izolowanego kontenera,
  NIGDY produkcyjnej Supabase — testy nie zaśmiecają prod bazy. *(Do 2026-10-17 ta linia opisywała
  `@JdbcTest` z `@ServiceConnection`; żaden test persystencji nigdy tego nie używał.)*
- Rozruch kontekstu: `PulseDigestContextIT` — jedyny test ładujący PEŁNY kontekst produkcyjny, na
  Testcontainers Postgres z `@ServiceConnection` i atrapami czterech wartości `@NotBlank`. Zastąpił
  wyłączony `@Disabled` `contextLoads()`. `@SpringBootTest` **wywołuje** `ApplicationRunner`, więc
  `DigestRunner` jest tam podstawiony `@MockitoBean` — inaczej jego `System.exit` ubija JVM w
  środku suity.

### Architecture Tests (ArchUnit)

`ArchitectureRulesTest` enforces six rules:

1. `domain/` is free of Spring.
2. `domain/` does not depend on the outer layers.
3. `application/` does not depend on module infrastructure.
4. `application/` does not depend on infrastructure configuration.
5. The reactive stack is not used.
6. Use-case `handle` methods return `Result`.

A rule whose selector stops matching must fail ("failed to check any classes"), not pass vacuously.
Trust any new test or gate only after it has been seen failing on purpose.

## Code Quality Tools

| Tool               | Purpose                  | Command                    |
|--------------------|--------------------------|----------------------------|
| Checkstyle 10.21.2 | Style enforcement        | `./gradlew check`          |
| JaCoCo             | Coverage (80% minimum)   | `./gradlew check`          |
| ArchUnit           | Architecture enforcement | runs with `./gradlew test` |
| PIT                | Mutation testing, informational, never a gate | `./gradlew pitest` |

**Checkstyle limits:**

- Max file length: 500 lines.
- Max line length: 140 characters.
- One top-level class per file.
- No star imports, no unused imports.
- Javadoc is required on public interfaces and annotations (enforced). It is expected on public
  classes and records that carry a decision, explaining the why and citing the finding ID or ADR.
- No `@Data`, `@UtilityClass` or `@SneakyThrows` (`RegexpSinglelineJava`). This gate lives in
  Checkstyle rather than ArchUnit on purpose: all three are `@Retention(SOURCE)`, so the compiler
  erases them and no bytecode analyser can observe them. The ArchUnit rule that used to carry this
  ban passed whether or not the annotation was present, and was removed on 2026-10-17.

**JaCoCo exclusions** (do not need coverage; Sonar mirrors the same list, so change both together):
`*Application`, `*Bootstrap`, `*Command`, `*Config`, `*Dto`, `*Error`, `*Properties`, `*Query`, `*View`,
`DigestRunner`, `FlywayMigrate`, `shared/error/*`, and every record and enum (detected from source).

**Structural gates** (CI `check` job; each runs locally too):

- `ConfigurationDocsTest`: every `@ConfigurationProperties`/`@Value` key is documented in
  `docs/configuration.md`, and every documented key still exists.
- `CommentLanguageTest`: Polish in a Java comment fails the build (ADR-0001). The same boundary
  makes log and exception messages English, though no test checks them.
- `DigestWorkflowTest`: the `Run digest` step keeps `shell: bash`. Without it, GitHub runs `bash -e`
  with no `pipefail`, `tee` hides the application's exit code, and a failed run stays green.
- `./scripts/check-markdown-links.sh`: relative links in tracked Markdown resolve against tracked
  files. An ignored path (`docs/concepts/`, `CLAUDE.md`, `.maister/`) may be named but never linked.
- `./scripts/check-merge-subjects.sh`: a merge commit never repeats a parent's subject. Prefer a
  squash merge, then a merge with its own subject (`merge: feedback loop (#24)`), then a
  fast-forward. Published duplicates are listed in `KNOWN_DUPLICATES`.
- `git ls-files -z '*.sh' | xargs -0 -r shellcheck` and `actionlint`: fix findings, suppress only
  with a stated reason.

Commit subjects follow Conventional Commits: `type(scope): lowercase imperative description`.

---

## Lombok Rules

Lombok is on the classpath but **restricted**:

| Annotation                 | Status                                 |
|----------------------------|----------------------------------------|
| `@Slf4j`                   | Allowed                                |
| `@RequiredArgsConstructor` | Allowed — the default for beans        |
| `@Builder`                 | Allowed on application-layer DTOs only |
| `@Data`                    | **FORBIDDEN**                          |
| `@UtilityClass`            | **FORBIDDEN**                          |
| `@SneakyThrows`            | **FORBIDDEN**                          |

The three FORBIDDEN entries are enforced by Checkstyle (see **Checkstyle limits** above), not by
ArchUnit — source-retention annotations never reach the bytecode ArchUnit reads.

Prefer Java `record`s for immutable DTOs and Value Objects over any Lombok annotation.

Dependencies are injected through the constructor only, never field `@Autowired`.
`@RequiredArgsConstructor` over `private final` fields is the norm for beans; `lombok.config`
copies `@Qualifier` and `@Value` onto the generated constructor.

---

## Concurrency

- Source fetches fan out via `CompletableFuture.supplyAsync()` on the `dataFetchExecutor`
  (virtual-thread executor, configured in `AsyncConfig`, injected with
  `@Qualifier(AsyncQualifiers.DATA_FETCH_EXECUTOR)`), each future with an explicit `orTimeout`.
  No `parallelStream`, no `new Thread`, no `synchronized` around a blocking call.
- The report pipeline itself runs **synchronously** on the application-runner thread (`DigestRunner` → `GenerateMarketReportService` → `GenerateMarketReportProcessor`). A single-shot batch needs no report executor; the synchronous flow lets the terminal job status map deterministically to the process exit code (0 only on `DELIVERED`).

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
./gradlew check                # Checkstyle + tests + ArchUnit + JaCoCo coverage gate
./gradlew test                 # Unit and integration tests
./gradlew compileJava          # Compile only (fast feedback)
./gradlew bootRun              # Run locally (real run: needs OpenAI + Resend + Supabase)
./gradlew bootJar              # Build production JAR
```

Run modes without a full run ([docs/running-locally.md](docs/running-locally.md) has the details):

```bash
java -jar build/libs/app.jar --dry-run --spring.profiles.active=dry-run   # no credentials, committed edition
java -jar build/libs/app.jar --spring.profiles.active=preview             # real sources, no mail, no database
```

`preview`'s "sends nothing" guarantee lives in bean wiring (`PreviewConfig` provides `@Primary` no-op
mail and persistence ports), not in property values, because environment variables outrank
profile YAML.

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

## Local project concepts

Keep new feature proposals, plans, audits, and exploratory notes in local
`docs/concepts/`, which is ignored by Git. Maintained technical documentation
and accepted architecture decisions remain tracked elsewhere under `docs/`.
