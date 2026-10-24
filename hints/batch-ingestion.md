# Batch ingestion decisions (personal hint)

The ingest endpoint accepts **a single event object OR an array** on the same `POST /v1/events/ingest`.

## Single-or-array binding
- Controller takes `@RequestBody List<SecurityEvent>` + `spring.jackson.deserialization.accept-single-value-as-array=true`.
  A lone object binds as a 1-element list; an array binds as-is.
- Why this way: all request binding stays at the Jackson `@RequestBody` boundary, so our
  **transport-level error handling** (`HttpMessageNotReadableException` → `ErrorResponse`) still
  works for bad enums / malformed JSON.
- *Alt rejected:* `@RequestBody JsonNode` + manual `isArray()` branching + `treeToValue(...)` — those
  Jackson exceptions are thrown *outside* the `HttpMessageNotReadableException` path, so we'd lose
  (or have to re-implement) the clean field-level error handling.

## Response is always a JSON array
- Returns `List<EnrichedEvent>` for both cases — a single event comes back as a 1-element array.
- *Contract change:* previously a single event returned a bare object. Accepted deliberately for a
  batch-capable endpoint.
- *Alt rejected:* mirror the input shape (object→object, array→array) — impossible, because a single
  object and a 1-element array both bind to a size-1 `List` (indistinguishable after binding).

## Atomic, all-or-nothing
- The service validates **every** event first; if any is invalid it throws → `400`, and **nothing is
  stored**. Only once all pass are they enriched and saved, in order.
- Avoids partial writes (a `400` never leaves some events persisted).
- Note: within a batch, a later event's repeat-offender count sees earlier same-IP events from the
  *same batch* that were already saved — which is correct (they are events from that IP).

## Intra-batch duplicate eventId (edge case)
- A batch like `[{id:"x"}, {id:"x"}]`: both validate and are enriched/returned (response has 2), but
  storage is keyed by `eventId` so only one lands — response count can exceed stored count.
- Cross-*request* replays are deduped cleanly (idempotent upsert); intra-batch dupes aren't deduped
  in the response. Documented as a known limitation.

## Error-reporting asymmetry (known limitation)
- **Transport** errors are indexed — `[0].rule.category` — because Jackson knows the element index.
- **Semantic** errors are not — plain `eventId` — because the service validates per-event and
  fails fast without threading the batch index through.
- Fine for now; per-index semantic errors (and/or collecting *all* invalid events instead of
  fail-fast) is a future enhancement.
