# ADR-0006: Virtual Threads over Reactive

## Status

Accepted

## Context

PulseDigest performs many outbound HTTP calls during a short-lived batch run. The application does not expose HTTP endpoints
and does not need reactive backpressure across user traffic.

## Decision

Use blocking I/O on Java virtual threads for external data fetching. Do not use Spring WebFlux or Reactor.

## Consequences

Adapter code stays straightforward and debuggable. Parallelism is handled with `CompletableFuture` on a virtual-thread
executor, while LLM report generation stays isolated on a small fixed executor to avoid concurrent expensive calls.
