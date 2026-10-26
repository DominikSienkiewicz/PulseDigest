# Running and shipping it

## Tech stack

- **Java 26**, compiled by **Eclipse Temurin 26.0.2.1+1** — the Gradle toolchain
  (`languageVersion = 26`) provisions it into `~/.gradle/jdks` automatically, so you do not need a
  matching JDK installed to build. CI uses **Oracle JDK 26**. Class files are major version 70.
- **No `--enable-preview`.** It was passed in six places until 2026-10-18 — compiler, test JVM,
  `bootRun`, Sonar, the CI run command, and a tech-stack row calling it "required" — while **zero of
  264 compiled classes** carried the preview marker (class-file minor version 65535). The build
  comment blamed "sealed pattern matching in switch", finalised in Java 21, five releases earlier.
  Because nothing is preview-marked the JAR is **not** locked to one JDK major version: a JAR built
  on 26 starts normally under Temurin 27, which is how this was confirmed rather than assumed.
- **Spring Boot 4.1.0-SNAPSHOT** — `web-application-type: none` (headless)
- **GPT-4o via OpenAI** — called over a hand-rolled `RestClient` adapter (`spring-boot-starter-restclient`), not an AI framework: the project treats the model as a ranking function behind a port, so a client library would add a dependency without removing any code
- **Supabase (Postgres) via JDBC** — `JdbcClient` + `JSONB` payloads; schema owned by **Flyway** migrations (`db/migration`), applied as a discrete CI step (`./gradlew flywayMigrate`), not by the app at startup
- **springboot4-dotenv** — auto-loads `.env` locally (parity with GitHub Actions secrets)
- **Testcontainers** — Postgres container for integration tests (isolated, never touches prod Supabase)
- **Bean Validation** — startup validation for required report/Twitter configuration
- **Micrometer (core only)** — counters/timers registered into a `SimpleMeterRegistry`; on shutdown a `MetricsLogger` dumps every meter as a structured log line (no HTTP scrape, since the batch has no web server). Currently instruments `http.client.retries` per host/reason; new meters can be added with `Metrics.counter(...)` from anywhere.
- **ArchUnit + JaCoCo** — architecture boundaries and minimum coverage gate
- **gitleaks** — secret scan on every push and pull request, over the **full history** (a secret
  removed from the tip is still a leaked secret). Allowlists live in
  [`.gitleaks.toml`](../.gitleaks.toml) and are narrow by design: a scanner that cries wolf on test
  fixtures gets switched off, and a switched-off scanner is worse than none because the badge stays.
  Verified both ways on 2026-10-18 — clean across all 80 commits, and a planted AWS key fails it
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
- OpenAI API key (GPT-4o for synthesis, GPT-4o-mini for the reader-model distillation)
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
TWITTER_BEARER_TOKEN=     # optional since K7 — X is disabled; set it only to re-enable
OPENAI_API_KEY=
RESEND_API_KEY=
DIGEST_FROM_EMAIL=
DIGEST_TO_EMAIL=

# Optional — omittable; the adapters degrade gracefully when unset
PRODUCTHUNT_DEVELOPER_TOKEN=
YOUTUBE_API_KEY=
GITHUB_API_TOKEN=          # api.github.com sources; blank = 60 requests/h per IP (see below)

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
`feedback` and `reader_profile` tables. The app **does not**
migrate at startup (no Flyway on its runtime classpath); migrating the database is a
discrete step:

```bash
FLYWAY_URL=$SUPABASE_DB_URL FLYWAY_USER=$SUPABASE_DB_USERNAME FLYWAY_PASSWORD=$SUPABASE_DB_PASSWORD \
  ./gradlew flywayMigrate
```

Flyway records applied versions in `flyway_schema_history`, so this is a no-op once the
schema is current: on a fresh database it applies `V1__initial_schema.sql`,
`V2__drop_tech_demand_history.sql`, `V3__edition_inputs.sql`,
`V4__feedback_save_vote.sql` (which widens the `vote` constraint to accept the mail's third
action, `SAVE`) and `V5__edition_deliveries.sql` (the one-edition-per-day interlock, F16), on
later runs only new `V6`, `V7`, … files. In CI the **Migrate database (Flyway)** step runs this before
every scheduled digest. The persistence ITs apply the same migrations to their
Testcontainers Postgres, so each PR proves the migration set still applies cleanly to a
fresh database. `PulseDigestContextIT` goes one step further on the same container: it boots
the **entire production Spring context** against the migrated schema, so a PR that breaks a
bean, an adapter or a `@ConfigurationProperties` binding fails the build instead of failing
the next scheduled digest. To evolve the schema, add a new `V<n>__<name>.sql` — never edit a migration
that has already been applied.

**A fresh database is only half the question, and the other half is the one that ships.**
The ITs always start from an empty Postgres, so they exercise the engine *writing* a new
`flyway_schema_history`. The deployed database has a history table that was written by
whatever Flyway version is on `main`, and every bump has to *read* that table before it
writes to it — a path no fresh-database run ever touches. The **`flyway`** job covers
exactly that difference: it seeds the history using `main`'s build file **and `main`'s
migration directory**, restores the branch's, migrates again, and then requires a third run
to report `0 migration(s) applied`. The no-op is asserted on that log line rather than on
the exit code, because re-applying a migration and applying nothing both exit `0`.

Swapping the migration directory as well as the build file is what makes the job honest on
a branch that adds `V2` *and* bumps Flyway. Seed from the branch's migrations and the old
engine would apply `V2`, leaving the new engine nothing to do — the job would pass having
skipped the thing it exists to exercise. Seeding from `main`'s migrations means the new
engine both reads a history the old one wrote and applies the new migration itself.

That job is also the only place `./gradlew flywayMigrate` itself runs on a pull request.
The task hangs off its own `flywayCli` configuration and is otherwise invoked solely by the
scheduled `digest` job, so before this gate existed a green PR said nothing about whether
the migration step could even resolve its classpath and start.

### `tech_demand_history` is dropped by `V2` — export before you migrate

This reverses what this page said until 2026-10-18. The earlier argument was that dropping a
table is the one step here that cannot be undone and its rows cost nothing to keep. The first
half still holds; the second stopped being the whole story once the feature was gone from the
code, because the table then sat in `V1` — the first thing anyone reads about the data model —
describing a capability the project no longer has.

`V2__drop_tech_demand_history.sql` drops it. **The rows are not recoverable afterwards**, and
the aggregator that produced them was deleted with the feature, so they cannot be recomputed
either. Export them first:

```bash
docker run --rm -e PGPASSWORD="$SUPABASE_DB_PASSWORD" postgres:16-alpine pg_dump "${SUPABASE_DB_URL#jdbc:}" -U "$SUPABASE_DB_USERNAME" -t tech_demand_history --data-only --inserts > tech_demand_history.sql
```

Two details that make the obvious version of this command fail. `SUPABASE_DB_URL` is a **JDBC**
URL (it feeds `spring.datasource.url`), and `pg_dump` rejects the `jdbc:` prefix — hence
`${SUPABASE_DB_URL#jdbc:}`. And `pg_dump` is not necessarily installed on a machine that runs this
project, since nothing else here needs it; running it from the `postgres` image avoids that, and
Docker is already a prerequisite for the tests. Verified against a throwaway container, not just
written down.

Retention for `reports` is deliberately **not** part of `V2`. The payload is load-bearing, not
archival: cross-edition dedup reads `payload->'report'->'items'` over `report.dedup.lookback-days`
(10) and trend memory and the weekly recap read `payload->'report'->'signals'` over
`report.history.lookback-days` (28). Any policy would have to keep at least 28 days — and the
directions under consideration (a prediction scoreboard, a quarterly edition, replaying past
editions, a public archive) all need *older* payloads, not fewer. At roughly 104 editions a year
the storage is trivial and a deleted edition is gone for good.

## On-demand commands

Four things the digest can do besides producing an edition. None of them fetches news.

```bash
java -jar build/libs/app.jar --dry-run --spring.profiles.active=dry-run
```

Renders the committed sample edition through the real builder chain and writes `build/digest.html`
(N37). **No credentials, no network, no mail** — the `dry-run` profile supplies obviously fake values
for the four properties that are `@NotBlank` precisely so a real run fails early, and points the
DataSource at a host that is never dialled. It reads `samples/edition.json`, the same file the
golden-file test renders, so the demo cannot show something the product no longer does.

### The preview profile (F12)

```bash
OPENAI_API_KEY=sk-... java -jar build/libs/app.jar --spring.profiles.active=preview
```

The one mode that *does* fetch news, and the middle that used to be missing between "renders a
stored sample" and "needs three accounts". Today's real sources, a real synthesis, the full
enrichment chain, and then the edition on disk instead of in an inbox and a database. **One
credential: the OpenAI key.**

It is a profile and not a flag because the substitution happens at wiring time: under `preview`,
`PreviewConfig` supplies `@Primary` no-op implementations of every mail and database port, and the
production pipeline then runs completely unmodified. That is deliberate — a second code path that
skipped persistence would be a different pipeline, drifting from the real one until the demo stopped
demonstrating.

Two consequences worth knowing before you read the output:

- **It cannot send or store anything, whatever is in your environment.** The guarantee is in the bean
  selection, not in the fake credentials in `application-preview.yaml`: a real `RESEND_API_KEY`
  exported in your shell outranks a profile's yaml, so a preview that relied on properties alone
  would mail a real reader on the first machine that had one.
- **History-backed sections are empty** — the weekly recap, the calibration line, the saved shelf,
  the silent-source footer, cross-edition dedup. Not suppressed: the read ports answer as they do on
  a new installation, because that is what a preview of a new installation honestly is.

`PreviewProfileIT` boots the production context under this profile with no Docker, no database and no
mail credentials, and asserts which implementations the pipeline got. What no test covers is a live
preview run, which would spend real money at OpenAI.


Three things the digest can do besides producing an edition. All read stored data; none fetches
news.

```bash
java -jar build/libs/app.jar --retrospective
```

Builds and sends the **quarterly retrospective** (N20): the 🔴 badge graded against what actually
happened (N11), what you up-voted, which stories refused to go away, and what each source
contributed. Reads only stored editions, so the same quarter always produces the same thing — which
matters more here than anywhere else, since a quarterly artefact otherwise gets four chances a year
to be right. Exits `1` on an empty window, because "no editions" is a database that could not be
read, not a quiet quarter.

```bash
java -jar build/libs/app.jar --discover-sources
```

Prints **source proposals** (N28): which feeds or accounts the high-ranking published items keep
citing that this digest does not read. At most three, each with the items that justify it, and it
changes no configuration — a human still makes the edit.

```bash
java -jar build/libs/app.jar --replay=<jobId>
java -jar build/libs/app.jar --replay-prompt=<jobId>
```

Replays a stored edition's rubric (free, deterministic) or its prompt (one real synthesis call).

## Mutation testing (N46)

```bash
./gradlew pitest
```

PIT over `domain/model`, the scoring services and the policies — and nothing else. The arithmetic is
pure, deterministic and central, and it is the one place where a test that asserts nothing would hide
a real bug: a signal ranked one tier too low changes what the reader sees and nothing fails.
Mutation coverage answers there what line coverage cannot.

**Not part of `check`, and there is no threshold.** The moment a mutation score becomes a gate it
becomes a number to game, and tests written to move it are worse than tests written to catch bugs.
Read the report at `build/reports/pitest/index.html`; do not grade it. Last run: 572 mutations,
72% killed, test strength 87%.

PIT is invoked through a `JavaExec` task rather than the usual `info.solidsoft.pitest` plugin,
because that plugin's latest release fails on Gradle 9 — it reads `reporting.baseDir`, removed in
Gradle 9 — and there is no newer release.

## Visual regression (mobile)

```bash
scripts/mobile-snapshot.sh
```

Renders [`docs/sample-digest.html`](sample-digest.html) at 375px through a pinned
`zenika/alpine-chrome:124` container into `build/mobile/`, which
`MobileRenderingRegressionTest` then diffs against the committed baseline (N17). **Requires Docker**;
without a rendered snapshot the test aborts rather than fails, so a developer without Docker is not
blocked by a test whose input they cannot produce — CI always renders first, so the comparison is
never skipped where it matters.

```bash
scripts/mobile-snapshot.sh --update
```

Accepts the current rendering as the new baseline. Look at the image before committing it: from then
on it is what the test trusts.

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

### Replaying a past edition

```bash
java -jar build/libs/app.jar --replay=<jobId>
```

Re-scores a stored edition with today's rubric and prints an item-by-item diff of what would have
changed. **Sends nothing, writes nothing, costs nothing** — the rubric is deterministic and runs over
items the edition already published, so this is the way to find out whether a scoring change helped
before it ships to the next edition. Exit `0` means the edition was found and replayed; a replay that
reports differences is a successful replay.

Replaying the **prompt** is a different exercise and deliberately not behind this flag: it needs a
real model call over the stored input in `edition_inputs`, so it spends money per invocation and its
output is not deterministic.

### Replaying the prompt

```bash
java -jar build/libs/app.jar --replay-prompt=<jobId>
```

**This spends money** — one synthesis call per invocation — which is why it is a separate flag from
`--replay` and why the run prints the estimated prompt tokens and cost before calling. It re-runs
today's prompt over the input stored for that edition in `edition_inputs` and diffs the result
against what shipped, naming the source of each item that appeared or disappeared. Editions written
before `edition_inputs` existed cannot be prompt-replayed and the run says so instead of replaying
something else. Set `llm.pricing` to see the estimate in dollars rather than tokens.

### The rendered edition

Every run writes the rendered edition to `build/digest.html` (`report.rendered-edition-path`) before
it attempts delivery, and the workflow uploads it as a run artifact on **every** run, not only on
failure. So a run whose email bounced still leaves the digest downloadable from the Actions tab, and
"what did Thursday's edition look like?" is answerable without the recipient's inbox. The same run
also writes a summary table — items published, 🔴 count, per-source intake, tokens and cost — to the
Actions run page via `GITHUB_STEP_SUMMARY`, which is the default of
[`report.run-summary-path`](configuration.md) — point that property at a file and you get the same
table locally. Outside Actions, with neither set, both are no-ops beyond the local file.

## GitHub Actions

The workflow at [`.github/workflows/digest.yml`](../.github/workflows/digest.yml) runs
`./gradlew check` on `push`/`pull_request`, a **`flyway`** job that migrates a throwaway
Postgres service container to prove the schema upgrade path (see *Database migrations*
above), plus a parallel **`sonarcloud`** job that runs
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
test/tooling dependencies current (the Spring Boot SNAPSHOT is intentionally excluded).

The scheduled run also generates a **CycloneDX SBOM from the built JAR** and signs **build
provenance** for it (N44), uploading both as run artifacts, so "which dependencies were in the run
that sent Thursday's edition" is answerable without anyone having recorded it. Both degrade honestly
under the one remaining SNAPSHOT: the SBOM records a version string that resolves to different bytes
over time, which is a limitation of depending on a pre-release rather than of the SBOM.

It runs under the **`ci` Spring profile**, which turns the console log into ECS JSON (N41) so a
failed run is greppable by source and stage rather than by sentence; every record carries the run's
`jobId` from the MDC. Local runs keep the readable console — JSON is worse to read by eye, and
that is the whole reason the switch is a profile.

**The double-send guard no longer lives only here.** The `concurrency` group protects the one path
that goes through this workflow; a manual `java -jar`, a second runner or a rewritten workflow
bypasses it entirely. The application now also claims the edition in Postgres before sending
(F16, `report.delivery.*`, table `edition_deliveries`), which makes the decision the database's and
therefore race-free across processes.

### Watchdog — the failure this workflow cannot report

[`.github/workflows/watchdog.yml`](../.github/workflows/watchdog.yml) runs **daily at 10:00 UTC**
and asks Supabase one question: is there an edition newer than `MAX_AGE_HOURS` (108h = the longest
Thu→Mon gap plus half a day)? If not, it fails and emails via Resend.

It exists because `if: failure()` needs a run that fails. It cannot fire for the run that never
started — a disabled schedule, a deleted workflow, a runner outage, a credential that expires before
the job reaches its notification step. Nothing happens, nobody is told (N42).

It runs **daily** rather than Mon/Thu on purpose: a Monday-only check would need Monday's schedule
to work in order to report that Monday's schedule did not. Asking every day means a missed Monday is
still caught on Tuesday.

**What it does not cover**, stated plainly because a watchdog believed to be stronger than it is is
worse than none: it is a scheduled workflow in the same repository as the thing it watches. GitHub
disables `schedule` triggers after 60 days without repository activity, and that single event
silences the digest *and* this watchdog together. Closing that gap needs a host outside this
repository — the deployed feedback receiver is the cheapest one, and since 2026-10-19 its source is
in [`receiver/`](../receiver/README.md), so the work is now "add a scheduled check to a function that
exists" rather than "find somewhere to put one". Every other cause is covered, and those are most of them.

It needs no new secrets: `SUPABASE_DB_*`, `RESEND_API_KEY`, `DIGEST_FROM_EMAIL` and
`DIGEST_TO_EMAIL` are the ones the digest job already uses.

### Secrets and variables

Required repository **secrets**: `OPENAI_API_KEY`, `RESEND_API_KEY`,
`DIGEST_FROM_EMAIL`, `DIGEST_TO_EMAIL`, `SUPABASE_DB_URL`, `SUPABASE_DB_USERNAME`,
`SUPABASE_DB_PASSWORD`.

Optional secrets: `PRODUCTHUNT_DEVELOPER_TOKEN`, `YOUTUBE_API_KEY`, `SONAR_TOKEN` (enables
the SonarCloud scan — the `sonarcloud` job is skipped without it), `FEEDBACK_SIGNING_SECRET`
(shared with the feedback receiver; blank → links are rendered unsigned).

`GITHUB_API_TOKEN` is **not** a secret to set: the `Run digest` step maps it from the
automatic `GITHUB_TOKEN`. Every api.github.com source (Search, Releases, Security Advisories,
OpenJDK JEP, CNCF) shares one rate limit — 60 requests an hour per IP unauthenticated, on a
runner whose IP other tenants share — and running out of it is what opens the edition with the
"GitHub API (token)" banner. With the token it is 1,000 an hour per repository. Locally it is
optional: a fine-grained PAT with *Public repositories (read-only)* access and no permissions,
created at <https://github.com/settings/personal-access-tokens/new>. It is deliberately not
read from `GITHUB_TOKEN`, which may be exported globally in `~/.zshrc` and, once expired, would
turn every GitHub source into a 401 instead of the unauthenticated limit.

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
