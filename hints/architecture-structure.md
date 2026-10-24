# Architecture & structure decisions (personal hint)

## Layered, dependencies point downward
```
TRANSPORT (controller, advice, error DTOs)
   ↓
CORE / APPLICATION (EventIngestionService — validate, enrich, store)
   ↓
STORAGE (EventRepository interface + in-memory impl)
   ↓
DOMAIN (records + enums; depended on by all layers)
```
- Dependencies only point **down**. The core never imports the web layer; storage is reached only
  through its interface.
- Why: the business logic (validation/enrichment) is independent of HTTP and of the storage engine,
  so both can change without touching the core.

## Packages by feature, not by technical layer
- `domain/`, `ingest/`, `enrichment/`, `storage/`.
- Why: related things sit together (the ingestion controller, service, exception, error DTOs are
  all in `ingest/`), easier to navigate than `controllers/`, `services/`, `models/` split.

## Thin transport adapter
- The controller deserializes and delegates to `ingest(...)`, returns `201`; it has no error-code
  logic — unhandled exceptions bubble to the `@RestControllerAdvice`. Keeps the controller a dumb pipe.

## What the design buys us (interview framing)
- Swap storage engine → implement `EventRepository`, nothing above changes.
- Add a new ingestion entry point (queue consumer, batch, generator) → reuse `ingest(...)`, get
  validation + enrichment + storage for free.
- Validation/enrichment are pure-ish and unit-tested without Spring.
