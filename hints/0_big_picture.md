# Big picture (personal hint)

Explain-it-to-a-friend view of Mini WSA. Detail lives under `docs/`; this page is the shape.

---

## What this is

A small security **inbox + calculator**.

Something at the edge (a WAF, a gateway, a demo script) sends you short records of “someone tried something on a site.” The system checks those records, stamps them with a label and a danger score, files them away, and later lets you ask: *how bad was it?* and *show me some of the raw events.*

It is not a full SIEM. It is a thin pipeline you can run on a laptop or with Docker.

---

## Who the “user” is

From the outside, the user is almost always a **machine talking HTTP** — curl, Swagger UI, or the bash generator.

What they care about in practice:

1. **Drop off events** — “here are one or many security events; keep the good ones.”
2. **Ask for a summary** — “for this window of time, how many events, by type, who were the loudest IPs, which paths got hit?”
3. **Leaf through samples** — “show me a page of the actual stored events, optionally filtered.”

A human’s path is usually: open Swagger → try ingest with a sample body → hit stats → hit samples. The generator is a helper that invents realistic traffic so those screens are not empty.

---

## Main pieces

| Piece | Job |
|---|---|
| **EventIngestionController** + **EventIngestionService** | Accept one event or a batch; validate; enrich; store; return enriched list. |
| **Jakarta Validator** + **InvalidEventException** / **IngestionExceptionHandler** | Make sure each event looks trustworthy. Bad batches are refused as a whole with a clear error body. |
| **ThreatScorer** + **RuleCategory.attackType()** | Put a readable attack label and a threat score on the event (severity, action, path, “have we seen this IP lately?”). Service also stamps `receivedAt`. |
| **EventRepository** | Remembers enriched events. **InMemoryEventRepository** by default (fine for demos; gone on restart). **ClickHouseEventRepository** on the `clickhouse` profile for durable storage. Same interface either way. |
| **StatsController** / **StatsService** + **SamplesController** / **SamplesService** | Build the summary and the paginated list by asking the repository (aggregations / filters live in the store). |
| **AnalyticsRateLimitFilter** + **FixedWindowRateLimiter** | Only on stats/samples. Too many questions per minute from one client IP → “come back later.” Ingest is not throttled. |
| **tools/generate-events.sh** | Not part of the app. Fills ingest with fake-but-shaped traffic. |

---

## Happy-day flow

1. Client posts one event or a batch to ingest (`EventIngestionController`).
2. Service validates every item. If all pass, each one is labeled, scored, stamped, and saved via `EventRepository` — in order.
3. Client gets back the enriched versions (always as a list).
4. Later, client hits stats or samples; the matching controller/service asks the repository for counts / pages.
5. `AnalyticsRateLimitFilter` lets reasonable report traffic through; JSON comes back.

Mental picture: **receive → check → decorate → store → query.**

---

## When things go wrong (user’s eyes)

- **Junk on drop-off** — missing fields, bad enums, broken JSON. `IngestionExceptionHandler` answers with a clear “no” and a list of what’s wrong. Nothing from that batch is stored.
- **Hammering reports** — same client asks stats/samples too often. Filter answers `429` / “wait a bit.” Ingest still works.
- **Empty answers** — valid questions over a quiet window. You get zeros / empty lists / empty pages, not an error.

---

## One sentence to remember

People (and scripts) **hand you security events**; you **vet, decorate, and file** them; then they **ask what the pile looks like** — with a light brake only on the asking.
