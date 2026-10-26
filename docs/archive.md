# The public archive

Every edition, republished as a static site: an index, one page per edition, an Atom feed and a
client-side search. Built by `--publish-archive` and deployed to GitHub Pages by the `archive` job.

This is the only irreversible thing the project does, so most of this page is about what the
published copy is **not**.

## What is removed, and why

The delivered edition is personal in ways that are easy to underestimate. It knows what the reader
up-voted, what they bookmarked, which libraries they pin, what the model has inferred about them —
and it carries one signed URL per item. [`PublicEditionRenderer`](../src/main/java/pl/seniordeveloper/pulsedigest/modules/market_intel/infrastructure/adapter/out/archive/PublicEditionRenderer.java)
hands the ordinary email builder a deliberately emptied report:

| Removed | Because |
|---------|---------|
| **Every feedback link** | The HMAC covers values printed beside it. A signed link in a public archive is a forgeable vote handed to everyone who reads the page |
| Reader model, calibration, "Twoje głosy" | Statements about a person, not about the week |
| Saved shelf | One person's bookmarks |
| Dependency radar, stack markers, end-of-life notices | All three describe the reader's own stack — not the public's business, and a small reconnaissance gift besides |
| Score explanations | They print the reader's voting history inline |
| The footer's intake counts | Not private, simply unknowable: editions are read back from storage, which never kept the research that produced those numbers. A footer confidently reporting "0 źródeł" is worse than one that omits the line |

The links disappear because the renderer uses a **blank receiver URL** — the documented degradation
for an unconfigured receiver, so the published path is the same code every unconfigured run already
takes, not a special case invented for publishing.

**Depersonalising is done on the report, never on the markup.** A regex over rendered HTML is a
guess; emptying the report is a fact. `PublicEditionRendererTest` asserts the mechanism as well as
the output, so a future "just filter the output" refactor fails there.

## Three layers of the same check

1. `PublicEditionRendererTest` renders a **fully populated** edition — reader profile, calibration,
   saved shelf, pinned dependencies, stack markers — because a renderer that strips nothing passes
   trivially against a fixture that contains nothing.
2. `ArchiveSiteFileAdapterTest` walks **every generated file** looking for `sig=`, `vote=`,
   `edition=` or the receiver URL. The site has five file types and a leak only has to happen in one.
3. The workflow greps `build/archive/` before uploading and **fails the deployment**, not the build.

The third caught nothing the first two missed, which is the point of having it.

## What is published

- `index.html` — every edition, newest first, with its preview line.
- `<YYYY-MM-DD>.html` — the edition, depersonalised, with a link back.
- `feed.xml` — Atom, one entry per edition. Not per item: an entry per story would turn a
  twice-weekly feed into a firehose and lose the one thing the digest adds, which is the selection.
- `search-index.json` + `search.html` — a flat row per item, filtered in the browser. No service to
  keep up, no dependency, no build step.
- `.nojekyll` — Pages would otherwise run the output through Jekyll.

## How it runs

```bash
java -jar build/libs/app.jar --publish-archive
```

Rebuilds **the whole site** from stored editions over `report.retrospective.lookback-days`, into
`report.archive.output-path`. A full rebuild rather than appending today's page: the site is then a
pure function of what is in the database, so a bad deployment is fixed by running it again.

Exit `1` on an empty archive — no stored editions means the database could not be read, and
publishing an empty site over a good one is the worst available outcome.

In CI the `archive` job runs **after** the digest with `if: success()`, so a failed or skipped
edition never republishes. It is a separate job with its own `pages: write` permission, and it is
deliberately **not given the feedback signing secret**: a job that cannot sign a link cannot leak a
signed one, even if the renderer changed.

**Once per repository, by hand:** Settings → Pages → Build and deployment → Source: **GitHub
Actions**. Until then `actions/configure-pages` fails with `Get Pages site failed … Not Found`: the
archive builds and passes the feedback-link guard, nothing is deployed, and the run is red although
the digest was delivered. The workflow cannot do this itself — the action's `enablement` input
needs a token other than `GITHUB_TOKEN`, and a long-lived admin token for a one-time switch is a
worse trade than a click.

## What is still true of the published pages

They are Polish. [ADR-0001](adr/0001-polish-as-the-product-language.md) explains why that is a
decision rather than an accident, and [forking.md](forking.md) is honest that changing it is the one
thing a fork cannot do by configuration.
