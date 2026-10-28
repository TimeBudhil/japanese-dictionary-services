# japanese-dictionary-services — Plan

Tasks, implementation details, and decisions. Background facts are in [`CONTEXT.md`](./CONTEXT.md).

_Last updated: 2026-10-28_

**How to use this file**
- Work phases in order. Tick tasks with `[x]` when done.
- New decisions: add them to the [Decision log](#decision-log) with the reason.
- Before a phase that depends on an [open decision](#open-decisions), settle it first.
  Workflow: Claude gives its advice, and the user picks in the CLI selector.

## Status

- Skeleton only: Spring Boot app, feature module folders (empty), and one empty
  `vocabulary/repository/vocabularyRepository.java`. No config, compose, or tests beyond `contextLoads`.
- **Current phase: Phase 0.**

## Build order

Phase 0 foundation → 1 vocabulary → 2 kanji → 3 names → 4 sentences →
5 normalization → 6 parsing → 7 search → 8 lists

---

## Phase 0 — Foundation

**pom.xml**
- [ ] Remove `spring-cloud-starter-config`, the `spring-cloud.version` property, and the `spring-cloud-dependencies` BOM (D5).
- [ ] Add the Postgres driver (`org.postgresql:postgresql`, runtime).
- [ ] Add Spring Modulith (`spring-modulith-starter-core`, `spring-modulith-starter-test`, plus the `spring-modulith-bom`). Check that the version is compatible with Boot 4.1 (D9).
- [ ] Add `springdoc-openapi-starter-webmvc-ui`, and check that the version is compatible with Boot 4 (D20).

**Config (`application.properties`)**
- [ ] Datasource `jdbc:postgresql://localhost:5433/...` with the **read-only role** (D23).
- [ ] `spring.jpa.hibernate.ddl-auto=validate` (D3), `spring.jpa.open-in-view=false`.
- [ ] `spring.elasticsearch.uris=http://localhost:9201`.
- [ ] Actuator: expose `health,info` only.
- [ ] CORS allow-list property (e.g. `app.cors.allowed-origins`).

**Local infra (D16, D19)**
- [ ] `docker-compose.yml`: `postgres:16` on 5433, `elasticsearch:8.15.3` on 9201 (same versions as the updater), with healthchecks and named volumes.
- [ ] Postgres init scripts (`/docker-entrypoint-initdb.d`):
  1. DDL that mirrors the updater schema (see CONTEXT.md). This is needed because `validate` requires the tables to exist, and only the updater creates them. Once the updater has Flyway (X1), copy or reuse its migrations instead of hand-written DDL.
  2. Create the read-only role (`GRANT SELECT` on all tables, plus default privileges).
  3. Seed data SQL.
- [ ] ES seed: index mappings (copy the updater's; add kuromoji/ICU fields once X2 lands, which needs a custom ES image with the plugins) and a bulk JSON file, loaded by a one-shot `curl` container.
- [ ] Seed dataset (a few hundred rows) covering edge cases: multi-reading word, kana-only word, conjugatable verb (る/う verbs), `common` vs non-common, slang misc tag, kanji with radicals and stroke paths, kana-only name, sentence whose `index_words` hit seeded vocab.
- [ ] Document the "full load" steps: run the updater with `spring.datasource.url=...:5433/...` and `spring.elasticsearch.uris=...:9201`.

**`shared` module (D13)**
- [ ] `shared/package-info.java` with `@ApplicationModule(type = ApplicationModule.Type.OPEN)`.
- [ ] `SecurityConfig`: `permitAll` on `/api/**`, Swagger UI, and `/actuator/health|info`. Deny all else. Stateless, CSRF off, CORS from the allow-list (D8).
- [ ] `@RestControllerAdvice` returning `ProblemDetail` (RFC 9457) for 400/404/500.
- [ ] Logging/tracing config (request id / correlation id filter). Leave the tracing backend open (see O4).
- [ ] Pagination convention: `page`, `size` (max cap) → a page DTO.

**Tests / housekeeping**
- [ ] `ModularityTests`: `ApplicationModules.of(JapaneseDictionaryServicesApplication.class).verify()` (D9).
- [ ] `contextLoads` needs a live DB/ES. Either `@Disabled` it (as the updater did) or leave it until later tests (D22).
- [ ] Rename `vocabularyRepository` → `VocabularyRepository`. Prefer extending `Repository<VocabEntry, Long>` with `find*` methods only, so no `save`/`delete` is exposed (read-only by design).

## Phase 1 — vocabulary

- [ ] `models/`: `VocabEntry`, `Reading`, `Sense` as `@Immutable` entities mapping the updater tables (D2). Collections are `sense_gloss`, `sense_pos`, `sense_misc`.
- [ ] `repository/`: lookup by `ent_seq`. Fetch readings and senses without N+1 (entity graph or join fetch).
- [ ] `integration/`: read-only `VocabDocument` (index `vocab`) + an ES query client that returns **candidates** with a match tier (exact on `kanji`/`readings`, prefix, gloss full-text).
- [ ] `services/`: public API used by `search`: `findByEntSeq`, `findCandidates(query, …)`.
- [ ] `dto/`: entry detail (readings split kanji/kana, senses with glosses/pos/misc, common).
- [ ] `controller/`: `GET /api/v1/vocabulary/{entSeq}`, `GET /api/v1/vocabulary?q=&page=&size=`.
- [ ] Controller unit tests (MockMvc + Mockito + Hamcrest) (D22).

## Phase 2 — kanji

- [ ] `models/`: `Kanji` (`text[]` columns via `@JdbcTypeCode(SqlTypes.ARRAY)`), `Radical`, `kanji_radical` many-to-many.
- [ ] Endpoints: `GET /api/v1/kanji/{character}`, `GET /api/v1/kanji?radicals=…` (radical search: kanji containing **all** selected radicals), `GET /api/v1/radicals` (radical picker, grouped by stroke count). Optional filters: stroke count, grade, joyo.
- [ ] Return `stroke_paths` for stroke-order display.
- [ ] Service API for `search`: lookup by character(s) contained in the query.
- [ ] Controller unit tests.
- Later: draw input / handwriting search (see Later modules).

## Phase 3 — names

- [ ] `models/`: `NameEntry` (`text[]` columns). Names stay Postgres-only (D18).
- [ ] Endpoints: `GET /api/v1/names/{entSeq}`, `GET /api/v1/names?q=` (match on kanji / readings / translations).
- [ ] Measure query speed on the full ~740k rows. If slow, request indexes (GIN on arrays / `pg_trgm`) via the updater's Flyway (X5), because this service can't create indexes.
- [ ] Service API for `search`, plus controller unit tests.

## Phase 4 — sentences

- [ ] `models/`: `ExampleSentence`. `integration/`: read-only `ExampleSentenceDocument` (index `example-sentence`).
- [ ] Endpoints: `GET /api/v1/sentences/{id}`, `GET /api/v1/sentences?word=` (by `indexWords`), `GET /api/v1/sentences?q=` (Japanese/English text).
- [ ] Service API for `search` and for a vocab detail ("example sentences for this word"), plus controller unit tests.

## Phase 5 — normalization

Decide **O2** first (in-house vs library).
- [ ] Input converter: romaji → hiragana/katakana (edge cases: ん before vowels/y, っ doubling, long vowels, wapuro vs Hepburn).
- [ ] Era-year conversion: 令和/平成/昭和/大正/明治 ↔ Gregorian (e.g. 令和7年 ↔ 2025).
- [ ] Number conversion: kanji numerals ↔ digits (e.g. 二千二十五 ↔ 2025, including 万/億).
- [ ] Public service API. Endpoints are optional (e.g. `GET /api/v1/convert?...`). Controller unit tests if endpoints are added.

## Phase 6 — parsing

Decide **O1** first (tokenizer library).
- [ ] Input identification: English / romaji / Japanese (kana, kanji, mixed) / likely name.
- [ ] Inflection parse: conjugated form → candidate dictionary forms + the inflection chain (e.g. 食べられなかった → 食べる, potential + negative + past).
- [ ] Sentence parse: split a sentence into words for per-word lookup.
- [ ] Public service API, plus controller unit tests if endpoints are added.

## Phase 7 — search (orchestrator)

- [ ] `GET /api/v1/search?q=&types=vocabulary,kanji,names,sentences&page=&size=`.
- [ ] Flow: `parsing` identifies the input → `normalization` converts it → `parsing` deinflects → call each selected module's service → collect candidates → rank (D10).
- [ ] Ranking (D17): one deterministic comparator. Order: match tier (exact > deinflected > prefix > partial) → `common` / frequency → type priority, adjusted by the detected input type. ES scores order results only **within** a module.
- [ ] Tag search (diagram V03: e.g. filter by POS/misc tags). Details TBD when reached.
- [ ] Controller unit tests. The ranking comparator is pure logic and suits plain unit tests.

## Phase 8 — lists (default lists, built last)

- [ ] Lists computable from current data: common words (`common`), part of speech (`sense_pos`), expressions (pos `exp`), slang (misc tag), kanji lists (grade / joyo / old JLPT / frequency).
- [ ] Lists blocked on the updater: **subject** (needs JMdict `<field>`, X3), **dialect** (needs `<dial>`, X3), **words by JLPT** (needs a word-level JLPT source, O3).
- [ ] Endpoints: `GET /api/v1/lists`, `GET /api/v1/lists/{id}?page=&size=`.
- Note: user-made lists belong to the future user service (D1). It can reference default lists by id.

---

## Cross-repo tasks (for `japanese-dictionary-updater-component`)

Tracked here so they're not forgotten. Do them in the updater repo.

| # | Task | Needed by |
|---|---|---|
| X1 | Replace `ddl-auto=update` with **Flyway** migrations. The updater owns the schema (D3) | Phase 0 (DDL reuse), schema stability |
| X2 | Add **kuromoji + ICU** multi-field mappings to `vocab` and `example-sentence` (keyword exact / kuromoji tokenized / edge-ngram prefix). Use a custom ES image with the plugins (D14) | Phases 1, 4, 7 (match quality) |
| X3 | Parse and store JMdict `<field>` (subject) and `<dial>` (dialect) tags on `Sense` (same pattern as `miscTags`), then re-import | Phase 8 |
| X4 | Update stale `NEXT_STEPS.md` (Kanji + JMnedict spokes are implemented) | housekeeping |
| X5 | (If needed) GIN / `pg_trgm` indexes on `name_entry` arrays via Flyway | Phase 3 |
| X6 | (Later) word-level JLPT source ingestion, once O3 is decided | Phase 8 |

---

## Decision log

Decided 2026-10-28 in a grilling session unless noted. Format: decision, then reason.

| # | Decision | Reason |
|---|---|---|
| D0 | **Postgres**, not MySQL (diagram V03 is outdated). The updater is the only writer; ES holds `vocab` + `example-sentence`. Services share only the DBs _(decided during the updater build, 2026-09-19)_ | Single source of truth; spokes decoupled |
| D1 | **Dictionary-only** scope. User lists, history, import/export → a future separate service | Clean boundaries, consistent with splitting repos per application |
| D2 | **Own read-only copies** of the entities/ES documents (`@Immutable`), not a shared library | Repos stay independently buildable/deployable; read shapes can differ |
| D3 | The **updater owns the schema** and moves to Flyway; this service uses `ddl-auto=validate` | One owner; drift fails at startup, not at query time |
| D4 | Java versions **independent per repo** (25 here, 26 in the updater) | Repos are independent; user's call |
| D5 | **Remove Spring Cloud Config** until a config server exists | No server; it adds startup noise for nothing |
| D6 | Package names **unchanged** (`baddee` here, `badee` in the updater) | User's call; harmless |
| D7 | **Package-by-feature** with `controller/dto/integration/models/repository/services` per module | Matches the modular monolith in V03; already scaffolded |
| D8 | **Public reads + hardening**: `permitAll` on `/api/**`, CORS allow-list, stateless, CSRF off, Actuator health/info only. Rate limiting later at the edge (API Gateway / CloudFront / WAF) | Public dictionary; no user auth in this service (D1) |
| D9 | **Spring Modulith** + `verify()` test | Enforces module boundaries in the build |
| D10 | `search` is the **orchestrator**; each module owns its own queries and exposes a service API | Single search bar; no cross-module table access |
| D11 | `lists` = **read-only default lists** only | Follows D1 |
| D12 | `integration/` = ES docs/clients + cross-module adapters + module plumbing (logging, tracing) | Keeps controller/services clean |
| D13 | **`shared` OPEN module** for app-wide config (security, CORS, errors, tracing) | Idiomatic Modulith; `verify()` stays green |
| D14 | The updater adds **kuromoji + ICU** analyzers (X2) | The analyzer controls candidate quality; `search` ranks on top |
| D16 | **Own compose file** in this repo | Self-contained dev setup |
| D17 | `search` ranks with **own tiered rules**, not raw ES scores | Scores are not comparable across indexes and Postgres |
| D18 | **Names stay Postgres-only** | Keep the updater's architecture |
| D19 | Compose on **5433/9201** + **committed seed data** + optional full load via the updater | Fast, repeatable dev data; runs beside the updater's stack |
| D20 | **REST `/api/v1`** + springdoc OpenAPI + page/size pagination | Simple for any client; typed contract |
| D22 | Testing **now**: controller-level unit tests per module (MockMvc, Mockito, Hamcrest) + Modulith `verify()`. **Later**: other testing (integration/Testcontainers with seed data, slice tests). The user adds these later | User's call |
| D23 | **Read-only Postgres role** (and a read-only ES key in production) | Enforces read-only at the DB level |
| D24 | Build order as in [Build order](#build-order). Parsing comes before search | User's call |

## Open decisions

Each has Claude's current advice. Settle each one at the phase shown.

| # | Question | Advice | Decide at |
|---|---|---|---|
| O1 (Q15) | Tokenizer / deinflection library | **Lucene Kuromoji** (`lucene-analysis-kuromoji`, pure Java, same segmentation as ES kuromoji) + a **rule-based deinflector** (Yomitan-style rule table). Alternative: Sudachi (better dictionary, heavier, mismatches ES unless the Sudachi ES plugin is used) | Phase 6 |
| O2 (Q25) | Normalization: in-house vs library | **In-house, table-driven, unit-tested.** The mappings are small and Java libraries are thin. Alternative: ICU4J transliterators (large dependency, harder to tune) | Phase 5 |
| O3 (Q21) | Word-level JLPT data source | No official list exists. Community lists (e.g. Tanos / Jonathan Waller) are the usual choice; this needs an updater spoke (X6) | Phase 8 |
| O4 (Q26) | Deployment: AWS hosting, Elasticsearch vs Amazon OpenSearch (supports kuromoji/ICU), Dockerfile, CI/CD, tracing backend | Defer. Decide together with the updater repo | Before first deploy |

## Later (not scheduled)

- **Testing** beyond controller unit tests (D22): Testcontainers integration tests reusing the seed data, JPA/ES slice tests.
- **Later API modules** from diagram V03: OCR from image, slang dataset, kanji mastery map, word of the day, recommended immersion resources, furigana display, drawing (handwriting) kanji search. Achievements and community features likely belong in the user service.
- **Data-model ideas not yet sourced** (`written_reports/Archived/japanese_app_data_model.puml`): pitch accent, frequency data (Kanjium / Innocent Corpus), grammar points (Hanabira), audio. Each needs a new updater spoke first.
- Possible enrichment: resolve `example_sentence.index_words` to `vocab_entry` ids (in the updater).
