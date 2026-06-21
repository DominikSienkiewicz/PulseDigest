# 🚀 PulseDigest
**Architected & Developed by [Dominik](https://www.linkedin.com/in/dominik-sienkiewicz/)** *Principal AI Engineer | Full Stack Architect*

Headless batch application that collects tech news from 16 sources three times a week (Mon/Wed/Fri), scores items with GPT-4o, **detects cross-source signals** (the same topic surfacing in Science + Code + Business = 🔴 Critical Trend), **detects recurring trends across the last 7 days** (Supabase-backed history), tracks per-source health, and delivers a tier'd, prioritized digest to your inbox — with a must-know hero block, deals & tools, critical trends, top picks, signals, and a weekly trend section.

![Java 26](https://img.shields.io/badge/Java-26-red?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot 4.1](https://img.shields.io/badge/Spring_Boot-4.1.0--SNAPSHOT-green?style=for-the-badge&logo=springboot&logoColor=white)
![Spring AI 2.0](https://img.shields.io/badge/Spring_AI-2.0.0--SNAPSHOT-blue?style=for-the-badge)
![Supabase](https://img.shields.io/badge/Supabase-Postgres-3ECF8E?style=for-the-badge&logo=supabase&logoColor=white)
![Architecture](https://img.shields.io/badge/Architecture-Hexagonal-orange?style=for-the-badge)

## 🧠 The Vision: Signal over Noise
In the era of AI-driven information overload, this tool is my personal solution to maintain high-level situational awareness without manual scrolling. It applies **Principal-level scoring logic** to filter out noise and focus only on high-impact architectural and AI shifts. It's not just a scraper; it's a cognitive filter designed for elite engineers.

## How it works

```
Twitter/X             ──┐
Hacker News             ┤
GitHub                  ┤
RSS feeds (30)          ┤
Reddit (8 subs)         ┤                                            ┌─► trend_analytics ─┐
arXiv                   ┤                                            │   (last 7 days from │
GitHub Releases         ┤                                            │   Supabase + LLM   │
Hugging Face Hub        ├─► MarketResearchService ─► GPT-4o synth ──┤   narratives)      ├─► Supabase save
Product Hunt            ┤   (parallel fetch,         (score 1-10,    │                    │   ↓
GitHub Advisories       ┤    URL canonicalization,    category, type,│                    │   Resend email
OpenJDK JEP             ┤    2-10 day window)         editorial, PL)  └────────────────────┘
CNCF Landscape          ┤
Tech Radar              ┤
YouTube Conferences     ┤
AI-lab announcements    ┤
Social (Bsky/Mastodon)  ┘
```

1. **Fetch** — 16 sources run in parallel via Virtual Threads (`CompletableFuture`) with per-source deadlines, shared HTTP connect/read timeouts, and automatic retry for 429/5xx responses (honoring the server's `Retry-After` header, with jitter so parallel fetches don't retry in lock-step). Each source filters to its **configured lookback window** (≈80h for fresh news, up to 10 days for slow sources), sized so nothing slips between the Mon/Wed/Fri runs; it records a source-health entry. Every lookback is config-driven (no hidden hardcoded 24h windows).
2. **Canonicalize URLs** — strip tracking params (`utm_*`, `fbclid`, `gclid`, etc.) right after fetch, before LLM sees anything. Prevents duplicate items from same article via different campaigns and avoids leaking our UTMs to advertisers when readers click.
3. **Score** — `ReportPromptBuilder` first suppresses items by URL — **cross-edition duplicates** (already published in an edition from the last `dedup.lookback-days` days, read from the `reports` table) and **reader down-votes** (🟥 "less like this" feedback from the last `feedback.lookback-days` days, read from the `feedback` table) — so the wider lookback windows don't re-surface the same item and the reader can mute things; both cover every source incl. tweets. It then selects up to 100 items using per-source caps and a **weighted pre-score** (`round(sourceWeight×100) + min(50, engagement/1000)`) to resolve overflow: a low-engagement GitHub Releases item (pre-score=95) survives over a viral tweet (max pre-score=90). Source weights are tuned to the reader's profile — usable tools/launches (Product Hunt, Hugging Face) and stack releases rank above research papers (arXiv demoted). GPT-4o then deduplicates, scores each surviving item 1–10 for an AI-native architect profile (returning **only score ≥ 6** — quality over quantity), assigns a **category** (topic) and **type** (signal kind, incl. `PROMOTION` for deals), and writes a 1–2 sentence Polish summary plus a one-sentence **`why_it_matters`** action line.
4. **Synthesize** — GPT-4o produces an editorial lead (meta-thesis of the day) + top-3 insights + email preheader text. Token `usage` is logged per call for cost visibility; a response truncated at the token cap (`finish_reason=length`) fails fast with an actionable error instead of a cryptic JSON-parse failure. The system prompt instructs the model to treat all scraped item text as untrusted data, never as instructions (prompt-injection defense).
5. **Trend enrichment** — `trend_analytics` module reads the last 7 days of reports from Supabase (JSONB query), counts recurring categories with frequency analysis, runs **one batched GPT-4o-mini call** to generate 1-sentence narratives ("trzeci dzień z rzędu CVE w popularnych narzędziach"), and adds them to the report. Graceful — if history is empty or LLM fails, mail still ships without the trend section.
6. **Signal scoring** — `SignalScoringService` groups items by LLM-assigned category, resolves each source to a domain type (`SCIENCE` / `CODE` / `BUSINESS` / `SOCIAL` / `SECURITY`), and computes a deterministic score: `round(sourceWeight × 100) + min(50, engagement / 1000)`, where `sourceWeight` is the base credibility weight **nudged by accumulated reader feedback** (see the **Feedback loop** section). Categories that surface across **3+ distinct source domains** in the same digest receive a +50 cross-source bonus and are promoted to 🔴 **CRITICAL**. Every item is wrapped in a `Signal` with rank `CRITICAL → STRONG → MODERATE → WEAK`. Output: `List<Signal>` sorted by rank then score descending.
7. **Persist** — full enriched `PersistedReport` saved to Supabase (`reports` table, JSONB payload) for tomorrow's trend analysis to read. Job status moves through `GENERATED → PERSISTED`.
8. **Deliver** — HTML email via Resend with a CV-targeted layout: ⚡ Must-know (top items + `why_it_matters`) · 🛠️ Deals & Tools · 🔴 Critical Trends · 🔄 Weekly trends · ⭐ Top picks (score ≥ 8) · 🔌 Signals (6–7). Both item tiers render the same full table — title, summary, category, type badge, source, engagement, score — differentiated only by header style and row background; items below score 6 are dropped (no long-tail padding). Every article link is scheme-allow-listed (only `http`/`https`; a `javascript:`/`data:` URL slipped in via scraped content collapses to `#`). The process exits successfully only after Resend confirms delivery (`DELIVERED`); email failure becomes `EMAIL_FAILED`.

## Sources

| Source              | Filter                                                                              | Notes                                       |
|---------------------|-------------------------------------------------------------------------------------|---------------------------------------------|
| Twitter/X           | 24 CV-curated accounts (3 batches of 8) + 4 topic queries, last 4 days, `max_results: 30` | Per-run call budget `twitter.max-calls-per-run: 10` hard-caps spend (~8 calls/run); authority accounts bypass keyword filter, others require relevance match. Server-side `min_faves: 3` floor enabled (tier-dependent operator — if your X tier rejects it the source fails gracefully; set `twitter.min-faves: 0`) |
| Hacker News         | Algolia `numericFilters=created_at_i>` (config `lookback-hours`), keyword query     | `min-score: 40`, `lookback-hours: 80`, max 15 items |
| HN Who-is-hiring    | Algolia `author_whoishiring` story + thread comments; aggregates tech mentions      | Monthly thread, shown only when fresh (`lookback-days: 7`). Feeds the 📈 tech-demand pulse, not the item table |
| GitHub              | `pushed:>=` (config `lookback-days`)                                                | Stars desc, `lookback-days: 4`, max 8 repos |
| RSS                 | 30 feeds — core dev (InfoQ, Spring Blog, Baeldung, DZone ×2, JVM Bloggers, devstyle.pl, TLDR Tech, Pragmatic Engineer, Quastor), security (Niebezpiecznik, Sekurak), official changelogs (OpenAI, Google Blog, JetBrains), AI newsletters (Import AI, Simon Willison, Latent Space, Sebastian Raschka, Andrej Karpathy), community (Lobsters, dev.to AI/Java), PL ecosystem (JVM Advent), cloud (AWS, GCP, Azure), JVM (Inside.java), cloud-native (CNCF Blog) | `pubDate` / `updated` filter (`lookback-hours: 80`), max 10 per feed |
| Reddit              | `t=day` (top 24 h)                                                                  | 8 subreddits, `min-score: 30`, max 15 per sub |
| arXiv               | Categories `cs.AI, cs.LG, cs.CR, cs.DC, cs.PL` + keyword filter                     | Last 80 h, max 20 papers                    |
| GitHub Releases     | 17 monitored repos (Spring Boot, Spring AI, Quarkus, GraalVM, vLLM, llama.cpp, K8s, OTel…) | Last 80 h, latest release per repo only |
| Hugging Face Hub    | Public `/api/models?sort=trendingScore`; pipeline filter (text-generation, text-to-image, text-to-speech, image-to-text, ASR, feature-extraction, text-to-video) | `min-likes: 10` OR `min-downloads: 1000`, max 30 trending models |
| Product Hunt        | GraphQL `posts(order: VOTES)`, topics: AI, Developer Tools, Productivity, Open Source, Tech | `min-votes: 100`, lookback 80 h. Bez tokenu adapter zwraca pustą listę bez crashu |
| GitHub Advisories   | Public `/advisories?sort=published`, severity HIGH+CRITICAL, ecosystems: maven, pip, docker, actions (stack-relevant only) | Last 72 h, max 20. Karmi badge `INCIDENT`. Off-stack advisories LLM scores ≤3 (long tail). Source weight demoted to `0.30` (background topic) |
| OpenJDK JEP         | GitHub Commits API on `openjdk/jdk`, parsing JEP IDs + status changes (Candidate, Proposed to Target, Integrated, Delivered) | Last 10 days. Deduplicated by JEP number |
| CNCF Landscape      | GitHub Commits API on `cncf/landscape`, filtering commits touching `landscape.yml` (sandbox, incubating, graduated, archived) | Last 7 days. Status-change detection |
| Tech Radar          | Thoughtworks Technology Radar (quarterly). Rings: Adopt, Trial, Assess, Hold.                                | Quarterly cadence; pre-LLM intake capped at 2 (background) |
| YouTube Conferences | YouTube Data API v3 `search`, 3 tech channels (SpringDeveloper, Devoxx, CNCF) | API key optional. Last 10 days, sorted by recency; pre-LLM intake capped at 3 (background) |
| AI-lab announcements | Official lab blogs/newsrooms, 5 sources via 3 scrape strategies: **SANITY** — `anthropic.com/news`, `anthropic.com/engineering` (inline Sanity CMS data); **JSONLD** — `claude.com/blog`, `blog.google/.../gemini/` (listing → per-post `datePublished`); **OPENAI_DEV** — `developers.openai.com/blog` (cards inline). | Last 80 h. **Highest-signal source** for AI model/product news — very high engagement_score in LLM prompt so it never gets trimmed. Stateless (no DB). A failing source is skipped; only a total outage marks the source FAILED |
| Social (Bluesky + Mastodon) | Bluesky AppView author-feeds (public, no auth) for configured handles + Mastodon hashtag-timelines on one instance (public) | Free-source recovery of CV-relevant signal trimmed off the X budget. One merged "Social" source; each network degrades gracefully (FAILED only if both fail). `min-likes` + `limit`; tune `social.bluesky.handles` / `social.mastodon.hashtags` |

## Categorization (two dimensions)

Each digest item is classified along **two orthogonal axes**:

**Category** — *what topic area the item belongs to*
`Java/JVM` · `AI/LLM` · `Cloud/DevOps` · `Security/Privacy` · `Architecture` · `Open Source` · `Research` · `Releases` · `Community` · `Other`

> **Editorial priority:** the four core areas — **JVM/Backend, Python-AI, AI/LLM, Containers & Cloud-Native** — must make up ≥ 80 % of items scored ≥ 7. `Security/Privacy` is a **background** topic, capped at 1 item and only when the CVE/advisory directly hits the audience's stack (Spring/Maven, PyPI, container runtime, Kubernetes/CNCF, cloud compute). Off-stack security is scored ≤ 3 or dropped. This is enforced at three layers: deterministic intake caps (`PromptItemSelector`), down-weighted source credibility (`SourceWeights`), and the LLM scoring rubric (`system-prompt.txt`).

**Type** — *what kind of signal the item carries*

| Type           | Meaning                                                              |
|----------------|----------------------------------------------------------------------|
| `RELEASE`      | New version of existing software (Spring Boot 4.1, K8s 1.30)         |
| `FEATURE`      | New capability added to existing tool/platform                       |
| `LAUNCH`       | Premiere of a brand-new product, tool, or service                    |
| `PROMOTION`    | Deal / offer: price drop, free tier, credits, grant, early-access     |
| `BREAKTHROUGH` | Breakthrough research result, dramatic benchmark improvement, SOTA   |
| `TREND`        | Growing community signal, topic gaining momentum                     |
| `INCIDENT`     | Bug, CVE, breach, security incident, postmortem                      |
| `OPINION`      | Expert analysis, architectural essay, thought-leader commentary      |
| `DISCUSSION`   | Hot community discussion (viral HN thread, Twitter/X debate)         |
| `RESOURCE`     | Tutorial, course, guide, documentation, cheatsheet                   |
| `HIRING`       | Job posting, hiring market shift, company restructure                |

Both fields are LLM-assigned. The email renders `type` as a colored badge (semantic palette: red for INCIDENT, purple for RELEASE, green for RESOURCE, etc.).

## Email anatomy

The delivered HTML email is a structured digest, not just a link list:

- **Hidden preheader** — 60–90 char preview text shown in inbox snippet (Gmail/Apple Mail).
- **⚠️ Exhausted-limits banner** — amber block at the very top, shown only when one or more API accounts hit their quota / rate limit this run (e.g. depleted X API credits, GitHub/YouTube rate limit). Names each account to top up so the recipient knows the digest is partial. Absent when every source succeeded. Quota detection (`QuotaSignals` / `QuotaErrors`) classifies failures by HTTP 402/429/403-quota and provider wording; generic transient failures stay in the footer's source-health line instead.
- **Editorial lead** — 2–3 sentence meta-thesis tying together the day's most important signals (italic, prominent).
- **⚡ Must-know** — hero block: the up-to-5 highest-score items (score ≥ 7), each with a one-sentence **"why it matters to you"** action line generated against the reader's profile (`why_it_matters`). When `feedback.receiver-url` is set, each item also carries 👍/👎 links (the feedback loop, see below). The "if you read nothing else" section. Skipped when no item clears the bar.
- **🔑 Top insights** — top-3 takeaways extracted from the day.
- **🛠️ Deals & Tools** — up-to-5 adoptable items of type `LAUNCH` / `RELEASE` / `FEATURE` / `PROMOTION` (score ≥ 6): new tools the reader can use and deals/offers worth claiming. Skipped when none qualify.
- **🔄 Weekly trends** — recurring categories from the last 7 days with LLM-generated narratives ("Trzeci dzień z rzędu CVE…"). Skipped if history is empty or no category passes the `min-occurrences` threshold.
- **📈 Puls rynku (tech-demand pulse)** — demand ranking from the monthly HN "Who is hiring?" thread, computed **outside** the core item budget. Shows: a one-line **LLM interpretation**; technologies by **share of hiring posts** with month-over-month **▲/▼ delta** (e.g. "Python 26% ▲3 · TypeScript 23% ▼1 · Rust 9% ▲4"); a **"Twój stack"** line with demand for the reader's own JVM/Python-AI core (`tech-demand.priority-technologies`) even when outside the top ranking; and a "vs <prev month>" footnote. The delta is stateless — the adapter also fetches the **previous** month's thread and compares share in percentage points. Shown only in the ~week after a new monthly thread drops (`tech-demand.lookback-days`), then absent — so it never repeats every run. Off when no technology clears `min-mentions`.
- **🔴 Critical Trends** — items whose LLM-assigned category appears in 3+ distinct source domains (e.g., Science + Code + Business) in the current digest. Red-bordered block with domain labels. Skipped when no CRITICAL signals are present.
- **⭐ Top picks** — score ≥ 8, white background.
- **🔌 Signals** — score 6–7, muted `#fafafa` background.

Both item tiers render an identical full table: **article link + 1–2 sentence Polish summary · category badge · type badge · source + engagement (❤/pkt/★/↑) · color-coded score**. The background shade is the only visual distinction — every tier gives enough context to decide whether to click. Items below score 6 are dropped (quality-over-quantity — no long-tail padding).

> **Standalone failure-alert email.** Whenever the digest cannot be produced **before delivery** — for *any* reason: the LLM ran out of credits, every source was rate-limited, the model returned truncated JSON, or an unexpected error aborted the run — a separate minimal alert email (red header, "Digest nie powstał") is sent so a broken run is never silent. When a quota/rate-limit signature is detected the accounts to top up are named (including `OpenAI (model LLM)`); otherwise the alert carries the failure reason only. It is deliberately **not** sent on `EMAIL_FAILED` — there the email channel itself is down, so the GitHub Actions failure notification (below) is the backstop instead. Sending the alert never masks the original failure: if it too fails, the job error is preserved.
- **Footer** — transparency block: "selected N of M items · K sources · source warnings · Mon/Wed/Fri window".

## Feedback loop (👍/👎)

The reader can mute topics without the app ever serving HTTP (it stays a headless batch — see [ADR-0006](docs/adr/0006-virtual-threads-over-reactive.md)). The loop is **Supabase-mediated**:

1. Each Must-know item carries 👍/👎 links pointing at an **external receiver** you deploy (e.g. a Supabase Edge Function), set via `FEEDBACK_RECEIVER_URL`. When blank, no links are rendered.
2. The receiver records the click into the Supabase `feedback` table. **Contract:** `GET {receiver-url}?url=<item-url>&vote=up|down&source=<source>` → `INSERT INTO feedback (item_url, source, vote) VALUES (<url>, <source>, 'UP'|'DOWN')`.
3. On the next run the batch reads recent feedback ([`SupabaseFeedbackAdapter`](src/main/java/pl/seniordeveloper/pulsedigest/modules/market_intel/infrastructure/adapter/out/persistence/SupabaseFeedbackAdapter.java), `feedback.lookback-days` window) and acts on it two ways:
   - **Suppresses** down-voted item URLs before LLM scoring, alongside cross-edition dedup — the reader mutes a specific item.
   - **Nudges per-source weight** in deterministic signal scoring: votes aggregate at the **base-source** level (a vote on any item from a source — `arXiv/cs.AI`, `Reddit/r/java`, `RSS/InfoQ` — counts toward that source's `arXiv` / `Reddit` / `RSS` weight, not the exact label), and each source's net votes (👍 − 👎) shift its credibility weight by `±0.05` per vote, clamped to `±0.30` and `[0.10, 0.99]`. So a consistently down-voted source ranks lower (and an up-voted one higher) over time, without ever crossing the STRONG threshold on feedback alone. With no votes the weights are unchanged.

   The `feedback` table is created from [`schema.sql`](src/main/resources/schema.sql) on startup.

The receiver itself lives outside this repo (the batch only ever *reads* feedback).

## Tech stack

- **Java 26** with `--enable-preview` (pattern matching, sealed interfaces)
- **Spring Boot 4.1.0-SNAPSHOT** — `web-application-type: none` (headless)
- **Spring AI 2.0.0-SNAPSHOT** — GPT-4o via OpenAI
- **Supabase (Postgres) via JDBC** — `JdbcClient` + `JSONB` payloads, schema bootstrapped from `schema.sql` on startup
- **spring-dotenv** — auto-loads `.env` locally (parity with GitHub Actions secrets)
- **Testcontainers** — Postgres container for integration tests (isolated, never touches prod Supabase)
- **Bean Validation** — startup validation for required report/Twitter configuration
- **Micrometer (core only)** — counters/timers registered into a `SimpleMeterRegistry`; on shutdown a `MetricsLogger` dumps every meter as a structured log line (no HTTP scrape, since the batch has no web server). Currently instruments `http.client.retries` per host/reason; new meters can be added with `Metrics.counter(...)` from anywhere.
- **ArchUnit + JaCoCo** — architecture boundaries and minimum coverage gate
- **Gradle 9** (Kotlin DSL)
- **Project Loom** — Virtual Threads for all I/O

Architecture follows [Hexagonal (Ports & Adapters)](docs/adr/0002-hexagonal-architecture.md). Two bounded contexts: `market_intel` (fetching + scoring + persistence) and `trend_analytics` (historical analysis). Cross-module wiring via inverted port (`ReportEnrichmentPort` defined in `market_intel/domain`, implemented by `trend_analytics/infrastructure` — strzałka zależności idzie tylko w jedną stronę).

## Prerequisites

- JDK 26 (Oracle EA or Temurin)
- Twitter API v2 Bearer Token
- OpenAI API key (GPT-4o + GPT-4o-mini for trend narratives)
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

# Optional — pomijalne, adaptery degradują się gracefully gdy nieustawione
PRODUCTHUNT_DEVELOPER_TOKEN=
YOUTUBE_API_KEY=

# Supabase Session Pooler (NIE Direct connection — direct is IPv6-only on free tier)
# Format: jdbc:postgresql://aws-0-<region>.pooler.supabase.com:5432/postgres?sslmode=require
SUPABASE_DB_URL=
SUPABASE_DB_USERNAME=postgres.<project-ref>
SUPABASE_DB_PASSWORD=
```

The same env vars are used in **GitHub Actions secrets** — local and CI run against the **same Supabase database**, guaranteeing "works on my machine == works in prod" parity.

The `reports` table is created automatically on first run via `spring.sql.init.mode: always` + [`schema.sql`](src/main/resources/schema.sql).

## Build commands

```bash
./gradlew clean build      # full build
./gradlew check            # Checkstyle + tests + ArchUnit + JaCoCo coverage gate
./gradlew compileJava      # compile only
./gradlew bootJar          # production JAR → build/libs/
./gradlew test             # unit + integration tests
./run.sh                   # run locally (sources .env automatically)
```

## GitHub Actions

The workflow at [`.github/workflows/digest.yml`](.github/workflows/digest.yml) runs `./gradlew check` on `push`/`pull_request`. The digest job runs on a **Mon/Wed/Fri schedule** at **04:00 UTC** (= 06:00 CEST / 05:00 CET) via `cron: '0 4 * * 1,3,5'`, and can also be triggered manually via `workflow_dispatch`.

The scheduled job is hardened against silent failure: it runs the full `./gradlew check` gate **before** building the JAR (so a broken pre-release SNAPSHOT is caught instead of shipping into the digest), a job-level `concurrency` guard prevents a manual dispatch from overlapping the scheduled run (no double-send / double-spend of OpenAI credits), and an `if: failure()` step emails the recipient via Resend whenever the run fails for **any** reason — including a crash or `EMAIL_FAILED` where the app itself couldn't send its own alert. [`.github/dependabot.yml`](.github/dependabot.yml) keeps the Actions and test/tooling dependencies current (Spring Boot / Spring AI SNAPSHOTs are intentionally excluded).

Required repository **secrets**: `TWITTER_BEARER_TOKEN`, `OPENAI_API_KEY`, `RESEND_API_KEY`, `DIGEST_FROM_EMAIL`, `DIGEST_TO_EMAIL`, `SUPABASE_DB_URL`, `SUPABASE_DB_USERNAME`, `SUPABASE_DB_PASSWORD`. Optional secrets: `PRODUCTHUNT_DEVELOPER_TOKEN`, `YOUTUBE_API_KEY`. Repository **variable** (non-sensitive, read via `vars.*`): `FEEDBACK_RECEIVER_URL` — the public feedback-receiver endpoint; it lives in *Variables* rather than *Secrets* because, on a public repo, secrets are masked in Action logs but variables are not, and a public URL has nothing to hide. Blank/unset → no 👍/👎 links. (All injected by the workflow; adapters/email links no-op gracefully when absent.)

**Managing the secrets** — two helper scripts (require the [GitHub CLI](https://cli.github.com), authenticated via `gh auth login`). Both derive the required set **straight from `.github/workflows/*.yml`** (every `secrets.*` / `vars.*` reference, minus the auto-injected `GITHUB_TOKEN`), so the list never drifts from the workflow:

- [`scripts/gh-secrets-check.sh`](scripts/gh-secrets-check.sh) — read-only audit. Lists every secret/variable the workflows need and shows which are set (`✓`) or missing (`✗`) on the repo, and **flags a key placed in the wrong bucket** (e.g. set as a secret while the workflow reads it as a `vars.*` variable — a silent runtime miss). Exits non-zero when anything is missing (CI-friendly). `-R owner/repo` targets a specific repo.
- [`scripts/gh-secrets-sync.sh`](scripts/gh-secrets-sync.sh) — pushes the matching keys from your local `.env` (`gh secret set` / `gh variable set`, values piped over stdin so they never hit `ps`/history; non-referenced `.env` keys are ignored). Prints a masked plan and asks to confirm before applying; flags: `--dry-run`, `-y/--yes`, `-f <file>` (default `.env`), `-R owner/repo`.

> **Auth:** both scripts read `GH_TOKEN` from your `.env` (if set) and use it for every `gh` call, so they don't depend on your shell's gh auth — and a token in `.env` overrides a too-narrow `GH_TOKEN`/`GITHUB_TOKEN` you may have exported globally (e.g. in `~/.zshrc`). Give that token the repo **Secrets** (and **Variables**) permission: *Read* for `check`, *Read and write* for `sync`. Leave `GH_TOKEN=` blank in `.env` to fall back to your `gh auth login` (keyring) token. `GH_TOKEN` is the scripts' own auth — `sync` never pushes it as a repo secret. If gh still returns `HTTP 403`, the scripts surface it with a targeted hint instead of silently reporting everything as "missing".

## Configuration

All tuneable parameters live in [`src/main/resources/application.yaml`](src/main/resources/application.yaml) under the `report:` prefix.

**Env override convention:** any value written as `"${VAR:default}"` in `application.yaml` reads from the
environment first and falls back to `default` (or empty). All other keys below are baked into the YAML and
require a code change + redeploy to override. The keys currently accepting `${ENV}` are: `OPENAI_API_KEY`,
`TWITTER_BEARER_TOKEN`, `RESEND_API_KEY`, `DIGEST_FROM_EMAIL`, `DIGEST_TO_EMAIL`,
`PRODUCTHUNT_DEVELOPER_TOKEN`, `YOUTUBE_API_KEY`, `FEEDBACK_RECEIVER_URL`, plus the three Supabase
datasource keys (`SUPABASE_DB_URL`, `SUPABASE_DB_USERNAME`, `SUPABASE_DB_PASSWORD`). Tuning thresholds
(min-score, lookback windows, limits) are **not** env-overridable by default — they are project-policy
defaults, not per-environment knobs.

| Key                            | Default                  | Description                                        |
|--------------------------------|--------------------------|----------------------------------------------------|
| `twitter.max-calls-per-run`    | `10`                     | Hard ceiling on X API search calls per run (config-drift budget guard) |
| `twitter.min-faves`            | `3`                      | Server-side `min_faves:N` floor appended to queries (enabled; `0` = off). Tier-dependent operator — if your X tier rejects it the source fails gracefully |
| `research.days-back`           | `4`                      | Tweet age window (days) — 96h covers the Fri→Mon gap |
| `research.min-likes`           | `3`                      | Minimum likes for tweets                           |
| `dedup.enabled`                | `true`                   | Drop items already published in recent editions    |
| `dedup.lookback-days`          | `10`                     | Days of prior editions to dedup against (≥ widest window) |
| `interest-profile.persona`     | (CV-derived)             | Reader profile, injected into the synthesis + tech-demand prompts (single source of truth) |
| `interest-profile.relevance-keywords` | ~95 terms        | Keyword set driving the client-side tweet relevance filter |
| `feedback.enabled`             | `true`                   | Act on reader feedback: suppress 👎 URLs **and** nudge per-source weights from net votes |
| `feedback.lookback-days`       | `30`                     | Window for both down-vote suppression and per-source net-vote nudging |
| `feedback.receiver-url`        | `${FEEDBACK_RECEIVER_URL:}` | External endpoint the email 👍/👎 links point at; blank = no links rendered |
| `hacker-news.keywords`         | `[ai, llm, java, ...]`   | HN search keywords (parallel single-term queries)  |
| `hacker-news.min-score`        | `40`                     | Minimum HN points                                  |
| `hacker-news.limit`            | `15`                     | Max HN items                                       |
| `hacker-news.lookback-hours`   | `80`                     | HN post age window (was hardcoded 24h)             |
| `github.query`                 | `(topic:ai OR topic:machine-learning OR topic:llm)` | GitHub search query                              |
| `github.limit`                 | `8`                      | Max GitHub repos                                   |
| `github.lookback-days`         | `4`                      | GitHub `pushed:>=` window (was hardcoded 1 day)    |
| `rss.limit`                    | `10`                     | Max items per RSS feed                             |
| `rss.lookback-hours`           | `80`                     | RSS item age window (was hardcoded 24h)            |
| `reddit.min-score`             | `30`                     | Minimum Reddit upvotes                             |
| `reddit.limit`                 | `15`                     | Max posts per subreddit                            |
| `social.limit`                 | `15`                     | Max social posts (Bluesky + Mastodon, after min-likes) |
| `social.min-likes`             | `5`                      | Min likes/favs for a social post                   |
| `social.bluesky.handles`       | (list)                   | Bluesky handles to pull author-feeds from          |
| `social.mastodon.instance-url` | `https://fosstodon.org`  | Mastodon instance for hashtag timelines            |
| `social.mastodon.hashtags`     | `[java, quarkus, ...]`   | Mastodon hashtags to follow                        |
| `arxiv.max-results`            | `20`                     | Max arXiv papers                                   |
| `arxiv.lookback-hours`         | `80`                     | arXiv paper age window                             |
| `github-releases.lookback-hours` | `80`                   | GitHub Releases age window                         |
| `hugging-face.limit`           | `30`                     | Max HF trending models per fetch                   |
| `hugging-face.min-likes`       | `3`                      | Min HF likes (OR `min-downloads` to qualify)       |
| `hugging-face.min-downloads`   | `50`                     | Min HF downloads (OR `min-likes` to qualify)       |
| `product-hunt.min-votes`       | `100`                    | Min Product Hunt upvotes                           |
| `product-hunt.lookback-hours`  | `80`                     | Product Hunt launch age window                     |
| `security-advisories.lookback-hours` | `72`               | Security Advisory age window                       |
| `security-advisories.limit`    | `20`                     | Max advisories fetched per run                     |
| `trend.enabled`                | `true`                   | Toggle trend section in email                      |
| `trend.lookback-days`          | `7`                      | History window for trend detection                 |
| `trend.min-occurrences`        | `2`                      | Minimum category occurrences to qualify as a trend |
| `trend.max-clusters`           | `5`                      | Max trend clusters shown in email                  |
| `tech-demand.enabled`          | `true`                   | Toggle the 📈 tech-demand pulse                    |
| `tech-demand.lookback-days`    | `7`                      | Render only when HN Who-is-hiring thread is this fresh |
| `tech-demand.max-comments`     | `1000`                   | Cap on hiring posts (comments) analyzed            |
| `tech-demand.max-technologies` | `8`                      | Top-N technologies shown in the ranking            |
| `tech-demand.min-mentions`     | `3`                      | Ignore technologies below this many mentions       |
| `tech-demand.technologies`     | stack + market list      | Vocabulary counted in hiring posts (configurable)  |
| `tech-demand.priority-technologies` | JVM + Python core   | Reader's stack, shown on the "Twój stack" line     |
| `open-jdk.lookback-days`       | `10`                     | OpenJDK JEP age window                            |
| `cncf-landscape.lookback-days` | `7`                      | CNCF landscape change window                      |
| `technology-radar.lookback-months` | `6`                   | Tech Radar edition age window                     |
| `conference-talks.lookback-days`   | `10`                 | YouTube talks age window                          |
| `conference-talks.max-results` | `10`                     | Max items per channel                             |
| `lab-announcements.lookback-hours` | `80`                 | Window for new posts on AI-lab blogs (Anthropic, OpenAI, Google) |
| `lab-announcements.post-fetch-limit` | `15`               | Max post pages fetched per JSONLD source (listings have no dates) |
| `lab-announcements.sources`    | 5 sources                | Per-source `name` + `strategy` (SANITY/JSONLD/OPENAI_DEV) + `listing-url` |

## Architecture decision records

- [ADR-0002](docs/adr/0002-hexagonal-architecture.md) — Hexagonal Architecture
- [ADR-0006](docs/adr/0006-virtual-threads-over-reactive.md) — Virtual Threads over WebFlux
- [ADR-0007](docs/adr/0007-result-type-over-exceptions.md) — `Result<T,E>` over exceptions

## License

[MIT](LICENSE)
