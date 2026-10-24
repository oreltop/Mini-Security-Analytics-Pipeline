# Stats API design decisions (personal hint)

> Full flow is in `docs/stats.md`. This file is the *why* behind Part 3 choices.

## Time range on event `timestamp` (not `receivedAt`)
- Stats answer "what happened when" for a SOC dashboard → filter on the DLR's `timestamp`.
- `receivedAt` stays for repeat-offender enrichment (server-trusted, monotonic).
- Tradeoff: client timestamps are spoofable / skewed; late arrivals can change past windows.
  Hardening options: watermark, or a `timeField` query param. Catalogued in `docs/gaps.md`.

## Aggregation in the repository (Y), not in `StatsService` (X)
- ClickHouse: `GROUP BY` / `ORDER BY` / `LIMIT` — only small aggregates cross the wire.
- In-memory: equivalent stream grouping/sorting so both backends stay in lock-step.
- Why Y: we invested in ClickHouse for analytical aggregations; pulling rows into Java would
  undercut that story in an interview. Cost: duplicate aggregation logic in both impls +
  Testcontainers coverage for the SQL path.
- *Alt rejected (X):* one pure Java aggregator over `findInRange` — simpler, but wrong scale story.

## Granular repo methods, one per summary section
- `countInRange`, `statsByCategory`, `countByAction`, `topAttackers`, `topPaths`.
- Lets each TDD cycle land independently; `StatsService` only orchestrates and shapes the DTO.
- Top-N limit is fixed at 10 in the service (assignment); not a query param.

## Inclusive `[from, to]` on event timestamp
- Both backends: `timestamp >= from AND timestamp <= to` (in-memory: `!isBefore && !isAfter`).
- Empty / inverted range → zeroed summary (no explicit `from > to` rejection — see gaps).
