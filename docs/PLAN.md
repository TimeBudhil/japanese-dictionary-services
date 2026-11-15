# japanese-dictionary-services — Plan

What's next, what's left, and the decisions in force. How things work: [`CONTEXT.md`](./CONTEXT.md).
Done work and old decisions: [`HISTORY.md`](./HISTORY.md) (not read by default).

_Last updated: 2026-11-14_

## Next session

- **Where we are:** Phase 0 (foundation) and Phase 1 (vocabulary) are done and verified on a full
  load. `./mvnw test` is green. The updater's `main` has its full history and Flyway V1–V4.
- **Docs system (D78) is in place** in both repos; its rules are in `CLAUDE.md`.
- **Next: Phase 2 (kanji).** Before coding, settle or re-defer O5–O9, then decide O11–O14.
  Ask the updater for the order check X8 before the kanji and names phases end.
- **Data:** `docker compose up -d` gives seed data (8 kanji 日本語食書猫学校, 16 radicals).
  `sh docker/snapshot.sh restore full` gives every kanji (snapshot exists on the dev machine only).
- **Copy from `vocabulary/`:** the API interface + records, `services/`, `repository/`,
  `integration/VocabularySecurityConfig`, `aop/`, the controller and its tests.

## Build order

0 foundation ✅ → 1 vocabulary ✅ → 2 kanji → 3 names → 4 sentences → 5 normalization →
6 parsing → 7 search → 8 lists

## Phase 2 — kanji

Data (full load; Postgres only, no ES index for kanji):
- `kanji`: 13,138 rows. 13,108 from KanjiDic2, plus 30 **bare rows** (radical forms such as ⺉ ⺌,
  with no codepoint, readings, meanings or grade). Stroke paths on only 6,447; meanings on 10,384;
  grade on 2,999 (1–6, 8–10); jōyō on 2,136; `jlpt` is the **old** 1–4 scale (2,230 rows).
  `kunyomi` keeps KanjiDic2 notation (`た.べる`, `-び`). None retired.
- `radical`: 253 rows, 1–17 strokes. 6 radicals have no `kanji` row.
- `kanji_radical`: 6,355 kanji decomposed, at most 12 radicals each.
- Example, 日: on ニチ ジツ, kun ひ -び -か, "day, sun, Japan, counter for days", 4 strokes,
  grade 1, jōyō, old JLPT 4, frequency 1, radical 日.

Tasks:
- [ ] `models/`: `Kanji`, `Radical` as read-only entities; `text[]` as `List<String>` with
  `@JdbcTypeCode(SqlTypes.ARRAY)`; nullable numbers/booleans as wrappers. Kanji ↔ radicals lazy,
  constant queries per page (D35).
- [ ] `repository/`: read methods only. Radical search returns kanji containing **all** selected
  radicals (O11).
- [ ] `KanjiApi` + records in `kanji/` (D37). Lookup by character returns `Optional`. For `search`:
  the kanji of a query's characters, in query order, no duplicates.
- [ ] Endpoints: `GET /api/v1/kanji/{character}` (404 if unknown), `GET /api/v1/kanji?radicals=…`,
  `GET /api/v1/radicals` (O13, O14). Optional filters: strokes, grade, jōyō. Include stroke paths.
- [ ] Security chain claiming **both** `/api/v1/kanji/**` and `/api/v1/radicals/**`; the root chain
  denies any `/api` path no module claims (D25).
- [ ] Logging aspect and controller tests, copied from vocabulary. Fixtures are real rows (D77):
  日, 猫, a bare row (⺉), a kanji without stroke paths.
- [ ] Seed: add missing cases to `KANJI` in `docker/generate-seed.py` and regenerate (D59).
- [ ] Verify on the stack: `validate` passes, radical search is right (spot-check RADKFILE),
  query count per page is constant.
- [ ] Check whether the order of `meanings[]` and the reading arrays matters and is kept.

## Phase 3 — names

- [ ] `NameEntry` (`text[]` columns), Postgres only (D18).
- [ ] `GET /api/v1/names/{entSeq}`, `GET /api/v1/names?q=` (kanji, readings, translations).
- [ ] Measure on the full ~740k rows. If slow, ask the updater for indexes (X5).
- [ ] API for `search`, controller tests.

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
| X5 | GIN / `pg_trgm` indexes on `name_entry`, if Phase 3 needs them | Phase 3 |
| X6 | Word-level JLPT source (after O3) | Phase 8 |
| X8 | Keep upstream order for the remaining lists (kanji, names, X3's tables) | Phases 2, 3 |
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

## Open decisions

| # | Question | Advice | When |
|---|---|---|---|
| O1 | Tokenizer / deinflection library | Lucene Kuromoji + a rule-based deinflector | Phase 6 |
| O2 | Normalization: own code or a library | Own, table-driven, unit-tested | Phase 5 |
| O3 | Word-level JLPT source (no official list exists) | A community list; needs an updater spoke (X6) | Phase 8 |
| O4 | Deployment: hosting, hosted ES (must speak 9.x), CI/CD, tracing | Decide with the updater | First deploy |
| O5 | Keep the 100-character limit on `q`? | Keep | Before Phase 2 |
| O6 | Logging aspects: rely on the log pattern for the request id, or add it in code? | Rely on the pattern | Before Phase 2 |
| O7 | Declare `spring-boot-starter-aspectj` in the pom (today it comes in through JPA)? | Yes | Before Phase 2 |
| O8 | API records are the JSON contract; add a `dto/` layer? | Not until the shapes must differ | Before Phase 2 |
| O9 | Module chains allow every method (writes get 405). Restrict to GET/HEAD? | Keep as is | Before Phase 2 |
| O11 | Radical search: match and order | SQL `GROUP BY kanji HAVING count = n`; order by strokes, frequency, character; paged; no bare rows | Phase 2 |
| O12 | Detail for bare rows and retired kanji? | Return both unchanged, like D76 | Phase 2 |
| O13 | One kanji record, or a detail + a smaller list record? | Two: `KanjiDetail`, `KanjiSummary` (no stroke paths) | Phase 2 |
| O14 | What does `GET /api/v1/radicals` return? | All 253, grouped by stroke count, with kanji counts, not paged | Phase 2 |

## Later

- Integration tests (Testcontainers + seed data), JPA/ES slice tests.
- Modules from diagram V03: OCR, slang, kanji mastery map, word of the day, furigana, handwriting search.
- Unsourced data ideas (see `written_reports/Archived/`): pitch accent, frequency, grammar points, audio.
- Link `example_sentence.index_words` to vocab ids (in the updater).
