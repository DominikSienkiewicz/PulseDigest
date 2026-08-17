# ADR-0006 — Virtual Threads over WebFlux

- **Status:** Accepted
- **Context:** The run is I/O-bound almost end to end: sixteen source fetches, one large LLM call,
  one database round-trip, one email send. It executes twice a week, from GitHub Actions, and
  exits. Concurrency is needed to fan out the fetches, not to hold thousands of connections open.

## Decision

Use Project Loom virtual threads (`Executors.newVirtualThreadPerTaskExecutor()` in `AsyncConfig`)
with plain blocking calls and `CompletableFuture.supplyAsync`. **Spring WebFlux and the reactive
stack are forbidden**, and `spring-boot-starter-webflux` is absent from `build.gradle.kts`.

## Consequences

Adapter code reads top to bottom. A stack trace names the adapter that failed instead of an operator
chain. Timeouts are `orTimeout` on a future, not a `timeout()` operator. Debugging a failed source
means reading nineteen lines of straight-line code.

The application also runs **no web server** (`spring.main.web-application-type: none`). It cannot
serve HTTP, which is why the reader-feedback loop is mediated by Supabase and an external receiver
rather than by an endpoint in this process — see the feedback section of the README.

What is given up: back-pressure semantics and the reactive operator vocabulary. Neither buys anything
for a single-shot batch with a bounded, known fan-out.

Pinning remains the one hazard to watch. Any `synchronized` block around a blocking call would pin a
carrier thread; adapters use blocking I/O without holding monitors.

## Enforcement

`ArchitectureRulesTest` (ArchUnit) carries `REACTIVE_STACK_IS_NOT_USED`: no class may depend on
`reactor..` or `org.springframework.web.reactive..`. Adding the starter back does not quietly change
the architecture — it breaks the build.
