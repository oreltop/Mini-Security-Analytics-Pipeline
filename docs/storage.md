# Storage

Storage sits behind the `EventRepository` interface (the swappable seam), with two
implementations selected by Spring profile. The app stores the *enriched* event
(original fields + `attackType`/`threatScore`/`receivedAt`).

## Two implementations

| | Default (in-memory) | ClickHouse |
|---|---|---|
| Profile | `!clickhouse` (default) | `clickhouse` |
| Class | `InMemoryEventRepository` | `ClickHouseEventRepository` |
| Backing | `ConcurrentHashMap` keyed by `eventId` | ClickHouse `events` table (`MergeTree`) |
| Infra | none — always boots | ClickHouse server |
| Use | local dev, the fast test suite | production-like / the full-stack bonus |

`@SpringBootApplication` excludes `DataSourceAutoConfiguration` so the **default profile needs no
database to boot**; the `clickhouse` profile wires its own `DataSource` + `JdbcTemplate`
(`ClickHouseDataSourceConfig`) and creates the schema on startup.

## Why ClickHouse

The workload is append-heavy, immutable, time-series security events read by analytical
aggregations (stats) and filtered scans (samples) — a columnar-OLAP fit. ClickHouse is chosen over
a search engine (Elasticsearch) or a row store (Postgres) for aggregation throughput on that
workload at scale. In-memory is the zero-infra default for development.

## How to run

```bash
# In-memory (zero dependencies)
./mvnw spring-boot:run

# ClickHouse full stack (app + ClickHouse)
docker compose up --build        # app on the clickhouse profile, wired to the clickhouse service
```

Credentials/URL for the app come from the environment (`SPRING_DATASOURCE_URL`, `…_USERNAME`,
`…_PASSWORD`) — set by `docker-compose.yml`. The `events` table is created on startup.

## Schema & mapping

- `events` table, `ENGINE = MergeTree ORDER BY (configId, timestamp)`, flattened columns
  (`clickhouse-schema.sql`).
- `Instant` ↔ UTC `LocalDateTime` across JDBC; enums → `name()`; null optional fields → `""`.

## Testing

- **Unit:** `InMemoryEventRepositoryTest` (fast, no infra).
- **Integration (Testcontainers, `-Pintegration`):** `ClickHouseEventRepositoryTest` (repository
  contract) and `ClickHouseIngestionIntegrationTest` (full stack: HTTP ingest → stored in ClickHouse).
  Tagged `integration` and excluded from the default `mvn test`.

## Limitations

See `gaps.md` — notably: single-row INSERTs (batching would suit ClickHouse better at scale),
no retention/TTL, and null optional fields round-trip as empty strings.
