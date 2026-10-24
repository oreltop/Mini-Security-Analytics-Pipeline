# Tooling decisions (personal hint)

## Swagger / OpenAPI (springdoc-openapi)
- `springdoc-openapi-starter-webmvc-ui` 2.6.0 auto-generates the OpenAPI 3 spec and Swagger UI
  **from the controllers** — zero config.
- Endpoints: `/v3/api-docs` (JSON), `/v3/api-docs.yaml`, `/swagger-ui/index.html`.
- The schema (incl. enum allowed values) is derived from the `SecurityEvent`/`EnrichedEvent` types.
- Known nit: it advertises `200` for ingest while we return `201` — a one-line `@ApiResponse`/
  `@ResponseStatus` annotation fixes it (deferred docs polish).
- The assignment only requires README + curl, so this is a bonus. Added it because it auto-updates
  as endpoints grow and gives a live way to exercise the API.

## Docker: multi-stage Dockerfile driven by the Maven wrapper
- Build stage `eclipse-temurin:21-jdk` runs `./mvnw` (pinned Maven, consistent with local build);
  run stage `eclipse-temurin:21-jre` carries just the jar.
- Reviewer needs only Docker — no Java/Maven install.
- `dependency:go-offline` layer caches deps across source-only changes; tests run via `./mvnw test`,
  not during the image build.
- `.dockerignore` excludes `target/`, `.git`, the PDF, logs.

## docker-compose
- `docker compose up --build` brings up the app. Storage is in-memory today, so the app is the
  only service; a comment marks where a DB service (`depends_on`) slots in when storage is externalized.
- Use `--build` to pick up latest code (plain `up` reuses a stale image).
