# Rate limiting — decisions & ruled-out options (personal hint)

> Behaviour is in `docs/rate-limiting.md`. This file is the *why*.

## What we locked

| Choice | Decision |
|---|---|
| Scope | Stats + samples only |
| Key | Client IP |
| Algorithm | Fixed window, 100/min |
| Store | In-memory |
| Response | 429 + Retry-After + ErrorResponse |
| Placement | Servlet `Filter` |
| Failure mode | Fail-open |

## Why these (and what we ruled out)

### Scope: analytics only — not ingest
- **Chose:** match the assignment bonus (“stats and samples”); keep generator/`--post` usable.
- **Ruled out — also limit ingest:** would protect write amplification, but fights demos and Part 5; can add later behind a flag.
- **Ruled out — global only (no per-IP):** one noisy client blocks everyone.

### Key: client IP
- **Chose:** assignment example; trivial with `getRemoteAddr()`.
- **Ruled out — per configId:** analytics often omits configId; weak for anonymous scrapers.
- **Ruled out — API key / user:** no auth model in the mini.
- **Gap:** behind a reverse proxy, real client IP needs `X-Forwarded-For` trust config — not done (spoof risk if done naively).

### Algorithm: fixed window
- **Chose:** easiest to explain and to test with a fake `Clock`.
- **Ruled out — sliding window:** smoother, more code/state.
- **Ruled out — token bucket:** nicer bursts, harder interview walkthrough for little gain here.

### Store: in-memory
- **Chose:** one JVM today; zero infra.
- **Ruled out — Redis / shared store:** correct for multi-replica; out of scope for the take-home (document in gaps / README improve).
- **Implication:** limit resets on restart; each instance has its own counters.

### Response shape
- **Chose:** 429 + `Retry-After` + existing `ErrorResponse` so clients stay consistent.
- **Ruled out — empty 429 body:** harder to debug in Swagger.
- **Ruled out — 503:** wrong semantics (overload vs client quota).

### Placement: Filter
- **Chose:** one place for path prefixes; runs before controllers.
- **Ruled out — `@RateLimiter` annotations per method:** more boilerplate, easy to miss a path.
- **Ruled out — API gateway only:** we don’t ship a gateway in this repo.

### Fail-open vs fail-closed
- **Chose:** fail-open — a buggy limiter must not brick analytics in a take-home demo.
- **Ruled out — fail-closed:** safer for hostile prod; wrong default when the counter is best-effort in-memory.

## Interview one-liner
“We rate-limit read analytics per IP with a fixed one-minute window in-process; ingest stays open for load tests; multi-instance would move the counter to Redis.”
