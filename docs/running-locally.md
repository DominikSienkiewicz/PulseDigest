# Running and shipping it

## Tech stack

- **Java 26** with `--enable-preview` (pattern matching, sealed interfaces)
- **Spring Boot 4.1.0-SNAPSHOT** — `web-application-type: none` (headless)
- **Spring AI 2.0.0-SNAPSHOT** — GPT-4o via OpenAI
- **Supabase (Postgres) via JDBC** — `JdbcClient` + `JSONB` payloads; schema owned by **Flyway** migrations (`db/migration`), applied as a discrete CI step (`./gradlew flywayMigrate`), not by the app at startup
- **springboot4-dotenv** — auto-loads `.env` locally (parity with GitHub Actions secrets)
- **Testcontainers** — Postgres container for integration tests (isolated, never touches prod Supabase)
- **Bean Validation** — startup validation for required report/Twitter configuration
- **Micrometer (core only)** — counters/timers registered into a `SimpleMeterRegistry`; on shutdown a `MetricsLogger` dumps every meter as a structured log line (no HTTP scrape, since the batch has no web server). Currently instruments `http.client.retries` per host/reason; new meters can be added with `Metrics.counter(...)` from anywhere.
- **ArchUnit + JaCoCo** — architecture boundaries and minimum coverage gate
- **SonarCloud** — CI-based static analysis and coverage tracking (via the `org.sonarqube` Gradle plugin)
- **Gradle 9** (Kotlin DSL)
- **Project Loom** — Virtual Threads for all I/O

Architecture follows [Hexagonal (Ports & Adapters)](adr/0002-hexagonal-architecture.md).
One bounded context: `market_intel` (fetching + scoring + persistence + delivery). The
domain depends only on its own ports; Spring adapters in `infrastructure` implement them
and the dependency arrow points inward only (`infrastructure → application → domain`).

## Prerequisites

- JDK 26 (Oracle EA or Temurin)
- Twitter API v2 Bearer Token
- OpenAI API key (GPT-4o + GPT-4o-mini for the tech-demand narrative)
- Resend account + API key
- Supabase project (free tier — Postgres for report history)
- Docker Desktop (for Testcontainers when running `./gradlew test`)
- *(Optional)* Product Hunt developer token, YouTube Data API key — without them the respective adapters degrade gracefully (return empty list, log warn, pipeline keeps running)

## Local setup

```bash
cp .env.example .env
# fill in all keys, then:
./run.sh
```

`.env` keys:

```
TWITTER_BEARER_TOKEN=
OPENAI_API_KEY=
RESEND_API_KEY=
DIGEST_FROM_EMAIL=
DIGEST_TO_EMAIL=

# Optional — omittable; the adapters degrade gracefully when unset
PRODUCTHUNT_DEVELOPER_TOKEN=
YOUTUBE_API_KEY=

# Supabase Session Pooler (NOT the Direct connection — direct is IPv6-only on the free tier)
# Format: jdbc:postgresql://aws-0-<region>.pooler.supabase.com:5432/postgres?sslmode=require
SUPABASE_DB_URL=
SUPABASE_DB_USERNAME=postgres.<project-ref>
SUPABASE_DB_PASSWORD=
```

The same env vars are used in **GitHub Actions secrets** — local and CI run against the
**same Supabase database**, guaranteeing "works on my machine == works in prod" parity.

## Database migrations (Flyway)

The schema lives in versioned Flyway migrations under
[`src/main/resources/db/migration`](../src/main/resources/db/migration) — the `reports`,
`feedback`, `tech_demand_history` and `reader_profile` tables. The app **does not**
migrate at startup (no Flyway on its runtime classpath); migrating the database is a
discrete step:

```bash
FLYWAY_URL=$SUPABASE_DB_URL FLYWAY_USER=$SUPABASE_DB_USERNAME FLYWAY_PASSWORD=$SUPABASE_DB_PASSWORD \
  ./gradlew flywayMigrate
```

Flyway records applied versions in `flyway_schema_history`, so this is a no-op once the
schema is current: on a fresh database it applies `V1__initial_schema.sql`, on later runs
only new `V2`, `V3`, … files. In CI the **Migrate database (Flyway)** step runs this before
every scheduled digest. The persistence ITs apply the same migrations to their
Testcontainers Postgres, so each PR proves the migration set still applies cleanly to a
fresh database. To evolve the schema, add a new `V<n>__<name>.sql` — never edit a migration
that has already been applied.

`tech_demand_history` stores one mention snapshot per (month, vocabulary), which is what
lets the tech-demand pulse read last month's numbers instead of re-scraping ~1000 comments
every run. The vocabulary key exists because changing `tech-demand.technologies` changes
what "mentions" means, so counts must never be compared across that boundary.

## Build commands

```bash
./gradlew clean build      # full build
./gradlew check            # Checkstyle + tests + ArchUnit + JaCoCo coverage gate
./gradlew compileJava      # compile only
./gradlew bootJar          # production JAR → build/libs/
./gradlew test             # unit + integration tests
./gradlew sonar            # SonarCloud analysis (needs SONAR_TOKEN; run after test + jacocoTestReport)
./run.sh                   # run locally (sources .env automatically)
```

## GitHub Actions

The workflow at [`.github/workflows/digest.yml`](../.github/workflows/digest.yml) runs
`./gradlew check` on `push`/`pull_request`, plus a parallel **`sonarcloud`** job that runs
`./gradlew test jacocoTestReport sonar` to push code and JaCoCo coverage to
[SonarCloud](https://sonarcloud.io/project/overview?id=DominikSienkiewicz_PulseDigest)
(project key / organization live in [`build.gradle.kts`](../build.gradle.kts)). The digest
job runs on a **Mon/Thu schedule** at **04:00 UTC** (= 06:00 CEST / 05:00 CET) via
`cron: '0 4 * * 1,4'`, and can also be triggered manually via `workflow_dispatch`.

> **SonarCloud — one-time setup.** The scan step is gated on `SONAR_TOKEN` and is
> **skipped** until you set it, so CI never breaks before configuration. To enable it:
> (1) create a **`SONAR_TOKEN`** repository secret (SonarCloud → *My Account → Security*);
> (2) in SonarCloud, **disable *Automatic Analysis*** (*Administration → Analysis Method*) —
> CI-based and automatic analysis cannot both run on one project, and only the CI scan
> uploads the JaCoCo coverage report the "Sonar way" gate (≥ 80% on new code) needs.

The scheduled job is hardened against silent failure: it runs the full `./gradlew check`
gate **before** building the JAR (so a broken pre-release SNAPSHOT is caught instead of
shipping into the digest), a job-level `concurrency` guard prevents a manual dispatch from
overlapping the scheduled run (no double-send / double-spend of OpenAI credits), and an
`if: failure()` step emails the recipient via Resend whenever the run fails for **any**
reason — including a crash or `EMAIL_FAILED` where the app itself couldn't send its own
alert. [`.github/dependabot.yml`](../.github/dependabot.yml) keeps the Actions and
test/tooling dependencies current (Spring Boot / Spring AI SNAPSHOTs are intentionally
excluded).

### Secrets and variables

Required repository **secrets**: `TWITTER_BEARER_TOKEN`, `OPENAI_API_KEY`, `RESEND_API_KEY`,
`DIGEST_FROM_EMAIL`, `DIGEST_TO_EMAIL`, `SUPABASE_DB_URL`, `SUPABASE_DB_USERNAME`,
`SUPABASE_DB_PASSWORD`.

Optional secrets: `PRODUCTHUNT_DEVELOPER_TOKEN`, `YOUTUBE_API_KEY`, `SONAR_TOKEN` (enables
the SonarCloud scan — the `sonarcloud` job is skipped without it), `FEEDBACK_SIGNING_SECRET`
(shared with the feedback receiver; blank → links are rendered unsigned).

Repository **variable** (non-sensitive, read via `vars.*`): `FEEDBACK_RECEIVER_URL` — the
public feedback-receiver endpoint. It lives in *Variables* rather than *Secrets* because,
on a public repo, secrets are masked in Action logs but variables are not, and a public URL
has nothing to hide. Blank/unset → no 👍/👎 links. All of these are injected by the
workflow; adapters and email links no-op gracefully when absent.

**Managing the secrets** — two helper scripts (require the [GitHub CLI](https://cli.github.com),
authenticated via `gh auth login`). Both derive the required set **straight from
`.github/workflows/*.yml`** (every `secrets.*` / `vars.*` reference, minus the auto-injected
`GITHUB_TOKEN`), so the list never drifts from the workflow:

- [`scripts/gh-secrets-check.sh`](../scripts/gh-secrets-check.sh) — read-only audit. Lists every secret/variable the workflows need and shows which are set (`✓`) or missing (`✗`) on the repo, and **flags a key placed in the wrong bucket** (e.g. set as a secret while the workflow reads it as a `vars.*` variable — a silent runtime miss). Exits non-zero when anything is missing (CI-friendly). `-R owner/repo` targets a specific repo.
- [`scripts/gh-secrets-sync.sh`](../scripts/gh-secrets-sync.sh) — pushes the matching keys from your local `.env` (`gh secret set` / `gh variable set`, values piped over stdin so they never hit `ps`/history; non-referenced `.env` keys are ignored). Prints a masked plan and asks to confirm before applying; flags: `--dry-run`, `-y/--yes`, `-f <file>` (default `.env`), `-R owner/repo`.

> **Auth:** both scripts read `GH_TOKEN` from your `.env` (if set) and use it for every `gh`
> call, so they don't depend on your shell's gh auth — and a token in `.env` overrides a
> too-narrow `GH_TOKEN`/`GITHUB_TOKEN` you may have exported globally (e.g. in `~/.zshrc`).
> Give that token the repo **Secrets** (and **Variables**) permission: *Read* for `check`,
> *Read and write* for `sync`. Leave `GH_TOKEN=` blank in `.env` to fall back to your
> `gh auth login` (keyring) token. `GH_TOKEN` is the scripts' own auth — `sync` never pushes
> it as a repo secret. If gh still returns `HTTP 403`, the scripts surface it with a targeted
> hint instead of silently reporting everything as "missing".
