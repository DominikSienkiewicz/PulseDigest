# ADR-0004 — A hand-rolled OpenAI client, and no Spring AI

- **Status:** Accepted
- **Context:** Spring AI was on the classpath and used by nothing. Three adapters —
  `GptSynthesisAdapter`, `GptPreScoringAdapter`, `GptReaderProfileDistiller` — call
  `/chat/completions` through a plain `RestClient` and parse the response themselves. The audit
  asked which of the two was the decision.

## Decision

The hand-rolled client stays; the Spring AI dependency is removed.

Each adapter builds its own request body, sets `response_format: json_object`, reads `usage` out of
the response and hands it to `LlmUsageMeter`. Transport is `RestClient` from
`spring-boot-starter-restclient`.

## Consequences

Three things this project needs are visible in the request body, where a framework would have put
them behind an abstraction: **JSON mode**, which the whole parsing strategy depends on; the
**`usage` block**, which is the only source of the token and cost meters F11 and N40 are built on;
and the **per-call-site model settings**, which differ deliberately between a large warm synthesis
call and a small deterministic pre-scoring one.

The removal was not free and is worth recording precisely: `spring-web` reached the compile
classpath *only* through `spring-ai-starter-model-openai`, so deleting it broke twenty adapters that
use `RestClient`. The replacement is `spring-boot-starter-restclient`, which also drops
`spring-webflux` and Reactor — a stack [ADR-0006](0006-virtual-threads-over-reactive.md) already
rejected and which was arriving transitively anyway.

What is given up is provider portability. Switching to another model vendor means editing three
adapters rather than a configuration key. For a single-vendor batch that runs twice a week, that is
a cheaper bill than an abstraction over one implementation — and the day a second vendor appears,
the three request builders are the specification of what the abstraction would have to cover.
