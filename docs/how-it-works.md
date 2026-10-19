# How it works

The pipeline runs twice a week (Mon/Thu), fetches from 16 sources in
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

1. **Fetch** — 16 sources run in parallel via Virtual Threads (`CompletableFuture`) with per-source deadlines, shared HTTP connect/read timeouts, and automatic retry for 429/5xx responses (honoring the server's `Retry-After` header, with jitter so parallel fetches don't retry in lock-step). A 429 whose body says the **budget is depleted** (`insufficient_quota`, `CreditsDepleted`, `Payment Required`) is not retried at all — that is a billing state, and no backoff can fix it; only plain throttling earns a retry. Each source filters to its **configured lookback window** (≈104h for fresh news, up to 10 days for slow sources), sized to the longest gap between runs (Thu→Mon, 96h) plus margin, so every run covers the whole range since the previous report and nothing slips between them; the resulting overlap is removed by cross-edition dedup, not by narrowing the window. It records a source-health entry. Every lookback is config-driven (no hidden hardcoded 24h windows).

2. **Canonicalize URLs** — strip tracking params (`utm_*`, `fbclid`, `gclid`, etc.) right after fetch, before the LLM sees anything. Prevents duplicate items from the same article via different campaigns and avoids leaking our UTMs to advertisers when readers click.

3. **Score** — `ReportPromptBuilder` first suppresses items by URL — **cross-edition duplicates** (already published in an edition from the last `dedup.lookback-days` days, read from the `reports` table) and **reader down-votes** (🟥 "less like this" feedback from the last `feedback.lookback-days` days, read from the `feedback` table) — so the wider lookback windows don't re-surface the same item and the reader can mute things; both cover every source incl. tweets. It then selects up to 100 items using per-source caps and a **weighted pre-score** (`round(sourceWeight×100) + min(50, engagement/1000)`) to resolve overflow: a low-engagement GitHub Releases item (pre-score=95) survives over a viral tweet (max pre-score=90). Source weights are tuned to the reader's profile — usable tools/launches (Product Hunt, Hugging Face) and stack releases rank above research papers (arXiv demoted). GPT-4o then deduplicates, scores each surviving item 1–10 for an AI-native architect profile (returning **only score ≥ 6** — quality over quantity), assigns a **category** (topic) and **type** (signal kind, incl. `PROMOTION` for deals), and writes a 1–2 sentence Polish summary plus a one-sentence **`why_it_matters`** action line.

4. **Breadth floor, before a single token** (F10) — a run that lost most of its sources is stopped
   here rather than delivered. `report.research.min-active-sources` (default 3) is compared against
   the sources that returned something, counted from the fetch ledger; below it the run ends in
   ERROR with the failure alert, and the synthesis call is never made. Sources switched off by
   configuration are excluded from both sides of that comparison, so disabling a source (K7's X) can
   never starve a run, and the floor is capped at the number actually attempted. The older abort —
   *every* source returned nothing — still runs first and is unchanged. Until 2026-10-19 that was the
   only abort there was, so eighteen of nineteen sources down still shipped two items under a 🔴
   marker those two had earned: a stub in a full edition's clothes.

   A **second floor** follows it, because the ledger counts what each source *fetched*, not what
   survived (N60): `report.research.min-contributing-sections` (default 3) is checked against the
   sections that still carry an item after the relevance, like-count and recency filters. A real
   edition fills sixteen of sixteen, so three means thirteen sections came back empty while their
   sources answered — a broken filter or lookback window. It is capped at the number of live sources,
   and that bound is exact: several sources feed one section (three X searches share "tweets", every
   RSS feed shares "RSS"), so sections with content can never outnumber sources that answered.

   **Neither floor is the quiet-week case.** That is [`report.thin-week`](configuration.md) (N03),
   decided after synthesis on stories above the Must-know bar, and its answer is a three-line mail
   rather than a failure alert. These floors measure a different quantity, earlier: whether any
   content arrived at all.

5. **Synthesize** — GPT-4o produces an editorial lead (meta-thesis of the day) + top-3 insights + email preheader text. Token `usage` is logged per call for cost visibility. The synthesis call is the single point of failure in the run (it happens after ~19 fetches, some of them billed), so it is guarded three ways: an **app-level retry** (2 attempts, linear backoff) on top of the HTTP-layer 429/5xx retry; a **reduced-intake retry** when the model truncates at the token cap (`finish_reason=length`) — the same request is re-sent with half the items rather than failing the run; and finally a **fallback to `gpt-4o-mini`**, whose editorial lead is prefixed with `⚠️ Digest awaryjny` so a shallower edition is never delivered silently. **Quota exhaustion is never retried or fallen back on** (`QuotaSignals`) — a second call on a depleted account fails identically, so the run aborts straight into the failure-alert email. The system prompt instructs the model to treat all scraped item text as untrusted data, never as instructions (prompt-injection defense).

   **Re-join** — the model's output is not trusted as a data source, only as a ranking. Alongside the prompt, `ReportPromptBuilder` builds a `canonicalUrl → {source, engagement}` map of exactly the items it sent; after parsing, each returned item is matched back to that map (after URL canonicalization) and its `source` / `engagement_score` are **overwritten with the input values** — a mangled source label would otherwise drop the item into the `DEFAULT` credibility weight and orphan its 👍/👎 votes. An item whose URL was **never in the prompt** is dropped with a WARN: that closes the exfiltration path a successful prompt injection would otherwise have (until now the defense was textual only). The model no longer echoes `source` / `engagement_score` at all, which also trims ~15 output tokens per item. Counters `llm.output.rejected` / `llm.output.rejoined` land in the shutdown metrics dump.

6. **Signal scoring** — `SignalScoringService` groups items by the LLM-assigned **`topic_key`** — the slug of the underlying story (`model-context-protocol`), not the umbrella category — resolves each source to a domain type (`SCIENCE` / `CODE` / `BUSINESS` / `SOCIAL` / `SECURITY` / `LABS`), and computes a deterministic score: `round(sourceWeight × 100) + min(50, engagement / 1000)`, where `sourceWeight` is the base credibility weight **nudged by accumulated reader feedback** (see [the feedback loop](digest-email.md)). A **story** confirmed across **3+ distinct source domains** is 🔴 **CRITICAL**, and nothing else ever is — the rank is decided by the domain count, not by the score, so three weak sources carrying one story outrank a single popular source however far its engagement carries it. The +50 cross-source bonus survives as an ordering device *within* the rank. Until 2026-10-18 rank was a pure function of score with 🔴 at 101 points, which let a popular blog post earn the badge while a genuinely confirmed story missed it (F2). Grouping by category used to make three unrelated "AI/LLM" items critical, which measured category diversity rather than confirmation; an item that omits `topic_key` degrades to the old category grouping rather than switching Critical Trends off. Every item is wrapped in a `Signal` with rank `CRITICAL → STRONG → MODERATE → WEAK`.

7. **Remember** — `ReportHistoryPort` reads back the scored signals of past editions from the `reports` table (`history.lookback-days`, default 28 ≈ 8 editions). Each signal gains a `TrendRecurrence`: how many consecutive editions have carried the story, and the date the reader first saw it. On Thursdays `WeeklyRecapService` diffs this edition's ranks against each story's earliest rank of the week to produce the 📊 **week in signals** block — what escalated, what held, and what faded. History is read **before** the edition is persisted, so "3rd edition in a row" never counts the edition being assembled; an unreachable database degrades to an amnesiac digest rather than a lost run. The same history feeds two per-source ledgers in `SourceYieldService`, both logged each run. The **yield ledger** counts how often each source's items were published and how often they earned CRITICAL/STRONG. The **intake ledger** counts what each source actually *returned*, read back from the per-edition `fetchReports` the archive was already storing — and it carries a row for every source that was fetched, including the ones that returned nothing. That distinction is the whole point: the yield ledger tallies sources appearing in an edition's signals, so a source answering HTTP 200 with an empty body had no row at all and read exactly like a source nobody had configured (F7). A source that fetches successfully and returns zero items across three editions now produces a **WARN naming it**, because on a green run nobody reads an INFO line. Failures are excluded from that count deliberately — a source erroring out is already surfaced by the degraded-source warning and the footer; the invisible case was the silent success. Both ledgers report only: reallocating prompt caps from these numbers is a self-reinforcing loop and needs a slot floor first.

   Every call to the model is metered by call site and model — prompt and completion tokens, and dollars when a price list is configured (`llm.pricing.*`). The counters go through Micrometer, so the shutdown metrics dump carries them and every run self-reports its own spend without extra plumbing.

   **The edition's input is archived too** (`edition_inputs`, one JSONB document per edition). What is stored is the `ResearchResult` the prompt was built from — the boundary the prompt actually sees — rather than the raw HTTP bodies of nineteen adapters, which would mean intercepting every adapter to capture bytes the pipeline never looked at. It lives in its own table, not a column on `reports`, because these payloads are an order of magnitude larger and `reports` is read in the hot path by the dedup and trend memory on every run.

   **Your own stack** (N07 + N06). One list of Maven coordinates serves both features: it is read from the build files of your own projects where those paths exist, and falls back to coordinates declared in configuration where they do not — which is every CI run, and the run logs which list it used rather than quietly reporting a narrower stack. The **release radar** asks Maven Central for the current release of each coordinate and renders a deterministic section for anything you pin behind it: not "Spring Boot 4.1.2 is out", which every feed carries, but "4.1.2 is out and you pin 4.1.0". Pre-release pins are skipped by construction — Central indexes only releases, so comparing a `-SNAPSHOT` pin against its latest release would report going *backwards* as an upgrade. The **stack matcher** compares the packages each GitHub advisory names against the same list and marks matching items ⚠️ **W TWOIM STOSIE**, on exact `group:artifact` coordinates and never on keywords: `spring-webmvc` is a different artifact from `spring-web`, and a marker that overclaims costs its own credibility everywhere else.


   **The reader's own shelf** (N05). The mail's third action link records a save; the next run reads those rows back, drops anything the reader has since voted on, recovers each headline from the stored edition it was saved out of, and renders the oldest few. The honest limit is written into the section's own wording: nothing here observes a reader opening a link, so the shelf reports what was *saved and not voted on*, never what was unread.


   **The response has a schema** (N22). Both model calls ask for a named JSON schema in strict mode
rather than "some JSON", and the schema is generated from the records the response binds to — a
renamed component changes it in the same commit. That removes most of what the retry ladder, the
truncation exception and the fallback model exist to survive: the provider will not return a
document of the wrong shape.

   **Every edition says what made it** (N26). The system prompt's SHA-256, the model that actually
answered, and whether it was the fallback are stored with the payload. The hash *is* the version: a
hand-maintained number is a thing to forget to bump, and two editions made by different prompts are
distinguishable whether or not anyone remembered to say so.

   **The same story told three ways** (N21). Before scoring, the edition's items are embedded in a
single call and compared; items above the similarity threshold are given one correlation key, and
everything downstream already knows what that means — the layout folds them into one story, the
story lists its distinct sources, and the scorer counts distinct source domains. This is what makes
🔴 reachable: since F2 the badge means corroboration and nothing else, and a release announced on a
blog, a subreddit and Hacker News used to produce three unrelated items because nothing matched
them. Clustering does not add a ranking signal; it reveals the confirmations that were already
there.

   It ships in **shadow mode** for a sharper reason than the critique does. A duplicate costs the
reader a line; a wrong merge invents a corroboration, and therefore a critical trend, out of two
unrelated articles. Similarity is measured against each cluster's seed rather than any member, so a
chain of near-misses cannot become one story, and a cluster larger than the cap is rejected whole.
   **A second opinion, not yet trusted** (N23). After synthesis, one cheap call asks which items a
senior engineer would find useless — the failure a per-item rubric structurally cannot see is an
edition where everything is individually defensible and the whole is boring. It ships in **shadow
mode**: the verdict is logged item by item with its reason and the edition goes out unchanged. A few
editions of that log are what the decision to enforce should be made on, because a second model
opinion can be confidently wrong and an edition stripped by an uncalibrated critic is worse than a
dull one. A critique that would cut more than half the edition is rejected outright — that is
disagreement with the edition, not critique of it.

   **Grading itself** (N11, N20). `--retrospective` builds a quarterly edition from stored editions
alone, with no live fetch, and its central section is the 🔴 badge graded against what happened:
which CRITICAL calls kept being carried by sources afterwards, and which the reader up-voted. Only
calls old enough to have had an outcome are graded. Nothing is re-judged at grading time — a
scoreboard the graded system can influence is a press release.

   **Proposing its own sources** (N28). `--discover-sources` asks the model which feeds or accounts
the already-published high-ranking items keep citing that this digest does not read. Capped at
three, each requiring the items that justify it, and it **emits a report rather than changing
configuration** — ADR-0002's friction stays, it just stops being the only thing proposing sources.
   **Dates, not news** (N08). The end-of-life radar reads a configured table of support deadlines and renders anything already past, plus anything inside `report.eol.warn-within-months`. It is the one class of item no feed can deliver: nothing happened, a date arrived — so nothing triggers an article, and the reader finds out from a CVE instead. Because the table is hand-kept, every rendering carries the date it was last verified and flags itself once that is over 180 days old; a stale table that looks authoritative is worse than no table. The radar runs after scoring and attaches to the report separately, so a deadline never competes with the stories for a slot.

   **Replaying an edition.** `java -jar app.jar --replay=<jobId>` re-scores a past edition with today's rubric and prints an item-by-item diff of what would have changed. It sends nothing, writes nothing and costs nothing, because the rubric — scoring, the cross-source rule, source weights, the preference multiplier — is deterministic and operates on items the edition already published. Replaying the *prompt* has its own flag, `--replay-prompt=<jobId>`, because it spends a real synthesis call every time: the rubric replay is the one you run in a loop while tuning scoring, so making them one flag would have made the common case the expensive one. It re-runs the current prompt over the stored input, prints the estimated prompt tokens and cost **before** the call, and diffs the result against what shipped — naming the source of every item that appeared or disappeared, since "which source dropped out" is the question two bare lists leave unanswered. Unlike the rubric replay it is not deterministic, so a difference is evidence to read rather than an assertion to trust.

   **Predictive radar** — `TrendVelocityService` compares each story against its own state 3 editions ago. A story that gained a source domain, is climbing in score, and already stands on **two** domains is one independent confirmation short of 🔴, so it is flagged 🟠 **Critical candidate** on its row. A single-domain score spike is explicitly *not* a candidate — that is engagement, not corroboration. Predictions are persisted with the signal, so later editions grade them: the footer prints the radar's own hit rate (`radar: 7/10 kandydatów osiągnęło CRITICAL (70%)`), and it stays absent until at least one prediction's verdict window has closed. Deterministic, zero LLM cost, and no new `SignalRank` — candidacy is orthogonal to rank (a STRONG signal can be a candidate) and folding it into the enum would corrupt the sort rank exists to drive.

8. **Persist** — full `PersistedReport` saved to Supabase (`reports` table, JSONB payload) — including the **per-source fetch reports**, the denominator the yield ledger needs. Job status moves through `GENERATED → PERSISTED`.

9. **Deliver** — HTML email via Resend. The layout, the subject-line derivation and the link hardening are described in **[docs/digest-email.md](digest-email.md)**. The process exits successfully only after Resend confirms delivery (`DELIVERED`); email failure becomes `EMAIL_FAILED`.

## Sources

Every number this table used to carry — lookback windows, item caps, score floors, feed and account
counts — has been removed rather than corrected. It stated `80 h`, `72 h`, `last 4 days` and
`top 24 h` while `application.yaml` had moved to `104 h` and five-to-ten-day windows, and a number
duplicated in prose drifts again the moment the config changes (F8/K3). The windows and limits live
in [`application.yaml`](../src/main/resources/application.yaml), with the tunable ones described in
[configuration.md](configuration.md). What stays here is what each source *is*, which does not drift.

| Source              | What it reads                                                                       | Notes                                       |
|---------------------|-------------------------------------------------------------------------------------|---------------------------------------------|
| Twitter/X           | CV-curated accounts in batches, plus topic queries | A per-run call budget hard-caps spend; authority accounts bypass the keyword filter, others require a relevance match. A server-side `min_faves` floor trims low-engagement noise before it consumes read quota — the operator is tier-dependent, and a tier that rejects it fails the source gracefully |
| Hacker News         | Algolia search with a `created_at_i` cutoff and a keyword query | Score floor applied before intake |
| GitHub              | Repository search by recent push | Sorted by stars |
| RSS                 | Feeds across core dev, security, official changelogs, AI newsletters, community, the PL ecosystem, cloud and the JVM | Filtered on `pubDate` / `updated`, capped per feed |
| Reddit              | Top posts of the day across curated subreddits | Score floor applied before intake |
| arXiv               | Selected CS categories plus a keyword filter | |
| GitHub Releases     | A roster of monitored repositories | Latest release per repository only |
| Hugging Face Hub    | Public model API sorted by trending score, filtered to relevant pipelines | Likes-or-downloads floor |
| Product Hunt        | GraphQL posts ordered by votes across AI, developer-tool, productivity, open-source and tech topics | Vote floor. Without a token the adapter returns an empty list instead of crashing |
| GitHub Advisories   | Public advisories, high severity and above, in stack-relevant ecosystems | Feeds the `INCIDENT` badge. Off-stack advisories are LLM-scored low (long tail) and the source weight is demoted to a background topic |
| OpenJDK JEP         | Commits on `openjdk/jdk`, parsing JEP IDs and status changes (Candidate, Proposed to Target, Integrated, Delivered) | Deduplicated by JEP number |
| CNCF Landscape      | Commits on `cncf/landscape` touching `landscape.yml` (sandbox, incubating, graduated, archived) | Status-change detection |
| Tech Radar          | Thoughtworks Technology Radar. Rings: Adopt, Trial, Assess, Hold | Published roughly twice a year, so the same volume is read for months; pre-LLM intake capped as a background source |
| YouTube Conferences | YouTube Data API search across a few conference channels | API key optional; sorted by recency, pre-LLM intake capped as a background source |
| AI-lab announcements | Official lab blogs and newsrooms via three scrape strategies: **SANITY** — `anthropic.com/news`, `anthropic.com/engineering` (inline Sanity CMS data); **JSONLD** — `claude.com/blog`, `blog.google/.../gemini/` (listing → per-post `datePublished`); **OPENAI_DEV** — `developers.openai.com/blog` (cards inline) | **Highest-signal source** for AI model and product news — carries a very high engagement score into the prompt so it is never trimmed. Stateless (no DB). A failing strategy is skipped; only a total outage marks the source FAILED |
| Social (Bluesky + Mastodon) | Bluesky AppView author-feeds (public, no auth) for configured handles, plus Mastodon hashtag timelines on one instance (public) | Free-source recovery of CV-relevant signal trimmed off the X budget. One merged "Social" source; each network degrades gracefully (FAILED only if both fail). Each network has its **own** like floor and no account may win more than `social.max-per-account` slots — see [configuration.md](configuration.md#why-the-social-floors-differ-k6) for the measurement that forced both. Tune `social.bluesky.handles` / `social.mastodon.hashtags` |

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
