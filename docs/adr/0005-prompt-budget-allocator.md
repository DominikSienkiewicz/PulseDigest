# ADR-0005 — Per-source caps and one hard total for the prompt

- **Status:** Accepted
- **Context:** Sixteen sources feed one prompt. RSS alone can return several hundred items in a
  lookback window; Hacker News returns tens; the AI labs return a handful. Sending everything is
  neither affordable nor useful — and the failure is not "the prompt is large", it is that the
  loudest source crowds out every other one, so the digest silently becomes an RSS digest.

## Decision

`PromptItemSelector` allocates the prompt in two steps.

**A cap per source.** Each source has a fixed ceiling — RSS 30, Twitter 20, Reddit 15, Hacker News
10, GitHub 10, releases 10, Hugging Face 8, arXiv 8, Product Hunt 6, JEP 5, CNCF 5, talks 3,
advisories 3, Tech Radar 2 — and a source contributes its best items by engagement up to that
ceiling. The numbers are not fair shares. They encode what this reader wants: a quarterly Tech Radar
is capped at 2 because it is background, and generic advisories at 3 because they are background for
*this* audience, while stack-relevant ones still surface on merit through scoring.

**A hard total.** The union is then trimmed to `TOTAL_CAP = 100` by a weighted pre-score, so a
source that is within its own cap can still lose to a better item from elsewhere.

`report.pre-scoring` adds an optional third step: a gpt-4o-mini pass that rates the survivors and
keeps the best `report.pre-scoring.keep` of them, with anything scoring ≥ 90 immune to the trim.

## Consequences

The expensive call sees a bounded, source-balanced payload, and the bound is one number rather than
an emergent property of sixteen feeds.

The cost is that **an item can be dropped before the model ever judges it**, and a dropped item
leaves no trace in the digest. The source yield ledger exists because of this: it reports what each
source contributed against what it returned, which is the only way to tell a cap that is too tight
from a source that has nothing to say.

*Until 2026-10-26 a second feature answered the same cost: the 🎯 watchlist scanned
`ResearchResult.allTitles()` — every headline fetched, not the ones that survived the budget — so
"0 wzmianek" meant the sources were silent rather than that an item lost a slot. It was removed
because another application now tracks the watched technologies. Anything that needs the whole
intake again has to restore a pre-budget view of it; scanning the prompt would repeat the mistake
the watchlist was built to avoid.*

The caps are constants in `PromptItemSelector` rather than configuration, deliberately. They encode
an editorial judgement about one reader, and the yield ledger is the evidence for changing them —
a configuration key would invite tuning them without it.
