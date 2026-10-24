# Storage engine choice — reasoning (personal hint)

Ranking for *this* workload — append-only time-series security events, read mainly
by time-range aggregations (Stats) and filtered retrieval (Samples), at big-data scale.

Framing note: "SQL vs NoSQL" is the wrong first axis. The workload decides, and the
best fits here are columnar engines that happen to speak SQL. The real axis is
OLTP row-store vs OLAP columnar vs search engine.

## Actual access patterns

| Pattern | Source | Shape |
|---|---|---|
| Heavy append writes | ingestion (millions of DLRs) | write-intensive, immutable events, never updated |
| Time-range aggregations | Stats (counts by category/action, avg score, top IPs, top paths) | GROUP BY over a configId + time window |
| Filtered, paginated retrieval | Samples (configId/time/category/action, sort by time desc, limit/offset) | range scan + filters |
| Count-in-window | repeat-offender, time-series bonus | time-bucketed counts, often per key (IP) |

→ write-heavy, append-only, time-partitioned, read by analytical aggregation and
filtered scans. Textbook log/event-analytics; not transactional.

## What do we need from our engine?

Derived from the access patterns above — roughly in priority order for *this* system:

- **R1 — High write throughput (append-only).** Ingest millions of immutable DLRs; writes dominate, no updates.
- **R2 — Fast time-range aggregations.** Stats API: `count`, `avg(threatScore)`, `GROUP BY category/action`, top-N by IP/path over a configId + time window.
- **R3 — Filtered retrieval + pagination + sort.** Samples API: filter by configId/time/category/action, sort by time desc, `limit`/`offset`.
- **R4 — Time-bucketing.** Repeat-offender window counts; the time-series bonus (counts per 1m/5m/1h bucket).
- **R5 — Horizontal scale to big-data volumes.** Billions of events, distributed/sharded.
- **R6 — Schema enforcement.** The DLR schema is stable and strongly typed; we want the store to reject malformed shapes (a second integrity guard atop zero-trust validation).
- **R7 — Operational simplicity / cost / familiarity.** How hard and expensive to run and operate.
- **R8 — Ad-hoc / full-text search (secondary).** Investigation over path/userAgent. Useful but *underused* by the required endpoints (they're exact-match filters + fixed aggregations).
- **R9 — Retention / TTL.** Security events age out; need cheap time-based expiry.

Priority for this system: **R1, R2, R4, R5, R6 are primary** (ingest + aggregate + scale + schema); R3 matters but is broadly satisfied; R8 is a future/nice-to-have.

## Scorecard — engines vs our requirements

Legend: ✅ strong · ◐ adequate · ⚠️ weak

| Requirement | ClickHouse | Elasticsearch | Postgres (+Timescale) |
|---|:---:|:---:|:---:|
| R1 Write throughput | ✅ | ◐ | ◐ |
| R2 Time-range aggregations | ✅ | ✅ | ◐ → ⚠️ at scale |
| R3 Filtered retrieval + pagination | ◐ | ✅ | ✅ |
| R4 Time-bucketing | ✅ | ✅ | ◐ (✅ w/ Timescale) |
| R5 Horizontal scale (big data) | ✅ | ✅ | ⚠️ |
| R6 Schema enforcement | ✅ | ⚠️ (dynamic) | ✅ |
| R7 Ops simplicity / cost | ◐ | ⚠️ | ✅ |
| R8 Ad-hoc / full-text search | ⚠️ | ✅ | ◐ |
| R9 Retention / TTL | ✅ | ✅ | ◐ |

Read-out: **ClickHouse is ✅ on the primary reqs (R1/R2/R4/R5/R6)**; its ◐/⚠️ fall on R3 (offset
pagination), R7 (ops) and R8 (search) — the least-weighted for our endpoints. **Elasticsearch**
trades R1/R6/R7 to win R8. **Postgres** wins R7 but is weakest on R2/R5 — exactly where our
workload is heaviest.

## Trade-offs per engine

### ClickHouse
- **Pros:** top-tier append ingestion (R1); columnar aggregations (R2) and time-bucketing /
  materialized views (R4); scales horizontally (R5); strict schema-on-write (R6); native TTL (R9); speaks SQL.
- **Cons:** not a search engine — no inverted index/relevance (R8); offset pagination awkward at
  huge scale (R3); weak at updates/deletes (fine — we're append-only); smaller ecosystem and more
  ops than Postgres (R7); some eventual-consistency quirks.

### Elasticsearch / OpenSearch
- **Pros:** best filtered retrieval + full-text/relevance (R3/R8); strong aggregations incl.
  `date_histogram` (R2/R4); horizontal scale (R5); ILM for retention (R9); de-facto SOC/SIEM store.
- **Cons:** heavier, resource-hungry ingestion + JVM/cluster ops and cost (R1/R7); **dynamic
  mapping** by default → mapping-explosion / type-conflict risk unless `strict` (R6); deep
  pagination needs `search_after` (offset is problematic); aggregations less efficient than columnar at the extreme.

### PostgreSQL (+ TimescaleDB)
- **Pros:** simplest, cheapest, most familiar, great local dev (R7); excellent filtered retrieval +
  keyset pagination (R3); strict relational schema (R6); Timescale adds `time_bucket` + continuous
  aggregates (R4) and retention policies (R9); transactional if ever needed.
- **Cons:** row store — aggregations degrade at billions (R2); write throughput below the
  columnar/distributed options (R1); horizontal scale is bolt-on (Citus), not native (R5);
  full-text exists (`tsvector`) but it's not a search engine (R8).

## #1 — ClickHouse (the pick)

The heart of the system is the Stats API — counts/averages, GROUP BY category/action,
top-N by IP and path, over a configId + time window. Pure columnar-OLAP aggregation,
which is exactly ClickHouse's design center:
- Columnar storage → `GROUP BY category, avg(threatScore)` scans only those columns, not whole rows.
- Ingestion throughput top-tier (millions of rows/sec, append-optimized) — matches "millions of DLRs".
- Time-partitioning + materialized views pre-aggregate rollups → directly serves the time-series/dashboard bonus.
- Speaks SQL → approachable, queries read clearly.
- Append-only immutable events = its comfort zone (CH is weak at updates/deletes, which we never do).

Cost: not a search engine (no full-text relevance on path/userAgent), offset pagination
less graceful than cursor-based, smaller ecosystem than ES.

## #2 — Elasticsearch / OpenSearch

Why close: de-facto SOC/SIEM store (ELK), most "security-realistic". Superb at filtered
retrieval + aggregations + full-text, scales horizontally, shines for ad-hoc investigation.

Why #2 not #1: the specified endpoints lean on exact-match filters + fixed aggregations,
not free-text search — so ES's biggest advantage (inverted index / relevance) is underused,
while its downsides bite: heavier, resource-hungry ingestion, JVM ops burden, aggregations
strong but less efficient than columnar at the extreme. Overtakes ClickHouse the moment the
product is driven by ad-hoc search/investigation rather than pre-defined dashboards.

## #3 — PostgreSQL (+ TimescaleDB / partitioning)

Why: simplest, most universal — flexible SQL, trivial local dev, transactional if ever needed;
TimescaleDB adds time-partitioning + continuous aggregates that cover a lot of this workload.

Why #3: row store — aggregations over billions of events don't scale as gracefully, so it
becomes the bottleneck as the primary analytics store at true big-data scale. Actually the
right pick at low-to-moderate scale, or if operational simplicity/relational flexibility
matter more than peak analytical throughput. For a take-home that had to run a real DB,
Postgres would be the pragmatic choice; for the "big-data scale" justification it ranks third.

## Schema model — another point for ClickHouse

Does the engine enforce a schema (reject a made-up field)? This matters because our DLR
event schema is well-defined and stable, and our domain model is already strongly typed
(records + enums).

- **ClickHouse / Postgres — schema-on-write (strict).** Tables have explicit typed columns;
  inserting an undefined field is rejected ("No such column"). (ClickHouse has
  `input_format_skip_unknown_fields=1` to *skip* unknown keys, but skipped ≠ stored.) Adding
  a field is a deliberate `ALTER TABLE` migration.
- **Elasticsearch — dynamic mapping by default:** a new field is auto-mapped and stored (set
  `dynamic: strict` to reject). More forgiving, but risks mapping explosions / type conflicts.
- **MongoDB — schemaless by default:** any field goes in (optional JSON-schema validation).

Why this favors ClickHouse for us: schema-on-write makes the **storage layer a second integrity
guard** on top of our zero-trust validation — the Java type is effectively the table schema.
Trade-off to acknowledge: evolving the schema is an explicit migration (`ALTER TABLE`) rather
than "just send the new field". For stable, high-volume analytics data that's a feature
(predictable types, efficient columnar storage); for rapidly-changing/ad-hoc data it'd be friction.

Escape hatches in ClickHouse if some flexibility is needed: a `Map(String, String)` column for
arbitrary key-values, or the newer `JSON`/`Dynamic` column types for semi-structured subfields —
without giving up typed columns for the known fields.

## One-line README summary

Workload is append-heavy analytical time-series, so the choice is columnar/search, not OLTP —
ClickHouse primary (aggregation horsepower), Elasticsearch if investigation/search dominates,
Postgres/Timescale at modest scale; in-memory today behind `EventRepository` so the engine is swappable.

Note: none of this changes the current code — it's the justification narrative, and the
EventRepository seam keeps the engine swappable.
