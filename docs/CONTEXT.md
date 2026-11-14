# japanese-dictionary-services — Context

Stable facts about this service. Read this first. For tasks, implementation details,
and the decision log, see [`PLAN.md`](./PLAN.md).

_Last updated: 2026-11-02_

## What the application is

**baddee** is an app for Japanese learners and anyone dealing with Japanese media or
society (a jisho.org-style dictionary, plus learning features later). It was one repo
(`~/repos/baddeeApp`) and is being split into one repo per application:

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
| `vocab_entry` | `ent_seq` PK, `common` bool | JMdict entry |
| `reading` | `id` PK, `ent_seq` FK, `text`, `kanji` bool | `kanji=true` → kanji writing (keb), false → kana reading (reb) |
| `sense` | `id` PK, `ent_seq` FK | one meaning of an entry |
| `sense_gloss` / `sense_pos` / `sense_misc` | `sense_id` FK, `gloss` / `pos` / `misc`, `gloss_order` / `pos_order` / `misc_order` | element collections, ordered as in JMdict. The order columns are **assumed** (X8, D35): the updater doesn't create them yet. JMdict `<field>` and `<dial>` tags are **not stored** |
| `example_sentence` | `id` PK, `japanese`, `english`, `index_words`, `source` | `index_words` = space-separated headwords (loose link to vocab, no FK). `japanese`/`english`/`index_words` are **`oid` large objects** (updater maps them `@Lob`), see X7 |
| `kanji` | `character` PK, `codepoint`, `onyomi[]`, `kunyomi[]`, `nanori[]`, `meanings[]`, `stroke_count`, `grade`, `joyo`, `jlpt` (old 1–4), `frequency`, `classical_radical`, `stroke_paths[]` | `[]` = Postgres `varchar(255) array` |
| `radical` | `character` PK, `stroke_count` | RADKFILE radicals |
| `kanji_radical` | `kanji` FK, `radical` FK | many-to-many |
| `name_entry` | `ent_seq` PK, `kanji[]`, `readings[]`, `name_types[]`, `translations[]` | ~740k rows |
| `source_version` | `feed_id` PK, `content_hash`, timestamps | updater bookkeeping; ignore |

### Elasticsearch indexes (written by the updater)

| Index | Fields |
|---|---|
| `vocab` | `id` (= ent_seq), `kanji` keyword[], `readings` keyword[], `glosses` text[], `common` bool |
| `example-sentence` | `id`, `japanese` text, `english` text, `indexWords` keyword |

Japanese fields use the default analyzer today. kuromoji/ICU multi-fields are planned
in the updater (see PLAN.md, decision D14).

Source of truth for the schema is the updater's entity classes:
`japanese-dictionary-updater-component/src/main/java/badee/updaterComponent/spokes/*/domain/` and `*/search/`.

The exact DDL is in `docker/postgres/init/01-schema.sql` and the exact ES mappings are in
`docker/elasticsearch/mappings/`. Both were **generated offline** from the updater's classes
(Hibernate 7.4.5 `PostgreSQLDialect` schema export; Spring Data ES `MappingBuilder`), not
hand-written. All plain strings are `varchar(255)`; booleans are nullable.

### Pending verification (needs Docker)

The schema and mappings above were generated **without a running database** (no Docker on
the dev machine, 2026-11-02). Before relying on them, check them against a real database on
a machine with Docker:
1. Boot the updater against an empty Postgres, then compare
   `pg_dump --schema-only` with `docker/postgres/init/01-schema.sql`.
2. Run an updater load, then compare `GET /vocab/_mapping` and `GET /example-sentence/_mapping`
   with `docker/elasticsearch/mappings/*.json` (expect an extra dynamic `id` field).
3. `docker compose up -d`, then `./mvnw spring-boot:run`: `ddl-auto=validate` must pass,
   `/actuator/health` must be UP, and `/swagger-ui.html` must load.
4. Connect as `dictionary_reader`: `SELECT` works and `INSERT` fails.

Remove this section when all four pass. The PLAN.md Phase 0 box "Verify on a Docker machine"
tracks this.

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
- `models/`: read-only `@Immutable` JPA entities.
- `repository/`: Spring Data JPA, read methods only.
- `integration/`: ES documents/clients, adapters to other modules, and module plumbing. This includes
  the module's own `SecurityFilterChain` (`securityMatcher("/api/v1/<module>/**")`, `permitAll`,
  stateless) and its CORS, read from the one key `app.cors.allowed-origins` (D25).
- `aop/`: the module's own logging aspects (e.g. service call + timing). Read the request id with
  `MDC.get("requestId")`; never import root classes (D30).
- Errors: each module owns its exceptions, extending Spring's `ErrorResponseException`
  (status + detail). `spring.mvc.problemdetails.enabled=true` renders them (D29).
- Pagination: controllers take `page` (`@Min(0)`, default 0) and `size` (`@Min(1) @Max(100)`,
  default 20; over 100 → 400) and return `Page<Dto>`, serialized `via-dto` (D28).
- No module queries another module's tables. It calls that module's service instead.
- Module API (D37): one public `<Module>Api` interface plus the types it takes and returns sit in the
  module's **base package** (e.g. `vocabulary/VocabularyApi.java`, `vocabulary/VocabCandidate.java`).
  `services/` implements it. Every sub-package is internal (Modulith default); other modules use only the base package.

## Tech stack

- Java **25**, Spring Boot **4.1.2**, Maven wrapper (`./mvnw`).
- Spring Web MVC, Data JPA (Postgres driver), Data Elasticsearch, Security, Validation, Actuator.
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

This repo has its **own** `docker-compose.yml`: Postgres on **5433** and ES on **9201**
(so it can run beside the updater's stack on 5432/9200). Not yet run; see
[Pending verification](#pending-verification-needs-docker).

```bash
docker compose up -d      # postgres, elasticsearch, one-shot elasticsearch-init
./mvnw test               # needs no infra (Modulith verify; contextLoads is @Disabled)
./mvnw spring-boot:run    # needs the stack
```

What the stack sets up on first start (empty volumes):

| Piece | File | Does |
|---|---|---|
| Postgres DB `dictionary`, owner `dictionary_owner` | `docker-compose.yml` | Local dev credentials only |
| Schema | `docker/postgres/init/01-schema.sql` | Mirrors the updater schema so `validate` passes |
| Read-only role `dictionary_reader` | `docker/postgres/init/02-readonly-role.sh` | `SELECT` on all tables + default privileges for tables `dictionary_owner` creates later |
| Seed data | `docker/postgres/init/03-seed.sql`, `docker/elasticsearch/seed/<index>.ndjson` | **Not written yet** (waits for a Docker machine) |
| ES indexes `vocab`, `example-sentence` | `docker/elasticsearch/init.sh` + `mappings/` | Creates missing indexes, bulk-loads any seed files. `number_of_replicas=0` so one node is green |

Init scripts only run on an **empty** volume. To re-run them: `docker compose down -v`, then `up -d`.

**Full load** (the whole dictionary instead of seed data). Start from empty volumes, then run
the updater once against this stack **as the owner**, so the reader's default privileges apply:

```bash
# in ~/repos/japanese-dictionary-updater-component
./mvnw spring-boot:run \
  -Dspring-boot.run.arguments="--spring.datasource.url=jdbc:postgresql://localhost:5433/dictionary \
  --spring.datasource.username=dictionary_owner --spring.datasource.password=dictionary_owner \
  --spring.elasticsearch.uris=http://localhost:9201"
```

Known gap: sentence text written by the updater is in large objects, and Postgres 16 has no
default privileges for those. Until X7, `dictionary_reader` cannot read full-load sentence text.
A workaround for local use only: as owner, run
`DO $$ DECLARE o oid; BEGIN FOR o IN SELECT oid FROM pg_largeobject_metadata LOOP EXECUTE format('GRANT SELECT ON LARGE OBJECT %s TO dictionary_reader', o); END LOOP; END $$;`

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

## Reference material

- `written_reports/Architectures/Container_Diagram_V0{1,2,3}.JPG`: hand-drawn container diagrams (V03 is current).
- `written_reports/Archived/`: early data model (`japanese_app_data_model.puml`), use cases, story map, pipeline sketch. Includes ideas not yet sourced (pitch accent, frequency, grammar points).
- `~/repos/baddeeApp/claude-logs/2026-09-19.md`: updater build log and decisions.
- Updater `NEXT_STEPS.md` is **stale**: Kanji and JMnedict spokes are implemented (parsers + tests exist).
