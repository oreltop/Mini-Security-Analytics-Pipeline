# Enrichment & threat-scoring decisions (personal hint)

## Whole scoring formula in `ThreatScorer`; recent-count passed in
- The entire policy (severity + action + path bonus + repeat-offender + cap) reads top-to-bottom
  in one class.
- The scorer stays a **pure function** of its inputs: `score(event, recentEventsFromSameIp)`.
  Computing the recent count (a store query) is the *service's* job, not the scorer's.
- Why: cohesive, pure, trivially unit-testable with a given count; no storage coupling in the scorer.
- *Alt rejected:* weights on the `Severity`/`Action` enums (scatters the policy across files);
  scorer injects the repository and queries itself (couples a pure calculator to storage).
- **Contrast:** `attackType` *does* live on its enum — because it's a stable label, not tunable policy.

## Repeat-offender uses server `receivedAt`, not client `timestamp`
- Window: count events **already stored** from the same `clientIp` whose `receivedAt` is within the
  last 10 minutes; `> 5` → +15.
- Why `receivedAt`: zero-trust — the client-supplied `timestamp` is untrusted/spoofable; the
  server-assigned ingestion time is authoritative.
- The spec was ambiguous on "more than 5 … within the last 10 minutes"; documented the chosen
  interpretation (counts prior stored events, strictly `> 5`, so the 7th+ rapid request is flagged).

## Cap at 100 kept as a defensive guard
- With the spec's weights the max reachable is **90** (40+20+15+15), so `Math.min(score, 100)`
  never fires — kept anyway (with a comment) to honor the spec literally and stay correct if
  weights change later.

## Concurrency note (repeat-offender)
- The repeat-offender count is read from the store and the event then saved — **not atomic**. Under
  concurrent ingestion (we're on virtual threads) two requests from the same IP can read the same
  count and both miss the bonus. Benign (worst case: one missed +15); a real store would count+insert
  atomically. Documented as a known limitation.

## `receivedAt` is server-assigned
- Stamped as `Instant.now()` in the core on ingestion — again zero-trust (never trust client time).
