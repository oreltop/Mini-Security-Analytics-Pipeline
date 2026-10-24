# Samples API design decisions (personal hint)

> Full flow is in `docs/samples.md`.

## Optional `from`/`to` (unlike stats)
- Assignment: all sample filters optional → null timestamp bounds mean unbounded.
- Stats keeps required `from`/`to` (forensic window). Two APIs, two contracts — intentional.

## Envelope `{ total, limit, offset, items }`
- Prefer over bare array + `X-Total-Count` so clients don't need custom headers.
- `limit` in the body is the **effective** size after clamp.

## Two repo methods: `countMatching` + `findPage`
- Same Y pattern as stats: work in the store, both backends.
- Shared optional-filter helper in ClickHouse (`appendSampleFilters`); in-memory `matches(...)`.

## Clamp `limit` to 100 (don't 400)
- Default 20 via `@RequestParam(defaultValue)`.
- Oversized values capped in `SamplesService` so ClickHouse never sees huge LIMIT.
