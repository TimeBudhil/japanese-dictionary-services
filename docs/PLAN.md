# japanese-dictionary-services — Plan

Tasks, implementation details, and decisions. Background facts are in [`CONTEXT.md`](./CONTEXT.md).

_Last updated: 2026-11-02_

**How to use this file**
- Work phases in order. Tick tasks with `[x]` when done.
- New decisions: add them to the [Decision log](#decision-log) with the reason.
- Before a phase that depends on an [open decision](#open-decisions), settle it first.
  Workflow: Claude gives its advice, and the user picks in the CLI selector.

## Status

- Phase 0 code is written and `./mvnw test` is green (Modulith `verify()`; `contextLoads` `@Disabled`).
  It was written on a machine **without Docker** (2026-11-02), so two Phase 0 boxes stay open:
  the **seed dataset** and **verify on a Docker machine** (see CONTEXT.md, "Pending verification").
- **Next: Phase 1 (vocabulary).** It can start now. Do the two open Phase 0 boxes when a Docker machine is available.

## Build order

Phase 0 foundation → 1 vocabulary → 2 kanji → 3 names → 4 sentences →
5 normalization → 6 parsing → 7 search → 8 lists

---

## Phase 0 — Foundation

**pom.xml**
- [x] Remove `spring-cloud-starter-config`, the `spring-cloud.version` property, and the `spring-cloud-dependencies` BOM (D5).
- [x] Add the Postgres driver (`org.postgresql:postgresql`, runtime).
- [x] Add Spring Modulith (`spring-modulith-starter-core`, `spring-modulith-starter-test`, plus the `spring-modulith-bom`). **2.1.2**, built against Boot 4.1.2 (D9).
- [x] Add `springdoc-openapi-starter-webmvc-ui`. **3.2.0**, parent Boot 4.1.2 (D20).
- [x] Add `spring-boot-starter-validation` (for `@Min/@Max` on `page`/`size`, D28).

**Config (`application.properties`)**
- [x] Datasource `jdbc:postgresql://localhost:5433/dictionary` with the **read-only role** `dictionary_reader` (D23), via env vars with local defaults (D27).
- [x] `spring.jpa.hibernate.ddl-auto=validate` (D3), `spring.jpa.open-in-view=false`.
- [x] `spring.elasticsearch.uris=http://localhost:9201`.
- [x] Actuator: expose `health,info` only.
- [x] CORS allow-list property `app.cors.allowed-origins` (one key for all modules, D25).
- [x] `spring.mvc.problemdetails.enabled=true` (D29), `spring.data.web.pageable.serialization-mode=via-dto` (D28), `logging.pattern.correlation` with `requestId` (D30).

**Local infra (D16, D19)**
- [x] `docker-compose.yml`: `postgres:16` on 5433, `elasticsearch:8.15.3` on 9201 (same versions as the updater), with healthchecks and named volumes, plus a one-shot `elasticsearch-init` curl container.
- [x] Postgres init scripts (`docker/postgres/init/`):
  1. [x] `01-schema.sql`: DDL that mirrors the updater schema, **generated** from the updater's entities with Hibernate (D31). Once the updater has Flyway (X1), reuse its migrations instead.
  2. [x] `02-readonly-role.sh`: read-only role (`GRANT SELECT` on all tables, plus default privileges). Large objects need per-object grants (X7).
  3. [ ] `03-seed.sql`: seed data. Deferred to a Docker machine (D32). Must `GRANT SELECT ON LARGE OBJECT` for each sentence LO it creates.
- [x] ES: index mappings in `docker/elasticsearch/mappings/` (generated from the updater's documents, D31; add kuromoji/ICU fields once X2 lands, which needs a custom ES image with the plugins), loaded by `docker/elasticsearch/init.sh`. It also bulk-loads `docker/elasticsearch/seed/<index>.ndjson` if present.
- [ ] Seed dataset (Postgres SQL + ES ndjson) covering edge cases: multi-reading word, kana-only word, conjugatable verb (る/う verbs), `common` vs non-common, slang misc tag, kanji with radicals and stroke paths, kana-only name, sentence whose `index_words` hit seeded vocab. **Deferred to a Docker machine (D32).**
- [x] Document the "full load" steps (CONTEXT.md, "Running locally").
- [ ] **Verify on a Docker machine**: the 4 checks in CONTEXT.md, "Pending verification" (schema vs real `pg_dump`, ES mappings, app boots with `validate`, read-only role). Then remove that section.

**App-level, root package (D25; replaces the `shared` module of D13)**
- [x] `SecurityConfig`: catch-all chain, `@Order(LOWEST_PRECEDENCE)`. Swagger UI/API docs, `/actuator/health|info` and `/error` open; deny all else. Stateless, CSRF/basic/form login off (D8). Each module's own chain + CORS comes with its first controller.
- [x] Errors: `UnexpectedErrorHandler` (last-resort 500 `ProblemDetail`, RFC 9457). 400/404 come from Boot's ProblemDetail handler and the modules' own `ErrorResponseException`s (D29).
- [x] `RequestIdFilter`: `X-Request-Id` in/out, MDC key `requestId`. The tracing backend stays open (O4) (D30).
- [x] Pagination convention: `page`/`size` with `@Min/@Max` per controller (default 20, max 100, over max → 400), `Page<Dto>` serialized `via-dto` (D28).

**Tests / housekeeping**
- [x] `ModularityTests`: `ApplicationModules.of(JapaneseDictionaryServicesApplication.class).verify()` (D9).
- [x] `contextLoads` is `@Disabled` (needs a live DB/ES) (D22).
- [x] Rename `vocabularyRepository` → `VocabularyRepository` (empty interface for now). In Phase 1, extend `Repository<VocabEntry, Long>` with `find*` methods only, so no `save`/`delete` is exposed (read-only by design).

## Phase 1 — vocabulary

- [ ] `models/`: `VocabEntry`, `Reading`, `Sense` as `@Immutable` entities mapping the updater tables (D2). Collections are `sense_gloss`, `sense_pos`, `sense_misc`.
- [ ] `repository/`: lookup by `ent_seq`. Fetch readings and senses without N+1 (entity graph or join fetch).
- [ ] `integration/`: read-only `VocabDocument` (index `vocab`) + an ES query client that returns **candidates** with a match tier (exact on `kanji`/`readings`, prefix, gloss full-text).
- [ ] `services/`: public API used by `search`: `findByEntSeq`, `findCandidates(query, …)`.
- [ ] `dto/`: entry detail (readings split kanji/kana, senses with glosses/pos/misc, common).
- [ ] `controller/`: `GET /api/v1/vocabulary/{entSeq}`, `GET /api/v1/vocabulary?q=&page=&size=` (pagination per D28).
- [ ] `integration/`: the module's `SecurityFilterChain` (`securityMatcher("/api/v1/vocabulary/**")`, `permitAll`, stateless, CSRF off, `@Order` before the root chain) and its CORS (`GET/HEAD/OPTIONS`, origins from `app.cors.allowed-origins`, expose `X-Request-Id`) (D25). This is the template for the other modules.
- [ ] Exceptions: e.g. `VocabEntryNotFoundException extends ErrorResponseException` (404) (D29).
- [ ] `aop/`: logging aspect for the module's service calls (D30).
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
| X7 | Map `example_sentence.japanese/english/index_words` to `text`, not `@Lob` (which makes Postgres `oid` large objects), e.g. `@JdbcTypeCode(SqlTypes.LONGVARCHAR)`. Do it as a Flyway migration (X1). Then update this repo's `01-schema.sql` and drop the per-LO grants. Reason: large objects need per-object `GRANT`s (no default privileges in Postgres 16), so the read-only role can't read full-load sentences | Phase 4 |

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
| D7 | **Package-by-feature** with `aop/controller/dto/integration/models/repository/services` per module (`aop/` added 2026-11-02, D30) | Matches the modular monolith in V03; already scaffolded |
| D8 | **Public reads + hardening**: `permitAll` on `/api/**`, CORS allow-list, stateless, CSRF off, Actuator health/info only. Rate limiting later at the edge (API Gateway / CloudFront / WAF). **Amended by D25** (2026-11-02): each module's own chain `permitAll`s its `/api/v1/<module>/**` and sets its CORS; the root catch-all chain denies `/api/**` paths no module claims, so no `/api` request succeeds until a module's chain exists (Phase 1) | Public dictionary; no user auth in this service (D1) |
| D9 | **Spring Modulith** + `verify()` test | Enforces module boundaries in the build |
| D10 | `search` is the **orchestrator**; each module owns its own queries and exposes a service API | Single search bar; no cross-module table access |
| D11 | `lists` = **read-only default lists** only | Follows D1 |
| D12 | `integration/` = ES docs/clients + cross-module adapters + module plumbing (tracing, the module's security chain + CORS, D25). Logging aspects go in `aop/` (D30) | Keeps controller/services clean |
| D13 | ~~**`shared` OPEN module** for app-wide config (security, CORS, errors, tracing)~~ **Superseded by D25** (2026-11-02) | Idiomatic Modulith; `verify()` stays green |
| D14 | The updater adds **kuromoji + ICU** analyzers (X2) | The analyzer controls candidate quality; `search` ranks on top |
| D16 | **Own compose file** in this repo | Self-contained dev setup |
| D17 | `search` ranks with **own tiered rules**, not raw ES scores | Scores are not comparable across indexes and Postgres |
| D18 | **Names stay Postgres-only** | Keep the updater's architecture |
| D19 | Compose on **5433/9201** + **committed seed data** + optional full load via the updater. **Partly done** (2026-11-02): compose, schema, role and index setup exist; the seed data is deferred (D32) | Fast, repeatable dev data; runs beside the updater's stack |
| D20 | **REST `/api/v1`** + springdoc OpenAPI + page/size pagination | Simple for any client; typed contract |
| D22 | Testing **now**: controller-level unit tests per module (MockMvc, Mockito, Hamcrest) + Modulith `verify()`. **Later**: other testing (integration/Testcontainers with seed data, slice tests). The user adds these later | User's call |
| D23 | **Read-only Postgres role** (and a read-only ES key in production) | Enforces read-only at the DB level |
| D24 | Build order as in [Build order](#build-order). Parsing comes before search | User's call |
| D25 | _(2026-11-02)_ **No `shared` module.** Pieces that act before any module go in the **root package** (not a module, imported by no module): catch-all `SecurityConfig`, `RequestIdFilter`, `UnexpectedErrorHandler`. Each module owns its `SecurityFilterChain` + CORS in `integration/` (written with its first controller), reading the one key `app.cors.allowed-origins`. Fully per-module was considered: it works, but it loses the deny-all net for paths no module owns, and Boot's default login locks every URL until the first module chain exists | User wants no shared infrastructure that modules rely on; the root pieces are app-level, not module code |
| D26 | _(2026-11-02)_ Mirror the updater's `oid` (large object) sentence columns exactly; fix them in the updater (X7) | `validate` must match the real schema; the read-only role can't use default privileges on LOs |
| D27 | _(2026-11-02)_ Connection settings are **env vars with local defaults** (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `ES_URIS`, `CORS_ALLOWED_ORIGINS`). Only local dev credentials are committed | Works out of the box locally; production overrides without code changes |
| D28 | _(2026-11-02)_ Pagination: `page` (`@Min(0)`, default 0) + `size` (`@Min(1) @Max(100)`, default 20) on each controller. Over max → **400**, not clamped. Return `Page<Dto>`, serialized `via-dto` | Clear contract; no shared pagination classes |
| D29 | _(2026-11-02)_ Errors: **modules own their exceptions** (extend `ErrorResponseException`). `spring.mvc.problemdetails.enabled=true` renders them and Spring's own errors as RFC 9457. A root `UnexpectedErrorHandler` covers unexpected 500s | The root never needs to know any module; one format for the whole API |
| D30 | _(2026-11-02)_ Logging: root `RequestIdFilter` (`X-Request-Id`, MDC `requestId`). Each module gets an **`aop/`** folder for its own logging aspects, which read the id via `MDC.get("requestId")` | A request id must exist before any module runs; module logging stays per-module (firmer modular design, accepting repetition) |
| D31 | _(2026-11-02)_ Schema DDL and ES mappings are **generated offline** from the updater's classes (Hibernate schema export; Spring Data ES `MappingBuilder`), not hand-written. Verify against a real DB later | No Docker on the dev machine; hand-written DDL had already missed the `oid` columns |
| D32 | _(2026-11-02)_ **Seed dataset deferred** until a machine with Docker is available | Seed data should be checked against a running stack |
| D33 | _(2026-11-02)_ **Security is not Cognito's job here.** Cognito authenticates users; this API has none (D1). Cognito belongs to the future user service, the updater's admin API, or an API Gateway in front (O4). Removing Spring Security entirely was considered and rejected; the per-module chains stay | Keep in-app hardening (deny-all net, security headers) |

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
