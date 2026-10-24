# ClickHouse integration (personal hint)

Adding ClickHouse as a second `EventRepository` implementation, alongside in-memory, selected by
Spring profile. Why ClickHouse: see `storage-engine-choice.md`.

## Coexistence via profiles (not a hard replace)
- `InMemoryEventRepository` → `@Profile("!clickhouse")` (default: zero-infra, fast tests, always boots).
- `ClickHouseEventRepository` → `@Profile("clickhouse")`.
- `@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)` so the **default profile needs
  no database to start** — otherwise adding `spring-boot-starter-jdbc` makes Boot demand a DataSource
  URL at boot and the app (and every `@SpringBootTest`) fails.

## Dependency chain (this was fiddly — interview-worthy)
- `com.clickhouse:clickhouse-jdbc:0.6.5` — the **thin** jar. The `:all` classifier does NOT bundle
  the client (`com.clickhouse.client.ClickHouseClient` missing → `NoClassDefFoundError`), despite the name.
- `com.clickhouse:clickhouse-http-client:0.6.5` — the HTTP protocol implementation.
- `org.apache.httpcomponents.client5:httpclient5` — HTTP transport the http-client needs
  (`org.apache.hc.core5...NoHttpResponseException` missing otherwise).
- `org.lz4:lz4-java:1.8.0` — ClickHouse HTTP responses are LZ4-compressed; without it:
  "LZ4 is not supported… add the missing libraries to the classpath."

## Testcontainers on rootless Docker
- `DOCKER_HOST=unix:///run/user/<uid>/docker.sock`, `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` = same,
  and `TESTCONTAINERS_RYUK_DISABLED=true` (Ryuk reaper can't bind-mount the socket in rootless).

## The clickhouse-server image password quirk
- `clickhouse/clickhouse-server:24.3` gives the `default` user a password by default — even a plain
  `curl` with no creds is rejected ("password is incorrect"). Testcontainers reports an *empty*
  password, which doesn't match.
- Fix: `ClickHouseContainer.withUsername("default").withPassword("test")` → the image receives
  `CLICKHOUSE_PASSWORD` and sets it; connect with that. (Also: pass no password rather than an empty
  string for genuinely passwordless users.)

## Test strategy
- `ClickHouseEventRepositoryTest` is `@Tag("integration")` and **excluded from the default `mvn test`**
  via a property-driven surefire config (`test.excluded.groups=integration`); run it with `-Pintegration`.
  Keeps the normal suite fast and Docker-free; the ClickHouse test needs Docker.

## App wiring for the clickhouse profile (Cycle 2)
- `ClickHouseDataSourceConfig` (`@Profile("clickhouse")`) builds the `DataSource` from
  `spring.datasource.*` via `DataSourceBuilder` + `@Value` injection, plus a `JdbcTemplate` bean.
  - *Don't* define your own `@Bean @ConfigurationProperties DataSourceProperties` — Boot already
    registers one, and you get `NoUniqueBeanDefinitionException` (two DataSourceProperties beans).
- **Schema on startup via an `ApplicationRunner`** running `jdbcTemplate.execute(ddl)` — *not*
  `spring.sql.init`, whose script parser mis-splits the multi-line ClickHouse DDL and truncates it at
  the first `(` ("Unmatched parentheses"). The raw `execute(ddl)` runs the whole statement fine.
- `application-clickhouse.properties` fixes the driver class; URL/creds come from env (compose) or
  the test's `@DynamicPropertySource`.
- `docker-compose.yml` runs the **full stack**: `clickhouse-server` (with `CLICKHOUSE_PASSWORD`) +
  the app on `SPRING_PROFILES_ACTIVE=clickhouse`, `depends_on` healthcheck. Local zero-dep run stays
  in-memory via `./mvnw spring-boot:run`.
- Full-stack test: `@SpringBootTest @ActiveProfiles("clickhouse")` + Testcontainers +
  `@DynamicPropertySource` → boots the whole app, POSTs an event, asserts it's read back from ClickHouse.

## Mapping
- Flattened `events` table, `MergeTree ORDER BY (configId, timestamp)`; `DateTime64(3,'UTC')` columns.
- `Instant` ↔ UTC `LocalDateTime` across the JDBC boundary (avoids timezone drift).
- Enums → `name()`; null optional fields → `""` (a round-trip divergence vs in-memory: a null comes
  back as empty — documented; use `Nullable(...)` columns if exact null round-trip is needed).
