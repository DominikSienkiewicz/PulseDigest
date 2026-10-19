# ADR-0006 — Virtual Threads over WebFlux

- **Status:** Accepted
- **Context:** The run is I/O-bound almost end to end: sixteen source fetches, one large LLM call,
  one database round-trip, one email send. It executes twice a week, from GitHub Actions, and
  exits. Concurrency is needed to fan out the fetches, not to hold thousands of connections open.

## Decision

Use Project Loom virtual threads (`Executors.newVirtualThreadPerTaskExecutor()` in `AsyncConfig`)
with plain blocking calls and `CompletableFuture.supplyAsync`. **Spring WebFlux and the reactive
stack are forbidden**, and neither `spring-webflux` nor `reactor-core` resolves onto the compile or
runtime classpath.

That second sentence used to read "`spring-boot-starter-webflux` is absent from
`build.gradle.kts`", which was true of that one coordinate and false of the architecture: until
2026-10-17 a declared, never-imported `spring-ai-starter-model-openai` pulled
`spring-boot-starter-webclient` and with it `spring-webflux` and `reactor-core`, so the forbidden
stack sat on the classpath of every build, importable from anywhere. Removing the unused AI starter
(and declaring the `RestClient` the adapters actually use) is what made the prohibition real.

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

Until 2026-10-17 the pipeline's own entry point contradicted that sentence: `handle` was
`synchronized` around the entire run, every blocking call included. It did not in fact pin a
carrier, because it executes on the `ApplicationRunner`'s platform thread and the fan-out futures
acquire no monitor — the safety came from the thread topology, not from the keyword. The keyword was
removed with the single-run job-lifecycle apparatus, so the paragraph above now describes the whole
codebase rather than most of it.

## Enforcement

Two gates, in this order:

1. **Absence from the dependency graph.** `spring-webflux` and `reactor-core` resolve onto neither
   `compileClasspath` nor `runtimeClasspath`, so the reactive types cannot be referenced at all.
   This is the load-bearing gate, and it is the one that was missing until 2026-10-17.
2. **`REACTIVE_STACK_IS_NOT_USED`** in `ArchitectureRulesTest` (ArchUnit): no class may depend on
   `reactor..` or `org.springframework.web.reactive..`.

The second gate alone is weaker than it looks, and it is worth being precise about why: ArchUnit
reads this project's bytecode, so it fails on reactive code that is *written*, never on a reactive
dependency that is merely *present*. For as long as the AI starter was declared, the rule passed on
every build while the forbidden stack shipped inside the fat jar. It is kept as the guard against
someone writing reactive code against a dependency added later; gate 1 is what keeps the dependency
out.
