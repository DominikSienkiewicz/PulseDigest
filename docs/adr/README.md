# Architecture Decision Records

One page per decision that would otherwise be re-litigated, or re-discovered by reading the code and
guessing. A decision gets an ADR when reversing it would cost real work, when a reviewer would
reasonably ask "why not the obvious thing?", or when the obvious thing was tried and rejected.

| ADR | Decision | Status |
|-----|----------|--------|
| [0001](0001-polish-as-the-product-language.md) | Polish for the product, English for the engineering | Accepted |
| [0002](0002-hexagonal-architecture.md) | Hexagonal architecture, enforced by ArchUnit | Accepted |
| [0003](0003-jsonb-payload-with-domain-records.md) | One JSONB column, with the domain records as its schema | Accepted |
| [0004](0004-hand-rolled-openai-client.md) | A hand-rolled OpenAI client, and no Spring AI | Accepted |
| [0005](0005-prompt-budget-allocator.md) | Per-source caps and one hard total for the prompt | Accepted |
| [0006](0006-virtual-threads-over-reactive.md) | Virtual threads instead of WebFlux | Accepted |
| [0007](0007-result-type-over-exceptions.md) | `Result<T, E>` in use cases instead of exceptions | Accepted |

The sequence is contiguous and stays that way. Numbers are permanent identifiers — an ADR that is
superseded keeps its number and gains a status, rather than being renumbered or deleted, because
`CLAUDE.md`, the code and this table all point at it.
