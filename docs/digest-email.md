# The digest email

What lands in the inbox, and the 👍/👎/zapisz loop that feeds back into scoring.

## One story, one section

Sections are listed below in the order they appear, which is also the order in which they
*claim* material. Every section used to re-filter the whole item list against its own
threshold, so a high-scoring launch satisfied four of them at once and the reader met the
same news in four consecutive blocks. `DigestLayout` now decides membership once, before any
section renders.

Two rules follow from that:

- **Items sharing a `topic_key` are one story.** It is rendered once, led by its
  highest-scoring item, and the sources that independently carried it appear as a
  `Potwierdzone w:` line **linking each one to its own coverage** — so collapsing the entry
  loses the repetition, not the links. Grouping is on `topic_key` alone, never on
  `correlationKey()`'s category fallback, which would merge two unrelated `AI/LLM` stories.
- **The first section that can take a story, takes it.** Deals & Tools therefore means
  "adoptable things not already covered above", and the two tables index the remainder.
- **The weekly recap is filtered too**, against every URL the mail printed — members folded into a
  story included, since a recap line names an item by its own URL. Only faded lines are exempt.

## Email anatomy

The delivered HTML email is a structured digest, not just a link list:

- **Hidden preheader** — 60–90 char preview text shown in the inbox snippet (Gmail/Apple Mail).
- **📦 Twoje zależności** — a deterministic section listing dependencies you pin behind their current release, read from your own build files (`report.dependency-profile.project-paths`) and checked against Maven Central. Model-free on purpose: "you pin 3.5.0 and 3.5.3 is out" is a fact with one correct phrasing. Absent when everything is current, which is the normal state.
- **⚠️ W TWOIM STOSIE** — a loud badge on any item whose advisory names a dependency you actually ship, matched on exact `group:artifact` coordinates. "Critical CVE in a library you do not use" and "moderate CVE in one you ship" are different emails, and the difference should not need reading the sentence to notice.
- **Thin week** — when fewer than `report.thin-week.min-items` stories clear the Must-know bar, the whole mail is three lines instead: *"Cicho w tym tygodniu — nic nie zasługuje na Twoją uwagę"*, the count that was measured, and the one item that came closest, carrying its own 👍/👎 links. No tables, no recap, no score explanations — an edition with nothing above the bar has nothing those sections would be about, and rendering them anyway is the padding the product's own thesis forbids. The subject says so too, so it never promises a digest over a body that says nothing happened. Both shapes are under the golden file ([`sample-digest.html`](sample-digest.html) and [`sample-digest-thin.html`](sample-digest-thin.html)), because the thin shape is one the normal fixture can never exercise.
- **📈 Story continuity** — a story carried by two or more consecutive editions shows *"narasta — N. edycja z rzędu · Pierwszy sygnał: dd.mm.yyyy"*. It renders in **every** section as of 2026-10-18; before that it was confined to the critical-trends block, so a recurring story in Must-know or the tables looked new every time.
- **⚠️ Exhausted-limits banner** — amber block at the very top, shown only when one or more API accounts hit their quota / rate limit this run (e.g. depleted X API credits, GitHub/YouTube rate limit). Names each account to top up so the recipient knows the digest is partial. Absent when every source succeeded. Quota detection (`QuotaSignals` / `QuotaErrors`) classifies failures by HTTP 402/429/403-quota and provider wording; generic transient failures stay in the footer's source-health line instead.
- **Subject line** — deterministic marker (🔴 Critical / ⚡ Must-know / 📡 otherwise) + `email_preview` truncated on a word boundary + the date. Falls back to the plain `📡 PulseDigest dd.MM.yyyy` when the preview is blank. No model call, no clickbait: the same report always yields the same subject, so the inbox answers "read now or tonight?" without opening the mail.
- **Editorial lead** — 2–3 sentence meta-thesis tying together the day's most important signals (italic, prominent). Prefixed with `⚠️ Digest awaryjny (model zapasowy)` when the primary model failed and the edition was synthesized by the `gpt-4o-mini` fallback.
- **🔴 Critical trends** — the first block after the editorial, because it claims before every other section. Stories confirmed across 3+ source domains. Each carries a **`Potwierdzone w:` line naming the sources** that carried it (`arXiv/cs.AI + GitHub + Hacker News`), so the red frame is auditable evidence rather than a colour, plus a **`📈 narasta — N. edycja z rzędu`** badge and a `Pierwszy sygnał: dd.MM.yyyy` date once a story has survived into a second consecutive edition.
- **⚡ Must-know** — hero block: the up-to-5 highest-score stories (score ≥ 7) that Critical trends did not already take, each with a one-sentence **"why it matters to you"** action line generated against the reader's profile (`why_it_matters`). When `feedback.receiver-url` is set, each entry also carries 👍/👎 links. Skipped when nothing clears the bar.
- **🟠 Critical candidate** — title marker for stories the radar expects to break next, rendered wherever the story lands (Must-know, Deals & Tools or a table row). The digest's footer carries the radar's published hit rate.
- **📊 Tydzień w sygnałach** — Thursday only, and only when something moved: up to 7 lines showing which stories **escalated** (`MODERATE → CRITICAL`), which were **confirmed**, and which **faded**. `WeeklyRecapService` builds every line from *this* edition's signals, so a line is kept only when it says something the sections above cannot: it **faded** (a story that was 🔴 and came to nothing has no entry up there to read, and saying so is what makes the 🔴 credible), or its story scored too low to be rendered at all. An escalation or confirmation whose story is already printed above is dropped rather than restated.
- **🔍 „Dlaczego to widzisz"** — a micro-line under each Must-know and Critical item reconstructing its score from the components the scorer already computed: `Hacker News 0.80 → 80 pkt · engagement +12 · potwierdzenie w 3+ domenach +50 · Twoje głosy: źródło −4 (0.80 → 0.60)`. Rendered on those two blocks only — the same line under twenty table rows would be noise, not transparency.
- **🔑 Top insights** — top-3 takeaways extracted from the day.
- **🛠️ Deals & Tools** — up-to-5 adoptable stories of type `LAUNCH` / `RELEASE` / `FEATURE` / `PROMOTION` (score ≥ 6) **that Critical trends and Must-know did not already take**: new tools the reader can use and deals worth claiming. Skipped when none are left.
- **🔖 Zapisane na później** — what the reader kept with the "zapisz" link and has not voted on since, oldest first, capped at `report.saved.max-items` with the remainder counted in a trailing line rather than silently dropped (N05). **The heading says *saved*, not *unread*, and the section says so itself**: nothing in this system observes a reader opening a link — the batch serves no HTTP — so "unread" would be a claim the data cannot support. What removes an item from the shelf is a later 👍 or 👎 on the same URL, which is the only evidence available that the reader came back to it; everything else stays until it ages out of `report.feedback.lookback-days`. The feedback row stores no title, so the headline is recovered from the stored edition the item was saved out of, and an item whose edition has aged out of the history window is shown by its URL rather than dropped. Silent when the shelf is empty.
- **⏱️ Budżet czytania** — a line under the header: how many items and roughly how many minutes
(N04). Estimated from each item's **type and summary length**, never by fetching the linked page:
fetching is more accurate where it works and silently wrong where it does not (paywalls, consent
walls and single-page apps all return something that is not the article), and it would add an HTTP
call and a failure mode per item. The total is rounded to five minutes and the line says it is an
estimate — a precise-looking figure would be believed, and this one has not earned that.
- **🎤 Call for Papers** — conference deadlines still open, soonest first, with the days remaining
spelled out (N09). The other half of a conference source: a talk that already happened is something
to watch, a call closing in three weeks is something to do. Read from the structured confs.tech
dataset (`cfpEndDate`, `cfpUrl`) rather than scraped from CFP pages — a deadline parsed out of prose
and got wrong is worse than a deadline missing, because the reader plans around it.
- **⌛ Koniec wsparcia** — the end-of-life dates from `report.eol.entries` that are either already past or fall inside `report.eol.warn-within-months`, oldest first (N08). This is the one kind of item a news feed structurally cannot deliver: nothing *happened*, a date simply arrived — which is exactly what a twice-weekly digest is for. Rendered deterministically, never through the model, because an EOL date has one correct phrasing. Every rendering carries **when the table was last checked**, and says so loudly once that is over 180 days old: the dates are hand-kept, so the day comes when nobody has looked at them in a year, and a reader who cannot see that inherits a false confidence. Silent when nothing is due.
- **⭐ Top picks** — score ≥ 8, white background. The remainder after the blocks above, in the order the model emitted it.
- **🔌 Signals** — score 6–7, muted `#fafafa` background.
- **Footer** — transparency block: "selected N of M items · K sources · source warnings · Mon/Thu window". Since N27 it also names **silent sources**: sources that answered and returned nothing across several editions. The run log has warned about these since F7, and nobody reads a green run's log — a source that is reachable and empty produces no failure anywhere, so without this line the digest quietly narrows and only someone who goes looking finds out. The line names `report.sources.disabled` as the action.

Every entry in every block carries 👍/👎 and a plain-text **„zapisz"** link when a feedback
receiver is configured. One story now yields one set of links rather than up to four, which is
the point: a vote should mean "this story", not "this row".

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

## Two parts, two palettes, one phone

**`multipart/alternative` (N14).** Every edition ships an HTML part and a real `text/plain` part
rendered by [`PlainTextDigestBuilder`](../src/main/java/pl/seniordeveloper/pulsedigest/modules/market_intel/infrastructure/adapter/out/email/PlainTextDigestBuilder.java),
not HTML with the tags stripped. A message with no text part at all is a spam-filter signal, so this
is deliverability before it is accessibility; it is also the only version a terminal client or a
watch notification renders faithfully, and writing it is an editorial check — content that needs the
colour-coding to make sense did not have a shape to begin with. It is wrapped to 78 columns, carries
every story the HTML carries (including each confirming source's own link, so a text reader gets the
same evidence for a 🔴 badge), and **leaves the feedback links out**: three URLs per item would
triple the length of the part whose whole virtue is being scannable, and the footer says so rather
than letting a text reader wonder where the thumbs went. Both parts are under the golden file
([`sample-digest.txt`](sample-digest.txt)), because two renderers of one edition drift and the drift
is invisible until a reader whose client prefers text gets a different digest from the one that was
reviewed.

**Dark mode (N15).** The document declares `color-scheme: light dark` and carries a
`prefers-color-scheme: dark` block. The declaration is the important half: a client that finds a
light document with no opinion about colour schemes **inverts it itself**, and machine-inverted
`#f9fafb` panels come out as something nobody designed and several things nobody can read. The
overrides map each muted tier of the light palette onto a dark one by attribute selector rather than
flattening everything to one foreground — the first version of the block did flatten it, which left
every summary at its light-mode grey on a dark panel at about 3.7:1. That was visible in the rendered
sample and in nothing else, which is why the sample is committed next to the light one:
[`sample-digest-dark.png`](images/sample-digest-dark.png). Section tinting does not survive dark mode;
the coloured `border-left` on each panel does, and since N16 nothing was ever carried by the
background alone.

**Accessibility (N16).** 🔴 🟨 🟡 ⚪ differ from one another *only* by colour, so the rank now appears
as a word in each row's metadata line as well, the glyph carries an `aria-label`, and every feedback
link has a name (an `<a>` whose entire text is an emoji announces as "link" in a client that does not
render emoji). The table declares `scope="col"` rather than `role="presentation"` — it has real
column headers, and calling it presentational would take the association away rather than add one.
Every foreground colour on a light panel now meets WCAG AA at 4.5:1; the pass found four that did
not, including the score badge at 2.9:1. `EmailAccessibilityTest` asserts all of it against the
rendered document, because nothing else in this build has an opinion about a colour value or an
unlabelled link.

**Phone layout (N17).** The golden file proves the markup did not change, not that it still *looks*
right: a CSS edit can keep every byte it checks and still push a table off the right edge at 375px.
`scripts/mobile-snapshot.sh` renders the recorded digest at phone width through a **pinned container**
(`zenika/alpine-chrome:124`) and `MobileRenderingRegressionTest` diffs it against
[`sample-digest-mobile.png`](images/sample-digest-mobile.png) with a 0.1% tolerance. The renderer is
pinned because Chrome on a laptop and Chrome on a CI runner disagree about font hinting on every
glyph, so a baseline from one would fail on the other for reasons that have nothing to do with
layout. The test also asserts the snapshot reached the bottom of the document — the first baseline
captured 2400px of a 5664px page, so every table sat below the frame and the test guarded nothing
that mattered.

## Switching sections off

`report.sections.disabled` takes a list of section keys and the digest renders without them (N19).
The point is not customisation: the audit could not determine which sections are ever read, and
turning one off for a few editions is the cheapest way to find out. Keys live as constants in
`DigestSections`, so a typo fails a test rather than silently disabling nothing — which would be the
worst outcome for a switch whose whole job is to answer "was it missed?".

Disabling every section still produces a valid document; `SectionTogglesTest` asserts that, and that
every declared key changes the rendered output.

## Feedback loop (👍/👎/zapisz)

The reader can mute topics without the app ever serving HTTP (it stays a headless batch —
see [ADR-0006](adr/0006-virtual-threads-over-reactive.md)). The loop is **Supabase-mediated**:

1. **Every rendered item** — Must-know, Deals & Tools, Top picks and Signals — carries 👍/👎/„zapisz" links pointing at an **external receiver** you deploy (e.g. a Supabase Edge Function), set via `FEEDBACK_RECEIVER_URL`. When blank, no links are rendered. **The receiver must uppercase the vote**: links carry `vote=up`/`vote=down`/`vote=save`, the `feedback` table accepts only `CHECK (vote IN ('UP','DOWN','SAVE'))`, and both reading queries match the uppercase form — so a receiver that passes the value through leaves every vote either rejected or uncounted. That obligation is the loop's one contract living outside this repository, and `FeedbackVoteContractIT` pins all three sides of it against a real Postgres, starting from the link the mail actually renders. Thumbs used to appear on the ≤5 Must-know items only, so the loop never heard about the mid-tier it is supposed to learn to rank.

2. The receiver records the click into the Supabase `feedback` table. **Contract:** `GET {receiver-url}?url=<item-url>&vote=up|down|save&source=<source>&category=<category>&edition=<YYYY-MM-DD>[&sig=<HMAC>]` → `INSERT INTO feedback (item_url, source, vote, category, edition) VALUES (…)`.

   **The third action exists to make the first two mean something (N05).** With only 👍/👎 on offer, a reader who merely wanted to keep an item for later clicked 👍 — the only button that did not punish — and every such click entered the scorer as a preference for that source which the reader had never expressed. `save` is therefore recorded and counted as **zero**: `netVotesBySource` and `netVotesByCategory` spell every value out (`CASE vote WHEN 'UP' THEN 1 WHEN 'DOWN' THEN -1 ELSE 0 END`) instead of the earlier `ELSE -1`, which would have read every bookmark as a down-vote against its source. It is also excluded from calibration — "I'll read this later" is not the reader confirming the item earned its 🔴 slot, and folding it in would re-merge the two signals this split exists to separate. [`V4__feedback_save_vote.sql`](../src/main/resources/db/migration/V4__feedback_save_vote.sql) widens the constraint; the migration drops the V1 constraint by name without `IF EXISTS`, so a wrong guess fails the migration loudly instead of leaving a second constraint beside one that still rejects `SAVE`.

   All three links still share **one row per item per edition** (V1's partial unique index). A reader who clicks 👍 and then „zapisz" keeps only the first — the price of the property that matters more: a mail scanner prefetching all three links cannot write three rows. Saves are read back on the next run into the **🔖 Zapisane na później** section above.

   **Signed links (`FEEDBACK_SIGNING_SECRET`).** When the secret is set, each link carries `&sig=`, an unpadded URL-safe HMAC-SHA256 over the canonical `url|vote|source|category|edition` — everything that changes what the receiver writes is inside the signature, or a vote could be re-aimed at a topic the reader never voted on. Be precise about what that buys: it does **not** stop a mail scanner from following a link — the signature is right there in the href. It means a vote cannot be **forged or edited**: flipping `vote=up` to `vote=down`, or re-pointing the link at another item, invalidates the signature and the receiver rejects the write. What limits scanners is the **`edition` parameter plus a partial unique index** `(item_url, edition)`: prefetching the same link repeatedly — or fetching both 👍 and 👎 — yields exactly one row. Rows without an `edition` (a receiver that predates the parameter) are outside the index, so **signing can be rolled out before the receiver knows how to verify it**: leave the secret blank and the links render exactly as they do today.

3. **Per-category preference.** A 👎 on a dull paper should punish the topic ("Research"), not the whole of arXiv — half the information in every click used to be discarded. The `category` parameter feeds a `CategoryPreference` multiplier (±2% per net vote, **clamped to [0.90, 1.10]**) applied to an item's credibility and engagement score, plus a `== PREFERENCJE CZYTELNIKA ==` block in the LLM prompt naming the topics the reader repeatedly asked for more or less of (≥ 3 net votes; one stray click is noise).

   Two guards keep this from becoming a positive feedback loop, where a muted category disappears and can therefore never earn the votes that would revive it. The multiplier **never scales the cross-source bonus**, so a muted topic that three independent domains confirm still reaches 🔴 CRITICAL — corroboration is evidence, not taste. And the prompt block is worded as a preference, explicitly *not* a filter. Until the receiver starts sending `category`, the column stays null, the multiplier is a no-op and the block is absent — this degrades to the previous per-source behaviour rather than to a wrong one.

4. **„Twoje głosy w akcji".** The footer names every source whose credibility weight the reader's votes actually moved, with the weight it started from and the one it ended at (`Reddit: −4 głosów · waga 0.60 → 0.40`). Scoring used to be a black box in both directions: the reader could not see why an item surfaced, nor what his own 👎 had done to it. A vote whose effect is invisible is a vote nobody casts twice, which is how a learning loop starves. Every component of the explanation (`ScoreBreakdown`) is carried on the `Signal` and persisted with the edition, so it costs nothing extra to compute.

5. On the next run the batch reads recent feedback ([`SupabaseFeedbackAdapter`](../src/main/java/pl/seniordeveloper/pulsedigest/modules/market_intel/infrastructure/adapter/out/persistence/SupabaseFeedbackAdapter.java), `feedback.lookback-days` window) and acts on it two ways:
   - **Suppresses** down-voted item URLs before LLM scoring, alongside cross-edition dedup — the reader mutes a specific item.
   - **Nudges per-source weight** in deterministic signal scoring: votes aggregate at the **base-source** level (a vote on any item from a source — `arXiv/cs.AI`, `Reddit/r/java`, `RSS/InfoQ` — counts toward that source's `arXiv` / `Reddit` / `RSS` weight, not the exact label), and each source's net votes (👍 − 👎) shift its credibility weight by `±0.05` per vote, clamped to `±0.30` and `[0.10, 0.99]`. So a consistently down-voted source ranks lower (and an up-voted one higher) over time, without ever crossing the STRONG threshold on feedback alone. With no votes the weights are unchanged.

   The `feedback` table is part of the Flyway baseline migration [`V1__initial_schema.sql`](../src/main/resources/db/migration/V1__initial_schema.sql), widened to three actions by [`V4__feedback_save_vote.sql`](../src/main/resources/db/migration/V4__feedback_save_vote.sql).

The receiver itself lives outside this repo — the batch only ever *reads* feedback.
