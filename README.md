# 🚀 PulseDigest
**Architected & Developed by [Dominik](https://www.linkedin.com/in/dominik-sienkiewicz/)**

Headless batch application that collects tech news from 16 sources twice a week (Mon/Thu), scores items with GPT-4o, **detects cross-source signals** (the same topic surfacing in Science + Code + Business = 🔴 Critical Trend), tracks per-source health, and delivers a tier'd, prioritized digest to your inbox — with a must-know hero block, deals & tools, critical trends, top picks, and signals.

![Java 26](https://img.shields.io/badge/Java-26-red?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot 4.1](https://img.shields.io/badge/Spring_Boot-4.1.0--SNAPSHOT-green?style=for-the-badge&logo=springboot&logoColor=white)
![Spring AI 2.0](https://img.shields.io/badge/Spring_AI-2.0.0--SNAPSHOT-blue?style=for-the-badge)
![Supabase](https://img.shields.io/badge/Supabase-Postgres-3ECF8E?style=for-the-badge&logo=supabase&logoColor=white)
![Architecture](https://img.shields.io/badge/Architecture-Hexagonal-orange?style=for-the-badge)
![Quality Gate](https://img.shields.io/sonar/quality_gate/DominikSienkiewicz_PulseDigest?server=https%3A%2F%2Fsonarcloud.io&style=for-the-badge&logo=sonarcloud&label=Quality%20Gate)
![Coverage](https://img.shields.io/sonar/coverage/DominikSienkiewicz_PulseDigest?server=https%3A%2F%2Fsonarcloud.io&style=for-the-badge&logo=sonarcloud)

## 🧠 The Vision: Signal over Noise
In the era of AI-driven information overload, this tool is my personal solution to maintain situational awareness without manual scrolling. Every collected item gets a 1–10 score and a category; only what clears the threshold reaches the digest, and a topic surfacing in unrelated sources within the same run is flagged as a trend. It's not just a scraper — most of what it collects never reaches the inbox.


## How it works
```
Twitter/X             ──┐
Hacker News             ┤
GitHub                  ┤
RSS feeds (30)          ┤
Reddit (8 subs)         ┤
arXiv                   ┤
GitHub Releases         ┤
Hugging Face Hub        ├─► MarketResearchService ─► GPT-4o synth ──► Supabase save ─► Resend email
Product Hunt            ┤   (parallel fetch,         (score 1-10,
GitHub Advisories       ┤    URL canonicalization,    category, type,
OpenJDK JEP             ┤    2-10 day window)         editorial, PL)
CNCF Landscape          ┤
Tech Radar              ┤
YouTube Conferences     ┤
AI-lab announcements    ┤
Social (Bsky/Mastodon)  ┘
```

Each stage, the full source list, the two-dimensional classification and the reader model
are described in **[docs/how-it-works.md](docs/how-it-works.md)**.

## Documentation

- **What the pipeline does** → **[docs/how-it-works.md](docs/how-it-works.md)** — stages,
  sources, scoring and categorization, and the reader model that adapts over time.
- **What lands in the inbox** → **[docs/digest-email.md](docs/digest-email.md)** — the anatomy
  of the digest and the 👍/👎 feedback loop that feeds back into scoring.
- **Tuning it** → **[docs/configuration.md](docs/configuration.md)** — every key under the
  `report:` prefix, and which ones accept an environment override.
- **Running and shipping it** → **[docs/running-locally.md](docs/running-locally.md)** — tech
  stack, local setup, Flyway migrations and the GitHub Actions workflow.
- **Design decisions** → [ADR-0002](docs/adr/0002-hexagonal-architecture.md) (hexagonal
  architecture) · [ADR-0006](docs/adr/0006-virtual-threads-over-reactive.md) (virtual threads
  over WebFlux) · [ADR-0007](docs/adr/0007-result-type-over-exceptions.md) (`Result<T,E>` over
  exceptions) · [all ADRs](docs/adr/).

## Prerequisites

- JDK 26 (Oracle EA or Temurin)
- Twitter API v2 Bearer Token
- OpenAI API key (GPT-4o + GPT-4o-mini for the tech-demand narrative)
- Resend account + API key
- Supabase project (free tier — Postgres for report history)
- Docker Desktop (for Testcontainers when running `./gradlew test`)
- *(Optional)* Product Hunt developer token, YouTube Data API key — without them the respective adapters degrade gracefully (return empty list, log warn, pipeline keeps running)


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


## License

[MIT](LICENSE)
