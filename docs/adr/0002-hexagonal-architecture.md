# ADR-0002: Hexagonal Architecture

## Status

Accepted

## Context

PulseDigest is a headless batch application that fetches external data, calls LLMs, persists report history, and sends email.
External APIs and storage concerns change more often than the core report generation flow.

## Decision

Business modules use a ports-and-adapters layout:

- `domain` contains pure Java models and output ports.
- `application` contains use cases and orchestration.
- `infrastructure` contains Spring adapters for HTTP APIs, persistence, AI, email, and configuration.

Dependencies point inward: `domain <- application <- infrastructure`.

## Consequences

Use cases can be tested without Spring or real external services. Adapters can change independently as long as they preserve
the port contract. Architecture tests enforce the most important boundaries.
