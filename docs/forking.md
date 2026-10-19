# Running your own digest

PulseDigest is one person's digest, but nothing about it is personal to *this* person except
configuration. This page is the list of what to change, and an honest account of the one thing you
cannot change without touching code.

**Three credentials, and you can defer two of them.** OpenAI, [Resend](https://resend.com) and
[Supabase](https://supabase.com) — one paid, two with free tiers that comfortably cover a twice-weekly
run. Thirteen of the sixteen sources need no credential at all. See [costs.md](costs.md) for the
numbers. Step 0 below needs nothing; step 0½ needs only the OpenAI key.

## 0. Look before you commit to anything

```bash
./gradlew bootJar
java -jar build/libs/app.jar --dry-run --spring.profiles.active=dry-run
```

Renders a real edition from the committed sample and writes `build/digest.html`. No accounts, no
network, no mail. If you do not like what comes out, you have spent four minutes.

## 0½. Then see it on your own interests, before opening any other account

```bash
OPENAI_API_KEY=sk-... java -jar build/libs/app.jar --spring.profiles.active=preview
```

Same command after you have edited `interest-profile` in step 1, and the fastest loop there is for
tuning it: real sources, real synthesis, real layout, written to `build/digest.html`. The mail and
database adapters are no-ops under this profile, so nothing is sent and nothing is stored — you can
run it as often as you like for the price of the OpenAI call. Sections that read past editions are
empty, because there are none yet; that is what your first real run will look like too.

## 1. Say who the reader is

`application.yaml`, `interest-profile`. This is the single source of truth for "who is this for" —
it is injected into the synthesis prompt *and* drives the relevance filter, so there is nowhere else
to keep it in step.

```yaml
interest-profile:
  persona: >-
    A backend engineer working on payments in Go and Postgres, who cares about
    reliability and ignores frontend entirely.
  relevance-keywords: [go, golang, postgres, payments, idempotency, ...]
```

Everything downstream reads this. Scoring, the prompt, the tweet filter.

## 2. Say what to read

Each source is its own configuration block under `report.*`, and the ones you do not want go in one
list rather than being deleted:

```yaml
report:
  sources:
    disabled:
      - "arXiv"            # names exactly as the run log prints them
      - "Hugging Face"
  rss:
    feeds: [https://go.dev/blog/feed.atom, ...]
  reddit:
    subreddits: [golang, devops]
  github:
    language: go
```

A disabled source costs nothing: no HTTP call, no timeout budget, no prompt slot. The three
X/Twitter fetches ship disabled — see the K7 note in [configuration.md](configuration.md) and
[costs.md](costs.md) for what that credential was buying.

Two sources are optional and degrade to nothing when their free key is absent, without failing the
run: YouTube conference talks (`report.conference-talks.api-key`) and Product Hunt
(`report.product-hunt.developer-token`).

## 3. Say how to judge

`report.prompt.system` points at the system prompt. Copy
[`prompts/system-prompt.txt`](../src/main/resources/prompts/system-prompt.txt), edit the scoring
rubric to your own taste, and point at it:

```yaml
report:
  prompt:
    system: file:/absolute/path/to/my-prompt.txt
```

Keep the **security block at the top**. Every item in the prompt is attacker-controllable text, and
that block is the only thing telling the model so — see [security.md](security.md).

The prompt's SHA-256 is recorded with every edition, so when a change makes the digest better or
worse, you can tell which editions had which prompt.

## 4. Say where it goes

`.env` — `DIGEST_TO_EMAIL`, `DIGEST_FROM_EMAIL` (a domain verified with Resend), `OPENAI_API_KEY`,
and the three `SUPABASE_DB_*` values. Copy `.env.example` and fill it in.

In GitHub Actions the same names are repository secrets. `scripts/gh-secrets-check.sh` tells you
which are missing, and flags one set as a secret where the workflow reads a variable.

## 5. Say when

`.github/workflows/digest.yml`, the `cron` on the `digest` job. Mondays and Thursdays at 04:00 UTC
here. The lookback windows in `application.yaml` are tuned to the longer of the two gaps (96h), so
if you change the cadence, change those too — the comments beside them say which.

## 6. Run it

```bash
./gradlew flywayMigrate      # once, against your Supabase
./gradlew bootRun            # or let the workflow do it
```

## What you cannot change by configuration

**The digest is Polish, and that is in the code.** Section headings, badges, the footer and the
plain-text part are Polish string literals in the email builders; the prompt asks for Polish
summaries. Swapping the prompt gets you summaries in another language inside Polish chrome, which is
worse than either option.

Making the chrome translatable is a real change — roughly twenty builders, a message bundle, and a
re-record of both golden files — and it has not been done. [ADR-0001](adr/0001-polish-as-the-product-language.md)
explains why the language split is deliberate rather than accidental; it does not claim the output
language is configurable, because it is not.

If you fork this to run in English, that is the one piece of code you will write.

## What you get for free

Everything the fork does not have to think about: the feedback loop (👍 / 👎 / zapisz) with its HMAC
links and one-vote-per-edition index, the end-of-life radar, the dependency radar against your own
build files, the cost ceiling, the one-edition-per-day interlock, the missed-run watchdog, the
mobile regression test, and the golden file that will tell you the moment your prompt changes the
shape of the output.
