# 🚀 PulseDigest
**Architected & Developed by [Dominik](https://www.linkedin.com/in/dominik-sienkiewicz/)** *Principal AI Engineer | Full Stack Architect*

Headless batch application that collects tech news from 7 sources every morning, scores items with GPT-4o, and delivers a tier'd, prioritized digest to your inbox — with editorial lead, top picks, signals, and long-tail sections.

![Java 26](https://img.shields.io/badge/Java-26-red?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot 4.1](https://img.shields.io/badge/Spring_Boot-4.1.0--SNAPSHOT-green?style=for-the-badge&logo=springboot&logoColor=white)
![Spring AI 2.0](https://img.shields.io/badge/Spring_AI-2.0.0--SNAPSHOT-blue?style=for-the-badge)
![Architecture](https://img.shields.io/badge/Architecture-Hexagonal-orange?style=for-the-badge)

## 🧠 The Vision: Signal over Noise
In the era of AI-driven information overload, this tool is my personal solution to maintain high-level situational awareness without manual scrolling. It applies **Principal-level scoring logic** to filter out noise and focus only on high-impact architectural and AI shifts. It's not just a scraper; it's a cognitive filter designed for elite engineers.

## How it works

```
Twitter/X       ──┐
Hacker News       ┤
GitHub            ┤
RSS feeds (13)    ├──► MarketResearchService ──► GPT-4o ──► Resend email
Reddit (8 subs)   ┤     (parallel fetch,          (score 1-10,
arXiv             ┤      last 24 h)                category, type,
GitHub Releases ──┘                                editorial, PL summary)
```

1. **Fetch** — 7 sources run in parallel via Virtual Threads (`CompletableFuture`). Each source filters to the last 24h.
2. **Score** — GPT-4o deduplicates, scores each item 1–10 by relevance for a Senior/Principal Engineer + Architect profile, assigns a **category** (topic) and a **type** (signal kind), preserves engagement metrics, and writes a 1–2 sentence Polish summary with the key number front-loaded.
3. **Synthesize** — GPT-4o produces an editorial lead (meta-thesis of the day) + top-3 insights + email preheader text.
4. **Deliver** — HTML email via Resend with tier'd layout: ⭐ Top picks (score ≥ 8) · 🔌 Signals (5–7) · Long tail (< 5). All three tiers render the same full table — title, summary, category, type badge, source, engagement, score — differentiated only by header style and row background. Footer shows "selected N of M items · K sources · 24h window".

## Sources

| Source          | Filter                                                                              | Notes                                       |
|-----------------|-------------------------------------------------------------------------------------|---------------------------------------------|
| Twitter/X       | ~84 curated accounts + 5 topic queries, last 24 h                                   | Authority accounts bypass keyword filter; others require relevance match |
| Hacker News     | Algolia `numericFilters=created_at_i>`, keyword query                               | `min-score: 25`, max 15 items               |
| GitHub          | `pushed:>=yesterday`                                                                | Stars desc, configurable query, max 5 repos |
| RSS             | 13 feeds (InfoQ, Spring Blog, Baeldung, DZone ×2, Niebezpiecznik, Sekurak, JVM Bloggers, devstyle.pl, TLDR Tech, Pragmatic Engineer, Quastor) | `pubDate` / `updated` filter, max 10 per feed |
| Reddit          | `t=day` (top 24 h)                                                                  | 8 subreddits, `min-score: 20`, max 15 per sub |
| arXiv           | Categories `cs.AI, cs.LG, cs.CR, cs.DC, cs.PL` + keyword filter                     | Last 48 h, max 20 papers                    |
| GitHub Releases | 17 monitored repos (Spring Boot, Spring AI, Quarkus, GraalVM, vLLM, llama.cpp, K8s, OTel…) | Last 72 h, latest release per repo only |

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
- **🔑 Top insights** — top-3 trends extracted from the day.
- **⭐ Top picks** — score ≥ 8, white background.
- **🔌 Signals** — score 5–7, muted `#fafafa` background.
- **Long tail** — score < 5, lightest `#f9fafb` background.

All three tiers render an identical full table: **article link + 1–2 sentence Polish summary · category badge · type badge · source + engagement (❤/pkt/★/↑) · color-coded score**. The background shade is the only visual distinction — every tier gives enough context to decide whether to click.
- **Footer** — transparency block: "selected N of M items · K sources · 24h window".

## Tech stack

- **Java 26** with `--enable-preview` (pattern matching, sealed interfaces)
- **Spring Boot 4.1.0-SNAPSHOT** — `web-application-type: none` (headless)
- **Spring AI 2.0.0-SNAPSHOT** — GPT-4o via OpenAI
- **Gradle 9** (Kotlin DSL)
- **Project Loom** — Virtual Threads for all I/O

Architecture follows [Hexagonal (Ports & Adapters)](docs/adr/0002-hexagonal-architecture.md). No database, no messaging, no web layer.

## Prerequisites

- JDK 26 (Oracle EA or Temurin)
- Twitter API v2 Bearer Token
- OpenAI API key (GPT-4o)
- Resend account + API key

## Local setup

```bash
cp .env.example .env
# fill in the 5 keys, then:
./run.sh
```

`.env` keys:

```
TWITTER_BEARER_TOKEN=
OPENAI_API_KEY=
RESEND_API_KEY=
DIGEST_FROM_EMAIL=
DIGEST_TO_EMAIL=
```

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

Required repository secrets: `TWITTER_BEARER_TOKEN`, `OPENAI_API_KEY`, `RESEND_API_KEY`, `DIGEST_FROM_EMAIL`, `DIGEST_TO_EMAIL`.

## Configuration

All tuneable parameters live in [`src/main/resources/application.yaml`](src/main/resources/application.yaml) under the `report:` prefix:

| Key                            | Default                  | Description                                        |
|--------------------------------|--------------------------|----------------------------------------------------|
| `research.days-back`           | `1`                      | Tweet age window (days)                            |
| `research.min-likes`           | `3`                      | Minimum likes for tweets                           |
| `hacker-news.min-score`        | `25`                     | Minimum HN points                                  |
| `hacker-news.limit`            | `15`                     | Max HN items                                       |
| `github.query`                 | `topic:ai language:java` | GitHub search query                                |
| `github.limit`                 | `5`                      | Max GitHub repos                                   |
| `rss.limit`                    | `10`                     | Max items per RSS feed                             |
| `reddit.min-score`             | `20`                     | Minimum Reddit upvotes                             |
| `reddit.limit`                 | `15`                     | Max posts per subreddit                            |
| `arxiv.max-results`            | `20`                     | Max arXiv papers                                   |
| `arxiv.lookback-hours`         | `48`                     | arXiv paper age window                             |
| `github-releases.lookback-hours` | `72`                   | GitHub Releases age window                         |

## Architecture decision records

- [ADR-0002](docs/adr/0002-hexagonal-architecture.md) — Hexagonal Architecture
- [ADR-0006](docs/adr/0006-virtual-threads-over-reactive.md) — Virtual Threads over WebFlux
- [ADR-0007](docs/adr/0007-result-type-over-exceptions.md) — `Result<T,E>` over exceptions

## License

[MIT](LICENSE)
