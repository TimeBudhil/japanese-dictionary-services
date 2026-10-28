# japanese-dictionary-services — Context

Stable facts about this service. Read this first. For tasks, implementation details,
and the decision log, see [`PLAN.md`](./PLAN.md).

_Last updated: 2026-10-28_

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
| `sense_gloss` / `sense_pos` / `sense_misc` | `sense_id` FK, `gloss` / `pos` / `misc` | element collections. JMdict `<field>` and `<dial>` tags are **not stored** |
| `example_sentence` | `id` PK, `japanese`, `english`, `index_words`, `source` | `index_words` = space-separated headwords (loose link to vocab, no FK) |
| `kanji` | `character` PK, `codepoint`, `onyomi[]`, `kunyomi[]`, `nanori[]`, `meanings[]`, `stroke_count`, `grade`, `joyo`, `jlpt` (old 1–4), `frequency`, `classical_radical`, `stroke_paths[]` | `[]` = Postgres `text[]` |
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

## Module layout (package-by-feature, Spring Modulith)

Root package: `baddee.japanese_dictionary_services`. Each feature module has
`controller/ dto/ integration/ models/ repository/ services/`.

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
| `shared` _(planned)_ | Modulith OPEN module: security, CORS, error format, logging/tracing config |

Package conventions:
- `models/`: read-only `@Immutable` JPA entities.
- `repository/`: Spring Data JPA, read methods only.
- `integration/`: ES documents/clients, adapters to other modules, and module plumbing (logging, tracing).
- No module queries another module's tables. It calls that module's service instead.

## Tech stack

- Java **25**, Spring Boot **4.1.2**, Maven wrapper (`./mvnw`).
- Spring Web MVC, Data JPA (Postgres), Data Elasticsearch, Security, Actuator.
- Planned: Spring Modulith, springdoc-openapi, Postgres driver. `spring-cloud-starter-config` is being removed.
- API: REST + JSON under `/api/v1/...`, OpenAPI/Swagger UI, page/size pagination.
- Security: public reads (`permitAll` on `/api/**`), CORS allow-list, stateless, only Actuator health/info exposed.
- The updater uses Java 26 / Boot 4.1.1 and package `badee` (one "d"). The repos are independent. This difference is intentional.

## Running locally (planned, Phase 0 in PLAN.md)

This repo will have its **own** `docker-compose.yml`: Postgres on **5433** and ES on **9201**
(so it can run beside the updater's stack on 5432/9200). It will auto-load a small
committed **seed dataset**. For the full dictionary, run the updater once with its
datasource/ES URIs pointed at 5433/9201.

```bash
docker compose up -d
./mvnw test
./mvnw spring-boot:run
```

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
