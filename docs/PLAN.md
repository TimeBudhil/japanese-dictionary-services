# japanese-dictionary-services — Plan

What's next and the decisions in force. What's left is the ⬜/🟡/🔵 rows of
[`REQUIREMENTS.md`](../written_reports/REQUIREMENTS.md) (D95). How things work: [`CONTEXT.md`](./CONTEXT.md).
Done work and old decisions: [`HISTORY.md`](./HISTORY.md) (not read by default).

_Last updated: 2026-11-23_

## Next session

- **Closes:** the Docker half of R3.2, R14.1, R14.8 and N1.5 (all 🔵). The code is built: V5 and
  X12 in the updater, and here the Testcontainers base, three `*IT` classes, CI and the mapping copy.
  None of the ITs has run yet. Until `schema.sql` is regenerated, name search still returns 500
  and `NamesModuleIT` and CI are red.
- **On a Docker machine:** run the updater's `./mvnw verify` first. Then `sh docker/snapshot.sh
  restore full` and start the updater against it as the owner, so Flyway applies only V5. Put the
  `headwords`/`exampleFor` keyword mapping on the existing index (updater CONTEXT.md), re-run the
  `tatoeba` feed, time name search (R14.1), then `snapshot.sh save` as `full`.
- **Then seed:** add a full-load sentence that marks 食べる with `~` to `SENTENCES` in
  `generate-seed.py`, regenerate schema and seed, `docker compose down -v && up -d`, `./mvnw verify`.
- **Commit order:** updater's `spec/name-search-it` into its `main` first, then this repo's (D96).
- **After that:** R4.1–R4.4 (sentences, D97, D98). R4.5 waits on R6, so R4 stays 🟡. Then R1.5.
- **Data:** the dev volumes hold a full load without X5. `docker compose down -v && docker
  compose up -d` gives seed data; `sh docker/snapshot.sh restore full` brings the full load back.

## Build order

Done: R11 (platform), R1 (except R1.4, R1.5), R2, R3.1. Next, in order: R3.2 → R4.1–R4.4 → R1.5 →
R5 → R6 (+ R4.5) → R7 → R8, R10 → R9. Blockers (`X<n>`, `O<n>`) and status are on the rows in
REQUIREMENTS.md.

## Decisions in force

Full reasoning for each id: `HISTORY.md`.

| # | Decision |
|---|---|
| D0 | Postgres is the source of truth (diagram V03 says MySQL; outdated). ES holds search copies |
| D1 | Dictionary data only. User accounts and lists go to a future user service |
| D2 | Own read-only copies of the updater's entities and ES documents, no shared library |
| D3 | The updater owns the schema (Flyway). This service runs `ddl-auto=validate` |
| D4, D6 | Java version and package name differ per repo (25 / `baddee` here). Intentional |
| D7 | Package by feature. Every module uses the same sub-package names |
| D8 | Public reads, no auth. Rate limiting later, at the edge |
| D9 | Spring Modulith; `verify()` runs in the tests |
| D10 | `search` orchestrates; modules expose an API and never read each other's tables |
| D11 | `lists` serves premade read-only lists only |
| D14 | Japanese match quality comes from analyzers in the updater (X2) |
| D17 | `search` ranks with its own tier rules; ES scores aren't comparable across indexes |
| D18 | Names stay in Postgres only |
| D20 | REST under `/api/v1`, OpenAPI, page/size paging |
| D23 | Read-only database role (and read-only ES key in production) |
| D25 | No shared module. App-level pieces in the root package; each module owns its security chain and CORS |
| D27 | Connection settings are env vars with local defaults; the `prod` profile has none |
| D28 | `page` ≥ 0 (default 0), `size` 1–100 (default 20); out of range → 400 |
| D29 | Each module owns its exceptions; all errors render as RFC 9457 ProblemDetail |
| D30 | One request id per request, set at the root; each module logs in its own `aop/` |
| D33 | No Cognito here: this API has no users |
| D34 | Upstream order is data and the updater keeps it; `search` only ranks results |
| D35 | Collections load lazily in batches, never join-fetched |
| D36 | Vocab search is one ES query with named exact / prefix / partial clauses |
| D37 | Each module's public API is one interface + its records in the module's base package |
| D57 | Elasticsearch 9.x: Boot 4.1's client rejects 8.x servers |
| D59 | Seed data is generated from a full load, never hand-written |
| D71 | Do a full load once, then snapshot and restore it |
| D74 | Two stack modes: seed, or empty for a full load |
| D76 | Detail endpoints return retired entries unchanged; search never returns them |
| D77 | Test fixtures copy real entries from the data |
| D78 | Docs system: `CLAUDE.md` rules, one purpose per file, size budgets, `HISTORY.md` archive. A cross-repo decision lives in the repo it changes (replaces D70). Small choices are reported in chat only |
| D79 | Query text stays limited to 100 characters (was O5) |
| D80 | Logging aspects rely on the log pattern for the request id, no code (was O6) |
| D81 | `spring-boot-starter-aspectj` is declared in the pom, not inherited through JPA (was O7) |
| D82 | No `dto/` layer: API records are the JSON contract until the shapes must differ (was O8) |
| D83 | Module chains allow every method; writes get 405 (was O9) |
| D84 | Radical search: kanji containing all selected radicals; order strokes, frequency (unranked last), character; paged; no bare rows or retired kanji (was O11) |
| D85 | Kanji detail returns bare rows and retired kanji unchanged, no flags, like D76 (was O12) |
| D86 | Two kanji records: `KanjiDetail` (all, with stroke paths) and `KanjiSummary` for lists (was O13) |
| D87 | `GET /api/v1/radicals`: all radicals grouped by stroke count, with kanji counts, not paged (was O14) |
| D88 | RADKFILE stand-in radicals (犯 for 犭) are returned as stored; display mapping is the front-end's or a later updater task |
| D89 | Radical search filters: `strokes` (exact), `grade`, `joyo`, combined with AND |
| D90 | Name search tiers: exact (writing, reading or whole translation, any case) > prefix > a later translation word starts with q; then by id; no retired names (was O15) |
| D91 | Name search queries call the updater's X5 functions; the updater adds them first, this service never creates database objects |
| D92 | This repo holds the app-wide docs: `written_reports/REQUIREMENTS.md` and the diagrams, for all four applications. Per-repo decisions stay in each repo; the updater links here |
| D93 | Tests: controller tests + Modulith, and an integration test per module against seed data (Testcontainers), run by CI (replaces D22; requirement N1.5) |
| D94 | User lists, search history and Anki/CSV/TSV import and export live in the front-end, on the device. The user service (accounts, sync) is Stretch |
| D95 | REQUIREMENTS.md rows are the task list (status tags track progress); PLAN.md keeps only Next session, build order and decisions. Specs live in `written_reports/specs/`, gitignored |
| D96 | Updater code may change when an R row here needs it: tracked by its `X<n>`, committed in the updater first, D3 and D91 still hold. Its mirror is the updater's U18 |
| D97 | `sentences?word=` matches the headword only, through X12's ES arrays; order: `exampleFor` first, then shorter sentence, then id. Callers try a word's writings, then readings |
| D98 | Sentence detail returns retired sentences unchanged and leaves out raw `index_words`. Text search uses ES default analyzers until X2: `match_phrase` on Japanese, `match` on English, then by id |
| D99 | Integration tests are `*IT`, run by Failsafe in `./mvnw verify` only; single Postgres and ES containers load the same `docker/` seed files; a minimal GitHub Actions workflow runs `./mvnw verify` |

## Open decisions

| # | Question | Advice | When |
|---|---|---|---|
| O1 | Tokenizer / deinflection library | Lucene Kuromoji + a rule-based deinflector | Before R6 |
| O2 | Normalization: own code or a library | Own, table-driven, unit-tested | Before R5 |
| O3 | Word-level JLPT source (no official list exists) | A community list; needs an updater spoke (X6) | Before R8.4 |
| O4 | Deployment: hosting, hosted ES (must speak 9.x), CI/CD, tracing. Tentative budget $100–200/month | Decide with the updater | After every app runs in a container |
