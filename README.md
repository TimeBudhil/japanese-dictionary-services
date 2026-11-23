# japanese-dictionary-services

The read-only API of a Japanese dictionary app (vocabulary, kanji, names, example sentences).
Spring Boot modular monolith. All dictionary data is written by a separate repo, the updater
(`../japanese-dictionary-updater-component`); the two share only Postgres and Elasticsearch.

## The docs

| File | Holds |
|---|---|
| [`written_reports/PLAN.md`](written_reports/PLAN.md) | **Start here.** Next session, build order, decisions in force, open decisions |
| [`written_reports/REQUIREMENTS.md`](written_reports/REQUIREMENTS.md) | What the **whole app** must do, by module. Its rows and status tags are the task list. Read one section at a time |
| `written_reports/specs/` | Specs and their tickets for work in progress, one dated folder per session. Local only (gitignored) |
| [`written_reports/CONTEXT.md`](written_reports/CONTEXT.md) | How it works: architecture, rules, data caveats, conventions, glossary |
| [`written_reports/HISTORY.md`](written_reports/HISTORY.md) | Archive of done work and old decisions. Read only when digging |
| [`CLAUDE.md`](CLAUDE.md) | Rules, including "Keeping the docs current": which doc each kind of change updates |
| [`written_reports/Architectures/`](written_reports/Architectures/) | Hand-drawn container diagrams (V03 is current, but it says MySQL; it's Postgres) |
| `../japanese-dictionary-updater-component/docs/PLAN.md` | The updater's tasks (`X<n>`) and decisions (`U<n>`) |

This repo is the home of app-wide documents (D92).

Ids in the docs: `D<n>` decision, `O<n>` open decision, `X<n>` a task in the updater repo; in
REQUIREMENTS.md, `R<n>` in scope, `S<n>` stretch, `OUT<n>` out of scope, `N<n>` non-functional.
Search `PLAN.md` (or REQUIREMENTS.md for requirement ids) for the id; for the full reasoning,
search `HISTORY.md`.

## Coming back after a while

1. Read **"Next session"** at the top of [`written_reports/PLAN.md`](written_reports/PLAN.md). It says where things
   stand and what to do first.
2. Check both repos are clean and up to date: `git status` and `git pull` here and in
   `../japanese-dictionary-updater-component`. The updater must sit next to this repo; the
   scripts below use that path.
3. Find the requirement you are working on in
   [`written_reports/REQUIREMENTS.md`](written_reports/REQUIREMENTS.md): its module, status
   (`🔵` = built without Docker, not yet checked on data) and acceptance criterion.
4. Start the stack and run the app (next section).
5. When you need to know how something works, read [`written_reports/CONTEXT.md`](written_reports/CONTEXT.md).

## Run it

Needs: JDK 25+, Docker, a POSIX shell for `docker/*.sh` (Git Bash on Windows).

```bash
docker compose up -d       # Postgres :5433, Elasticsearch :9201, seed data
./mvnw test                # needs no stack, no Docker
./mvnw verify              # also runs the *IT classes on Testcontainers (needs Docker, not the compose stack)
./mvnw spring-boot:run     # needs the stack; http://localhost:8080/swagger-ui.html
curl "localhost:8080/api/v1/vocabulary?q=日本"
```

The ports differ from the updater's own stack (5432/9200), so both can run at once.

CI (`.github/workflows/ci.yml`) runs `./mvnw -B verify` on every push and pull request.

**Two modes**, chosen when the volumes are first created. Init scripts only run on empty
volumes, so to switch, run `docker compose down -v` first.
- **Seed mode** (default): the schema and a small seed (11 words, 8 kanji, 2 names, 2 sentences).
- **Empty mode** (`DICTIONARY_SEED=false docker compose up -d`): no tables, for a full load.
  Never point the updater at a seed-mode database: Flyway can't adopt tables it didn't create.

**Full load** (the whole dictionary, ~16 min). Start in empty mode, then run the updater
against this stack *as the owner*, so the read-only role can read the tables Flyway creates:

```bash
docker compose down -v && DICTIONARY_SEED=false docker compose up -d

cd ../japanese-dictionary-updater-component
./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local --server.port=8091 \
  --spring.datasource.url=jdbc:postgresql://localhost:5433/dictionary \
  --spring.datasource.username=dictionary_owner --spring.datasource.password=dictionary_owner \
  --spring.elasticsearch.uris=http://localhost:9201"
curl -X POST -H "Authorization: Bearer dev-token" localhost:8091/admin/updates   # all feeds
```

One feed only: `POST /admin/updates/{id}` with `jmdict`, `jmnedict`, `kanji` or `tatoeba`.

**Snapshots** save a full load so you don't repeat it:
`sh docker/snapshot.sh save` once, `sh docker/snapshot.sh restore` later (~30 s; it **deletes**
the current volumes first), `list` to see them. A snapshot only works with the same images, so
take a new one after a schema change or an image upgrade.

**Generated files**: never edit these by hand.

| File | Regenerate with | When |
|---|---|---|
| `docker/postgres/seed/schema.sql` | `sh docker/generate-schema.sh` | the updater adds a migration |
| `docker/postgres/seed/seed.sql`, `docker/elasticsearch/seed/*` | `python docker/generate-seed.py` (needs a full load) | schema change, or a new seed case |

After regenerating: `docker compose down -v && docker compose up -d`, then start the app.
It runs `ddl-auto=validate`, so a schema mismatch fails at startup.
