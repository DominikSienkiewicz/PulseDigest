# How it works

The pipeline runs three times a week (Mon/Wed/Fri), fetches from 16 sources in
parallel, scores what it collects, and delivers a prioritized digest. Most of what
it collects never reaches the inbox — that is the point.

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

## Stages

1. **Fetch** — 16 sources run in parallel via Virtual Threads (`CompletableFuture`) with per-source deadlines, shared HTTP connect/read timeouts, and automatic retry for 429/5xx responses (honoring the server's `Retry-After` header, with jitter so parallel fetches don't retry in lock-step). A 429 whose body says the **budget is depleted** (`insufficient_quota`, `CreditsDepleted`, `Payment Required`) is not retried at all — that is a billing state, and no backoff can fix it; only plain throttling earns a retry. Each source filters to its **configured lookback window** (≈80h for fresh news, up to 10 days for slow sources), sized so nothing slips between the Mon/Wed/Fri runs; it records a source-health entry. Every lookback is config-driven (no hidden hardcoded 24h windows).

2. **Canonicalize URLs** — strip tracking params (`utm_*`, `fbclid`, `gclid`, etc.) right after fetch, before the LLM sees anything. Prevents duplicate items from the same article via different campaigns and avoids leaking our UTMs to advertisers when readers click.

3. **Score** — `ReportPromptBuilder` first suppresses items by URL — **cross-edition duplicates** (already published in an edition from the last `dedup.lookback-days` days, read from the `reports` table) and **reader down-votes** (🟥 "less like this" feedback from the last `feedback.lookback-days` days, read from the `feedback` table) — so the wider lookback windows don't re-surface the same item and the reader can mute things; both cover every source incl. tweets. It then selects up to 100 items using per-source caps and a **weighted pre-score** (`round(sourceWeight×100) + min(50, engagement/1000)`) to resolve overflow: a low-engagement GitHub Releases item (pre-score=95) survives over a viral tweet (max pre-score=90). Source weights are tuned to the reader's profile — usable tools/launches (Product Hunt, Hugging Face) and stack releases rank above research papers (arXiv demoted). GPT-4o then deduplicates, scores each surviving item 1–10 for an AI-native architect profile (returning **only score ≥ 6** — quality over quantity), assigns a **category** (topic) and **type** (signal kind, incl. `PROMOTION` for deals), and writes a 1–2 sentence Polish summary plus a one-sentence **`why_it_matters`** action line.

4. **Synthesize** — GPT-4o produces an editorial lead (meta-thesis of the day) + top-3 insights + email preheader text. Token `usage` is logged per call for cost visibility. The synthesis call is the single point of failure in the run (it happens after ~19 fetches, some of them billed), so it is guarded three ways: an **app-level retry** (2 attempts, linear backoff) on top of the HTTP-layer 429/5xx retry; a **reduced-intake retry** when the model truncates at the token cap (`finish_reason=length`) — the same request is re-sent with half the items rather than failing the run; and finally a **fallback to `gpt-4o-mini`**, whose editorial lead is prefixed with `⚠️ Digest awaryjny` so a shallower edition is never delivered silently. **Quota exhaustion is never retried or fallen back on** (`QuotaSignals`) — a second call on a depleted account fails identically, so the run aborts straight into the failure-alert email. The system prompt instructs the model to treat all scraped item text as untrusted data, never as instructions (prompt-injection defense).

   **Re-join** — the model's output is not trusted as a data source, only as a ranking. Alongside the prompt, `ReportPromptBuilder` builds a `canonicalUrl → {source, engagement}` map of exactly the items it sent; after parsing, each returned item is matched back to that map (after URL canonicalization) and its `source` / `engagement_score` are **overwritten with the input values** — a mangled source label would otherwise drop the item into the `DEFAULT` credibility weight and orphan its 👍/👎 votes. An item whose URL was **never in the prompt** is dropped with a WARN: that closes the exfiltration path a successful prompt injection would otherwise have (until now the defense was textual only). The model no longer echoes `source` / `engagement_score` at all, which also trims ~15 output tokens per item. Counters `llm.output.rejected` / `llm.output.rejoined` land in the shutdown metrics dump.

5. **Signal scoring** — `SignalScoringService` groups items by the LLM-assigned **`topic_key`** — the slug of the underlying story (`model-context-protocol`), not the umbrella category — resolves each source to a domain type (`SCIENCE` / `CODE` / `BUSINESS` / `SOCIAL` / `SECURITY` / `LABS`), and computes a deterministic score: `round(sourceWeight × 100) + min(50, engagement / 1000)`, where `sourceWeight` is the base credibility weight **nudged by accumulated reader feedback** (see [the feedback loop](digest-email.md)). A **story** confirmed across **3+ distinct source domains** receives a +50 cross-source bonus and is promoted to 🔴 **CRITICAL**. Grouping by category used to make three unrelated "AI/LLM" items critical, which measured category diversity rather than confirmation; an item that omits `topic_key` degrades to the old category grouping rather than switching Critical Trends off. Every item is wrapped in a `Signal` with rank `CRITICAL → STRONG → MODERATE → WEAK`.

6. **Remember** — `ReportHistoryPort` reads back the scored signals of past editions from the `reports` table (`history.lookback-days`, default 21 ≈ 9 editions). Each signal gains a `TrendRecurrence`: how many consecutive editions have carried the story, and the date the reader first saw it. On Fridays `WeeklyRecapService` diffs this edition's ranks against each story's earliest rank of the week to produce the 📊 **week in signals** block — what escalated, what held, and what faded. History is read **before** the edition is persisted, so "3rd edition in a row" never counts the edition being assembled; an unreachable database degrades to an amnesiac digest rather than a lost run. The same history feeds a per-source **yield ledger** (`SourceYieldService`), logged each run: how often each source's items were published and how often they earned CRITICAL/STRONG. It reports only — reallocating prompt caps from these numbers is a self-reinforcing loop and needs a slot floor first.

   **Predictive radar** — `TrendVelocityService` compares each story against its own state 3 editions ago. A story that gained a source domain, is climbing in score, and already stands on **two** domains is one independent confirmation short of 🔴, so it is flagged 🟠 **Critical candidate** on its row. A single-domain score spike is explicitly *not* a candidate — that is engagement, not corroboration. Predictions are persisted with the signal, so later editions grade them: the footer prints the radar's own hit rate (`radar: 7/10 kandydatów osiągnęło CRITICAL (70%)`), and it stays absent until at least one prediction's verdict window has closed. Deterministic, zero LLM cost, and no new `SignalRank` — candidacy is orthogonal to rank (a STRONG signal can be a candidate) and folding it into the enum would corrupt the sort rank exists to drive.

7. **Persist** — full `PersistedReport` saved to Supabase (`reports` table, JSONB payload) — including the **per-source fetch reports**, the denominator the yield ledger needs. Job status moves through `GENERATED → PERSISTED`.

8. **Deliver** — HTML email via Resend. The layout, the subject-line derivation and the link hardening are described in **[docs/digest-email.md](digest-email.md)**. The process exits successfully only after Resend confirms delivery (`DELIVERED`); email failure becomes `EMAIL_FAILED`.

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
| Product Hunt        | GraphQL `posts(order: VOTES)`, topics: AI, Developer Tools, Productivity, Open Source, Tech | `min-votes: 100`, lookback 80 h. Without a token the adapter returns an empty list instead of crashing |
| GitHub Advisories   | Public `/advisories?sort=published`, severity HIGH+CRITICAL, ecosystems: maven, pip, docker, actions (stack-relevant only) | Last 72 h, max 20. Feeds the `INCIDENT` badge. Off-stack advisories are LLM-scored ≤3 (long tail). Source weight demoted to `0.30` (background topic) |
| OpenJDK JEP         | GitHub Commits API on `openjdk/jdk`, parsing JEP IDs + status changes (Candidate, Proposed to Target, Integrated, Delivered) | Last 10 days. Deduplicated by JEP number |
| CNCF Landscape      | GitHub Commits API on `cncf/landscape`, filtering commits touching `landscape.yml` (sandbox, incubating, graduated, archived) | Last 7 days. Status-change detection |
| Tech Radar          | Thoughtworks Technology Radar (quarterly). Rings: Adopt, Trial, Assess, Hold.                                | Quarterly cadence; pre-LLM intake capped at 2 (background) |
| YouTube Conferences | YouTube Data API v3 `search`, 3 tech channels (SpringDeveloper, Devoxx, CNCF) | API key optional. Last 10 days, sorted by recency; pre-LLM intake capped at 3 (background) |
| AI-lab announcements | Official lab blogs/newsrooms, 5 sources via 3 scrape strategies: **SANITY** — `anthropic.com/news`, `anthropic.com/engineering` (inline Sanity CMS data); **JSONLD** — `claude.com/blog`, `blog.google/.../gemini/` (listing → per-post `datePublished`); **OPENAI_DEV** — `developers.openai.com/blog` (cards inline). | Last 80 h. **Highest-signal source** for AI model/product news — very high engagement_score in the LLM prompt so it never gets trimmed. Stateless (no DB). A failing source is skipped; only a total outage marks the source FAILED |
| Social (Bluesky + Mastodon) | Bluesky AppView author-feeds (public, no auth) for configured handles + Mastodon hashtag-timelines on one instance (public) | Free-source recovery of CV-relevant signal trimmed off the X budget. One merged "Social" source; each network degrades gracefully (FAILED only if both fail). `min-likes` + `limit`; tune `social.bluesky.handles` / `social.mastodon.hashtags` |

## Categorization (two dimensions)

Each digest item is classified along **two orthogonal axes**.

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

Both fields are LLM-assigned. The email renders `type` as a colored badge (semantic
palette: red for INCIDENT, purple for RELEASE, green for RESOURCE, etc.).

## Living reader model

The persona in `interest-profile.persona` is a frozen string — after a year the digest
knows exactly as much about the reader as on day one. The reader model is the part that
compounds, and the part that can drift, so every guard here exists on purpose.

Once the reader has cast `reader-profile.min-votes` clicks (default 10), a weekly
`gpt-4o-mini` call distils those votes into at most five **dated, evidenced hypotheses**
(`ReaderProfile`), stored append-only in the versioned `reader_profile` table. They are
appended to the system prompt as `== MODEL CZYTELNIKA ==`, after the persona, and
**printed in the email footer with the evidence behind each claim** — a model that
silently reshapes what the reader sees is exactly the thing he must be able to audit
and disagree with.

Four defences against drift, all tested:

| Guard | Why |
|---|---|
| `min-votes` (10) | A profile built from three clicks is a hallucination with a database row. |
| `refresh-days` (7) | One cheap call a week, not one per run. |
| `hypothesis-ttl-days` (60) | A claim the reader stopped confirming expires instead of steering forever. |
| Evidence required | The distiller must cite the vote counts behind every claim; unevidenced hypotheses are dropped, not trusted. |

Every failure path — storage down, distiller down, empty response — leaves the previously
stored profile standing and publishes the digest without it. Distillation feeds on the same
per-category votes as the feedback loop, so **until the receiver writes `category`, there is
nothing to distil and the model stays absent.**

## Design decisions

- [ADR-0002](adr/0002-hexagonal-architecture.md) — Hexagonal Architecture
- [ADR-0006](adr/0006-virtual-threads-over-reactive.md) — Virtual Threads over WebFlux
- [ADR-0007](adr/0007-result-type-over-exceptions.md) — `Result<T,E>` over exceptions
- [all ADRs](adr/)
