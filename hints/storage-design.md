# Storage design decisions (personal hint)

> Engine choice (SQL vs NoSQL, ClickHouse/ES/Postgres ranking) is in `storage-engine-choice.md`.
> This file is the design around storage, independent of the engine.

## `EventRepository` interface — the swappable seam
- The app (ingestion, future analytics) depends on the **interface**, never a concrete engine.
- In-memory `ConcurrentHashMap` implementation today; a columnar/search store at real scale.
- Why: unblocks all downstream logic immediately with fast tests, keeps the engine decision
  deferred and the swap a contained change. This *is* the architecture story for the README.

## In-memory implementation
- `ConcurrentHashMap<String, EnrichedEvent>` keyed by `eventId` — thread-safe for concurrent
  ingestion (we're on Java 21 virtual threads precisely for throughput).
- Stores the **enriched** event (original + attackType/threatScore/receivedAt), not the raw event.
- Not durable (lost on restart) — acceptable for the mini version; justified in the README.

## Idempotent upsert on duplicate `eventId`
- `save` is keyed by `eventId`, so re-ingesting the same id overwrites → **one** stored record.
- Why: real edge/event pipelines deliver **at-least-once** (retries on timeout), so ingestion must
  be idempotent; replaying an event must be safe, not an error.
- Nice side effect: replays can't inflate the repeat-offender count (it's over distinct stored ids).
- Pinned by a **characterization test** (green-on-arrival) so a future keying change can't silently
  break idempotency.
- *Alt rejected:* reject duplicates with `409` (fragile under normal retry delivery); ignore (keep first).
