# Building PulseDigest

An account of the decisions, written for someone deciding whether the code is worth reading. The
[ADRs](adr/README.md) are the raw material; this is the version with the mistakes left in.

PulseDigest is a batch job that reads sixteen sources twice a week, asks a language model to rank
what it found, and emails one person a digest. One reader. That constraint is the reason for most of
what follows.

## Why a batch and not a service

The obvious shape is a service: an API, a scheduler, a queue, something you can point a dashboard at.
This is a `main` that runs, does one thing, and exits — and the exit code is the entire status API.
Zero means an edition was delivered; anything else means it was not.

That is not minimalism for its own sake. A service has uptime, and uptime for one reader twice a week
is a liability: something to monitor, to restart, to patch between editions, and to be woken by. A
batch has no uptime to lose. GitHub Actions is the scheduler, the process is the unit of work, and
the only question anyone can ask — "did Thursday's edition go out?" — is answered by a number the
operating system already provides.

It also made the hardest part honest. A pipeline that runs for ninety seconds and exits cannot hide
state between runs. Everything one run needs to know about the last one has to be *written down*,
which forced the stored edition to be complete rather than convenient — and the day a quarterly
retrospective was wanted, it could be built from editions already in the database because there was
nowhere else for that information to have gone.

## Why `Result<T, E>` and not exceptions

Java gives you two bad options for an expected failure. Checked exceptions put it in the signature
and then make every caller carry it whether or not they can act; unchecked ones take it out of the
signature entirely, so the caller cannot see it at all.

The use cases return `Result<T, E extends DomainError>`, a sealed type, and the compiler enforces
exhaustiveness. Adding a failure case breaks every `switch` that has not handled it — which is the
point. ([ADR-0007](adr/0007-result-type-over-exceptions.md).)

Genuinely exceptional things still throw. A socket dying is not a domain outcome.

The sharpest evidence that this was a real distinction and not a style preference came from deleting
things. When two query services were removed for having no callers, the module's error interface was
left with no permitted subtypes — and Java rejects a sealed interface with nothing permitted. The
compiler had noticed something true: the module's only use case was returning a `Result` whose
failure side could not be constructed. The type was documenting a possibility that did not exist.

## Why the model is a ranking function and not an author

The model writes summaries and picks an editorial line. It does **not** decide what is important.

Rank is computed outside it, from corroboration: a story reaches 🔴 CRITICAL when three independent
*kinds* of source carry it, and not before. Score nudges the order within a rank; it cannot promote
anything across one. That inversion came from a finding — the badge had been a popularity
measurement wearing the word "critical", which meant the loudest source could manufacture one.

Treating the model as untrusted has a security half too. Every item in the prompt is attacker
-controllable text: anyone can publish a blog post or name a repository `ignore all previous
instructions`. The system prompt's first block says so explicitly and names the concrete attacks,
the triage call sends indices rather than URLs so the model cannot introduce a link, and everything
rendered is escaped. [`docs/security.md`](security.md) also records the gap this has not closed,
which seems more useful than a page claiming there isn't one.

## What the feedback loop taught

The mail carries 👍 / 👎 links pointing at a receiver outside this repository, which writes rows the
next run reads. Two things that were not obvious:

**A vote has to be visible to be cast twice.** The first version moved source weights silently. A
reader who cannot see their click do anything stops clicking, and a learning loop with no input is
just a database. The footer now names every source whose weight the reader's votes moved, with the
before and after.

**Two buttons made the data wrong.** A reader who merely wanted to keep an item for later clicked 👍,
because it was the only button that did not punish — and every one of those clicks entered the
scorer as a preference nobody had expressed. Adding a third action, *zapisz*, was not a convenience
feature. It was a data-quality fix, and it required noticing that a bookmark had been silently
counted as an endorsement for months.

The arithmetic had a trap waiting for exactly that change: `CASE vote WHEN 'UP' THEN 1 ELSE -1`. The
day a third value shipped, every bookmark would have become a down-vote against its source.

## What was deleted

More than was added, in places, and the deletions were the better work.

A tech-demand pulse that nobody read. Two query services with no callers. Fifteen job-lifecycle
predicates left over from an HTTP era the project never had. Spring AI — declared, never imported,
and quietly holding `spring-web` on the compile classpath, which only became visible when removing
it broke twenty adapters at once.

And eleven of thirteen convenience constructors on the domain records. The audit's phrasing was that
they "widen the production API"; the worse problem was that they let tests assert on shapes the
pipeline cannot produce — a `Signal` with no breakdown, a `ResearchResult` with no fetch reports. A
test passing against an object no run will ever build proves nothing at all.

## The mistakes worth naming

A section on what went wrong, because an account without one is marketing.

**The end-of-life table reported on itself.** A feature that warns when part of your stack loses
support. The dates were fetched from endoflife.date rather than written from memory — and the first
thing it found was that this project's own toolchain, Eclipse Temurin 26, had gone end-of-life four
weeks earlier, along with the Oracle JDK that CI runs. The feature's first act was to indict the
thing that built it.

**A cost ceiling that would have been decoration.** The obvious implementation caps dollars. But the
price list is deliberately absent from this repository — prices change, and a stale number would be
a confident lie — so a dollars-only ceiling would have been inert in exactly the installation that
switched it on. Tokens are metered unconditionally, so the token ceiling is the one that guards
anything.

**A screenshot test that guarded nothing, twice.** A visual regression test renders the digest at
phone width and diffs it. Version one never ran: the baseline image was not a declared Gradle input,
so changing it left the test task up to date and the build green having executed nothing. Version
two ran but captured 2400 pixels of a 5664-pixel page, so every table — the most fragile part of the
layout, and the reason the test exists — sat below the frame. Both were found by deliberately
breaking a layout to see whether the test would notice, which turns out to be the only way to find
out.

**A grading routine that flattered itself.** A quarterly scoreboard grades the 🔴 badge against what
happened next. Matching stories on the existing correlation key looked right and was badly wrong:
that key falls back to the item's *category* when the model gave no topic key, and almost every
edition carries something in "Java/JVM". The scoreboard would have reported a hit rate near 100%
regardless of the truth. A test caught it, which was lucky; the lesson is that a routine grading its
own system needs its matching logic checked harder than anything else in the build.

**A regex that reformatted seventy files.** A one-character fix for a style violation used `\s+`,
which matches newlines, and joined wrapped lines across the entire test tree. Recovery was only
possible because the damage was mechanical and provable: re-applying the transformation to the
committed version and comparing identified exactly which files had no other changes.

**A security note that invented a hole.** [`docs/security.md`](security.md) listed, as a known and
unimplemented gap, the one control that was already there: items whose URL was not in the prompt
payload are dropped on every synthesis call. The reading that produced the note followed the adapter
and stopped at the line that delegates to the domain, and the grep that was supposed to catch the
mistake looked for the method's name in the tests — where it does not appear, because the test that
covers it is named after the behaviour. So two checks agreed with each other and both were wrong.
Fixing it took no production code: a unit test on the domain method, verified red by inverting the
matching branch. The correction is left in `security.md` rather than edited out, because a document
that claims a hole it does not have is a failure of the same kind as missing one, and worth leaving
visible.

**A gate that could be skipped by editing the file it guards.** `ConfigurationDocsTest` was written
three days ago to catch exactly one thing: a configuration key documented nowhere, or documented and
then removed. It had two independent holes. The stale-key half compared the table's literal text,
which omits the `report.` prefix on almost every row, against keys that all carry it — so it examined
almost nothing. And `docs/configuration.md` was not a declared Gradle input, so editing it left the
test task up to date and the check did not run at all. Both were found the same way as the mobile
baseline bug before them: by making the violation on purpose and watching the build stay green.
`CLAUDE.md` meanwhile asserted that a removed-but-documented key "też wywala build". It did not.

**A link checker that trusted the author's disk.** A CI step walks every tracked Markdown file and
reports relative links pointing at nothing — README is the router into `/docs/`, and a dead link
there is a public defect no Java test can see. It resolved each target with `[ -e ]`, against the
filesystem of whatever machine happened to run it. `docs/concepts/` is local and ignored by design,
so it exists on the author's disk and in no checkout anywhere; this document linked to it, in the
same sentence that calls it untracked. The check was therefore green on the machine that wrote the
link, and red in CI — which sees it only after the push that publishes it. Two mistakes of one kind:
a gate that runs only in CI reports a push too late, and a gate that asks the disk is not examining
the artifact. It is now `scripts/check-markdown-links.sh`, runs locally, and resolves against the
files git tracks — a target present here but untracked is reported dead, and says so. The
replacement was checked by planting the link again and watching the old logic stay green while the
new one failed, because a gate nobody has seen fail is not yet a gate.

## What is still wrong

`docs/concepts/` is local and untracked, so the backlog is not here. What is public:

- **`GenerateMarketReportProcessor` is at 488 of the 500 lines Checkstyle allows**, with three
  pre-synthesis aborts in it. The next guard added there does not fit, so the choice — extract the
  aborts into one collaborator, or split the class — is better made now than when something urgent
  needs the room.
- **The Bluesky roster needs watching over a few editions** rather than trusting one afternoon's
  measurement, and the two breadth floors and the thin-week threshold are all ceiling figures waiting
  on the same yield data.
- **Two features ship enabled but not enforcing** (semantic clustering, self-critique), which is a
  decision with an expiry date: a shadow mode nobody ever promotes is just a log file.
- **Adding an eighteenth component to `ReportData` still touches three places**, all inside that one
  file, because a convenience constructor's `this(...)` delegation cannot go through the builder. The
  compiler catches it on arity.

Until 2026-10-19 the first item here read "`ReportData` has grown to seventeen components with
seventeen copy-methods each restating the full argument list". That was true when it was written and
stopped being true the same day, which is the whole hazard this document keeps describing: a claim
nobody re-derives from the code.

The receiver's source was in this list until 2026-10-19 and is now in [`receiver/`](../receiver/README.md).

Until 2026-10-19 this paragraph also said the synthesis response's URLs were not checked against the
set that was sent. They always were. See below.

---

*Every claim here is checkable against the code and the tests. Where it says a test caught something,
the test is in the repository with a comment saying what it caught.*
