# ADR-0002 — Hexagonal Architecture (Ports & Adapters)

- **Status:** Accepted
- **Context:** PulseDigest is a headless batch that pulls from sixteen volatile external sources
  (X, Hacker News, GitHub, arXiv, scraped AI-lab blogs, an LLM, Postgres, an email provider). Those
  are exactly the parts most likely to change, break, or be replaced; the scoring and correlation
  rules are the part worth keeping.

## Decision

Every business capability lives in `modules/{context}/`, split into three layers with the dependency
arrow pointing inward only: `infrastructure → application → domain`.

- `domain/` — pure Java. Value objects are `record`s. **No Spring annotations.** Output ports are
  interfaces under `domain/port/out/`.
- `application/` — use cases, Spring `@Service` beans, one sealed `{Module}Error` per module.
- `infrastructure/` — the adapters that implement the ports, plus module `@Configuration`.

## Consequences

Swapping OpenAI for a local model, or Resend for SES, touches one adapter and no domain code. The
scoring rules (`SignalScoringService`, `TrendMemory`, `TrendVelocityService`, `WeeklyRecapService`)
are unit-testable without a Spring context or a container, which is why the suite runs in seconds.

The cost is real: sixteen sources means sixteen adapters, and adding a seventeenth is O(N) wiring
across the port, the adapter, `ResearchResult`, `SourceWeights`, `SourceDomain` and the intake caps.
That friction is deliberate — it is why the strategy backlog says "measure before you extend".

Infrastructure-bound `@ConfigurationProperties` records must not leak into `application/`. They are
translated into narrow policy value objects (`FeedbackNudgePolicy`, `ReportHistoryPolicy`) in
`MarketIntelBootstrap`.

## Enforcement

`ArchitectureRulesTest` (ArchUnit) fails the build on a violated layer direction, on a Spring
annotation inside `domain/`, and on an application class importing infrastructure configuration.
The rule is not a convention anyone has to remember.
