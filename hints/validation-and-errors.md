# Validation & error handling decisions (personal hint — zero-trust)

## Validation lives in the core service, not the controller
- `EventIngestionService.ingest(...)` is the trust boundary; the controller is a thin adapter.
- Why: **every** entry path — REST now, the data generator, a future queue consumer — flows
  through the core, so none can slip unvalidated data in. If validation lived only on the
  controller via `@Valid`, a non-HTTP caller would bypass it.
- Implementation: Bean Validation annotations (`@NotBlank`/`@NotNull`) on the domain model,
  enforced *programmatically* via an injected `Validator`.
- *Alt rejected:* `@Valid` on the controller (HTTP-only trust boundary).

## Two validation gates, one error shape
- **Transport gate:** values that can't even be typed (unknown enum, bad timestamp, malformed
  JSON) fail at Jackson parse → `HttpMessageNotReadableException`, handled in the advice. This
  *must* live at the boundary (the core never sees an unparseable body).
- **Semantic gate:** required-field/structural rules, in the core `Validator`.
- Both render as the **same** body: `{ "errors": [ { "field", "message" } ] }`, via a single
  `@RestControllerAdvice`. Consistent 400s whether the failure was transport or semantic.
- *Alt rejected:* let Spring's default opaque `{timestamp,status,error,path}` body stand (no reason).

## Error contract: data vs control flow
- `ValidationError` = a `(field, message)` **datum** — library-agnostic (decoupled from Hibernate
  Validator's `ConstraintViolation` and Jackson's internals), serializable, reusable.
- `InvalidEventException` = **control flow** — carries a `List<ValidationError>`; one per rejected
  event, thrown by the core, caught by the advice.
- `ErrorResponse` = `{ errors: List<ValidationError> }`, the JSON body.
- Report **all** violations at once (not fail-fast) so a client fixes everything in one round-trip.

## Transport-error field extraction
- From Jackson's `InvalidFormatException` we pull the JSON path and, for enums, the allowed values
  → message "must be one of [INJECTION, XSS, …]". Generic parse errors → field `body`,
  "malformed JSON request body".
- Since the endpoint binds a `List` (see `batch-ingestion.md`), the path is **indexed**: a bad enum
  in the first element reports field `[0].rule.category`.

## Strict schema — unknown fields rejected
- `spring.jackson.deserialization.fail-on-unknown-properties=true`: a payload field outside the
  schema → `400 "unknown field"`, not silently dropped (Jackson's default would drop it).
- Zero-trust, and mirrors the schema-on-write storage stance (see `storage-engine-choice.md`).
- Surfaced via `UnrecognizedPropertyException` in the transport error path.

## Generic 500 handler (safe catch-all)
- A `@ExceptionHandler(Exception)` returns a clean `500` — `{"errors":[{"message":"Internal server error"}]}`
  — and logs the real cause server-side; never leaks a stack trace or message to the client.
- Implemented by **extending `ResponseEntityExceptionHandler`**, not a bare `@ExceptionHandler(Exception)`
  on a plain advice. Why: a broad `Exception` handler preempts Spring's default MVC exception handling,
  turning `405` (wrong method) / `415` (wrong content-type) into `500`s. Extending RESEH keeps those
  framework exceptions at their correct status (they're more specific than our catch-all), and our
  `HttpMessageNotReadableException` body is customised via the `handleHttpMessageNotReadable` override.
- `ValidationError.field` is `@JsonInclude(NON_NULL)`, so the 500 body omits `field` (it isn't a field error).
- Interview point: "how do you add a catch-all without clobbering 405/415?" → extend RESEH.

## Numeric fields are boxed to enforce presence
- `configId`/`statusCode`/`requestSize`/`responseSize` are `Long`/`Integer` (NOT primitive
  `long`/`int`), annotated `@NotNull` + `@Positive` (configId/statusCode) / `@PositiveOrZero` (sizes).
- Why: a Java **primitive can't be null**, so an *omitted* JSON number silently deserializes to `0`
  and passes validation — you can't require its presence. Boxing lets `@NotNull` catch omission.
  Matters most for `configId` (Stats partitions on it) and `statusCode` (`0` isn't a valid HTTP status).
- Interview Q: "why `Long` not `long` here?" → to validate presence; primitives hide omission as `0`.
