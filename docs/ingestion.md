# Ingestion (Part 1)

The entry point of the Mini WSA pipeline: receive security events (DLRs) over
REST, reject anything that isn't trustworthy, and hand clean events on to the
rest of the system. This document describes everything built in the ingestion
step — architecture, request flow, components, design decisions, and how to run
and test it.

> Pipeline position: **ingest → (enrich) → (store)**. Validation is complete;
> enrichment and the wiring of storage into the flow come in later steps.

---

## Endpoint

```
POST /v1/events/ingest
Content-Type: application/json
```

- Accepts a single security event (batch/array support is a later step).
- Returns **`201 Created`** with the accepted event on success.
- Returns **`400 Bad Request`** with a consistent error body on any invalid input.

OpenAPI/Swagger is served alongside:

| Endpoint | Purpose |
|---|---|
| `GET /swagger-ui/index.html` | Interactive API UI |
| `GET /v3/api-docs` | OpenAPI 3 spec (JSON) — includes the `SecurityEvent` schema and enum values |
| `GET /v3/api-docs.yaml` | Same spec in YAML |

---

## Architecture

Dependencies point **downward** only (transport → core → storage; everyone →
domain). The core never imports the web layer; storage is reached only through
its interface.

```
┌─────────────────────────────────────────────────────────────┐
│  TRANSPORT (web)                                              │
│  EventIngestionController   @RestController  POST /v1/events/…│
│  IngestionExceptionHandler  @RestControllerAdvice → 400s      │
│  ErrorResponse, ValidationError   (the HTTP error contract)   │
├─────────────────────────────────────────────────────────────┤
│  APPLICATION / CORE                                           │
│  EventIngestionService.ingest(...)   ← validation lives HERE  │
│  InvalidEventException                                        │
├─────────────────────────────────────────────────────────────┤
│  STORAGE (seam)                                               │
│  EventRepository (interface)                                  │
│  InMemoryEventRepository (@Repository, ConcurrentHashMap)     │
├─────────────────────────────────────────────────────────────┤
│  DOMAIN (cross-cutting, depended on by all layers)           │
│  SecurityEvent, Rule, GeoLocation   (immutable records)       │
│  RuleCategory, Severity, Action     (enums)                   │
└─────────────────────────────────────────────────────────────┘

Bootstrap: MiniWsaApplication (@SpringBootApplication)
```

---

## Request flow

```
HTTP JSON
   │
   ▼
[1] EventIngestionController.ingest(@RequestBody SecurityEvent)
   │        Jackson deserializes JSON → typed SecurityEvent
   │        (enums reject unknown values at parse time)
   │
   ├─ parse fails ──► HttpMessageNotReadableException
   │                     └─► IngestionExceptionHandler.handleUnreadable
   │                           └─► 400 {errors:[{field,message}]}   ① transport
   ▼
[2] ingestionService.ingest(event)
   │        Validator.validate(event) against @NotBlank/@NotNull
   │
   ├─ violations ──► InvalidEventException(List<ValidationError>)
   │                     └─► IngestionExceptionHandler.handleInvalidEvent
   │                           └─► 400 {errors:[...]}               ② semantic
   ▼
[3] valid → service returns the event   (today: `return event;`)
   │
   ▼
[4] Controller → 201 Created, body = the event
```

### Two validation gates, one error shape

| Gate | Where | Catches | Why there |
|---|---|---|---|
| ① Transport | `IngestionExceptionHandler` | unknown enum, bad timestamp, malformed JSON | Jackson fails to build the object *before* the core runs, so this must live at the boundary |
| ② Semantic | `EventIngestionService.ingest` | missing/blank required fields, structural rules | Lives in the **core** so every entry path (REST now; data generator / queue later) is validated — not just HTTP |

Both converge on the same body via the `@RestControllerAdvice`:

```json
{ "errors": [ { "field": "rule.category", "message": "must be one of [INJECTION, XSS, ...]" } ] }
```

---

## Components

### Domain (`com.akamai.miniwsa.domain`)
Immutable records + enums. Modeling the closed value sets as enums means an
unknown value fails to deserialize — the schema is partly enforced by parsing.

- `SecurityEvent` — the DLR (top-level record); carries Bean Validation
  constraints (`@NotBlank`/`@NotNull`) on required fields, cascading into `Rule`.
- `Rule`, `GeoLocation` — nested records.
- `RuleCategory`, `Severity`, `Action` — the three closed enums.

### Core (`com.akamai.miniwsa.ingest`)
- `EventIngestionService` — the single ingestion operation, `ingest(...)`. Runs
  the `Validator` programmatically; throws `InvalidEventException` on violations;
  returns the event on success. **This is the trust boundary.** Validation lives
  here (not on the controller) so any caller is covered.
- `InvalidEventException` — carries `List<ValidationError>`; the event-level
  "rejected" signal.

### Transport (`com.akamai.miniwsa.ingest`)
- `EventIngestionController` — thin `@RestController`; deserializes and delegates
  to `ingest(...)`, returns `201`.
- `IngestionExceptionHandler` — `@RestControllerAdvice` mapping both failure
  types to `400` under one shape.
- `ErrorResponse` — `{ errors: List<ValidationError> }`, the JSON body of a `400`.
- `ValidationError` — a single `(field, message)` datum; library-agnostic
  (decoupled from Hibernate Validator / Jackson internal types).

### Storage (`com.akamai.miniwsa.storage`)
- `EventRepository` — the swappable storage **seam** (interface): `save`, `findAll`.
- `InMemoryEventRepository` — `@Repository` backed by a
  `ConcurrentHashMap<String, SecurityEvent>` keyed by `eventId` (thread-safe;
  de-duplicates re-sent events).
- **Built and tested, but not yet wired into `ingest`** — intentionally deferred
  until enrichment defines the final stored shape.

### Bootstrap
- `MiniWsaApplication` — `@SpringBootApplication` entry point.

---

## Design decisions

- **Typed, immutable domain** (records + enums): deserialization alone enforces
  closed value sets; no half-built mutable events can exist.
- **Zero-trust validation in the core, not the controller**: the controller is a
  thin adapter; `ingest(...)` is the one trust boundary every path flows through.
- **Unified error contract** (`ErrorResponse`/`ValidationError`): one shape for
  every `400`, whether semantic or transport, decoupled from framework types.
- **Storage behind an interface**: in-memory today, a columnar/search store at
  real WSA scale, with no change above the seam.
- **All violations reported at once**: an invalid event returns *every* problem
  in one `400`, not the first one only.

---

## Examples

**Valid → `201`** (event echoed back):

```bash
curl -i -X POST http://localhost:8080/v1/events/ingest \
  -H 'Content-Type: application/json' \
  -d '{
    "eventId":"evt-00132","timestamp":"2026-05-20T14:32:10Z","configId":14227,
    "policyId":"pol_web1","clientIp":"203.0.113.42","hostname":"www.example.com",
    "path":"/api/v1/login","method":"POST","statusCode":403,"userAgent":"Mozilla/5.0",
    "rule":{"id":"950001","name":"SQL_INJECTION","message":"SQLi","severity":"CRITICAL","category":"INJECTION"},
    "action":"DENY","geoLocation":{"country":"CN","city":"Beijing"},
    "requestSize":1024,"responseSize":256
  }'
```

**Semantic failure → `400`** (blank `eventId`, missing `clientIp`):

```json
{ "errors": [
  { "field": "clientIp", "message": "must not be blank" },
  { "field": "eventId",  "message": "must not be blank" }
] }
```

**Transport failures → `400`** (same shape):

| Input | Body |
|---|---|
| `rule.category: "BANANA"` | `{"errors":[{"field":"rule.category","message":"must be one of [INJECTION, XSS, PROTOCOL_VIOLATION, DATA_LEAKAGE, BOT, DOS, RATE_LIMIT]"}]}` |
| `timestamp: "not-a-date"` | `{"errors":[{"field":"timestamp","message":"invalid value for Instant"}]}` |
| truncated JSON | `{"errors":[{"field":"body","message":"malformed JSON request body"}]}` |

---

## Tests

| Test | Covers |
|---|---|
| `SecurityEventDeserializationTest` | sample DLR JSON → typed model (enums mapped) |
| `EventIngestionServiceTest` | core validation: missing required fields rejected |
| `EventIngestionControllerTest` | API end-to-end: `201`; semantic `400`; bad-enum `400`; malformed-JSON `400` |
| `InMemoryEventRepositoryTest` | store: empty on creation; save then read back |

---

## Run

```bash
# Local (needs JDK 21 + the Maven wrapper)
./mvnw spring-boot:run

# Containerized (needs only Docker; --build picks up latest code)
docker compose up --build
```

Then open <http://localhost:8080/swagger-ui/index.html>.

---

## Not yet done (later steps)

- Wire `EventRepository.save(...)` into `ingest` so events are persisted.
- Batch ingestion (accept an array of events).
- Server-side `receivedAt` timestamp on ingestion.
- Enrichment: `attackType`, `threatScore` (Part 2).
