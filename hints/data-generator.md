# Data generator decisions (personal hint)

> Full usage is in `docs/generator.md`.

## Outside the product (`tools/`, bash)
- Generator is for testing/demos — must not live in `src/main` or the fat jar.
- Bash + `jq` (no Python in the generator itself) keeps the runtime stack Java-only.

## Uniform random + waves
- Background: uniform over categories/actions/paths/IPs.
- Waves: every 100 events, 25 with the same `clientIp`+`path` so top-N / repeat-offender light up.

## `--seed` must not use `$(pick …)`
- Command substitution runs in a subshell; `RANDOM` updates there are discarded.
- Use `pick_into` / in-process helpers so the parent seed advances.

## File default, optional `--post`
- Always can write JSON and curl manually.
- `--post` is convenience when the app is up; large batches may stress single-request ingest (see gaps).
