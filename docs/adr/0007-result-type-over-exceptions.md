# ADR-0007 — `Result<T, E>` over exceptions in use cases

- **Status:** Accepted
- **Context:** A use case has two kinds of failure. One is expected and part of the domain ("no
  report has been generated yet"). The other is not ("the socket died"). Java's checked exceptions
  conflate them, and unchecked ones make the expected failures invisible in the signature.

## Decision

Application-layer use cases return `Result<T, E extends DomainError>` — a sealed interface permitting
`Result.Success` and `Result.Failure`. They **never** throw checked exceptions and never return
`Optional` to signal a domain failure. Each module defines exactly one sealed `{Module}Error`
interface whose cases are flat `record`s; no inheritance chains.

```java
return switch (service.handle(query)) {
    case Result.Success(var report) -> process(report);
    case Result.Failure(MarketIntelError.JobNotFound e) -> handleNotFound(e);
    case Result.Failure(var error) -> handleGenericError(error);
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
