# Design weaknesses (personal hint)

Interview / design-review crib. Public write-up: [`docs/weaknesses.md`](../docs/weaknesses.md).
Gap punch-list: [`docs/gaps.md`](../docs/gaps.md). Don’t confuse the two in conversation.

---

## How to talk about them

Lead with **trade-off language**, not apology:

- “For the take-home I chose X because Y; the cost is Z.”
- Rank: **contract leak > enrichment race > clock trust > rate-limit locality > error shape > validation polish.**

If they ask “what would you fix first?” → **#1 identity semantics**, then **#2 atomic enrich**.
Those unblock honest multi-backend demos; the rest is ops hardening.

---

## Soundbites (practice out loud)

| Weakness | Defend (why it’s OK here) | Change first (what you’d do) |
|---|---|---|
| Upsert vs append | In-memory default for zero-infra demos; CH proves OLAP path | Make upsert/dedup part of the seam or test both as explicit contracts |
| Count-then-save | Scoring stays testable; race is benign under-count | Atomic write path / store-side prior count |
| Filter on `timestamp` | Matches SOC “when did it happen” | Offer `receivedAt` or watermark for ops views |
| In-process rate limit | Single node, demos flood ingest on purpose | Redis + trusted proxy headers if multi-replica |
| Uneven errors | Ingest got the polished contract first | Extend advice to stats binding / 405/415 |
| Soft validation | Scope; gaps table owns the list | Caps, IP/status bounds — not architecture |

---

## Trap questions

- **“Is the system idempotent?”** — Only on in-memory. Say that before they find it in CH.
- **“Is enrichment correct under concurrency?”** — Best-effort; document the race.
- **“Why not Postgres?”** — Workload is append + time-range agg; CH was the OLAP bet; in-memory is the laptop bet. Weakness isn’t “wrong store,” it’s **unaligned semantics across stores**.
- **“Is gaps.md the same as weaknesses?”** — No. Gaps = unfinished/hardening items. Weaknesses = structural debts even if every gap were closed.

---

## Link to big picture

[`0_big_picture.md`](0_big_picture.md) is the happy path. This hint is the “and here’s where I’d push back on my own design” page. Reviewers like that combo.
