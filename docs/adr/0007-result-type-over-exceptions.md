# ADR-0007: Result Type over Exceptions

## Status

Accepted

## Context

Use cases need explicit, typed error handling. Exceptions are still useful at infrastructure boundaries, but application
flows should make expected failures visible in method signatures.

## Decision

Use `Result<T, E extends DomainError>` for application use case results. Module errors are flat sealed interfaces with
record variants.

## Consequences

Callers must handle success and failure explicitly. Expected business failures do not rely on exception control flow.
Infrastructure exceptions are translated by application services into module errors or job failure states.
