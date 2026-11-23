# japanese-dictionary-services — History

Archive. Not read by default (see `CLAUDE.md`). Newest entry first; entries are never edited.
Every decision id (`D<n>`, `X<n>`, `O<n>`) that left `PLAN.md` keeps its full text here.

## 2026-11-14 — Docs restructure (D78)

Archived word for word from `docs/PLAN.md` at commit `04462ac`: the status, the finished
phases 0 and 1 with their verification notes, the cross-repo task table, and the full decision
log (live decisions are repeated in short form in `PLAN.md`). Smaller choices (D38–D56, D58,
D60, D63, D72, …) and superseded ones (D13, D26, D31, D61, D62, D64, D66, D70) live only here.

### Status as of 2026-11-14


- Phase 0 is **done** and verified on a Docker machine _(2026-11-14)_: schema, ES mappings, app boot with `validate`, read-only role, seed dataset.
- Phase 1 (vocabulary) is **done** and verified against a full updater load _(2026-11-14)_: `validate` with the order columns, real ES queries, 6 SQL queries per 100-entry page, stable paging. `./mvnw test` is green (Modulith `verify()` + 21 controller tests).
- The Docker run found and fixed bugs in both repos (ES client/server versions, updater boot, column sizes, CRLF scripts). See X8, X9 and D57–D60.
- The updater repo was missing five commits of its own history (Flyway, retirement, FK indexes, profiles, integration tests). They were restored, plus X8 as Flyway V4 (D73, committed on branch `updater-history-and-sense-order`, not merged into `main` yet). This repo now follows Flyway: two stack modes (seed / empty for full loads, D74), `schema.sql` = the migrations, no large objects (X7). Phase 0 and 1 were re-checked on the Flyway-built schema (see the Phase 0 verify box).
- **Next: Phase 2 (kanji). A new session starts at [Phase 2, "Start here"](#start-here-handoff-written-2026-11-14).** Open decisions O5–O9 (Phase 1 template choices) are deferred by the user _(2026-11-13)_; the current behaviour stays until they are decided. O11–O14 (kanji design) are new and come first in Phase 2.

### Phase 0 — Foundation

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
- [x] `docker-compose.yml`: `postgres:16` on 5433, `elasticsearch:9.4.8` on 9201 (same versions as the updater; was 8.15.3, see D57), with healthchecks and named volumes, plus a one-shot `elasticsearch-init` curl container.
- [x] Postgres init scripts (`docker/postgres/init/`):
  1. [x] `docker/postgres/seed/schema.sql` (was `init/01-schema.sql`): the updater's Flyway migrations V1–V4, concatenated by `sh docker/generate-schema.sh` (D74). Before the port it was generated from the updater's entities with Hibernate (D31).
  2. [x] `02-readonly-role.sh`: read-only role (`GRANT SELECT` on all tables, plus default privileges). Large objects need per-object grants (X7).
  3. [x] `docker/postgres/seed/seed.sql` (was `init/03-seed.sql`): seed data, **generated** by `docker/generate-seed.py` (D59). Both run from `init/01-seed-data.sh`, in seed mode only (D74). Sentences are plain `text` since X7, so no large-object grants.
- [x] ES: index mappings in `docker/elasticsearch/mappings/` (generated from the updater's documents, D31; add kuromoji/ICU fields once X2 lands, which needs a custom ES image with the plugins), loaded by `docker/elasticsearch/init.sh`. In seed mode it also bulk-loads `docker/elasticsearch/seed/<index>.ndjson` into an index it has just created (D72, D74).
- [x] Seed dataset (Postgres SQL + ES ndjson) covering edge cases: multi-reading word, kana-only word, conjugatable verb (る/う verbs), `common` vs non-common, slang misc tag, kanji with radicals and stroke paths, kana-only name, sentence whose `index_words` hit seeded vocab. Plus: two entries with the same kana (two exact hits for `あの`), a gloss over 255 characters. _(2026-11-14: 11 entries, 8 kanji, 2 names, 2 sentences, copied from a full load by `docker/generate-seed.py`, D59)_ Do not copy ids from `VocabularyControllerTests` (`1358280` 食べる, `1415370` たばこ): they were written from memory and are unchecked against JMdict; take real ids from the loaded data. _(2026-11-14: all seed ids come from the loaded data. `1358280` is really 食べる there (食べる / 喰べる / たべる), so the seed keeps it. `1415370` たばこ is still unchecked; it is not in the seed.)_
- [x] Document the "full load" steps (CONTEXT.md, "Running locally").
- [x] **Verify on a Docker machine** _(2026-11-14)_, all 4 pass (the CONTEXT.md section is removed):
  1. Schema: an updater booted on an empty Postgres matches `01-schema.sql` (`pg_dump --schema-only`; only column order and FK names differ). The other way round, the updater's `ddl-auto=update` makes no changes to a DB built from `01-schema.sql`. Fixes: Hibernate's `CHECK (x_order >= 0)` on the order columns; `sense_gloss.gloss` and `kanji.stroke_paths` are now `text` (X9).
  2. ES mappings: the live `vocab` and `example-sentence` mappings match the committed ones, plus the expected dynamic `id` (`text` + `id.keyword`). `vocab.json` now declares `id` (D58).
  3. The app boots with `validate`, `/actuator/health` is UP, `/swagger-ui.html` loads (on ES 9.4.8, D57).
  4. `dictionary_reader`: `SELECT` works, `INSERT` and `CREATE TABLE` are denied, seeded sentence large objects are readable (large objects are gone since X7).
  5. **Re-checked after the port** _(2026-11-14)_, on the Flyway-built schema (V1–V4): seed mode builds `schema.sql` + `seed.sql` without errors and the app passes `validate`; empty mode leaves the DB empty, Flyway applies V1–V4, a full load passes (219,080 entries, 743,691 names, 13,108 kanji, 147,836 sentences, ~16 min); the app passes `validate` on it, `dictionary_reader` reads sentence text directly, a 100-entry page still costs 6 SQL queries. The regenerated seed equals the old one except the sentence ids (now the Japanese Tatoeba ids, `170682` / `207310`).

**App-level, root package (D25; replaces the `shared` module of D13)**
- [x] `SecurityConfig`: catch-all chain, `@Order(LOWEST_PRECEDENCE)`. Swagger UI/API docs, `/actuator/health|info` and `/error` open; deny all else. Stateless, CSRF/basic/form login off (D8). Each module's own chain + CORS comes with its first controller.
- [x] Errors: `UnexpectedErrorHandler` (last-resort 500 `ProblemDetail`, RFC 9457). 400/404 come from Boot's ProblemDetail handler and the modules' own `ErrorResponseException`s (D29).
- [x] `RequestIdFilter`: `X-Request-Id` in/out, MDC key `requestId`. The tracing backend stays open (O4) (D30).
- [x] Pagination convention: `page`/`size` with `@Min/@Max` per controller (default 20, max 100, over max → 400), `Page<Dto>` serialized `via-dto` (D28).

**Tests / housekeeping**
- [x] `ModularityTests`: `ApplicationModules.of(JapaneseDictionaryServicesApplication.class).verify()` (D9).
- [x] `contextLoads` is `@Disabled` (needs a live DB/ES) (D22).
- [x] Rename `vocabularyRepository` → `VocabularyRepository` (empty interface for now). In Phase 1, extend `Repository<VocabEntry, Long>` with `find*` methods only, so no `save`/`delete` is exposed (read-only by design).

### Phase 1 — vocabulary

- [x] `models/`: `VocabEntry`, `Reading`, `Sense` as `@Immutable` entities mapping the updater tables (D2). Collections are `sense_gloss`, `sense_pos`, `sense_misc`. Children are one-way `@OneToMany @JoinColumn(name="ent_seq")` with `@OrderBy("id")`; getters return unmodifiable lists. `Sense` lists have `@OrderColumn` (`gloss_order`/`pos_order`/`misc_order`), **assuming X8** (D35). Not yet checked by `validate` (needs Docker).
- [x] `repository/`: `findByEntSeq`, `findByEntSeqIn` on `Repository<VocabEntry, Long>`. No N+1: `readings`/`senses` use `@BatchSize(100)` (counts entries), the `Sense` lists use `@Fetch(SUBSELECT)` (one query per list, whatever the sense count), so a page of up to 100 entries costs 6 queries. `common`/`kanji` are `Boolean`, null reads as false. Not join fetch / entity graph: two `List`s in one fetch throw `MultipleBagFetchException` and multiply rows (D35).
- [x] `integration/`: read-only `VocabDocument` (index `vocab`, `createIndex=false`) + `VocabSearchClient` that returns `Page<VocabCandidate>` (`entSeq`, `MatchTier`, `common`) (exact on `kanji`/`readings`, prefix, gloss full-text) (D36). `MatchTier` and `VocabCandidate` are in the module base package (D37). Query JSON checked offline; not yet run against ES.
- [x] Public API (D37): `VocabularyApi` in the base package: `findByEntSeq` (`Optional`), `findAllByEntSeq(List)` (keeps input order), `findCandidates`, `searchEntries` (candidates → full entries, ES total). Implemented by `services/VocabularyService`; Postgres work is in `services/VocabEntryReader` (`@Transactional(readOnly)`), so the ES call holds no DB connection.
- [x] Entry detail type: `VocabEntryDetail` (`entSeq`, `common`, `kanji`, `readings`, `senses`) + `VocabSense` (`glosses`, `partsOfSpeech`, `miscTags`), records in the base package (D37). Written with Step 4, because the API returns them.
- [x] Deep-page guard: ES rejects `from + size > 10,000` (`index.max_result_window`). `VocabSearchClient` throws `VocabPageTooDeepException` (400, base package so `search` can catch it) before calling ES. A blank query is checked first and returns an empty page. A caller's `Sort` is dropped (page number + size only).
- [x] `controller/`: `GET /api/v1/vocabulary/{entSeq}` (404 if unknown), `GET /api/v1/vocabulary?q=&page=&size=` (`q` `@NotBlank`, max 100 chars; pagination per D28). Returns `VocabEntryDetail` / `Page<VocabEntryDetail>`; depends only on `VocabularyApi`. **Unreachable until the security chain below exists** (root chain denies `/api/**`).
- [x] _(done: `integration/VocabularySecurityConfig`, `@Order(100)` shared by all modules since paths don't overlap; CORS source built per chain, not a bean; allowed + exposed header `X-Request-Id`, no credentials, max age 1h)_ `integration/`: the module's `SecurityFilterChain` (`securityMatcher("/api/v1/vocabulary/**")`, `permitAll`, stateless, CSRF off, `@Order` before the root chain) and its CORS (`GET/HEAD/OPTIONS`, origins from `app.cors.allowed-origins`, expose `X-Request-Id`) (D25). This is the template for the other modules.
- [x] Exceptions (D29): `controller/VocabEntryNotFoundException` (404, controller-only), `VocabPageTooDeepException` (400, base package: thrown by `VocabularyApi`, D37). Both extend `ErrorResponseException`.
- [x] `aop/`: logging aspect for the module's service calls (D30). `VocabularyLoggingAspect` wraps every `VocabularyApi` method: DEBUG with time, WARN over 500 ms, DEBUG on failure (type only, rethrown). No arguments logged. Request id comes from `logging.pattern.correlation`, not added by hand. AspectJ comes in through `spring-boot-starter-data-jpa`.
- [x] Controller unit tests (MockMvc + Mockito + Hamcrest) (D22). `VocabularyControllerTests`: `@WebMvcTest` + `@MockitoBean VocabularyApi`, with the **real** root + module security chains imported. 21 tests: JSON shape, 404/400/500 ProblemDetails, validation (`q`, `page`, `size`), `via-dto` page metadata, root chain denies other `/api` paths, CORS preflight allow/deny, `X-Request-Id` echo + exposed. Template for the other modules.
- [x] **Verify on a Docker machine** _(2026-11-14, full updater load: 219,080 entries)_:
  1. `validate` passes for the vocab entities, with X8's order columns (now real in the updater).
  2. The query JSON works against the real `vocab` index (exact / prefix / gloss tiers; `q=日本` → 日本, then prefix hits; unknown id → 404 ProblemDetail; page 100 × 100 → 400).
  3. `searchEntries` with `size=100` costs **6** SQL queries, checked with `q=あ` (223 senses), `q=生` (166) and `q=run` (106).
  4. Stable paging: `id.keyword` (unique, from the dynamic `id` field) is now the last sort key in `VocabSearchClient`, with `unmapped_type: keyword` so an empty index does not fail (D58). `q=あ` and `q=生`, pages 0–2 × 100: 300 unique ids, same order on repeat.
  - Seen, not fixed: English queries have no exact tier until X2 (D36), so `q=cat` ranks 猫タワー above 猫. POS/misc are stored as the expanded DTD text (`noun (common) (futsuumeishi)`), not JMdict codes (`n`); see X10.
- Known limitation (review #7): if an id is in ES but not in Postgres, `searchEntries` skips it, and on the **last** page `PageImpl` recomputes `totalElements` from the shorter content. Only happens when the stores are out of sync. Documented on `VocabularyApi.searchEntries`; no fix planned.

### Cross-repo tasks (for `japanese-dictionary-updater-component`)

Tracked here so they're not forgotten. Do them in the updater repo.

| # | Task | Needed by |
|---|---|---|
| X1 | ✅ _Done in the updater (Flyway V1–V3 from its restored history, V4 for X8; ported 2026-11-14, D73). This repo reuses the migrations (D74)._ Replace `ddl-auto=update` with **Flyway** migrations. The updater owns the schema (D3) | Phase 0 (DDL reuse), schema stability |
| X2 | Add **kuromoji + ICU** multi-field mappings to `vocab` and `example-sentence` (keyword exact / kuromoji tokenized / edge-ngram prefix). Use a custom ES image with the plugins (D14) | Phases 1, 4, 7 (match quality) |
| X3 | Parse and store JMdict `<field>` (subject) and `<dial>` (dialect) tags on `Sense` (same pattern as `miscTags`), then re-import | Phase 8 |
| X4 | ✅ _Done: the ported `NEXT_STEPS.md` is current (D73)._ Update stale `NEXT_STEPS.md` (Kanji + JMnedict spokes are implemented) | housekeeping |
| X5 | (If needed) GIN / `pg_trgm` indexes on `name_entry` arrays via Flyway | Phase 3 |
| X6 | (Later) word-level JLPT source ingestion, once O3 is decided | Phase 8 |
| X7 | ✅ _Done in the updater (V1: sentence text is `text`; ported, D73). This repo's schema, seed and role script no longer handle large objects._ Map `example_sentence.japanese/english/index_words` to `text`, not `@Lob` (which makes Postgres `oid` large objects), e.g. `@JdbcTypeCode(SqlTypes.LONGVARCHAR)`. Do it as a Flyway migration (X1). Then update this repo's `01-schema.sql` and drop the per-LO grants. Reason: large objects need per-object `GRANT`s (no default privileges in Postgres 16), so the read-only role can't read full-load sentences | Phase 4 |
| X8 | _Shipped as updater Flyway **V4** (U3/U4): on an existing DB it drops the unordered rows and forces a JMdict re-import. This repo's `seed/schema.sql` includes V4._ ✅ _Done 2026-11-14 for `sense_gloss`/`sense_pos`/`sense_misc` (`@OrderColumn` in the updater's `Sense`; this repo's `01-schema.sql` regenerated). Still open: X3's tables when they exist, and checking the other spokes' arrays._ **Keep upstream order** for ordered lists. The updater loses it today: `Sense` glosses / POS / misc are `@ElementCollection` lists with no order column, so the order of the JMdict XML (most important gloss first) is not stored. Add `@OrderColumn` (e.g. `gloss_order`) to `sense_gloss`, `sense_pos`, `sense_misc` (and X3's `<field>`/`<dial>` tables) via Flyway (X1), then re-import. **This repo already expects these exact column names** (`gloss_order`, `pos_order`, `misc_order`, 0-based, PK `(sense_id, <x>_order)`); until X8 lands, `validate` fails against a real updater DB. Also check the other spokes for lists whose source order matters (e.g. `name_entry` arrays, kanji `meanings[]`). Readings and senses are fine: identity ids follow insert order. Then update this repo's `01-schema.sql` and add the matching `@OrderColumn` in `vocabulary/models/Sense` | Phase 1 (gloss display order) |
| X9 | _Superseded 2026-11-14 by the port (D73): the ported code already had its own fixes (top-level `VocabEsRepository`, `text` columns in V1, ES 9). Kept as a record of what the Docker run found._ ✅ _Done 2026-11-14_ (found by the Docker run). (a) The app could not start: the ES repositories are nested interfaces inside the `*IndexWriter` facades, which Spring Data skips by default, so the fix is `@EnableElasticsearchRepositories(considerNestedRepositories = true)`. (b) The compose ES was 8.15.3, but Boot 4.1's client is 9.x, and ES 8 rejects its `compatible-with=9` header (400), so ES is now 9.4.8 (D57). (c) Full loads failed on `varchar(255)`: one JMdict gloss is 354 chars and KanjiVG stroke paths are up to 377, so `sense_gloss.gloss` is now `text` and `kanji.stroke_paths` is `text[]` (`length = Length.LONG32`). An existing updater DB needs `ALTER ... TYPE text` (no Flyway yet, X1) | Phase 0/1 (full load) |
| X10 | _Note (2026-11-14): the updater asserts the expansion on purpose (`JmdictModuleIT`, "DTD entity expanded to its description", U7), and this repo's fixtures now use the expanded text (D77). Changing it is a design decision in both repos._ Store JMdict **entity codes** for POS/misc (`n`, `v1`, `exp`, `sl`), not the expanded DTD text (`noun (common) (futsuumeishi)`). The parser resolves the inline DTD entities today. Codes are short, stable, and what Phase 7 tag search and Phase 8 lists (`exp`, slang) want; the display text can be a lookup table. Needs a re-import | Phases 7, 8 |
| X11 | _Mostly resolved by the port: the GitHub API call is gone (KanjiVG's latest release comes from the `releases/latest` redirect), and failures now carry status, rate-limit headers and body (D69, U6). Pinning `updater.kanjivg.url` in production is still an option._ (Optional) KanjiVG's "latest release" lookup calls the GitHub API without a token, so it can get **HTTP 403** (rate limit; seen once 2026-11-14, fine on retry). Pin `updater.kanjivg.url` or send a token in production | Reliability |

### Decision log

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
| D26 | _Superseded by X7 (done, D73): there are no large objects any more._ _(2026-11-02)_ Mirror the updater's `oid` (large object) sentence columns exactly; fix them in the updater (X7) | `validate` must match the real schema; the read-only role can't use default privileges on LOs |
| D27 | _(2026-11-02)_ Connection settings are **env vars with local defaults** (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `ES_URIS`, `CORS_ALLOWED_ORIGINS`). Only local dev credentials are committed | Works out of the box locally; production overrides without code changes |
| D28 | _(2026-11-02)_ Pagination: `page` (`@Min(0)`, default 0) + `size` (`@Min(1) @Max(100)`, default 20) on each controller. Over max → **400**, not clamped. Return `Page<Dto>`, serialized `via-dto` | Clear contract; no shared pagination classes |
| D29 | _(2026-11-02)_ Errors: **modules own their exceptions** (extend `ErrorResponseException`). `spring.mvc.problemdetails.enabled=true` renders them and Spring's own errors as RFC 9457. A root `UnexpectedErrorHandler` covers unexpected 500s | The root never needs to know any module; one format for the whole API |
| D30 | _(2026-11-02)_ Logging: root `RequestIdFilter` (`X-Request-Id`, MDC `requestId`). Each module gets an **`aop/`** folder for its own logging aspects, which read the id via `MDC.get("requestId")` | A request id must exist before any module runs; module logging stays per-module (firmer modular design, accepting repetition) |
| D31 | _Amended by D74: the schema now comes from the updater's Flyway migrations, not a Hibernate export._ _(2026-11-02)_ Schema DDL and ES mappings are **generated offline** from the updater's classes (Hibernate schema export; Spring Data ES `MappingBuilder`), not hand-written. Verify against a real DB later | No Docker on the dev machine; hand-written DDL had already missed the `oid` columns |
| D32 | _(2026-11-02)_ **Seed dataset deferred** until a machine with Docker is available | Seed data should be checked against a running stack |
| D33 | _(2026-11-02)_ **Security is not Cognito's job here.** Cognito authenticates users; this API has none (D1). Cognito belongs to the future user service, the updater's admin API, or an API Gateway in front (O4). Removing Spring Security entirely was considered and rejected; the per-module chains stay | Keep in-app hardening (deny-all net, security headers) |
| D34 | _(2026-11-13)_ **Source order is data, not ranking.** The updater must keep upstream order (e.g. gloss order in a sense) (X8). The `search` module ranks *results* (D17); it does not rebuild order inside an entry | The order lives only in the source files, so once the updater drops it, no downstream code can restore it |
| D35 | _(2026-11-13)_ **Code as if X8 is done**: `Sense` maps `gloss_order`/`pos_order`/`misc_order` now, and `01-schema.sql` is hand-edited to match (an exception to D31). Vocab collections load lazily, not join-fetched: `@BatchSize` on `readings`/`senses`, `@Fetch(SUBSELECT)` on the `Sense` lists (amended after review, 2026-11-13: a batch size on the `Sense` lists counted senses, not entries) | User's call: assume the updater keeps gloss order. Batch fetch avoids N+1 without `MultipleBagFetchException` and keeps the setting inside the module |
| D36 | _(2026-11-13)_ Vocab candidates: **one ES `bool` query with named clauses** (`exact` term, constant score 1000; `prefix`, constant score 100; `partial` gloss `match`, operator AND). The tier comes from `matched_queries` (always correct). The order is by `_score`, then `common`; the boosts keep tiers in order in practice, not by guarantee. Tiers here: `EXACT`, `PREFIX`, `PARTIAL`. `search` adds `DEINFLECTED`. English has no exact tier until X2 adds a keyword subfield on `glosses` | One request per page with correct paging; `search` re-ranks by tier anyway (D17) |
| D37 | _(2026-11-13)_ **Module API = facade in the base package.** Each module puts one public `<Module>Api` interface, plus the types it takes and returns, directly in its base package. Sub-packages (`services/`, `models/`, `repository/`, `integration/`, `controller/`, `dto/`) stay internal under Modulith's default rule. Other modules depend only on the interface. Considered: `@NamedInterface` on `services/` + `dto/` (keeps every class in a sub-folder), and an OPEN module (rejected: exposes repositories/entities) | User's call. A small, explicit API per module, with no Modulith annotations needed; template for all modules |

Phase 1 smaller choices _(2026-11-13)_. Made by Claude while implementing, unless noted. **Provisional** = not yet confirmed by the user; see the matching open decision.

| # | Decision | Reason / impact |
|---|---|---|
| D38 | Child entities are **one-way**: `@OneToMany @JoinColumn(name="ent_seq")` on `VocabEntry`, with no back-reference on `Reading`/`Sense` | Simpler read model; same DB columns as the updater, so `validate` is unaffected |
| D39 | Readings and senses are ordered by **`@OrderBy("id")`** | Gives JMdict order **only because** the updater inserts rows in document order with identity ids. If the updater ever loads in parallel or out of order, this breaks; then readings/senses need order columns too (extend X8) |
| D40 | Nullable schema booleans (`vocab_entry.common`, `reading.kanji`) map to **`Boolean`, null reads as false**; `VocabSense` **drops null** glosses/tags _(user's call, code review)_ | One bad row cannot fail a whole page with a 500 |
| D41 | `VocabularyApi.findByEntSeq` returns **`Optional`**, not an exception; only the controller raises the 404 | `search` never handles HTTP errors for missing entries |
| D42 | `searchEntries`: the **total comes from ES**; ids missing from Postgres are **skipped** | Pages can be short. On the last page `PageImpl` recomputes the total (known limitation, review #7, accepted) |
| D43 | **Deep-page guard**: a page ending past 10,000 hits → 400 (`VocabPageTooDeepException`), checked before ES is called. The limit is **hard-coded** to ES's default `index.max_result_window` | No 500 from ES. Results past 10,000 cannot be paged. If the updater changes the index setting, update the constant |
| D44 | `q` is limited to **100 characters** (`@Size`) → 400 if longer. **Provisional (O5)** | Not in the original plan. Bounds ES term/prefix work; dictionary queries are short |
| D45 | Every module's security chain uses **`@Order(100)`** | Safe because module paths (`/api/v1/<module>/**`) never overlap. A module with an overlapping path would need its own order |
| D46 | **CORS per chain, not a bean**: `GET/HEAD/OPTIONS`, request header and exposed header `X-Request-Id`, no credentials, preflight max age 1 h. The header name is a constant in each module, not imported from `RequestIdFilter` (D30) | A `corsConfigurationSource` bean would apply to every chain. Each module repeats the setup (accepted, D25) |
| D47 | Logging aspect wraps **`VocabularyApi` methods only** (not internal beans), logs **no arguments**, and does **not** add the request id itself (the log pattern prints it). **Provisional (O6)**: differs from D30's wording ("read via `MDC.get`") | One log line per API call. Queries are user input and are not logged. Success lines are DEBUG, so hidden by default |
| D48 | AspectJ is **not declared in the pom**; it comes in through `spring-boot-starter-data-jpa`. **Provisional (O7)** | If that dependency changes, the aspects stop running silently |
| D49 | Controller tests **import the real security chains** (`SecurityConfig` + the module's config) instead of mocking security | The tests also cover security and CORS; a change there can break controller tests (intended) |
| D50 | `logging.pattern.correlation` ends with **` `**, not a plain space _(bug fix)_ | Editors stripped the trailing space, so log lines had no space after the request id. The escape survives |
| D51 | Vocab search uses **`trackTotalHits(true)`** | `totalElements` is exact, also above 10,000. Cost: ES counts every match on every request; fine for `vocab`'s size, heavier for broad queries (`q=あ`). Revisit if search latency matters |
| D52 | `VocabCandidate` carries **`entSeq`, `tier`, `common`** | `search` can rank by tier and `common` (D17) without loading entries from Postgres. Ranking on any other field needs a new candidate field or a DB load |
| D53 | The exact and prefix clauses run for **every input**, including English and romaji | Simple; harmless (no Latin text in `kanji`/`readings`) but extra work. Once `parsing` (Phase 6) detects the input type, `search` can skip clauses that cannot match |
| D54 | `findAllByEntSeq` **drops duplicate ids**, keeping the first position | `search` gets each entry once even if several clauses return the same id |
| D55 | `VocabEntry.BATCH_SIZE = 100` **equals the max page size** (D28) | One query each for readings and senses per page. If the max `size` ever goes above 100, raise `BATCH_SIZE` too, or pages take 2+ queries |
| D56 | The logging aspect's slow-call threshold is **500 ms, hard-coded** (`SLOW_MS`) | Changing it means a code change, not config. Make it a property if it needs tuning per environment |

Docker verification _(2026-11-14)_. Made by Claude while testing; no user decision needed unless noted.

| # | Decision | Reason / impact |
|---|---|---|
| D57 | **Elasticsearch 9.4.8** in both repos' compose files (was 8.15.3) | Boot 4.1 ships the 9.x Java client (`elasticsearch-java` 9.4.x); an 8.x server rejects it. 9.4.8 is this repo's exact client version. Affects O4: hosted ES/OpenSearch must speak the 9.x API |
| D58 | Vocab sort ends with **`id.keyword` asc** (`unmapped_type: keyword`); `vocab.json` declares `id` as the updater's dynamic mapping produces it | Unique last key → stable paging. `_id` sorting is off in ES 8+. Relies on the updater writing `id` into `_source` (it does) |
| D59 | Seed data is **copied from a full load** by `docker/generate-seed.py` (fixed id lists), not hand-written. Amends D19/D32 | The seed always has the real schema and data shapes. Regenerate after schema changes |
| D60 | `.gitattributes`: `*.sh` and `docker/**` are always **LF** | On a Windows checkout, CRLF broke both init scripts (`set: illegal option -`, `cannot execute: required file not found`) |
| D61 | _Superseded by updater V4 (U4): the migration itself drops the unordered rows and forces the re-import._ The updater's X8 order columns and X9 `text` columns are **entity changes only, no Flyway** (X1 is not done). Amends X8 ("via Flyway") | `ddl-auto=update` creates them on a **new** database. It does not change an **existing** one: an old updater DB has no order columns, so this service's `validate` fails on it. **Existing DBs: drop and re-import** _(user's call, 2026-11-14)_. All data comes from the upstream feeds, so nothing is lost, and the old rows have no order to keep anyway. No `ALTER` script; write migrations once Flyway exists (X1) |
| D62 | _Superseded by D74 (schema = concatenated migrations)._ `01-schema.sql` is **partly hand-edited**, then checked with a `pg_dump` diff against a real updater DB. Amends D31 (generated, not hand-written). D35's "ahead of X8" exception is gone | Hibernate's exported DDL script and what `ddl-auto=update` really builds differ in PK column order (`(gloss_order, sense_id)` vs `(sense_id, gloss_order)`). The file follows the **real** DB. Rule from now on: the `pg_dump` of an updater-built DB is the reference, not the export script |
| D63 | This repo's `Sense.glosses` declares `length = Length.LONG32`, the same as the updater | `validate` passes without it (it does not check lengths). Kept so the read-only copy (D2) shows the real column type |
| D64 | _Superseded: V1 already makes every free-text and list column `text` / `text[]`, guarded by `UpdaterComponentApplicationIT.freeTextColumnsAreNotLengthLimited`._ Only the columns that overflowed are widened (`gloss`, `stroke_paths`). Other `varchar(255)` columns and arrays (`meanings`, `translations`, `readings`, …) stay as they are | They fit the current data (full load, 2026-11-14). Risk: a future upstream feed can overflow them, and the updater then fails at load time. Widen when that happens, or all at once with Flyway (X1) |
| D65 | X8 is **partly done**: `sense_gloss`/`sense_pos`/`sense_misc` only. Not done: check the order of the other spokes' arrays (`name_entry`, kanji `meanings[]`), and X3's tables (which do not exist yet) | The vocab order columns block Phase 1; the others block nothing yet. Check them before Phase 2 (kanji) and Phase 3 (names) |
| D66 | _Obsolete since X7: no large objects, no grant loop._ `03-seed.sql` grants `SELECT` on **every** large object, not only the ones it creates | On the fresh DB where init scripts run, the two sets are the same. One loop is simpler than tracking each new oid |
| D67 | Schema checks use a **throwaway Postgres** (`docker run --rm`, port 5434, no volume), not the updater's own compose stack | The updater's `updatercomponent_*` volumes may hold data, so they were not touched. Repeat with: `docker run -d --rm --name x8-schema-pg -e POSTGRES_DB=updater -e POSTGRES_USER=updater -e POSTGRES_PASSWORD=updater -p 5434:5432 postgres:16`, start the updater on it, then `pg_dump --schema-only` |
| D68 | _Amended after the port: a new full load is saved as snapshot `full` (D71) and the stack currently runs it (restored). Fresh volumes still start in seed mode._ The dev stack is left on **seed data**; the full-load data was removed (`docker compose down -v`) to test the seed on empty volumes | The seed is the committed default (D19). A full load is the documented optional step (CONTEXT.md, "Running locally"); it takes about 25 minutes |
| D69 | _Amended after the port: the GitHub API call is gone (KanjiVG's latest release comes from the `releases/latest` redirect); the diagnostics cover the download and the redirect (U6)._ The KanjiVG HTTP 403 is treated as **transient** (likely GitHub's rate limit for requests without a token), not fixed. Tracked as X11. **Amended** _(user's call, 2026-11-14)_: the updater's `KanjiFileFetcher` now puts the status, the rate-limit headers (`x-ratelimit-*`, `retry-after`, `x-github-request-id`) and the first 300 characters of the body into the error, so the next failure shows the cause. Production: pin `updater.kanjivg.url` | It passed on retry, and the same call from Java returned 200. The cause is **not confirmed** |
| D70 | **Cross-repo decisions are logged in both repos**: here (PLAN.md) and in the updater's `NEXT_STEPS.md`, section "Decisions" (U-numbers). Repo-only decisions stay in their own repo. **In both repos** (updater `NEXT_STEPS.md` section 9, renumbered after the port): D73 (U1), D57 (U2), X8 (U3), D61 (U4), D69 (U6), X10 (U7), D65 (U8), D75 (U9); U5 is updater-only. Superseded by the port: X9's updater fixes and D64. **This repo only:** D58, D59, D60, D62, D63, D66, D67, D68, D70, D71, D72, D74, D76, D77. Rule for new decisions: if it changes the updater's code, schema, feeds or compose file, log it in both | The repos are independent (D2, D4). A reader of one repo must not need the other repo's docs to know why its code changed |
| D71 | **Full load once, then snapshot/restore** (`sh docker/snapshot.sh save|restore [name] | list`) _(user's call, 2026-11-14)_. Saves both data volumes as `.tar.gz` in `docker/snapshots/<name>/` (git-ignored). `restore` deletes the current volumes first and refuses a snapshot made with other images. Single spokes still load via `POST /admin/updates/{id}` | A full load took ~25 min (Tatoeba 11, JMdict 8.5, JMnedict 5, Kanji <1); after the port ~16 min (Tatoeba 1.3: plain `text` instead of large objects, X7). Saving takes ~50 s (228 MB), a restore ~30 s. Raw volume files only work with the same images: take a new snapshot after a schema change or an image upgrade. Rejected for now: speeding up the updater (batch inserts, X7); revisit with Flyway (X1) |
| D72 | `init.sh` bulk-loads a seed file **only into an index it has just created**. Before, it loaded the seed on every start | On a full-load or restored index, the seed documents overwrote real ones. The Postgres seed already behaved this way (init scripts run only on an empty volume) |
| D73 | **The updater's code lives in `japanese-dictionary-updater-component`, with its full history** _(user's call, 2026-11-14; was O10)_. The repo was created from an older commit (`b6fdd02`, 2026-09-26) of the earlier combined repository, so five later commits (integration tests, Flyway V1–V3, retirement, FK indexes, config profiles) were missing. They were restored onto branch `updater-history-and-sense-order` with their author and dates; then X8 (Flyway V4), the compose stack, the fetch diagnostics and the `dictionary_reader` rename were added (updater U1–U9). Today's duplicate fixes on the older code were dropped. The combined repository is retired: no doc should point to it | The split was meant to move the newest code. Consequences for this repo: X1, X4 and X7 are done in the updater, and `schema.sql`, the seed and the full-load steps follow Flyway (D74) |
| D74 | **Two stack modes** _(user's call, 2026-11-14)_. Seed mode (default): `init/01-seed-data.sh` runs `seed/schema.sql` + `seed/seed.sql`. Empty mode (`DICTIONARY_SEED=false docker compose up -d` on empty volumes): no tables, no ES seed; the updater's Flyway builds the schema during a full load. `seed/schema.sql` is the updater's migrations concatenated in version order by `sh docker/generate-schema.sh`. Amends D31; supersedes D62 | Flyway cannot run on tables it did not create: the `local` profile adopts them as V1 and then fails on V2 (`retired_at` exists). Concatenating the migrations is exact and needs no Java. A seed-mode DB must not be given to the updater |
| D75 | The read-only login is **`dictionary_reader`** in both stacks and for production _(user's call, 2026-11-14)_; the updater's `api_reader` was renamed (U9) | One name for one job. This repo's `DB_USERNAME` default (D27) already used it |
| D76 | **Retired entries are returned unchanged** by `GET /api/v1/vocabulary/{entSeq}` _(user's call, 2026-11-14)_. `VocabEntry` does not map `retired_at`. Search never returns them (the updater deletes their ES documents) | Matches updater ADR-0001: stored ids keep resolving. Clients cannot tell a retired entry from an active one; add a flag later if the user service needs it |
| D77 | Controller-test fixtures **copy real JMdict entries** (id, writings, readings, senses, expanded POS/misc text) _(user's call, 2026-11-14)_ | The old fixtures were from memory: `1415370` is 第一歩, not たばこ, and real POS/misc are the expanded DTD text, not codes (X10) |


## 2026-11-16 — Phase 2 (kanji) done

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

Open decisions closed this session (see D79–D89):

| # | Question | Advice | When |
|---|---|---|---|
| O5 | Keep the 100-character limit on `q`? | Keep | Before Phase 2 |
| O6 | Logging aspects: rely on the log pattern for the request id, or add it in code? | Rely on the pattern | Before Phase 2 |
| O7 | Declare `spring-boot-starter-aspectj` in the pom (today it comes in through JPA)? | Yes | Before Phase 2 |
| O8 | API records are the JSON contract; add a `dto/` layer? | Not until the shapes must differ | Before Phase 2 |
| O9 | Module chains allow every method (writes get 405). Restrict to GET/HEAD? | Keep as is | Before Phase 2 |
| O11 | Radical search: match and order | SQL `GROUP BY kanji HAVING count = n`; order by strokes, frequency, character; paged; no bare rows | Phase 2 |
| O12 | Detail for bare rows and retired kanji? | Return both unchanged, like D76 | Phase 2 |
| O13 | One kanji record, or a detail + a smaller list record? | Two: `KanjiDetail`, `KanjiSummary` (no stroke paths) | Phase 2 |
| O14 | What does `GET /api/v1/radicals` return? | All 253, grouped by stroke count, with kanji counts, not paged | Phase 2 |

Verified on a full load: `validate` passes; 化+木 returns 休体侏保…, matching RADKFILE; queries per request: detail 2, radical search ≤ 2, radicals list 1. The 30 bare rows come from KanjiVG (no kanji_radical rows): 21 radical forms or characters KanjiDic2 lacks, 9 CJK compatibility ideographs (U+F9xx).

Outcome (2026-11-16). All Phase 2 tasks above are done. O5–O9 were accepted as advised (D79–D83).
O11–O14 went as advised (D84–D87). For O14 the user chose the grouped shape, even though the
session had advised a flat list by then. The session asked three more questions that had no O id:
RADKFILE stand-in radicals are returned as stored (D88); the filters are `strokes` (exact), `grade`
and `joyo` (D89); greying out radicals that can't combine was put off and listed under "Later" in
PLAN.md (alternatives offered: `/radicals?selected=…`, or compatible radicals inside the search
response). The archived data note above calls bare rows "radical forms"; the full load shows they
come from KanjiVG (see the verification line, and CONTEXT.md).

## 2026-11-16 — Phase 3 (names) built, search waits on X5

## Phase 3 — names

- [x] `NameEntry` (`text[]` columns), Postgres only (D18).
- [x] `GET /api/v1/names/{entSeq}`, `GET /api/v1/names?q=` (kanji, readings, translations).
- [x] Measure on the full ~740k rows. If slow, ask the updater for indexes (X5).
- [x] API for `search`, controller tests.

Measured on the full load (743,691 names, none retired): unindexed, a tiered search page took
~2 s and its count ~0.9 s. GIN trigram indexes on two space-joined, lower-cased text forms (each
value preceded by a space, so `LIKE '% q%'` is a word-start match) bring it to 3–40 ms, including
2-character Japanese like 田中; a lone kana such as た takes ~200 ms (trigrams need 2+ characters).
Plain GIN indexes on the `kanji`/`readings` arrays were tried and dropped from the spec: the
queries use `= any()`, which they can't serve. `array_to_string` is only STABLE, so the updater
must wrap it in IMMUTABLE functions to index it.

Decisions:
- **D90 (was O15) — name match tiers.** Options offered: tiers with word-start partial
  (advised), tiers with substring partial (`li` would match `William`; 1–2 letter queries can't use
  the index), or exact + prefix only. Chosen as advised: EXACT = q equals a kanji writing, a reading
  or a whole translation (case-insensitive); PREFIX = one of them starts with q; PARTIAL = a later
  word of a translation starts with q. Order by tier, then ent_seq (names have no frequency or
  common flag). Retired names never appear in search.
- **D91 — X5 sequencing.** Options offered: updater first (advised), names now with unindexed
  `unnest()` SQL and a rewrite later, or no indexes. Chosen: updater first, but the session was
  only allowed to write the X5 spec into the updater's `docs/PLAN.md`, not its code. The names
  queries call `name_entry_jp_text` / `name_entry_en_text` by name; this service never creates
  database objects. To verify before X5 lands, the user chose to apply the spec SQL by hand to the
  dev DB as `dictionary_owner`, test, and then restore the `full` snapshot.

Verified on the full load with the spec SQL applied: 田中 → 475 hits, 田中 (5543705) first;
`tanaka kakuei` → 2; `kakuei` → 36, exact "Kakuei" names first; `a%b` and `x_y`
→ 0 (wildcards escaped); paging page 0 + 1 (size 3) equals page 0 (size 6). Detail by id and the
404 work. Then `snapshot.sh restore full`; the dev DB has no X5 objects. X8 for names was also
answered by reading the updater's `JmnedictParser`: `keb`/`reb`/`trans_det` are appended in file
order and repeated name types keep the first occurrence.

## 2026-11-23 — App-wide requirements written

`written_reports/REQUIREMENTS.md` was written with `/grill-requirements`, covering all four
applications (front-end, this service, updater, user service).

Superseded:
- | D22 | Tests now: controller tests + Modulith. Integration tests later |

Decisions:
- **D92 — where app-wide docs live.** Options offered: this repo (advised), the updater, or a new
  umbrella docs repo. Chosen: this repo, since `written_reports/` already held the app-wide
  diagrams. Per-repo decisions (D/U) stay where they are; the updater's CLAUDE.md should link here.
- **D93 (replaces D22) — integration tests required.** Options offered: Stretch (advised, kept
  D22), In-Scope NFR, or Out-of-Scope. Chosen: In-Scope NFR (requirement N1.5).

Second round (same day), after pulling the updater:
- **D94 — lists on the device.** The user asked whether lists could live on the device instead of
  in the user service. Options offered: local-first with sync later (advised), local plus accounts
  now, or server only. Chosen: local-first. Lists, history, and Anki/CSV/TSV import and export
  move to the front-end. The user service and learner Cognito accounts become Stretch (S22).
- **O4 deferred.** A budget under $50/month was first chosen, which doesn't fit Elastic Cloud plus
  Fargate. Options offered: one EC2 box with Compose (advised), drop ES, or raise the budget. The
  user deferred hosting until every app runs in a container, keeping $100–200/month as a
  tentative budget.
- Other answers, recorded in REQUIREMENTS.md: JmdictFurigana spoke for furigana; tag search as
  both `tags=` and `#tag`; search on Enter only; English-only UI; Anki fields configurable per
  entry type; lists hold all four entry types; four cross-links; empty results explain the
  interpretation; list caps 100 × 5,000; import cap 5 MB / 5,000 rows.

## 2026-11-23 — PLAN.md slimmed (D95)

The task sections below left PLAN.md: REQUIREMENTS.md rows and their status tags are now the
task list, to cut repeated context. Moved here word for word.

## Phase 3 — names

- [ ] After X5: regenerate `schema.sql`, check name search on seed data, new full-load snapshot.

## Phase 4 — sentences

- [ ] `ExampleSentence` entity + read-only ES document (index `example-sentence`).
- [ ] `GET /api/v1/sentences/{id}`, `?word=` (by index words), `?q=` (Japanese/English text).
- [ ] API for `search` and for "example sentences of this word", controller tests.
- [ ] After Phase 6: sentence detail returns word segments linked to `ent_seq` (R4.5).
- [ ] `vocabulary`: words containing a given kanji, common first (R1.5).

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
- [ ] Tag search (POS/misc), details when reached. A `tags=` parameter; the front-end turns `#tag` into it (R7.5).
- [ ] The response reports input type, normalized form and deinflections tried (R7.6).

## Phase 8 — lists

- [ ] From current data: common words, POS, expressions, slang, kanji by grade/jōyō/JLPT/frequency.
- [ ] Blocked: subject and dialect lists (X3), words by JLPT (O3, X6).
- [ ] `GET /api/v1/lists`, `GET /api/v1/lists/{id}`. User-made lists live on the device (D94).
- [ ] Word of the day: `GET /api/v1/lists/word-of-the-day` (R10).

## Phase 9 — furigana

- [ ] Furigana segments on vocab and sentence detail, from the updater's JmdictFurigana spoke (R9, R14.7).

## Testing

- [ ] An integration test per module against seed data (Testcontainers), run by CI (D93). Needs Docker.

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

## Later

- Radical picker: grey out radicals that can't combine with the selection (`/radicals?selected=…`).
- JPA/ES slice tests.
- Modules from diagram V03: OCR, slang, kanji mastery map, word of the day, furigana, handwriting search.
- Unsourced data ideas (see `written_reports/Archived/`): pitch accent, frequency, grammar points, audio.
- Link `example_sentence.index_words` to vocab ids (in the updater).

Decision:
- **D95 — one task list.** The user, worried about context use, asked whether PLAN.md was
  obsolete. Options offered: slim it (advised), delete it (decisions into REQUIREMENTS.md), or
  keep it. Chosen: slim. REQUIREMENTS.md is read by section only. Specs go to
  `written_reports/specs/`, gitignored.

## 2026-11-23 — Scope of the next two sessions (D96–D99)

A grilling session on "Next session" and the session after it. No code changed; this session
had no Docker.

Found: no integration test exists, though D93 and N1.5 require one per module and R1 and R2
are ✅. The updater's "Next session" was stale (X8 open, "next phase (kanji)"). In ES,
`example-sentence.indexWords` is a single keyword and `example_sentence.index_words` has no
index, so R4.2 had nothing efficient to query. There was no CI. "Phase" survived only in the
two PLAN.md files (10 mentions); phase N equalled RN except phase 0 (now R11) and phase 8
(R8 + R10), so phases were dropped for R-ids.

Decisions:
- **D96 — updater changes allowed.** Before this, updater work was parked as an `X<n>` spec
  and done later, which left `/names?q=` returning 500 for a week. Pros: no blocked rows, both
  sides tested together, specs can't drift. Cons: two commits per unit (drift if only one
  lands), more snapshot rebuilds, blurred ownership, two rule sets at once. Guardrails chosen:
  only when an R row needs it, tracked by `X<n>`, the updater committed first, D3 and D91
  still hold.
- **D97 — sentence word search.** Options: (a) a Postgres regex over `index_words`, unindexed,
  about 150k rows; (b) the updater parses `index_words` into ES keyword arrays (X12). Chosen
  (b): parsing the source format is the updater's job, like D14. It matches the headword only;
  callers try the entry's writings, then readings. Order: `~` (Tatoeba's checked good example)
  first, then shorter sentences (easier for learners), then id.
- **D98 — sentence detail and text search.** Retired sentences are returned by id like D76.
  Raw `index_words` stays out of the response; R4.5 will add segments. Text search ships on the
  default analyzers now, with match quality left to X2.
- **D99 — integration tests.** `*IT` under Failsafe keeps `./mvnw test` Docker-free (N1.4).
  The containers load the same `docker/` seed files, so seed data has one source (D59), not
  test-only fixtures. Single containers, not the compose file. A minimal GitHub Actions
  workflow now; deployment stays with O4.

Small choices agreed: the X5 migration goes onto the restored `full` snapshot in place (Flyway
applies only V5), then it is saved as the new `full`; it is a one-off, so not in README. One new
seed sentence marked `~` for 食べる tests D97's order.

## 2026-11-23 — D100: a standing "Docker session" list

This machine has no Docker, so the name-search and integration-test work stopped at 🔵 with its
Docker steps left over. Options were to keep those steps in "Next session" (which blocks the next
codeable work behind a machine change), a separate doc (one more file with its own budget), or a
standing section in PLAN.md. Chosen: the section. "Next session" moves on to codeable work, and
each Docker-only step is added to "Docker session" until a Docker machine clears them in one go.
