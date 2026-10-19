# ADR-0007 — `Result<T, E>` over exceptions in use cases

- **Status:** Accepted
- **Context:** A use case has two kinds of failure. One is expected and part of the domain ("the
  run finished without delivering an edition"). The other is not ("the socket died"). Java's checked
  exceptions conflate them, and unchecked ones make the expected failures invisible in the
  signature.

## Decision

Application-layer use cases return `Result<T, E extends DomainError>` — a sealed interface permitting
`Result.Success` and `Result.Failure`. They **never** throw checked exceptions and never return
`Optional` to signal a domain failure. Each module defines exactly one sealed `{Module}Error`
interface whose cases are flat `record`s; no inheritance chains.

```java
return switch (generateService.handle(command)) {
    case Result.Success(var jobId) -> deliveredExitCode(jobId);
    case Result.Failure(MarketIntelError.PipelineFailed e) -> failedExitCode(e.status(), e.detail());
    case Result.Failure(var error) -> failedExitCode(error);
};
```

## Consequences

The compiler enforces exhaustiveness: adding an error case to the sealed interface breaks every
`switch` that has not handled it. A caller cannot forget the failure path, because there is no path
that does not go through the `Result`.

Genuinely exceptional conditions still throw. `LlmSynthesisException`, `LlmQuotaException` and
`QuotaExhaustedException` are unchecked and live in `infrastructure/`; `GenerateMarketReportProcessor`
catches them at the pipeline boundary and maps them onto a terminal job status. That boundary is the
one place where the two failure vocabularies meet, and it is deliberate that there is only one.

The cost is ceremony on the happy path, and the temptation to pattern-match on `Result.Failure(var
error)` instead of on the specific case — which throws away exactly the type information this ADR
exists to preserve.

## A failure mode this ADR did not anticipate (2026-10-18)

Ceremony is not the only cost: a `Result` can also become **decorative**, and the type system will
not say so. After the single-run job-lifecycle apparatus was removed, `MarketIntelError`'s only two
cases (`JobNotFound`, `ReportNotAvailable`) belonged to two query services that no production code
ever called. Deleting those left the sealed interface empty, and *that* is what Java refused to
compile — "sealed class must have subclasses". Up to that point the module's only use case had been
returning `Result<String, MarketIntelError>` with a failure side that could not be constructed: a
signature promising a possibility that did not exist, while the caller re-queried the job tracker to
find out what had actually happened.

The fix was to make the promise true rather than to drop it. `handle` now reads the terminal job
status it already produced and returns `PipelineFailed` or `JobVanished`, so the outcome travels
with the return value and `DigestRunner` pattern-matches it into an exit code — one source of truth
instead of two, and roughly twenty lines shorter than the version that ignored the `Result`.

The lesson worth keeping: **an error interface with no reachable cases is a gate that cannot fail.**
When a module's `{Module}Error` starts shrinking, the question is not how to keep the hierarchy
non-empty, but whether the use case still reports anything the caller cannot get elsewhere.

## The decision now has a gate (2026-10-19)

Until this date nothing enforced it. `ArchitectureRulesTest` checked layer direction, a
framework-free domain and the absence of the reactive stack, and no rule mentioned `Result` — the
most-cited decision in the repository was kept alive by whoever remembered it. It now has one rule,
`useCaseHandlersReturnResult`: a method named `handle` on a `*Service` in `application.command` or
`application.query` must return `Result`.

The selector is narrow on purpose, and it guards exactly one method today. The other `*Service` classes
under `application/` are collaborators returning domain values, so a rule over every public method there
would fail at once and be wrong; the value of this one is the first use case someone adds with a raw
return type. It also refuses to pass vacuously: rename `handle` or move the service out of those
packages and the rule fails with "failed to check any classes" rather than silently stopping to guard
anything — the lesson above, applied to the gate itself.

What it checks is the signature. Whether the failure side of that `Result` is reachable is still the
question the previous section raised, and no rule answers it.
