# The digest email

What lands in the inbox, and the 👍/👎 loop that feeds back into scoring.

## Email anatomy

The delivered HTML email is a structured digest, not just a link list:

- **Hidden preheader** — 60–90 char preview text shown in the inbox snippet (Gmail/Apple Mail).
- **⚠️ Exhausted-limits banner** — amber block at the very top, shown only when one or more API accounts hit their quota / rate limit this run (e.g. depleted X API credits, GitHub/YouTube rate limit). Names each account to top up so the recipient knows the digest is partial. Absent when every source succeeded. Quota detection (`QuotaSignals` / `QuotaErrors`) classifies failures by HTTP 402/429/403-quota and provider wording; generic transient failures stay in the footer's source-health line instead.
- **Subject line** — deterministic marker (🔴 Critical / ⚡ Must-know / 📡 otherwise) + `email_preview` truncated on a word boundary + the date. Falls back to the plain `📡 PulseDigest dd.MM.yyyy` when the preview is blank. No model call, no clickbait: the same report always yields the same subject, so the inbox answers "read now or tonight?" without opening the mail.
- **Editorial lead** — 2–3 sentence meta-thesis tying together the day's most important signals (italic, prominent). Prefixed with `⚠️ Digest awaryjny (model zapasowy)` when the primary model failed and the edition was synthesized by the `gpt-4o-mini` fallback.
- **⚡ Must-know** — hero block: the up-to-5 highest-score items (score ≥ 7), each with a one-sentence **"why it matters to you"** action line generated against the reader's profile (`why_it_matters`). When `feedback.receiver-url` is set, each item also carries 👍/👎 links. The "if you read nothing else" section. Skipped when no item clears the bar.
- **🔴 Critical trends** — stories confirmed across 3+ source domains. Each carries a **`Potwierdzone w:` line naming the sources** that carried it (`arXiv/cs.AI + GitHub + Hacker News`), so the red frame is auditable evidence rather than a colour, plus a **`📈 narasta — N. edycja z rzędu`** badge and a `Pierwszy sygnał: dd.MM.yyyy` date once a story has survived into a second consecutive edition.
- **🟠 Critical candidate** — title marker on Top picks / Signals rows for stories the radar expects to break next. The digest's footer carries the radar's published hit rate.
- **📊 Tydzień w sygnałach** — Thursday only, and only when something moved: up to 7 lines showing which stories **escalated** (`MODERATE → CRITICAL`), which were **confirmed**, and which **faded**. Saying that Monday's 🔴 came to nothing is what makes the 🔴 credible.
- **🎯 Twój radar** — one line per watched technology from `report.watchlist.technologies`, scanned across **every headline fetched this run** (not just the items that survived the prompt budget). A keyword with no matches renders an explicit `0 wzmianek`: confirmed silence, not an item that quietly lost a slot.
- **🔍 „Dlaczego to widzisz"** — a micro-line under each Must-know and Critical item reconstructing its score from the components the scorer already computed: `Hacker News 0.80 → 80 pkt · engagement +12 · potwierdzenie w 3+ domenach +50 · Twoje głosy: źródło −4 (0.80 → 0.60)`. Rendered on those two blocks only — the same line under twenty table rows would be noise, not transparency.
- **🔑 Top insights** — top-3 takeaways extracted from the day.
- **🛠️ Deals & Tools** — up-to-5 adoptable items of type `LAUNCH` / `RELEASE` / `FEATURE` / `PROMOTION` (score ≥ 6): new tools the reader can use and deals worth claiming. Skipped when none qualify.
- **📈 Puls rynku (tech-demand pulse)** — demand ranking from the monthly HN "Who is hiring?" thread, computed **outside** the core item budget. Shows: a one-line **LLM interpretation**; technologies by **share of hiring posts** with month-over-month **▲/▼ delta** (e.g. "Python 26% ▲3 · TypeScript 23% ▼1 · Rust 9% ▲4"); a **„Twój stack"** line with demand for the reader's own JVM/Python-AI core (`tech-demand.priority-technologies`) even when outside the top ranking; and a "vs <prev month>" footnote. The delta is stateless — the adapter also fetches the **previous** month's thread and compares share in percentage points. Shown only in the ~week after a new monthly thread drops (`tech-demand.lookback-days`), then absent, so it never repeats every run. Off when no technology clears `min-mentions`.
- **⭐ Top picks** — score ≥ 8, white background.
- **🔌 Signals** — score 6–7, muted `#fafafa` background.
- **Footer** — transparency block: "selected N of M items · K sources · source warnings · Mon/Thu window".

Every item row in all four blocks carries 👍/👎 links when a feedback receiver is
configured — roughly 15–20 points of contact per edition instead of the previous five.

Both item tiers render an identical full table: **article link + 1–2 sentence Polish
summary · category badge · type badge · source + engagement (❤/pkt/★/↑) · color-coded
score**. The background shade is the only visual distinction — every tier gives enough
context to decide whether to click. Items below score 6 are dropped (quality over
quantity, no long-tail padding).

Every article link is scheme-allow-listed (only `http`/`https`; a `javascript:`/`data:`
URL slipped in via scraped content collapses to `#`).

> **Standalone failure-alert email.** Whenever the digest cannot be produced **before
> delivery** — for *any* reason: the LLM ran out of credits, every source was rate-limited,
> the model returned truncated JSON, or an unexpected error aborted the run — a separate
> minimal alert email (red header, „Digest nie powstał") is sent so a broken run is never
> silent. When a quota/rate-limit signature is detected the accounts to top up are named
> (including `OpenAI (model LLM)`); otherwise the alert carries the failure reason only.
> It is deliberately **not** sent on `EMAIL_FAILED` — there the email channel itself is
> down, so the GitHub Actions failure notification is the backstop instead (see
> [docs/running-locally.md](running-locally.md#github-actions)). Sending the alert never
> masks the original failure: if it too fails, the job error is preserved.

## Feedback loop (👍/👎)

The reader can mute topics without the app ever serving HTTP (it stays a headless batch —
see [ADR-0006](adr/0006-virtual-threads-over-reactive.md)). The loop is **Supabase-mediated**:

1. **Every rendered item** — Must-know, Deals & Tools, Top picks and Signals — carries 👍/👎 links pointing at an **external receiver** you deploy (e.g. a Supabase Edge Function), set via `FEEDBACK_RECEIVER_URL`. When blank, no links are rendered. Thumbs used to appear on the ≤5 Must-know items only, so the loop never heard about the mid-tier it is supposed to learn to rank.

2. The receiver records the click into the Supabase `feedback` table. **Contract:** `GET {receiver-url}?url=<item-url>&vote=up|down&source=<source>&category=<category>&edition=<YYYY-MM-DD>[&sig=<HMAC>]` → `INSERT INTO feedback (item_url, source, vote, category, edition) VALUES (…)`.

   **Signed links (`FEEDBACK_SIGNING_SECRET`).** When the secret is set, each link carries `&sig=`, an unpadded URL-safe HMAC-SHA256 over the canonical `url|vote|source|category|edition` — everything that changes what the receiver writes is inside the signature, or a vote could be re-aimed at a topic the reader never voted on. Be precise about what that buys: it does **not** stop a mail scanner from following a link — the signature is right there in the href. It means a vote cannot be **forged or edited**: flipping `vote=up` to `vote=down`, or re-pointing the link at another item, invalidates the signature and the receiver rejects the write. What limits scanners is the **`edition` parameter plus a partial unique index** `(item_url, edition)`: prefetching the same link repeatedly — or fetching both 👍 and 👎 — yields exactly one row. Rows without an `edition` (a receiver that predates the parameter) are outside the index, so **signing can be rolled out before the receiver knows how to verify it**: leave the secret blank and the links render exactly as they do today.

3. **Per-category preference.** A 👎 on a dull paper should punish the topic ("Research"), not the whole of arXiv — half the information in every click used to be discarded. The `category` parameter feeds a `CategoryPreference` multiplier (±2% per net vote, **clamped to [0.90, 1.10]**) applied to an item's credibility and engagement score, plus a `== PREFERENCJE CZYTELNIKA ==` block in the LLM prompt naming the topics the reader repeatedly asked for more or less of (≥ 3 net votes; one stray click is noise).

   Two guards keep this from becoming a positive feedback loop, where a muted category disappears and can therefore never earn the votes that would revive it. The multiplier **never scales the cross-source bonus**, so a muted topic that three independent domains confirm still reaches 🔴 CRITICAL — corroboration is evidence, not taste. And the prompt block is worded as a preference, explicitly *not* a filter. Until the receiver starts sending `category`, the column stays null, the multiplier is a no-op and the block is absent — this degrades to the previous per-source behaviour rather than to a wrong one.

4. **„Twoje głosy w akcji".** The footer names every source whose credibility weight the reader's votes actually moved, with the weight it started from and the one it ended at (`Reddit: −4 głosów · waga 0.60 → 0.40`). Scoring used to be a black box in both directions: the reader could not see why an item surfaced, nor what his own 👎 had done to it. A vote whose effect is invisible is a vote nobody casts twice, which is how a learning loop starves. Every component of the explanation (`ScoreBreakdown`) is carried on the `Signal` and persisted with the edition, so it costs nothing extra to compute.

5. On the next run the batch reads recent feedback ([`SupabaseFeedbackAdapter`](../src/main/java/pl/seniordeveloper/pulsedigest/modules/market_intel/infrastructure/adapter/out/persistence/SupabaseFeedbackAdapter.java), `feedback.lookback-days` window) and acts on it two ways:
   - **Suppresses** down-voted item URLs before LLM scoring, alongside cross-edition dedup — the reader mutes a specific item.
   - **Nudges per-source weight** in deterministic signal scoring: votes aggregate at the **base-source** level (a vote on any item from a source — `arXiv/cs.AI`, `Reddit/r/java`, `RSS/InfoQ` — counts toward that source's `arXiv` / `Reddit` / `RSS` weight, not the exact label), and each source's net votes (👍 − 👎) shift its credibility weight by `±0.05` per vote, clamped to `±0.30` and `[0.10, 0.99]`. So a consistently down-voted source ranks lower (and an up-voted one higher) over time, without ever crossing the STRONG threshold on feedback alone. With no votes the weights are unchanged.

   The `feedback` table is part of the Flyway baseline migration [`V1__initial_schema.sql`](../src/main/resources/db/migration/V1__initial_schema.sql).

The receiver itself lives outside this repo — the batch only ever *reads* feedback.
