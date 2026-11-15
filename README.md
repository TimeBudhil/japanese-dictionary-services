# japanese-dictionary-services

The read-only dictionary API (vocabulary, kanji, names, example sentences) of a
jisho.org-style Japanese dictionary app. Spring Boot modular monolith, Java 25.

It reads Postgres + Elasticsearch and never writes them. All dictionary data is loaded by a
separate repo, the **updater** (`../japanese-dictionary-updater-component`). The two repos
never call each other; they share only the databases.

This file is the entry point. It tells you **what to read, in what order**, and how to get
running again after time away. The facts themselves live in `docs/`, so they are kept in one
place.

## Coming back after a while: do this first

1. **Read the status.** Open [`docs/PLAN.md`](docs/PLAN.md), section **Status**. Its last bullet
   says what is next and links the place to start (currently Phase 2, "Start here").
2. **Check both repos are where the docs expect.**
   ```bash
   git status && git log --oneline -5
   git -C ../japanese-dictionary-updater-component status
   git -C ../japanese-dictionary-updater-component branch -a
   ```
   The updater must be checked out **next to this repo**: `docker/generate-schema.sh` and the
   full-load steps use the path `../japanese-dictionary-updater-component`. Compare its branch
   with what PLAN.md's Status says.
3. **Start the dev stack and run the tests.**
   ```bash
   docker compose up -d       # Postgres :5433 + Elasticsearch :9201, seed data
   ./mvnw test                # no infra needed: Modulith verify + controller tests
   ./mvnw spring-boot:run     # needs the stack
   ```
   Then open <http://localhost:8080/swagger-ui.html>, or try
   `curl "localhost:8080/api/v1/vocabulary?q=日本"`.
   For the whole dictionary instead of the small seed, see CONTEXT.md, "Running locally"
   (full load or snapshot restore).
4. **Settle open decisions before coding.** PLAN.md, "Open decisions" lists each one with
   advice and the phase it blocks.

Requirements: JDK 25 or newer, Docker, and a POSIX shell for the `docker/*.sh` scripts (Git
Bash on Windows).

## How the documentation is organised

| File | What it holds | Read it when |
|---|---|---|
| [`docs/CONTEXT.md`](docs/CONTEXT.md) | **Stable facts**: the repos and their roles, hard rules, architecture, where each kind of data lives, the Postgres schema and ES indexes, module layout and code conventions, tech stack, running locally, glossary | First, always. It answers "how does this work?" |
| [`docs/PLAN.md`](docs/PLAN.md) | **Moving parts**: status, build order, per-phase task lists, cross-repo tasks, the decision log, open decisions | Second. It answers "where are we, what's next, and why is it like this?" |
| `written_reports/` | Hand-drawn container diagrams (`Architectures/Container_Diagram_V03.JPG` is current) and archived early designs | For the big picture or old ideas. V03 says MySQL; the real choice is Postgres |

### Suggested reading path

1. CONTEXT.md, top to bottom (about 10 minutes). Pay most attention to **Hard rules**,
   **Module layout** and the **package conventions**: every new module copies them.
2. PLAN.md → **Status** → the current phase. The current phase lists the decisions to read
   first and the classes to copy.
3. The code of the finished template module, `src/main/java/.../vocabulary/`. Its classes cite
   the decision numbers that explain them.
4. Look up any decision number you meet (next section).

### The numbering scheme

The docs cite short ids instead of repeating reasons. Search PLAN.md for the id to find it.

| Prefix | Meaning | Where |
|---|---|---|
| `D<n>` | A decision that was made, with its reason. Superseded ones are kept and marked | PLAN.md, "Decision log" |
| `O<n>` | An open decision, with Claude's advice and the phase that must settle it | PLAN.md, "Open decisions" |
| `X<n>` | A task to do **in the updater repo**, tracked here so it is not forgotten | PLAN.md, "Cross-repo tasks" |
| `U<n>` | A decision logged in the updater repo. Cross-repo ones appear in both repos (D70) | updater `NEXT_STEPS.md`, section 9 |
| `Phase <n>` | Build order: 0 foundation, 1 vocabulary, 2 kanji, 3 names, 4 sentences, 5 normalization, 6 parsing, 7 search, 8 lists | PLAN.md |

## Repo map

```
src/main/java/baddee/japanese_dictionary_services/
  SecurityConfig, RequestIdFilter, UnexpectedErrorHandler   app-level, root package (D25)
  vocabulary/              Phase 1 module, the template for all others
    VocabularyApi + records  the module's public API (D37)
    aop/ controller/ integration/ models/ repository/ services/
src/main/resources/        application.properties (local defaults), application-prod.properties
src/test/java/...          ModularityTests, VocabularyControllerTests (template)
docker-compose.yml         dev stack: Postgres 16 :5433, Elasticsearch 9 :9201
docker/
  postgres/init/           first-start scripts: schema + seed (seed mode), read-only role
  postgres/seed/           schema.sql (GENERATED from the updater's Flyway), seed.sql (GENERATED)
  elasticsearch/           index mappings, seed ndjson, init.sh
  generate-schema.sh       rebuild schema.sql from the updater's migrations
  generate-seed.py         rebuild the seed files from a full load
  snapshot.sh              save / restore / list full-load volume snapshots
docs/                      CONTEXT.md, PLAN.md
written_reports/           diagrams and archived designs
```

Generated files: never edit `docker/postgres/seed/*.sql` or `docker/elasticsearch/seed/*` by
hand. Regenerate them with the scripts above (CONTEXT.md, "Running locally").

## The updater repo's docs

`../japanese-dictionary-updater-component`:

- `NEXT_STEPS.md`: its status, open work (AWS, Cognito, monitoring) and decisions U1–U9.
- `CONTEXT.md`: its glossary (feed, spoke, sub-source, entry, retired entry, retire guard).
- `docs/configuration.md`: profiles (`local`, `docker`, `prod`), properties, env vars, logins.
- `docs/adr/`: ADR-0001 (retire in Postgres, delete from search), ADR-0002 (open sources only).
- `src/main/resources/db/migration/`: the Flyway migrations, the source of truth for the schema.

## Keeping the docs useful

- A new **fact** about how things are → CONTEXT.md. A **task**, **status** or **decision** →
  PLAN.md. Bump the `_Last updated_` line of the file you change.
- Every decision gets a `D<n>` with its reason. If it changes the updater's code, schema,
  feeds or compose file, also log it in the updater's `NEXT_STEPS.md` as a `U<n>` (D70).
- Tick tasks `[x]` with a date. When a later change makes an old line wrong, mark it
  superseded and say by what; don't delete it.
- At the end of a work session, update PLAN.md's **Status** so its last bullet says exactly
  where to start next time.
- After an updater schema change: `sh docker/generate-schema.sh`, then
  `docker compose down -v && docker compose up -d`, and check that the app still boots
  (`ddl-auto=validate`).
