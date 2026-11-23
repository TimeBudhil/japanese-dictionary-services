# japanese-dictionary-services — Context

How the service works and the rules it follows: only what the code can't tell you.
Tasks and decisions: [`PLAN.md`](./PLAN.md). Running it: [`README.md`](../README.md).

_Last updated: 2026-11-24_

## The application

A jisho.org-style Japanese dictionary, with learning features later. One repo per application:

| Repo | Role | Writes | Reads |
|---|---|---|---|
| `../japanese-dictionary-updater-component` | **Updater**: scheduled import of upstream dictionary files | Postgres + ES | upstream files |
| this repo | **Dictionary service**: the read-only API | nothing | Postgres + ES |
| _future_ user service | Stretch: accounts and syncing lists across devices (D94) | its own store | this API |
| _future_ front-end | Web UI; user lists, history, import/export on the device (D94) | browser storage | this API |

**Hard rules**
- The updater is the only writer of dictionary data. This service connects with a read-only role.
- The two never call each other. They share only the databases.
- No user accounts or user data in this service.

```
JMdict  JMnedict  KanjiDic2  KanjiVG  RADKFILE  Tatoeba   (upstream files)
                        ▼
      Updater: scheduled, hash-checked, idempotent
          ▼                          ▼
 Postgres (source of truth)    Elasticsearch (search copy)
          └────────────┬─────────────┘
                       ▼
   Dictionary service (this repo, modular monolith)
                       ▼
            Front-end / user service
```

## The data

| Data | Postgres | Elasticsearch | Source |
|---|---|---|---|
| Vocabulary | yes | index `vocab` | JMdict |
| Example sentences | yes | index `example-sentence` | Tatoeba |
| Kanji + radicals | yes | no | KanjiDic2, KanjiVG, RADKFILE |
| Names | yes | no | JMnedict (~740k rows) |

The schema is the updater's Flyway migrations, copied in `docker/postgres/seed/schema.sql`. The
ES mappings are in `docker/elasticsearch/mappings/`. Read those for tables and columns.
What they don't show:
- **Retired entries.** When an entry leaves upstream, the updater sets `retired_at`, keeps the
  row and deletes the ES document. This service returns retired rows like active ones (D76).
- **Order.** Gloss, POS and misc lists keep JMdict order through their `*_order` columns.
  Readings and senses are in JMdict order only because the updater inserts them in order.
- **POS and misc tags** are the expanded text (`noun (common) (futsuumeishi)`), not codes (X10).
  JMdict `<field>` and `<dial>` tags are not stored (X3).
- `reading.kanji`: true = kanji writing (keb), false = kana reading (reb).
- `example_sentence.id` is the Japanese Tatoeba id. `index_words` is a space-separated list of
  headwords, a loose link to vocab with no foreign key. In ES, `indexWords` is one keyword (the
  whole string), so `word=` search needs X12's `headwords` array (D97).
- **Kanji.** Arrays are in KanjiDic2 / KanjiVG order. Every jōyō kanji has stroke paths.
  `codepoint` hex is mixed case as KanjiDic2 gives it (`732b`, `342E`); it is returned as stored.
- **Bare rows** come from KanjiVG: stroke paths only, no KanjiDic2 data and no radicals. 21 are
  radical forms or characters KanjiDic2 lacks (⺉, 々); 9 are CJK compatibility ideographs that
  look like ordinary kanji (令 U+F9A8, not U+4EE4). `codepoint IS NULL` identifies them.
- **RADKFILE stand-ins.** About 20 radicals are written as a kanji containing them (犯 for 犭,
  艾 for 艹, 尚 for ⺌, 化 for 亻); the API returns them as stored (D88).
- **Names.** `name_entry` arrays are in JMnedict order; `name_types` holds each type once. The
  lists are flat, so which reading goes with which writing is not stored. Name search depends on
  two functions and trigram indexes that the updater's X5 migration adds; until it lands (and
  `schema.sql` is regenerated), `GET /api/v1/names?q=` returns 500 (D91).
- `kanji.jlpt` is the **old** 1–4 scale (`oldJlpt` in the API). `source_version` and `flyway_schema_history` are the
  updater's bookkeeping; ignore them.
- ES has no `id` field in the updater's mapping. ES adds it dynamically, and vocab search sorts
  on `id.keyword` last for stable paging.
- Japanese ES fields use the default analyzer, so matching is exact characters only (X2).

## Modules

Root package `baddee.japanese_dictionary_services`. One Spring Modulith module per feature:

| Module | Job | Status |
|---|---|---|
| `vocabulary` | Vocab details (Postgres) + search candidates (ES) | done, **the template** |
| `kanji` | Kanji, radicals, radical search (Postgres only) | done |
| `names` | Names, search by kanji/reading/translation (Postgres only) | built, waits on X5 |
| `sentences` | Example sentences (Postgres + ES) | |
| `normalization` | Romaji → kana, era years, numerals | |
| `parsing` | Input type, deinflection, sentence splitting | |
| `search` | Orchestrator: normalize, parse, call modules, rank | |
| `lists` | Premade read-only lists | last |

There is no shared module (D25). Three classes in the root package act before any module, and
no module imports them: `SecurityConfig` (catch-all chain that denies `/api` paths no module
claims), `RequestIdFilter` (request id in MDC and the `X-Request-Id` header),
`UnexpectedErrorHandler` (last-resort 500).

## Conventions every module follows

Copy them from `vocabulary/`; the code shows how.
- **Public API:** one `<Module>Api` interface + the records it uses, in the module's base package.
  Sub-packages are internal. Other modules use only the API, never another module's tables (D37).
- **Sub-packages:** `aop/` (own logging aspect), `controller/`, `integration/` (ES clients, the
  module's own security chain + CORS), `models/` (read-only entities), `repository/` (read methods
  only), `services/` (implements the API).
- **Security:** each module's chain claims only its own `/api/v1/<module>/**` paths. Read the CORS
  origins from `app.cors.allowed-origins`.
- **Errors:** module exceptions extend `ErrorResponseException`. The API returns `Optional`; only
  the controller turns empty into a 404.
- **Paging and limits:** `page`/`size` as in D28; query text max 100 chars; ES-backed searches
  reject pages past 10,000 hits with a 400.
- **Entities:** read-only, children mapped one way, nullable booleans read as false, no N+1 (D35).
- **Tests:** `@WebMvcTest` with the module API mocked and the **real** security configs imported;
  fixtures are real entries (D77).

## Stack

Java 25, Spring Boot 4.1.2, Spring Modulith, springdoc (see `pom.xml`). The ES client is 9.x,
so the server must be ES 9 (D57). Settings are env vars with local defaults
(`src/main/resources/application.properties`); the `prod` profile has no defaults, so a missing
variable fails startup. The updater runs Java 26 / Boot 4.1.1 in package `badee`, on purpose.

## Glossary

| Term | Meaning |
|---|---|
| ent_seq | JMdict / JMnedict entry id; primary key of vocab and names |
| keb / reb | JMdict kanji writing / kana reading |
| sense / gloss | One meaning of an entry / one English translation in it |
| feed / spoke | Updater terms: one tracked upstream file / the module importing one source |
| candidate | A hit a module returns to `search` before ranking |
| match tier | Ranking class: exact > deinflected > prefix > partial |
| retired entry | Gone upstream; row kept, ES document deleted |
| bare row | A `kanji` row with only KanjiVG stroke paths (radical forms such as ⺉) |
| seed / empty mode | How the dev stack starts: small seed data, or empty for a full load |
| full load / snapshot | All feeds imported (~16 min) / its saved volumes (restore ~30 s) |

## Integration tests

- `*IT` classes extend `support/IntegrationTest`: full app, MockMvc, one shared Postgres and ES container started per JVM from the `docker/` init, mapping and seed files. The app connects as `dictionary_reader`. Surefire skips them; Failsafe runs them in `verify` (D99).
