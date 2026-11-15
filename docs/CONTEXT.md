# japanese-dictionary-services — Context

Stable facts about this service. Read this first. For tasks, implementation details,
and the decision log, see [`PLAN.md`](./PLAN.md).

_Last updated: 2026-11-14_

## What the application is

An app for Japanese learners and anyone dealing with Japanese media or society (a
jisho.org-style dictionary, plus learning features later). It has **one repo per
application**; these two are the application's repos today:

| Repo | Role | Writes | Reads |
|---|---|---|---|
| `~/repos/japanese-dictionary-updater-component` | **Updater**: scheduled hub-and-spoke ingester of upstream dictionary data | Postgres + Elasticsearch | upstream files |
| `~/repos/japanese-dictionary-services` (**this repo**) | **Dictionary service**: the read-only dictionary API (the "API Backend" container in diagram V03) | nothing (dictionary data) | Postgres + Elasticsearch |
| _future_ user service | User lists, search history, Anki/CSV/TSV import/export, settings | its own store | this API |
| _future_ front-end | Web/mobile UI (S3 + CloudFront per V03) | — | this API |

**Hard rules**
- The updater is the **only writer** of dictionary data. This service is **read-only**,
  and it connects with a read-only DB role.
- This service and the updater **never talk directly**. They share only the databases.
- This service is **dictionary-only**. No user accounts or user data live here.

## Architecture (from `written_reports/Architectures/Container_Diagram_V03.JPG`)

```
 JMdict  JMnedict  KanjiVG  KanjiDic2  RADKFILE  Tatoeba
    \________\________|________|_________/________/
                       ▼
          Updater (Spring Boot, hub & spoke)  ── scheduled, version-checked, idempotent
              │                       │
              ▼                       ▼
     Postgres (source of truth)   Elasticsearch (search copy)
              │                       │
              └──────────┬────────────┘
                         ▼
     Dictionary service (this repo, Spring Boot modular monolith, read-only)
                         ▼
                 Front-end / user service
```

Note: diagram V03 says MySQL. The decision is **Postgres** (`postgres:16`).

## Where the data lives

| Data | Postgres | Elasticsearch | Upstream source |
|---|---|---|---|
| Vocabulary | yes | yes (`vocab` index) | JMdict (`JMdict_e`) |
| Example sentences | yes | yes (`example-sentence` index) | Tatoeba / Tanaka Corpus |
| Kanji + radicals | yes | no | KanjiDic2 + KanjiVG + RADKFILE |
| Names | yes | no | JMnedict |

### Postgres schema (owned by the updater; this service mirrors it read-only)

| Table | Columns | Notes |
|---|---|---|
| `vocab_entry` | `ent_seq` PK, `common` bool, `retired_at` | JMdict entry. `retired_at` set = the entry left upstream (updater ADR-0001): the row stays, the ES document is deleted. This service does not map it; the detail endpoint returns retired entries like active ones (D76) |
| `reading` | `id` PK, `ent_seq` FK, `text`, `kanji` bool | `kanji=true` → kanji writing (keb), false → kana reading (reb) |
| `sense` | `id` PK, `ent_seq` FK | one meaning of an entry |
| `sense_gloss` / `sense_pos` / `sense_misc` | `sense_id` FK, `gloss` / `pos` / `misc`, `gloss_order` / `pos_order` / `misc_order` | element collections, ordered as in JMdict (0-based order columns, X8). PK `(sense_id, <x>_order)`. `gloss` is `text` (some glosses are over 255 chars). POS/misc hold the expanded DTD text, not codes (X10). JMdict `<field>` and `<dial>` tags are **not stored** |
| `example_sentence` | `id` PK (the **Japanese** Tatoeba sentence id, since the port; it was the English id before), `japanese`, `english`, `index_words`, `source`, `retired_at` | `index_words` = space-separated headwords (loose link to vocab, no FK). Text columns are plain `text` (X7, done in the updater) |
| `kanji` | `character` PK, `codepoint`, `onyomi[]`, `kunyomi[]`, `nanori[]`, `meanings[]`, `stroke_count`, `grade`, `joyo`, `jlpt` (old 1–4), `frequency`, `classical_radical`, `stroke_paths[]`, `retired_at` | `[]` = Postgres `text[]` |
| `radical` | `character` PK, `stroke_count` | RADKFILE radicals |
| `kanji_radical` | `kanji` FK, `radical` FK | many-to-many |
| `name_entry` | `ent_seq` PK, `kanji[]`, `readings[]`, `name_types[]`, `translations[]`, `retired_at` | ~740k rows. `[]` = `text[]` |
| `source_version` | `feed_id` PK, `content_hash`, timestamps | updater bookkeeping; ignore |
| `flyway_schema_history` | Flyway's record of applied migrations | Only in a DB the updater built (empty mode / full load, D74), not in seed mode; ignore |

### Elasticsearch indexes (written by the updater)

| Index | Fields |
|---|---|
| `vocab` | `id` (= ent_seq), `kanji` keyword[], `readings` keyword[], `glosses` text[], `common` bool |
| `example-sentence` | `id`, `japanese` text, `english` text, `indexWords` keyword |

Japanese fields use the default analyzer today. kuromoji/ICU multi-fields are planned
in the updater (see PLAN.md, decision D14).

Source of truth for the schema is the updater's **Flyway migrations** (D3, X1):
`japanese-dictionary-updater-component/src/main/resources/db/migration/V*.sql`. The ES documents are
its `spokes/*/search/` classes. Short identifier columns are `varchar(255)`, free text and lists
are `text` / `text[]`, booleans are nullable. Foreign keys are indexed (V3).

The DDL for the seed mode is `docker/postgres/seed/schema.sql`: the migrations concatenated by
`sh docker/generate-schema.sh` (D74), never hand-edited. The ES mappings in
`docker/elasticsearch/mappings/` are generated from the updater's documents (Spring Data ES
`MappingBuilder`) and were verified against a real index on 2026-11-14.
`vocab.json` also declares the `id` field that ES maps dynamically from the updater's documents
(`text` + `id.keyword`, used as the last sort key, D58).

## Module layout (package-by-feature, Spring Modulith)

Root package: `baddee.japanese_dictionary_services`. Each feature module has
`aop/ controller/ dto/ integration/ models/ repository/ services/`. This is the target layout.
Git does not track empty folders, so each folder appears when its first class is written
(`aop/` and the module security chain in `integration/` start in Phase 1).

| Module | Responsibility |
|---|---|
| `vocabulary` | Vocab reads: Postgres details + ES `vocab` candidates |
| `kanji` | Kanji + radical reads, radical search (Postgres) |
| `names` | Name reads (Postgres) |
| `sentences` | Example sentence reads: Postgres + ES `example-sentence` |
| `normalization` | Input converter (romaji→kana), era-year conversion, number conversion |
| `parsing` | Input identification (English / romaji / Japanese), inflection parse (deinflection), sentence parse (tokenize) |
| `search` | **Orchestrator**: normalize/parse input → call the other modules' public services → merge + rank with its own tiered rules |
| `lists` | Read-only premade default lists (common words, JLPT, POS, …). Built last |

There is **no `shared` module** (D25). App-level pieces that act before any module sit in the
**root package** (not a Modulith module), and no module imports them:

| Root class | Role |
|---|---|
| `SecurityConfig` | Catch-all chain, runs last: API docs, Actuator health/info and `/error` open; everything else (incl. unclaimed `/api/**`) denied. Stateless, no CSRF/login |
| `RequestIdFilter` | One id per request from `X-Request-Id` (or generated) → MDC key `requestId` + response header |
| `UnexpectedErrorHandler` | Last-resort 500 as RFC 9457 `ProblemDetail`, no internals leaked |

Package conventions:
- `models/`: read-only `@Immutable` JPA entities. Children are one-way (`@OneToMany @JoinColumn`
  on the parent, D38). Nullable schema booleans map to `Boolean`, null reads as false (D40).
- `repository/`: Spring Data JPA, read methods only.
- `integration/`: ES documents/clients, adapters to other modules, and module plumbing. This includes
  the module's own `SecurityFilterChain` (`securityMatcher("/api/v1/<module>/**")`, `permitAll`,
  stateless, `@Order(100)` in every module, D45) and its CORS, read from the one key
  `app.cors.allowed-origins` (D25). CORS is built per chain, not as a bean (D46). Template:
  `vocabulary/integration/VocabularySecurityConfig`.
- `aop/`: the module's own logging aspects (e.g. service call + timing), wrapping the module's
  `<Module>Api` methods, no arguments logged. The request id is printed by
  `logging.pattern.correlation`; if an aspect needs it in code, read `MDC.get("requestId")`. Never
  import root classes (D30, D47, provisional O6).
- Errors: each module owns its exceptions, extending Spring's `ErrorResponseException`
  (status + detail). `spring.mvc.problemdetails.enabled=true` renders them (D29). An exception the
  `<Module>Api` throws lives in the base package (D37); controller-only ones stay in `controller/`.
  Lookups by id return `Optional` from the API; only the controller turns empty into 404 (D41).
- Pagination: controllers take `page` (`@Min(0)`, default 0) and `size` (`@Min(1) @Max(100)`,
  default 20; over 100 → 400) and return `Page<Dto>`, serialized `via-dto` (D28). ES-backed
  searches reject pages past 10,000 hits with a 400 (D43). Query params are `@NotBlank`,
  max 100 chars (D44, provisional O5).
- Controller tests: `@WebMvcTest` + `@MockitoBean <Module>Api`, importing the real root and module
  security configs (D49). Template: `vocabulary/controller/VocabularyControllerTests`.
- No module queries another module's tables. It calls that module's service instead.
- Module API (D37): one public `<Module>Api` interface plus the types it takes and returns sit in the
  module's **base package** (e.g. `vocabulary/VocabularyApi.java`, `vocabulary/VocabCandidate.java`).
  `services/` implements it. Every sub-package is internal (Modulith default); other modules use only the base package.

## Tech stack

- Java **25**, Spring Boot **4.1.2**, Maven wrapper (`./mvnw`).
- Spring Web MVC, Data JPA (Postgres driver), Data Elasticsearch (9.x client → needs an ES 9 server), Security, Validation, Actuator.
- Spring Modulith **2.1.2** (built against Boot 4.1.2) and springdoc-openapi **3.2.0** (parent Boot 4.1.2).
  Spring Cloud Config was removed (D5).
- API: REST + JSON under `/api/v1/...`, OpenAPI/Swagger UI (`/swagger-ui.html`), page/size pagination.
- Security: public reads (each module's chain `permitAll`s its own `/api/v1/<module>/**`), CORS
  allow-list, stateless, only Actuator health/info exposed.
- Config: connection settings are env vars with local defaults (`DB_URL`, `DB_USERNAME`,
  `DB_PASSWORD`, `ES_URIS`, `CORS_ALLOWED_ORIGINS`), D27. The `prod` profile
  (`application-prod.properties`) has no defaults, so a missing env var fails startup.
- The updater uses Java 26 / Boot 4.1.1 and package `badee` (one "d"). The repos are independent. This difference is intentional.

## Running locally

This repo has its **own** `docker-compose.yml`: Postgres 16 on **5433** and Elasticsearch **9.4.8** on **9201**
(so it can run beside the updater's stack on 5432/9200). ES must be 9.x: Boot 4.1's ES client is 9.x
and an 8.x server rejects it (D57).

```bash
docker compose up -d      # seed mode: postgres, elasticsearch, one-shot elasticsearch-init
./mvnw test               # needs no infra (Modulith verify + controller tests; contextLoads is @Disabled)
./mvnw spring-boot:run    # needs the stack
```

The stack has two modes, picked on first start of empty volumes (D74):
- **Seed mode** (default): schema + seed data, ready in seconds.
- **Empty mode** (`DICTIONARY_SEED=false`): no tables and no seed, for a full load. The updater's
  Flyway builds the schema. Flyway cannot adopt the seed mode's tables (they have no Flyway history).

What the stack sets up on first start:

| Piece | File | Does |
|---|---|---|
| Postgres DB `dictionary`, owner `dictionary_owner` | `docker-compose.yml` | Local dev credentials only |
| Schema (seed mode) | `docker/postgres/init/01-seed-data.sh` runs `docker/postgres/seed/schema.sql` | The updater's Flyway migrations, so `validate` passes. Regenerate with `sh docker/generate-schema.sh` |
| Read-only role `dictionary_reader` | `docker/postgres/init/02-readonly-role.sh` | `SELECT` on all tables + default privileges for tables `dictionary_owner` creates later |
| Seed data (seed mode) | `docker/postgres/seed/seed.sql`, `docker/elasticsearch/seed/<index>.ndjson` | 11 vocab entries, 8 kanji, 2 names, 2 sentences, picked for edge cases. **Generated** from a full load by `python docker/generate-seed.py` (D59); don't hand-edit |
| ES indexes `vocab`, `example-sentence` | `docker/elasticsearch/init.sh` + `mappings/` | Creates missing indexes (both modes); bulk-loads the seed files into new indexes in seed mode only (D72). `number_of_replicas=0` so one node is green |

Init scripts only run on an **empty** volume. To re-run them: `docker compose down -v`, then `up -d`.

**Snapshots** (D71). After a full load, run `sh docker/snapshot.sh save` once. Later, `sh docker/snapshot.sh restore`
brings the full load back in about a minute (it **deletes** the current volumes first). `list` shows the saved snapshots.
A snapshot only works with the same images; take a new one after a schema change or an image upgrade.

**Full load** (the whole dictionary instead of seed data, about 16 min: JMdict 9, JMnedict 5, Tatoeba 1.5, Kanji <1). Start in empty mode, run
the updater (profile `local`) against this stack **as the owner**, so the reader's default
privileges cover the tables Flyway creates, then trigger every feed:

```bash
docker compose down -v && DICTIONARY_SEED=false docker compose up -d

# in ~/repos/japanese-dictionary-updater-component
./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local --server.port=8091 \
  --spring.datasource.url=jdbc:postgresql://localhost:5433/dictionary \
  --spring.datasource.username=dictionary_owner --spring.datasource.password=dictionary_owner \
  --spring.elasticsearch.uris=http://localhost:9201"
curl -X POST -H "Authorization: Bearer dev-token" localhost:8091/admin/updates   # all feeds
```

Then save it: `sh docker/snapshot.sh save` (D71). To load one spoke only: `POST /admin/updates/{id}`
(`jmdict`, `jmnedict`, `kanji`, `tatoeba`).

## Glossary

| Term | Meaning |
|---|---|
| ent_seq | JMdict / JMnedict entry sequence id (primary key for vocab and names) |
| keb / reb | JMdict kanji writing / kana reading elements → `reading.kanji` true/false |
| sense | One meaning of a vocab entry, with glosses (English), POS, and misc tags |
| gloss | An English translation within a sense |
| spoke / feed | Updater terms: a spoke = one source module; a feed = one upstream file tracked by hash |
| candidate | A result a feature module returns to `search` before ranking |
| match tier | Ranking class used by `search`: exact > deinflected > prefix > partial |
| default list | Premade read-only word/kanji list served by `lists` |
| retired entry | Updater term (its ADR-0001): an entry whose key left upstream. The Postgres row stays (`retired_at` set), the ES document is deleted. This service returns it on the detail endpoint like an active entry (D76) |
| seed mode / empty mode | The two ways the dev stack starts on empty volumes: seed data, or an empty DB for a full load (`DICTIONARY_SEED=false`) (D74) |
| full load / snapshot | A full load = the updater importing every feed into the dev stack (~16 min). A snapshot = its saved volumes, restored in ~30 s (`docker/snapshot.sh`, D71) |

## Reference material

- `written_reports/Architectures/Container_Diagram_V0{1,2,3}.JPG`: hand-drawn container diagrams (V03 is current).
- `written_reports/Archived/`: early data model (`japanese_app_data_model.puml`), use cases, story map, pipeline sketch. Includes ideas not yet sourced (pitch accent, frequency, grammar points).
- Updater docs: `NEXT_STEPS.md` (status, decisions U1–U9), `CONTEXT.md` (its glossary: feed, spoke, entry, retired entry), `docs/adr/`, `docs/configuration.md` (profiles).
- The updater's history (build, Flyway, retirement, profiles) is in its own git log (D73).
