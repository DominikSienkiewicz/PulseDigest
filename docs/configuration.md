# Configuration

All tuneable parameters live in
[`src/main/resources/application.yaml`](../src/main/resources/application.yaml)
under the `report:` prefix.

## Environment override convention

Any value written as `"${VAR:default}"` in `application.yaml` reads from the environment
first and falls back to `default` (or empty). **All other keys below are baked into the
YAML** and require a code change plus a redeploy to override.

The keys currently accepting `${ENV}`:

`OPENAI_API_KEY` (binds to `openai.api-key`; it was `spring.ai.openai.api-key` until
2026-10-18, a namespace left over from a Spring AI starter that was removed — the environment
variable itself never changed) · `TWITTER_BEARER_TOKEN` · `RESEND_API_KEY` · `DIGEST_FROM_EMAIL` ·
`DIGEST_TO_EMAIL` · `PRODUCTHUNT_DEVELOPER_TOKEN` · `YOUTUBE_API_KEY` · `GITHUB_API_TOKEN` ·
`FEEDBACK_RECEIVER_URL` · `FEEDBACK_SIGNING_SECRET` · plus the three Supabase datasource
keys (`SUPABASE_DB_URL`, `SUPABASE_DB_USERNAME`, `SUPABASE_DB_PASSWORD`).

Tuning thresholds — min-score, lookback windows, limits — are **not** env-overridable by
default. They are project-policy defaults, not per-environment knobs.

## Model settings (`llm.*`)

Per call site, because the three calls are deliberately different: synthesis runs a large model warm,
pre-scoring runs a small one at temperature zero, the reader-profile distillation is small and rare.
These were `private static final` constants in three adapters until 2026-10-18 — the only settings in
the project that needed a recompile (F11).

| Key | Default | Description |
|---|---|---|
| `llm.synthesis.model` | `gpt-4o` | Main synthesis model |
| `llm.synthesis.fallback-model` | `gpt-4o-mini` | Retried on failure; blank disables the fallback |
| `llm.pre-scoring.fallback-model` / `llm.reader-profile.fallback-model` | *(empty)* | Retry model for the two small calls; blank means no fallback, and both degrade to skipping rather than retrying |
| `llm.synthesis.temperature` / `.max-tokens` | `0.25` / `10000` | |
| `llm.pre-scoring.model` / `.temperature` / `.max-tokens` | `gpt-4o-mini` / `0.0` / `4000` | gpt-4o-mini triage |
| `llm.reader-profile.model` / `.temperature` / `.max-tokens` | `gpt-4o-mini` / `0.2` / `700` | Weekly profile distillation |
| `llm.pricing.<model>.input-per-million-tokens` | *(absent)* | Dollars per million prompt tokens |
| `llm.pricing.<model>.output-per-million-tokens` | *(absent)* | Dollars per million completion tokens |
| `report.rendered-edition-path` | `build/digest.html` | Where the run writes the rendered edition for the workflow artifact |

**The price list is absent by default, not zero.** Prices change, and a stale number committed here
would be a confident lie: unset, the run meters tokens only and reports cost as `unpriced`; set, the
same meters report dollars. Note that YAML `pricing: {}` does **not** work — an empty map binds as an
empty string and the context fails to start. Omit the key entirely.

### Per-run budget (`llm.budget.*`, N40)

| Key | Default | Meaning |
|-----|---------|---------|
| `llm.budget.enabled` | `true` | Whether the ceiling is enforced |
| `llm.budget.max-tokens` | `1000000` | Prompt + completion tokens one run may spend before further calls are refused; `0` disables |
| `llm.budget.max-usd` | `0` (off) | Dollars one run may spend; `0` disables, and it is **inert without `llm.pricing`** |

Counted in tokens first and dollars second, which is the opposite of how a budget is usually
written and is the point: `llm.pricing` is deliberately absent from the shipped configuration, so a
dollars-only ceiling would be inert in exactly the installation that switched it on. Tokens are
metered unconditionally.

Nothing bounded a run's spend before this except `max-tokens` **per call**, which caps one response
and is blind to a run that makes many — a prompt bug spends its money across many small calls.

What happens at the ceiling differs by call site, because aborting an almost-complete edition wastes
everything already spent: **pre-scoring** and the **reader-profile distillation** are skipped and the
run continues without them (the full payload simply goes to the main model, which is the documented
fallback for a failed pre-scoring call), while **synthesis** has nothing to degrade to and fails the
run, which reaches you as the same failure alert any other broken run does.

The guarantee is "no *next* call past the ceiling", not "never a dollar over": usage is reported by
the provider after a call completes, so a run can overshoot by at most one call.

`1000000` is a round number, not a measured one — it is meant to catch a run that has come off the
rails, not to punish a busy week. Calibrate it from your own runs: every run logs its own total
(`llm-usage` lines, and the Actions step summary), and a warning is logged at 80% of the ceiling.

## Keys

| Key                            | Default                  | Description                                        |
|--------------------------------|--------------------------|----------------------------------------------------|
| `twitter.max-calls-per-run`    | `10`                     | Hard ceiling on X API search calls per run (config-drift budget guard) |
| `report.pre-scoring.enabled`   | `true`                   | gpt-4o-mini triage of prompt candidates before the gpt-4o call |
| `report.pre-scoring.keep`      | `50`                     | How many of the ~100 selected items reach gpt-4o (pre-score ≥ 90 is immune) |
| `report.history.enabled`       | `true`                   | Read past editions back from `reports` (trend memory, weekly recap, yield ledger) |
| `report.history.lookback-days` | `28`                     | How far back report history is read (≈ 8 editions at Mon/Thu) |
| `report.scoring.cross-source-threshold` | `3`           | Distinct source domains required for 🔴 (F2's open experiment: try 2) |
| `report.scoring.cross-source-bonus` | `50`              | Points added when corroborated; orders items *within* the rank |
| `report.scoring.strong-threshold` / `.moderate-threshold` | `100` / `60` | Score floors for STRONG and MODERATE |
| `report.scoring.max-engagement-bonus` / `.engagement-divisor` | `50` / `1000` | Engagement contribution and its cap |
| `report.scoring.mute-multiplier` | `0.5`                | What a muted topic's score is multiplied by (N13). Never applied to the cross-source bonus, so a muted topic three domains confirm still reaches 🔴 |
| `report.scoring.domain-overrides` | *(empty)*            | Source-label prefix → `SourceDomain`, e.g. `Reddit: CODE`. Longest prefix wins; an unknown domain name fails at startup. Cannot invent a new domain — splitting `SOCIAL` into `SOCIAL`/`PRESS` needs a code change |
| `report.dependency-profile.enabled` | `true`              | Release radar (N07) and stack matching (N06) |
| `report.dependency-profile.project-paths` | *(empty)*     | Directories or build files of **your own** projects. Must stay empty in this repository: those paths exist on your machine and not in CI, so a non-empty default would make the scheduled run depend on directories it never sees |
| `report.dependency-profile.coordinates` | *(empty)*       | Fallback `group:artifact:version` list, used when no build file is readable — the normal case in CI. The run logs which list it used |
| `report.dependency-profile.registry-url` | `https://search.maven.org` | Registry queried for the current release |
| `report.thin-week.enabled`     | `true`                   | Below the bar, send a three-line note instead of a padded digest (N03) |
| `report.thin-week.min-items`   | `3`                      | Stories above the Must-know bar required for a full digest. Every run logs the measured count and whether the note would fire, enabled or not |
| `report.delivery.guard-enabled`| `true`                   | Refuse to send an edition that already went out (F16). Off, and the only protection is the CI `concurrency` group, which a manual JAR run bypasses entirely |
| `report.delivery.claim-timeout-minutes` | `60`            | How long an **unfinished** delivery claim is respected before another run may take it over. Exists for the run that dies between claiming and sending — without it, one crash would make that edition unretryable forever. A claim that reached delivery is never taken over, whatever this is set to. Keep it above a real send and below the gap between editions (the workflow's own `timeout-minutes` is 30) |
| `report.prompt.system`         | `classpath:prompts/system-prompt.txt` | The system prompt the synthesis call uses (N39). Accepts `classpath:` or `file:`, so a fork can point at its own prompt without editing anything inside the jar. Its SHA-256 is recorded with every edition (N26), so two editions made by different prompts are distinguishable afterwards |
| `report.archive.output-path`   | `build/archive`          | Where `--publish-archive` writes the static site (N31) |
| `report.archive.site-url`      | the Pages URL            | Absolute base used for Atom `id` and `link` elements, which must be resolvable rather than relative |
| `report.sections.disabled`     | *(empty)*                | Sections of the digest switched off without touching code (N19). Keys as `DigestSections` declares them: `critical-trends`, `must-know`, `insights`, `deals`, `weekly-recap`, `dependency-radar`, `eol`, `call-for-papers`, `saved`, `tables`, `reading-budget`, `score-explanations`. The point is not customisation — the audit could not determine which sections are ever read, and turning one off for a few editions is the cheap way to find out |
| `report.sources.disabled`      | the three X fetches      | Sources the run must not even attempt (N27). Ships with `Twitter/X influencer`, `Twitter/X topic` and `Twitter/X Anthropic` in it — see K7 above. Names exactly as the run log prints them, case-insensitively. Empty is the expected state: an entry here is a decision that a source is not worth the fetch, taken on the evidence the run already prints |
| `report.semantic-clustering.enabled` | `true`             | Embed each edition's items and look for the same story told twice (N21) |
| `report.semantic-clustering.enforcing` | `false`          | Whether a cluster actually rewrites correlation keys. **Off on purpose**: over-merging is worse than duplication, and worse here than elsewhere — since F2, corroboration is the only route to 🔴, so a wrong merge manufactures a critical trend out of two unrelated articles. The shadow log (`semantic-clustering would-merge`) is what the threshold should be tuned against, with `--replay` |
| `report.semantic-clustering.threshold` | `0.86`           | Cosine similarity above which two items are the same story, measured against the cluster's **seed** rather than any member — comparing against any member lets A~B~C chain into one story when A and C are unrelated |
| `report.semantic-clustering.max-cluster-size` | `4`       | Largest group one story may form. Beyond it the cluster is rejected whole: a dozen identical headlines is a threshold problem, and keeping an arbitrary subset would hide that |
| `llm.embeddings.model`         | `text-embedding-3-small` | Model used for clustering. **One call per edition, not per item** — the endpoint takes an array |
| `report.self-critique.enabled` | `true`                   | Ask a second, cheap model which items a senior engineer would find useless (N23) |
| `report.self-critique.enforcing` | `false`                | Whether that verdict removes items. **Off on purpose**: the critique logs what it *would* cut, and a few editions of that log are what the decision to enforce should be made on. A second model opinion can be confidently wrong, and an edition stripped by an uncalibrated critic is worse than a boring one |
| `report.retrospective.lookback-days` | `90`               | Window the quarterly retrospective reads (N20, `--retrospective`) |
| `report.retrospective.maturity-days` | `30`               | How long a 🔴 call must have had to prove itself before the scoreboard grades it (N11). Younger calls are excluded, or every hit rate is dragged towards zero by construction |
| `report.call-for-papers.enabled` | `true`                 | Render the 🎤 open-CFP block (N09) |
| `report.call-for-papers.base-url` | (confs.tech dataset)  | Where the conference JSON is read from — structured `cfpEndDate` / `cfpUrl` fields rather than scraped prose |
| `report.call-for-papers.topics` | (5 topics)             | Topic files to read, for this year and next |
| `report.call-for-papers.lookahead-days` | `120`          | How far ahead a deadline may be and still be worth printing |
| `report.call-for-papers.limit`  | `5`                     | Most CFPs shown in one edition |
| `report.saved.enabled`         | `true`                   | Render the 🔖 "zapisane na później" shelf (N05) |
| `report.saved.max-items`       | `5`                      | How many of the oldest saved items the mail shows; the rest are counted, never silently dropped. The window is **not** a key of its own — saves are read over `report.feedback.lookback-days`, so the shelf and the loop that fills it cannot develop two notions of "recent" |
| `report.eol.enabled`           | `true`                   | Render the ⌛ end-of-life block (N08) |
| `report.eol.warn-within-months`| `6`                      | How far ahead support deadlines are announced; anything further away stays silent, and anything already past is always shown |
| `report.eol.valid-as-of`       | `2026-10-19`             | When the table below was last verified against [endoflife.date](https://endoflife.date). **Required.** Rendered in the mail, and flagged there once it is over 180 days old — a hand-kept table of dates rots, and one that cannot show its own age states an EOL with full confidence years after anyone checked it |
| `report.eol.entries`           | (7 entries)              | `name` + `date` pairs for the stack you actually run |
| `report.reader-profile.enabled`| `true`                   | Distil and inject the living reader model |
| `report.reader-profile.min-votes` | `10`                  | Clicks required before a profile is distilled at all |
| `report.reader-profile.refresh-days` | `7`                | How often the profile is re-distilled (one gpt-4o-mini call) |
| `report.reader-profile.hypothesis-ttl-days` | `60`        | How long a hypothesis survives without fresh confirmation |
| `twitter.min-faves`            | `3`                      | Server-side `min_faves:N` floor appended to queries (enabled; `0` = off). Tier-dependent operator — if your X tier rejects it the source fails gracefully |
| `research.authority-usernames` | (curated list)      | Accounts whose tweets bypass the relevance filter — the authority tier of the Twitter intake |
| `research.days-back`           | `5`                      | Tweet age window (days) — 120h covers the Thu→Mon gap (96h) |
| `research.min-likes`           | `3`                      | Minimum likes for tweets                           |
| `research.min-active-sources`  | `3`                      | **F10.** Fewest sources that must have returned something before an edition is worth sending. Below it the run aborts after the fetch and **before synthesis**, so a collapsed intake costs no tokens, and the failure alert goes out instead of a two-item digest dressed as a full edition (the all-empty case keeps its own abort). Counted from the run's fetch ledger — a source that answered with nothing is not active, and a source switched off in `report.sources.disabled` is neither active nor attempted, so disabling X cannot push a run under the floor. The floor is capped by the number of sources actually attempted. `0` switches it off. The number is from the ceiling, not from measurement: calibrate it against `SourceYieldService` scoreboard data after a few editions |
| `research.min-contributing-sections` | `3`              | Min sections still carrying an item after filtering — aborts before synthesis (N60); `0` disables |
| `report.run-summary-path`      | `$GITHUB_STEP_SUMMARY`   | Where the run summary is appended; blank writes nothing (N61) |
| `dedup.enabled`                | `true`                   | Drop items already published in recent editions    |
| `dedup.lookback-days`          | `10`                     | Days of prior editions to dedup against (≥ widest window) |
| `interest-profile.persona`     | (CV-derived)             | Reader profile, injected into the synthesis prompt (single source of truth) |
| `interest-profile.relevance-keywords` | ~95 terms         | Keyword set driving the client-side tweet relevance filter |
| `feedback.enabled`             | `true`                   | Act on reader feedback: suppress 👎 URLs **and** nudge per-source weights from net votes |
| `feedback.lookback-days`       | `30`                     | Window for both down-vote suppression and per-source net-vote nudging |
| `feedback.receiver-url`        | `${FEEDBACK_RECEIVER_URL:}` | External endpoint the email 👍/👎 links point at; blank = no links rendered |
| `feedback.signing-secret`      | `${FEEDBACK_SIGNING_SECRET:}` | Shared with the receiver; set = links carry `&sig=` HMAC; blank = unsigned links |
| `hacker-news.keywords`         | `[ai, llm, java, ...]`   | HN search keywords (parallel single-term queries)  |
| `hacker-news.min-score`        | `40`                     | Minimum HN points                                  |
| `hacker-news.limit`            | `15`                     | Max HN items                                       |
| `hacker-news.lookback-hours`   | `104`                    | HN post age window                                 |
| `github.query`                 | `(topic:ai OR topic:machine-learning OR topic:llm)` | GitHub search query              |
| `github.limit`                 | `8`                      | Max GitHub repos                                   |
| `github.lookback-days`         | `5`                      | GitHub `pushed:>=` window                          |
| `github-api.token`             | `${GITHUB_API_TOKEN:}`  | Bearer token sent by every api.github.com source (Search, Releases, Advisories, JEP, CNCF); blank = unauthenticated, 60 requests/h per IP. CI maps it from the automatic `GITHUB_TOKEN` |
| `rss.limit`                    | `10`                     | Max items per RSS feed                             |
| `rss.lookback-hours`           | `104`                    | RSS item age window                                |
| `reddit.min-score`             | `30`                     | Minimum Reddit upvotes                             |
| `reddit.limit`                 | `15`                     | Max posts per subreddit                            |
| `social.limit`                 | `15`                     | Max social posts (Bluesky + Mastodon, after the floors) |
| `social.max-per-account`       | `3`                      | Max slots one account may win — see K6 below       |
| `social.bluesky.min-likes`     | `5`                      | Min likes for a Bluesky post                       |
| `social.bluesky.handles`       | (list)                   | Bluesky handles to pull author-feeds from          |
| `social.mastodon.instance-url` | `https://fosstodon.org`  | Mastodon instance for hashtag timelines            |
| `social.mastodon.min-likes`    | `1`                      | Min favourites for a Mastodon post (not 5 — see K6) |
| `social.mastodon.hashtags`     | `[java, quarkus, ...]`   | Mastodon hashtags to follow                        |
| `arxiv.max-results`            | `20`                     | Max arXiv papers                                   |
| `arxiv.lookback-hours`         | `104`                    | arXiv paper age window                             |
| `github-releases.lookback-hours` | `104`                  | GitHub Releases age window                         |
| `hugging-face.limit`           | `30`                     | Max HF trending models per fetch                   |
| `hugging-face.min-likes`       | `3`                      | Min HF likes (OR `min-downloads` to qualify)       |
| `hugging-face.min-downloads`   | `50`                     | Min HF downloads (OR `min-likes` to qualify)       |
| `product-hunt.min-votes`       | `100`                    | Min Product Hunt upvotes                           |
| `product-hunt.lookback-hours`  | `104`                    | Product Hunt launch age window                     |
| `security-advisories.lookback-hours` | `104`              | Security Advisory age window                       |
| `security-advisories.limit`    | `20`                     | Max advisories fetched per run                     |
| `open-jdk.lookback-days`       | `10`                     | OpenJDK JEP age window                             |
| `cncf-landscape.lookback-days` | `7`                      | CNCF landscape change window                       |
| `conference-talks.lookback-days`   | `10`                 | YouTube talks age window                           |
| `conference-talks.max-results` | `10`                     | Max items per channel                              |
| `lab-announcements.lookback-hours` | `104`                | Window for new posts on AI-lab blogs (Anthropic, OpenAI, Google) |
| `lab-announcements.post-fetch-limit` | `15`               | Max post pages fetched per JSONLD source (listings have no dates) |
| `lab-announcements.sources`    | 5 sources                | Per-source `name` + `strategy` (SANITY/JSONLD/OPENAI_DEV) + `listing-url` |

## Source adapters (`report.<source>.*`)

Each source carries its own small block — typically `base-url`, `limit`, `lookback-hours` and a
query or filter list. They are documented here as families rather than one row per base URL:
sixteen sources times four plumbing keys would bury the dozen settings a reader actually tunes, and
`application.yaml` carries the per-value commentary beside each default, where it is read.

`ConfigurationDocsTest` holds this line: a source may be documented as a family, but a source
documented *nowhere* fails the build — so a new adapter cannot ship undocumented.

| Family | What it configures |
|--------|--------------------|
| `twitter.*` | **Disabled since K7 (2026-10-19)** — the three X fetches are in `report.sources.disabled`, so none of these keys is read on a normal run and `TWITTER_BEARER_TOKEN` is not needed. The code stays; re-enabling is deleting three lines and setting the secret. Query list, `min-faves` floor, per-query limits for the X intake. The one source whose prefix is bare rather than under `report.` — it predates the grouping |
| `report.hacker-news.*` | Algolia endpoint, point floor, lookback window, result cap |
| `report.github.*` | Search endpoint, language and lookback window, result cap |
| `report.github-releases.*` | Watched repositories and how many releases to take from each |
| `report.rss.*` | Feed list (≈30) and the per-feed item cap |
| `report.reddit.*` | Subreddit list, score floor, per-subreddit cap |
| `report.arxiv.*` | Categories, lookback window, result cap |
| `report.hugging-face.*` | Trending endpoint, result cap, minimum downloads |
| `report.product-hunt.*` | GraphQL endpoint, result cap, lookback window |
| `report.security-advisories.*` | GitHub advisory endpoint, severities, ecosystems, caps |
| `report.open-jdk.*` | JEP index endpoint, statuses of interest, cap |
| `report.cncf-landscape.*` | Landscape commit feed, lookback window, cap |
| `report.technology-radar.*` | Radar endpoint and the rings kept |
| `report.conference-talks.*` | YouTube channels, lookback window, per-channel cap |
| `report.lab-announcements.*` | AI-lab blogs and their parsing strategies, lookback window |
| `report.social.*` | Bluesky handles and Mastodon hashtags, per-network like floors, caps |

## Why the social floors differ (K6)

Measured live on 2026-10-19, because the single shared `min-likes: 5` looked reasonable and was not:

| Feed | Posts / 14 days | Clearing 5 likes | Median likes |
|------|-----------------|------------------|--------------|
| `bsky.app` (the old seed handle) | 29 | 28 | **1403** |
| fosstodon `#java #quarkus #kubernetes #llm #ai` | 200 | **1** | 0–3 |

A like on Bluesky and a favourite on fosstodon are not the same quantity, so one threshold cannot
serve both: at 5 the Mastodon half contributed nothing at all. And because selection ranked by raw
like count across both networks, the single most popular account took every slot — the source called
"Bluesky + Mastodon, fifteen posts" was one company account's timeline. Hence two changes rather than
a new seed handle: per-network floors, and `max-per-account`, which caps how many of the fifteen
slots one account can win no matter how popular it is.

## Related

- What these thresholds feed → [docs/how-it-works.md](how-it-works.md)
- Which of these become GitHub Actions secrets → [docs/running-locally.md](running-locally.md#github-actions)
