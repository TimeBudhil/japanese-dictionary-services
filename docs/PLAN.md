# japanese-dictionary-services — Plan

What's next, what's left, and the decisions in force. How things work: [`CONTEXT.md`](./CONTEXT.md).
Done work and old decisions: [`HISTORY.md`](./HISTORY.md) (not read by default).

_Last updated: 2026-11-16_

## Next session

- **Where we are:** Phases 0–2 are done. Phase 3 (names) is built and tested (`./mvnw test`
  green, 57 tests) but its search needs the updater's X5 migration: until then
  `GET /api/v1/names?q=` returns 500. Detail by id works today.
- **X5 is specified** word for word in the updater's `docs/PLAN.md` (pg_trgm, two functions, two
  indexes). It was tested by hand on the full load (3–40 ms per search, a lone kana ~200 ms),
  then the `full` snapshot was restored, so the dev DB has no X5 objects.
- **Next:** implement X5 in the updater, run `sh docker/generate-schema.sh`, `docker compose down
  -v && docker compose up -d`, and check `GET /api/v1/names?q=田中` on seed data. Take a new
  full-load snapshot afterwards (the schema changed). That closes Phase 3; then Phase 4.
- **X8 is answered for names too:** the parser appends `keb`/`reb`/`trans_det` in file order and
  keeps the first of each repeated name type. Tell the updater; only X3's future tables remain.
- **Data:** the dev volumes hold a full load. `docker compose down -v && docker compose up -d`
  gives seed data; `sh docker/snapshot.sh restore full` brings the full load back.

## Build order

0 foundation ✅ → 1 vocabulary ✅ → 2 kanji ✅ → 3 names (waits on X5) → 4 sentences →
5 normalization → 6 parsing → 7 search → 8 lists

## Phase 3 — names

- [ ] After X5: regenerate `schema.sql`, check name search on seed data, new full-load snapshot.

## Phase 4 — sentences

- [ ] `ExampleSentence` entity + read-only ES document (index `example-sentence`).
- [ ] `GET /api/v1/sentences/{id}`, `?word=` (by index words), `?q=` (Japanese/English text).
- [ ] API for `search` and for "example sentences of this word", controller tests.

## Phase 5 — normalization

Decide O2 first.
- [ ] Romaji → kana (ん before vowels/y, っ, long vowels, wāpuro vs Hepburn).
- [ ] Era years ↔ Gregorian (令和7年 ↔ 2025). Kanji numerals ↔ digits (二千二十五 ↔ 2025, 万/億).
- [ ] Public API; endpoints optional.

## Phase 6 — parsing

Decide O1 first.
- [ ] Detect input type: English, romaji, Japanese, likely name.
- [ ] Deinflect: 食べられなかった → 食べる (potential + negative + past).
- [ ] Split a sentence into words. Public API.

## Phase 7 — search

- [ ] `GET /api/v1/search?q=&types=vocabulary,kanji,names,sentences&page=&size=`.
- [ ] Flow: detect input → normalize → deinflect → call each module → rank (D10).
- [ ] One deterministic ranking: tier (exact > deinflected > prefix > partial) → common/frequency
  → type, adjusted by input type (D17). Plain unit tests for the comparator.
- [ ] Tag search (POS/misc), details when reached.

## Phase 8 — lists

- [ ] From current data: common words, POS, expressions, slang, kanji by grade/jōyō/JLPT/frequency.
- [ ] Blocked: subject and dialect lists (X3), words by JLPT (O3, X6).
- [ ] `GET /api/v1/lists`, `GET /api/v1/lists/{id}`. User-made lists belong to the user service.

## Waiting on the updater

Tracked in the updater's `docs/PLAN.md`; listed here for what they block.

| # | Task | Blocks |
|---|---|---|
| X2 | kuromoji + ICU fields on `vocab` and `example-sentence`; English gets an exact tier | match quality, Phases 4, 7 |
| X3 | Store JMdict `<field>` and `<dial>` tags | Phase 8 |
| X5 | `pg_trgm` + two functions + trigram indexes on `name_entry`, spec in the updater's PLAN (D91) | name search, Phase 3 |
| X6 | Word-level JLPT source (after O3) | Phase 8 |
| X8 | Keep upstream order for the remaining lists (X3's tables; kanji and names checked, kept) | Phase 8 |
| X10 | Store POS/misc as codes (`n`, `v1`), not expanded text — a design change in both repos | Phases 7, 8 |

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
| D22 | Tests now: controller tests + Modulith. Integration tests later |
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

## Open decisions

| # | Question | Advice | When |
|---|---|---|---|
| O1 | Tokenizer / deinflection library | Lucene Kuromoji + a rule-based deinflector | Phase 6 |
| O2 | Normalization: own code or a library | Own, table-driven, unit-tested | Phase 5 |
| O3 | Word-level JLPT source (no official list exists) | A community list; needs an updater spoke (X6) | Phase 8 |
| O4 | Deployment: hosting, hosted ES (must speak 9.x), CI/CD, tracing | Decide with the updater | First deploy |

## Later

- Radical picker: grey out radicals that can't combine with the selection (`/radicals?selected=…`).
- Integration tests (Testcontainers + seed data), JPA/ES slice tests.
- Modules from diagram V03: OCR, slang, kanji mastery map, word of the day, furigana, handwriting search.
- Unsourced data ideas (see `written_reports/Archived/`): pitch accent, frequency, grammar points, audio.
- Link `example_sentence.index_words` to vocab ids (in the updater).
