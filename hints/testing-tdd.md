# Testing & TDD decisions (personal hint)

## Strict TDD, red → green → refactor
- Every feature started with a failing test (often a compile failure for a missing type), then the
  minimal code to pass, then cleanup. Mostly inside-out (domain/core first), outside-in for the API.

## JUnit 5 + AssertJ
- The common Spring Boot combo (what Boot itself and start.spring.io use). AssertJ's fluent
  `assertThat(...).isEqualTo(...)` reads well and gives rich failure messages.
- *Alt rejected:* plain JUnit assertions (argument-order traps), Hamcrest (older, no advantage).

## `@SpringBootTest` + MockMvc for the API test (not `@WebMvcTest`)
- Full-stack: real controller → real `EventIngestionService` → real `Validator` → real advice.
- Why: the point of the API test is to prove zero-trust validation surfaces as a real `400` **end
  to end**; a `@WebMvcTest` slice with a mocked service would stub out exactly what we want to prove.
- This doubles as the assignment's required "integration test for the API".

## Programmatic `Validator` in pure unit tests
- No Spring context → build it via `Validation.buildDefaultValidatorFactory()`.
- The `ValidatorFactory` owns resources and must outlive the `Validator`, so it's created in
  `@BeforeAll` and closed in `@AfterAll` (a first attempt closed it immediately via
  try-with-resources — a bug, since the returned validator outlived its factory).
- *Alt rejected:* `@SpringBootTest` just to get the bean (slow); mock the `Validator` (tests nothing real).

## Characterization test for upsert
- The duplicate-`eventId` → single-record behavior already existed, so the test is green-on-arrival;
  its job is to **lock in** the idempotency guarantee against future regressions.

## Test shape
- Shared sample-event builders per test class; targeted single-class runs during a cycle,
  full-suite run to confirm no regression before moving on.
