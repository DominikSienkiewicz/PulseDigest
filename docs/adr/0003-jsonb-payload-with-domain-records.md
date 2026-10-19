# ADR-0003 — One JSONB column, and the domain records as its schema

- **Status:** Accepted
- **Context:** An edition is a tree: a report with items, signals, each signal with a score
  breakdown, each story with corroborating members. The obvious relational shape is five or six
  tables and a mapping layer. The obvious document shape is one column.

## Decision

`reports.payload` is a single `JSONB` column holding the serialized `PersistedReport`, and the
**domain records are the wire schema** — Jackson serializes them directly, with no DTO layer and no
mapper.

Queries go through Postgres' JSON operators (`payload->'report'->'items'`), supported by a
`jsonb_path_ops` GIN index. `edition_inputs` stores the run's `ResearchResult` the same way.

## Consequences

Adding a field to the digest is one change in one record. There is no mapping layer to keep in step,
which is the layer that silently rots in every project that has one.

The price is that **the domain model is a published format**. A renamed component renames a JSON
field, and every edition already stored keeps the old name. That is why:

- derived accessors carry `@JsonIgnore` — without it they serialize as fields no constructor can
  read back, which makes the payload unreadable rather than merely larger (caught twice by
  `PersistedReportPayloadTest`, which exists for this reason);
- every persisted record carries `@JsonIgnoreProperties(ignoreUnknown = true)`, so an old edition
  deserializes under a newer model instead of throwing;
- a structural change needs a deliberate compatibility decision. N48 grouped five flat count fields
  into a nested `raw_counts` object; editions written before it degrade to zero rather than failing,
  and `RawCountsTest` pins that.

There is no schema migration for the payload, because there is no schema. The compatibility
obligation moved from the database into the records, and it is only met because tests check it.
