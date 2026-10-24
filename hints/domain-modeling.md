# Domain modeling decisions (personal hint)

## Immutable records + enums
- `SecurityEvent`, `Rule`, `GeoLocation` are Java **records** — immutable, no half-built events.
- `RuleCategory`, `Severity`, `Action` are **enums** — the closed value sets are enforced *by
  deserialization itself*: an unknown value (e.g. `"BANANA"`) fails to parse, so the schema is
  partly self-enforcing before any validation runs.
- *Alt rejected:* plain classes with String fields (no type safety, no free validation).

## `attackType` lives on the `RuleCategory` enum
- Each constant carries its label via a constructor field: `INJECTION("SQL/Command Injection")`,
  exposed as `category.attackType()`.
- Why: cohesive (the category *owns* its canonical name), zero extra classes, **exhaustive by
  construction** (a new category can't be added without a label), trivially testable.
- *Alt rejected:* a standalone `AttackTypeClassifier` service (ceremony around a static lookup).
- **Contrast with threat weights** (see enrichment-scoring): those live in the scorer, NOT on the
  enums. The distinction — `attackType` is a stable *identity/label*; threat weights are *tunable
  policy* that belongs together as one formula.

## `EnrichedEvent`: composition + `@JsonUnwrapped`
- `record EnrichedEvent(@JsonUnwrapped SecurityEvent event, String attackType, int threatScore, Instant receivedAt)`.
- Composition internally → no duplicating the 15 original fields.
- `@JsonUnwrapped` flattens the JSON so the wire shape is "the original fields **plus**
  attackType/threatScore/receivedAt" (matches the spec) and keeps the `$.eventId` top-level contract.
- *Alt rejected:* a flattened 18-field record (field duplication); nested JSON (`{event:{…}}`,
  breaks the spec's "original fields plus" wording and the existing contract).
