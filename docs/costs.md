# What this costs to run

The reader of a portfolio project always wonders; the author almost never says. So: the arithmetic,
the free tiers it leans on, what happens when one runs out, and — stated plainly — which figures
here are measured and which are derived.

## Cadence

`cron: '0 4 * * 1,4'` — Mondays and Thursdays, **104 editions a year**. Every number below multiplies
by that.

## Where the money goes

**OpenAI is the only paid dependency.** Three calls per edition, two of them small:

| Call | Model | Configured cap | When |
|------|-------|----------------|------|
| Synthesis | `gpt-4o` | 10 000 completion tokens | every edition |
| Pre-scoring triage | `gpt-4o-mini` | 4 000 | every edition (`report.pre-scoring.enabled`) |
| Reader-profile distillation | `gpt-4o-mini` | 700 | every ~7 days (`report.reader-profile.refresh-days`) |

Since N23 a fourth call runs in shadow mode (self-critique, `gpt-4o-mini`), and two more exist but
only on demand: `--discover-sources` and the prompt half of `--replay-prompt`.

### The size of the big call, derived

The prompt is bounded by design rather than by hope: `PromptItemSelector.TOTAL_CAP` is **100 items**,
and `report.pre-scoring.keep` trims that to **50** before the expensive call. With a ~263-line system
prompt and a title-plus-summary per item, a synthesis call is on the order of **10–15k prompt tokens
and 4–6k completion tokens**, against a 10k completion ceiling.

**These are derived from the configured caps, not measured.** Multiply by your provider's current
per-million rates to get money; this repository deliberately does not carry a price list, because a
stale price committed to a file is a confident lie (see the comment above `llm.pricing` in
`application.yaml`).

### Getting the measured number

The metering is already there (F11) and reports itself every run:

- every call logs `llm-usage call=… model=… prompt=… completion=… cost=…`;
- the GitHub Actions **step summary** totals tokens and, when `llm.pricing` is filled in, dollars;
- `MetricsLogger` dumps the counters at shutdown.

So the honest instruction is: fill in `llm.pricing` with today's rates, run one edition, and read the
step summary. That number is yours and current; any number written here would be neither.

```yaml
llm:
  pricing:
    gpt-4o:
      input-per-million-tokens: 2.50      # today's rate, not a committed truth
      output-per-million-tokens: 10.00
```

### The ceiling that stops a bad week

`llm.budget.max-tokens` is **1 000 000 per run** (N40). That is roughly five to ten times a normal
edition — it exists to catch a prompt bug or an intake spike, not to ration a busy week. Optional
calls are skipped at the ceiling; synthesis fails the run and alerts. A run can overshoot by at most
one call, because usage is reported after a call completes.

The number is a round guess and the config says so. Calibrate it from your own runs: a warning fires
at 80%.

## The free tiers it leans on

| Service | What for | Tier | What happens at the limit |
|---------|----------|------|---------------------------|
| **Supabase** | Postgres: editions, feedback, reader profile | Free | One JSONB row per edition — 104 a year is nothing. A paused free project is the realistic failure, and it fails loudly: the run cannot persist and exits non-zero |
| **Resend** | Delivery | Free (100 mail/day) | 104 editions a year plus alerts is far inside it |
| **GitHub Actions** | The scheduler and the runner | Free for public repositories | Two short jobs a week. **The real limit is not minutes**: GitHub disables `schedule` triggers after 60 days without repository activity, which silences the digest *and* the watchdog that would report it — see `watchdog.yml` |
| **YouTube Data API** | Conference talks | Free quota | Optional: a blank key skips the source and logs it. No failure |
| **Product Hunt** | Launches | Free token | Optional, same degradation |
| **GitHub REST** | Repo search, releases, security advisories | Unauthenticated rate limit | The source fails for that run and is reported; the digest ships without it |

Thirteen of the sixteen sources need **no credential at all** — RSS, Hacker News, arXiv, Reddit,
CNCF, Tech Radar, OpenJDK JEP, Hugging Face, Bluesky, Mastodon, the AI-lab blogs, conference CFP
data and GitHub's public endpoints.

## What the paid X tier bought, and why it is off

This is the part the metering makes concrete, and it is why K7 was settled the way it was.

X/Twitter was the **only paid credential besides OpenAI**. It fed a source weighted **0.40** — the
lowest tier of source credibility in the scorer — holding **20 of the 100 prompt slots**, in a trim
that evicts the weakest items first. In other words: the one thing in this project that required a
paid subscription was structurally last in line for the attention it was paying for.

Since 2026-10-19 the three X fetches are in `report.sources.disabled`. The code stays, the credential
is no longer required by CI or by the application, and those 20 slots go to whatever else clears the
bar — automatically, because the trim works on the union. Re-enabling it is deleting three lines and
setting the secret.

## Summary

- **104 editions a year.**
- **One paid dependency** (OpenAI), three calls per edition, two of them on a small model, bounded by
  a per-run token ceiling.
- **Three free tiers** that the volume does not stress, and whose realistic failure is administrative
  rather than quantitative.
- **Thirteen sources that cost nothing**, and two optional ones that degrade silently and correctly
  when their free key is absent.
- **No per-edition dollar figure is published here**, because this repository carries no price list
  and an invented number would be worse than the instruction for getting a real one.
