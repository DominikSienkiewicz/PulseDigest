# 🚀 PulseDigest
**Architected & Developed by [Dominik](https://www.linkedin.com/in/dominik-sienkiewicz/)** *Principal AI Engineer | Full Stack Architect*

Headless batch application that collects tech news from 17 sources every morning, scores items with GPT-4o, **detects recurring trends across the last 7 days** (Supabase-backed history), and delivers a tier'd, prioritized digest to your inbox — with editorial lead, top picks, signals, weekly trend section, and long-tail sections.

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
Reddit (8 subs)         ┤
arXiv                   ┤
GitHub Releases         ┤                                            ┌─► trend_analytics ─┐
Hugging Face Hub        ┤                                            │   (last 7 days from │
Product Hunt            ├─► MarketResearchService ─► GPT-4o synth ──┤   Supabase + LLM   ├─► Supabase save
GitHub Advisories       ┤   (parallel fetch,         (score 1-10,    │   narratives)      │   ↓
NVD/CVE                 ┤    URL canonicalization,    category, type,│                    │   Resend email
Libraries.io            ┤    last 24 h)               editorial, PL) └────────────────────┘
OpenJDK JEP             ┤
CNCF Landscape          ┤
Tech Radar              ┤
YouTube Conferences     ┤
DB-Engines Ranking      ┘
```

1. **Fetch** — 17 sources run in parallel via Virtual Threads (`CompletableFuture`). Each source filters to the last 24-72h depending on cadence.
2. **Canonicalize URLs** — strip tracking params (`utm_*`, `fbclid`, `gclid`, etc.) right after fetch, before LLM sees anything. Prevents duplicate items from same article via different campaigns and avoids leaking our UTMs to advertisers when readers click.
3. **Score** — GPT-4o deduplicates, scores each item 1–10 by relevance for a Senior/Principal Engineer + Architect profile, assigns a **category** (topic) and a **type** (signal kind), preserves engagement metrics, and writes a 1–2 sentence Polish summary with the key number front-loaded.
4. **Synthesize** — GPT-4o produces an editorial lead (meta-thesis of the day) + top-3 insights + email preheader text.
5. **Trend enrichment** — `trend_analytics` module reads the last 7 days of reports from Supabase (JSONB query), counts recurring categories with frequency analysis, runs **one batched GPT-4o-mini call** to generate 1-sentence narratives ("trzeci dzień z rzędu CVE w popularnych narzędziach"), and adds them to the report. Graceful — if history is empty or LLM fails, mail still ships without the trend section.
6. **Persist** — full enriched `PersistedReport` saved to Supabase (`reports` table, JSONB payload) for tomorrow's trend analysis to read.
7. **Deliver** — HTML email via Resend with tier'd layout: 🔄 Weekly trends · ⭐ Top picks (score ≥ 8) · 🔌 Signals (5–7) · Long tail (< 5). All three item tiers render the same full table — title, summary, category, type badge, source, engagement, score — differentiated only by header style and row background. Footer shows "selected N of M items · K sources · 24h window".

## Sources

| Source              | Filter                                                                              | Notes                                       |
|---------------------|-------------------------------------------------------------------------------------|---------------------------------------------|
| Twitter/X           | ~84 curated accounts + 5 topic queries, last 24 h                                   | Authority accounts bypass keyword filter; others require relevance match |
| Hacker News         | Algolia `numericFilters=created_at_i>`, keyword query                               | `min-score: 25`, max 15 items               |
| GitHub              | `pushed:>=yesterday`                                                                | Stars desc, configurable query, max 5 repos |
| RSS                 | 30 feeds — core dev (InfoQ, Spring Blog, Baeldung, DZone ×2, JVM Bloggers, devstyle.pl, TLDR Tech, Pragmatic Engineer, Quastor), security (Niebezpiecznik, Sekurak), official changelogs (OpenAI, Google Blog, JetBrains), AI newsletters (Import AI, Simon Willison, Latent Space, Sebastian Raschka, Andrej Karpathy), community (Lobsters, dev.to AI/Java), PL ecosystem (JVM Advent), cloud (AWS, GCP, Azure), JVM (Inside.java), cloud-native (CNCF Blog) | `pubDate` / `updated` filter, max 10 per feed |
| Reddit              | `t=day` (top 24 h)                                                                  | 8 subreddits, `min-score: 20`, max 15 per sub |
| arXiv               | Categories `cs.AI, cs.LG, cs.CR, cs.DC, cs.PL` + keyword filter                     | Last 48 h, max 20 papers                    |
| GitHub Releases     | 17 monitored repos (Spring Boot, Spring AI, Quarkus, GraalVM, vLLM, llama.cpp, K8s, OTel…) | Last 72 h, latest release per repo only |
| Hugging Face Hub    | Public `/api/models?sort=trendingScore`; pipeline filter (text-generation, text-to-image, text-to-speech, image-to-text, ASR, feature-extraction, text-to-video) | `min-likes: 10` OR `min-downloads: 1000`, max 30 trending models |
| Product Hunt        | GraphQL `posts(order: VOTES)`, topics: AI, Developer Tools, Productivity, Open Source, Tech | `min-votes: 100`, lookback 36 h. Bez tokenu adapter zwraca pustą listę bez crashu |
| GitHub Advisories   | Public `/advisories?sort=published`, severity HIGH+CRITICAL, ecosystems: maven, npm, pip, actions, go, docker, composer, rubygems | Last 72 h, max 50. Karmi badge `INCIDENT` |
| NVD/CVE             | Public NVD API 2.0, `cvssV3Severity=CRITICAL&cvssV3Severity=HIGH`                                             | Last 48 h, max 20 CVEs. Complements GHSA with non-GitHub ecosystems |
| Libraries.io        | Public API `?sort=rank`, platforms: maven, npm, pypi                                                       | API key optional; graceful degradation. Last 90 days trending |
| OpenJDK JEP         | GitHub Commits API on `openjdk/jdk`, parsing JEP IDs + status changes (Candidate, Proposed to Target, Integrated, Delivered) | Last 7 days. Deduplicated by JEP number |
| CNCF Landscape      | GitHub Commits API on `cncf/landscape`, filtering commits touching `landscape.yml` (sandbox, incubating, graduated, archived) | Last 7 days. Status-change detection |
| Tech Radar          | Thoughtworks Technology Radar (quarterly). Rings: Adopt, Trial, Assess, Hold.                                | Quarterly cadence, all entries included |
| YouTube Conferences | YouTube Data API v3 `search`, 6 tech channels (SpringDeveloper, CNCF, Devoxx, Google Cloud Tech, InfoQ, GOTO Conferences) | API key optional. Last 7 days, sorted by recency |
| DB-Engines          | DB-Engines.com ranking table, detecting score changes ≥ 5 points                                           | Monthly cadence; significant movers only |

## Categorization (two dimensions)

Each digest item is classified along **two orthogonal axes**:

**Category** — *what topic area the item belongs to*
`Java/JVM` · `AI/LLM` · `Cloud/DevOps` · `Security/Privacy` · `Architecture` · `Open Source` · `Research` · `Releases` · `Community` · `Other`

**Type** — *what kind of signal the item carries*

| Type           | Meaning                                                              |
|----------------|----------------------------------------------------------------------|
| `RELEASE`      | New version of existing software (Spring Boot 4.1, K8s 1.30)         |
| `FEATURE`      | New capability added to existing tool/platform                       |
| `LAUNCH`       | Premiere of a brand-new product, tool, or service                    |
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
- **Editorial lead** — 2–3 sentence meta-thesis tying together the day's most important signals (italic, prominent).
- **🔑 Top insights** — top-3 takeaways extracted from the day.
- **🔄 Weekly trends** — recurring categories from the last 7 days with LLM-generated narratives ("Trzeci dzień z rzędu CVE…"). Skipped if history is empty or no category passes the `min-occurrences` threshold.
- **⭐ Top picks** — score ≥ 8, white background.
- **🔌 Signals** — score 5–7, muted `#fafafa` background.
- **Long tail** — score < 5, lightest `#f9fafb` background.

All three item tiers render an identical full table: **article link + 1–2 sentence Polish summary · category badge · type badge · source + engagement (❤/pkt/★/↑) · color-coded score**. The background shade is the only visual distinction — every tier gives enough context to decide whether to click.
- **Footer** — transparency block: "selected N of M items · K sources · 24h window".

## Tech stack

- **Java 26** with `--enable-preview` (pattern matching, sealed interfaces)
- **Spring Boot 4.1.0-SNAPSHOT** — `web-application-type: none` (headless)
- **Spring AI 2.0.0-SNAPSHOT** — GPT-4o via OpenAI
- **Supabase (Postgres) via JDBC** — `JdbcClient` + `JSONB` payloads, schema bootstrapped from `schema.sql` on startup
- **spring-dotenv** — auto-loads `.env` locally (parity with GitHub Actions secrets)
- **Testcontainers** — Postgres container for integration tests (isolated, never touches prod Supabase)
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
- *(Optional)* Product Hunt developer token, Libraries.io API key, YouTube Data API key — without them the respective adapters degrade gracefully (return empty list, log warn, pipeline keeps running)

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
LIBRARIES_IO_API_KEY=
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
./gradlew clean build      # full build (Checkstyle + tests + JaCoCo)
./gradlew compileJava      # compile only
./gradlew bootJar          # production JAR → build/libs/
./gradlew test             # unit + integration tests
./run.sh                   # run locally (sources .env automatically)
```

## GitHub Actions

The workflow at [`.github/workflows/digest.yml`](.github/workflows/digest.yml) runs daily at **04:00 UTC** (06:00 CEST / 05:00 CET) and can also be triggered manually via `workflow_dispatch`.

Required repository secrets: `TWITTER_BEARER_TOKEN`, `OPENAI_API_KEY`, `RESEND_API_KEY`, `DIGEST_FROM_EMAIL`, `DIGEST_TO_EMAIL`, `SUPABASE_DB_URL`, `SUPABASE_DB_USERNAME`, `SUPABASE_DB_PASSWORD`. Optional: `PRODUCTHUNT_DEVELOPER_TOKEN`, `LIBRARIES_IO_API_KEY`, `YOUTUBE_API_KEY` (workflow injects them; adapters no-op gracefully when absent).

## Configuration

All tuneable parameters live in [`src/main/resources/application.yaml`](src/main/resources/application.yaml) under the `report:` prefix:

| Key                            | Default                  | Description                                        |
|--------------------------------|--------------------------|----------------------------------------------------|
| `research.days-back`           | `2`                      | Tweet age window (days)                            |
| `research.min-likes`           | `3`                      | Minimum likes for tweets                           |
| `hacker-news.keywords`         | `[ai, llm, java, ...]`   | HN search keywords (parallel single-term queries)  |
| `hacker-news.min-score`        | `25`                     | Minimum HN points                                  |
| `hacker-news.limit`            | `15`                     | Max HN items                                       |
| `github.query`                 | `(topic:ai OR topic:machine-learning OR topic:llm)` | GitHub search query                              |
| `github.limit`                 | `5`                      | Max GitHub repos                                   |
| `rss.limit`                    | `10`                     | Max items per RSS feed                             |
| `reddit.min-score`             | `20`                     | Minimum Reddit upvotes                             |
| `reddit.limit`                 | `15`                     | Max posts per subreddit                            |
| `arxiv.max-results`            | `20`                     | Max arXiv papers                                   |
| `arxiv.lookback-hours`         | `48`                     | arXiv paper age window                             |
| `github-releases.lookback-hours` | `72`                   | GitHub Releases age window                         |
| `hugging-face.limit`           | `30`                     | Max HF trending models per fetch                   |
| `hugging-face.min-likes`       | `3`                      | Min HF likes (OR `min-downloads` to qualify)       |
| `hugging-face.min-downloads`   | `50`                     | Min HF downloads (OR `min-likes` to qualify)       |
| `product-hunt.min-votes`       | `100`                    | Min Product Hunt upvotes                           |
| `product-hunt.lookback-hours`  | `36`                     | Product Hunt launch age window                     |
| `security-advisories.lookback-hours` | `72`               | Security Advisory age window                       |
| `security-advisories.limit`    | `50`                     | Max advisories fetched per run                     |
| `trend.enabled`                | `true`                   | Toggle trend section in email                      |
| `trend.lookback-days`          | `7`                      | History window for trend detection                 |
| `trend.min-occurrences`        | `2`                      | Minimum category occurrences to qualify as a trend |
| `trend.max-clusters`           | `5`                      | Max trend clusters shown in email                  |
| `nvd.lookback-hours`           | `48`                     | NVD CVE age window                                |
| `nvd.results-per-page`         | `20`                     | Max NVD CVEs per fetch                            |
| `libraries-io.lookback-days`   | `90`                     | Package trend age window                          |
| `libraries-io.limit`           | `20`                     | Max Libraries.io items                            |
| `open-jdk.lookback-days`       | `7`                      | OpenJDK JEP age window                            |
| `cncf-landscape.lookback-days` | `7`                      | CNCF landscape change window                      |
| `technology-radar.lookback-months` | `6`                   | Tech Radar edition age window                     |
| `conference-talks.lookback-days`   | `7`                  | YouTube talks age window                          |
| `conference-talks.max-results` | `10`                     | Max items per channel                             |
| `db-engines.min-score-change`  | `5`                      | Min score change to qualify as significant mover  |

## Architecture decision records

- [ADR-0002](docs/adr/0002-hexagonal-architecture.md) — Hexagonal Architecture
- [ADR-0006](docs/adr/0006-virtual-threads-over-reactive.md) — Virtual Threads over WebFlux
- [ADR-0007](docs/adr/0007-result-type-over-exceptions.md) — `Result<T,E>` over exceptions

## License

[MIT](LICENSE)
