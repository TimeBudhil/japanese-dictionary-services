# Japanese Dictionary App: Requirements

The requirements for the **whole application**: all four applications, not just this repo.
This file is the authority for app-wide requirements (D92). Per-repo decisions stay in each repo's
`docs/PLAN.md` (`D<n>` here, `U<n>` in the updater).

_Last updated: 2026-11-24_

## Summary

**Problem:** Learners need a Japanese dictionary in the style of jisho.org, built on open data
(JMdict, JMnedict, KanjiDic2, KanjiVG, RADKFILE, Tatoeba). It should search words, kanji, names
and example sentences from any kind of input: English, romaji, kana, kanji or inflected forms.
Learners also need to keep their own lists and move them in and out of Anki.

**Users:** Japanese learners, through a web front-end, with no account. Fewer than 100 daily users
in the first year (peak around 5 requests per second).

**Success looks like:** A learner types `tabenakatta`, `食べられなかった` or `eat` and gets 食べる
ranked first, together with its kanji, related names and example sentences. They save it to a list
on their device and export the list to Anki with the fields they chose. Upstream dictionary
releases appear within 48 h without anyone doing it by hand.

**Constraints:**
- One developer.
- AWS hosting is planned. The exact shape (Fargate and Elastic Cloud, or something smaller) is
  decided once every application runs in a container (O4). The tentative budget is
  $100–200 a month.
- Only openly licensed sources with a public download (U12).
- The development machine used on 2026-11-23 has no Docker. Code can be written and unit-tested
  with mocks there, but not run against Postgres or ES (see the 🔵 tag).

### The four applications

| Application | Repo | Role | First release? |
|---|---|---|---|
| Front-end | _not created_ | React SPA on S3 + CloudFront. Search UI, plus user lists and Anki/CSV/TSV import and export kept **on the device** (D94) | yes |
| Dictionary service | `japanese-dictionary-services` (this repo) | Read-only dictionary API, a Spring Modulith monolith | yes |
| Updater | `japanese-dictionary-updater-component` | The only writer of dictionary data: a scheduled import into Postgres and ES | yes |
| User service | _not created_ | Learner accounts (Cognito) and syncing lists across devices. Spring Boot + Postgres | no, Stretch (S22) |

The front-end calls the dictionary service. The updater and the dictionary service share only the
databases and never call each other.

### Conventions in this file

- **Status:** `✅ existing` built and checked against real data · `🔵 built, unverified` mocked
  tests pass, not yet checked against seed or full-load data (promote to ✅ on a machine with
  Docker) · `🟡 partial` · `⬜ new`.
- **Module:** for the dictionary service, the Spring Modulith module that owns the feature
  (package `baddee.japanese_dictionary_services.<module>`; `root` = the root package). For the
  updater, the spoke or `hub`.
- **Blocked by:** an updater task (`X<n>`, in the updater's `docs/PLAN.md`) or an open decision
  (`O<n>`, in this repo's `docs/PLAN.md`).
- **IDs:** `R` in scope, `S` stretch, `OUT` out of scope (not `X`, which already names updater
  tasks), `N` non-functional. Numbers are unique across the whole file and permanent. A moved
  requirement keeps its ID.

---

## 1. Dictionary service (`japanese-dictionary-services`)

### 1.1 In-Scope

#### R1: Vocabulary lookup and search `🟡` · module `vocabulary`

| ID | Requirement | Acceptance criterion | Status |
|----|-------------|----------------------|--------|
| R1.1 | `GET /api/v1/vocabulary/{entSeq}` returns the entry with readings and senses (glosses, POS, misc) in JMdict order. Retired entries are returned unchanged (D76) | Seed entry 1358280 returns 食べる with senses in `*_order`. An unknown id returns a 404 ProblemDetail | `✅` |
| R1.2 | `GET /api/v1/vocabulary?q=` searches ES in tiers: exact > prefix > partial, paged, with no retired entries (D36) | `q=日本` on seed data returns 日本 before longer matches. A retired entry never appears | `✅` |
| R1.3 | A page past 10,000 hits is rejected | `page=600&size=20` returns 400 | `✅` |
| R1.4 | Japanese matches tokens and kana variants, and English has an exact tier | `q=cat` ranks entries whose gloss is exactly "cat" first | `⬜` blocked by X2 |
| R1.5 | "Words using this kanji": active words whose writing contains a given kanji, common words first, paged | `containsKanji=猫` on seed data returns 猫 and 猫タワー, with 猫 (common) first | `⬜` |

#### R2: Kanji and radicals `✅` · module `kanji`

| ID | Requirement | Acceptance criterion | Status |
|----|-------------|----------------------|--------|
| R2.1 | `GET /api/v1/kanji/{character}` returns `KanjiDetail` with stroke paths. Bare rows and retired kanji are returned unchanged (D85, D86) | 猫 returns readings, meanings and stroke paths. ⺉ returns a bare row. An unknown character returns 404 | `✅` |
| R2.2 | Radical search returns kanji containing **all** the selected radicals, ordered by strokes, then frequency (unranked last), then character. It is paged, with no bare rows or retired kanji (D84) | 化 + 木 returns 休 and 体, in that order | `✅` |
| R2.3 | Radical search filters `strokes` (exact), `grade` and `joyo`, combined with AND (D89) | `strokes=11&joyo=true` returns only 11-stroke jōyō kanji | `✅` |
| R2.4 | `GET /api/v1/radicals` returns all radicals grouped by stroke count, with kanji counts, not paged (D87) | The response has one group per stroke count. Each radical's count equals its kanji in the data | `✅` |

#### R3: Names `🟡` · module `names`

| ID | Requirement | Acceptance criterion | Status |
|----|-------------|----------------------|--------|
| R3.1 | `GET /api/v1/names/{entSeq}` returns a name with its writings, readings, types and translations in JMnedict order | Seed name 5543705 (田中) returns 200. An unknown id returns 404 | `✅` |
| R3.2 | `GET /api/v1/names?q=` searches in tiers: exact (writing, reading or whole translation, any case) > prefix > a later translation word starts with q. Then by id, with no retired names (D90) | `q=田中` on seed data returns 200 with 5543705 first | `🟡` blocked by X5 (returns 500 until then) |

#### R4: Example sentences `⬜` · module `sentences`

| ID | Requirement | Acceptance criterion | Status |
|----|-------------|----------------------|--------|
| R4.1 | `GET /api/v1/sentences/{id}` returns a Japanese sentence with its English translation, without the raw `index_words`. Retired sentences are returned unchanged (D98) | Seed sentence 170682 returns 200. An unknown id returns 404 | `⬜` |
| R4.2 | `GET /api/v1/sentences?word=` returns sentences whose index words contain that headword, paged. Sentences marked as a good example of the word (`~`) come first, then shorter ones, then by id (D97) | `word=食べる` returns only sentences that list 食べる in `index_words`, with the seed sentence marked `~` first | `⬜` blocked by X12 |
| R4.3 | `GET /api/v1/sentences?q=` searches the Japanese or English text in ES, paged, with no retired sentences. Until X2 it uses the default analyzers (D98) | `q=eat` returns sentences whose English contains "eat" | `⬜` (match quality needs X2) |
| R4.4 | `SentencesApi` exposes both searches to `search` and for "example sentences of this word" | `ApplicationModules.verify()` passes, and `search` uses only `SentencesApi` | `⬜` |
| R4.5 | Sentence detail returns its words as segments, each linked to a vocab `ent_seq` when one matches (uses `ParsingApi` R6.3 and `VocabularyApi`) | Every segment of seed sentence 170682 that is a dictionary word carries an `ent_seq` | `⬜` needs R6 |

#### R5: Input normalization `⬜` · module `normalization` · blocked by O2

| ID | Requirement | Acceptance criterion | Status |
|----|-------------|----------------------|--------|
| R5.1 | Converts romaji to kana in Hepburn and wāpuro spellings: ん before a vowel or y, っ from doubled consonants, long vowels | `konnichiha`→こんにちは, `kan'i`→かんい, `gakkou`→がっこう, `tōkyō`→とうきょう | `⬜` |
| R5.2 | Converts era years to Gregorian years and back | 令和7年 ↔ 2025, 平成元年 → 1989 | `⬜` |
| R5.3 | Converts kanji numerals to digits and back, including 万 and 億 | 二千二十五 ↔ 2025, 三億 → 300000000 | `⬜` |
| R5.4 | `NormalizationApi` is the only entry point other modules use | `verify()` passes. Unit tests cover each table | `⬜` |

#### R6: Input parsing `⬜` · module `parsing` · blocked by O1

| ID | Requirement | Acceptance criterion | Status |
|----|-------------|----------------------|--------|
| R6.1 | Detects the input type: English, romaji, Japanese, or likely name | `eat`→English, `taberu`→romaji, `食べる`→Japanese | `⬜` |
| R6.2 | Deinflects a word to its dictionary form and names the chain of inflections | 食べられなかった → 食べる (potential + negative + past) | `⬜` |
| R6.3 | Splits a sentence into words | 私は学生です → 私 / は / 学生 / です | `⬜` |
| R6.4 | `ParsingApi` is the only entry point other modules use | `verify()` passes | `⬜` |

#### R7: Unified search `⬜` · module `search`

| ID | Requirement | Acceptance criterion | Status |
|----|-------------|----------------------|--------|
| R7.1 | `GET /api/v1/search?q=&types=vocabulary,kanji,names,sentences&page=&size=` returns ranked results across types | `types=kanji` returns only kanji. An unknown type returns 400 | `⬜` |
| R7.2 | The flow is: detect input → normalize → deinflect → call each module's API. It never reads another module's tables (D10) | `tabenakatta` finds 食べる. `verify()` passes | `⬜` |
| R7.3 | One deterministic ranking: tier (exact > deinflected > prefix > partial), then common/frequency, then type, adjusted by input type (D17) | Plain unit tests of the comparator. The same query always gives the same order | `⬜` |
| R7.4 | Never returns retired entries (D76) | Searching a retired entry's headword doesn't return it | `⬜` |
| R7.5 | Tag filter as a `tags=` parameter (POS or misc codes, combined with AND), with or without `q` | `tags=slang` returns only words tagged slang. An unknown tag returns 400 | `⬜` blocked by X10 |
| R7.6 | The response says how the input was read: input type, normalized form, and the deinflected forms tried | `q=tabenakatta` reports romaji → たべなかった → 食べる (negative + past), even when there are no results | `⬜` |

#### R8: Premade lists `⬜` · module `lists`

| ID | Requirement | Acceptance criterion | Status |
|----|-------------|----------------------|--------|
| R8.1 | `GET /api/v1/lists` returns the catalogue, and `GET /api/v1/lists/{id}` a paged list. Read-only (D11) | An unknown list id returns 404 | `⬜` |
| R8.2 | Lists built from current data: common words, by POS, expressions, slang (misc tag), and kanji by grade, jōyō, old JLPT or frequency | Every entry in "jōyō grade 1" has `grade=1` | `⬜` |
| R8.3 | Subject and dialect lists | `field=medicine` lists only words tagged with that subject | `⬜` blocked by X3 |
| R8.4 | Words by JLPT level | N5 list is non-empty and every entry has `jlpt=5` | `⬜` blocked by O3, X6 |

#### R9: Furigana `⬜` · modules `vocabulary` (word readings) and `sentences` (sentence readings, using `parsing`)

| ID | Requirement | Acceptance criterion | Status |
|----|-------------|----------------------|--------|
| R9.1 | Vocabulary detail returns furigana segments, from the JmdictFurigana data (R14.7), that align each kanji run with its kana | 食べる → [食:た][べる]. 今日 → [今日:きょう] as one segment | `⬜` needs R14.7 |
| R9.2 | Sentence detail returns furigana segments for its kanji words | Every kanji run in the sentence has a reading segment | `⬜` needs R4.5 and R9.1 |

#### R10: Word of the day `⬜` · module `lists`

| ID | Requirement | Acceptance criterion | Status |
|----|-------------|----------------------|--------|
| R10.1 | `GET /api/v1/lists/word-of-the-day` returns one common, non-retired word, the same for every caller on a UTC date | Two calls on the same day return the same entry. The next day returns a different one | `⬜` |

#### R11: API platform `✅` · `root` package + every module

| ID | Requirement | Acceptance criterion | Status |
|----|-------------|----------------------|--------|
| R11.1 | Every error is an RFC 9457 ProblemDetail (D29) | A 400, 404, 405 and 500 each return `application/problem+json` | `✅` |
| R11.2 | `page` ≥ 0 (default 0), `size` 1–100 (default 20). Anything out of range returns 400 (D28) | `size=101` returns 400 | `✅` |
| R11.3 | Query text is at most 100 characters (D79) | A 101-character `q` returns 400 | `✅` |
| R11.4 | Every response carries an `X-Request-Id`, and every log line includes it (D30, D80) | The header is present, and the same id appears in that request's logs | `✅` |
| R11.5 | Reads are public. Writes return 405, and `/api` paths no module claims are denied (D8, D83) | `POST /api/v1/vocabulary` returns 405. `GET /api/v1/nope` is denied | `✅` |
| R11.6 | CORS allows only the origins in `app.cors.allowed-origins` | A request from an unlisted origin gets no CORS headers | `✅` |
| R11.7 | OpenAPI docs and Swagger UI are served | `/swagger-ui.html` lists every endpoint | `✅` |
| R11.8 | Only actuator `health` and `info` are exposed | `/actuator/env` returns 404 | `✅` |
| R11.9 | Connects with the read-only role and validates the schema at startup (D3, D23) | A schema mismatch fails startup. An `INSERT` through the role is refused | `✅` |

### 1.2 Stretch

- **S1: Radical picker grey-out** (`kanji`): `GET /api/v1/radicals?selected=…` marks radicals that
  can't combine with the selection. _Why stretch:_ the picker works without it.
- **S2: Normalization and parsing endpoints** (`normalization`, `parsing`): public HTTP endpoints
  for the APIs in R5 and R6. _Why stretch:_ `search` only needs the Java APIs.
- **S3: Handwriting kanji search** (new module `handwriting`, or recognition in the front-end):
  draw a kanji and get candidates from its strokes. _Why stretch:_ radical search covers the need.
- **S4: OCR from image** (new module `ocr`): photo → text → `search`. _Why stretch:_ it needs an
  OCR engine or a paid service.
- **S5: Slang dataset** (`lists`, plus a new updater spoke): _Why stretch:_ there is no openly
  licensed source yet (U12).
- **S6: Kanji mastery map** (front-end, or the user service): a grid of the kanji a user knows,
  possibly from Anki data. _Why stretch:_ it depends on user data.
- **S7: Pitch accent, frequency, grammar points, audio** (`vocabulary`): _Why stretch:_ there is
  no sourced data yet (`written_reports/Archived/`).
- **S23: Did-you-mean spelling suggestions** (`search`): fuzzy suggestions when nothing matches.
  _Why stretch:_ R7.6 already explains an empty result.

### 1.3 Out-of-Scope

- **OUT1: User data in the dictionary service.** Lists live on the device (D94), and accounts
  belong to the user service (D1, D33).
- **OUT2: Writing dictionary data.** The updater is the only writer. This service never creates
  database objects (D91).
- **OUT3: Calling the updater.** The two share only the databases.

### 1.4 Non-functional

| ID | Requirement | Acceptance criterion |
|----|-------------|----------------------|
| N1.1 | Search endpoints (`/vocabulary`, `/names`, `/sentences`, `/kanji` radical search, `/search`) respond fast enough for search pages | p95 < 500 ms on the full load, measured over 1,000 mixed real queries |
| N1.2 | Detail endpoints are at least as fast | p95 < 500 ms on the full load |
| N1.3 | Each module stays inside its boundary (D9, D37) | `ApplicationModules.verify()` passes in `./mvnw test` |
| N1.4 | Every endpoint has a controller test with the real security configs (D93) | `./mvnw test` is green, with no Docker needed |
| N1.5 | Every module has an integration test against seed data (Testcontainers) (D93, D99). `vocabulary` is covered by `VocabularyModuleIT` (🔵, not yet run); `names` and `kanji` are next | `./mvnw verify` is green on CI |
| N1.6 | In production it reads with a read-only ES key as well as the read-only DB role (D23) | An index or delete call with the production key returns 403 |
| N1.7 | API tasks are stateless, so more of them can run behind a load balancer | Two tasks serve the same results, with no sticky sessions |

---

## 2. Updater (`japanese-dictionary-updater-component`)

### 2.1 In-Scope

#### R12: Scheduled import `✅` · `hub` + spokes `jmdict`, `jmnedict`, `kanji`, `tatoeba`

| ID | Requirement | Acceptance criterion | Status |
|----|-------------|----------------------|--------|
| R12.1 | A nightly cron (03:00 UTC) runs every feed | The logs show one run per feed per day | `✅` |
| R12.2 | Unchanged files are skipped by content hash | A second run on the same file writes nothing | `✅` |
| R12.3 | Upserts are idempotent under stable keys | Running the same file twice gives identical rows | `✅` |
| R12.4 | Entries gone upstream are retired, not deleted, behind a retire guard (U11) | A file below the guard retires nothing and logs a WARN | `✅` |
| R12.5 | Search copies are written to ES `vocab` and `example-sentence`. Names and kanji stay in Postgres only | Index counts equal active Postgres rows | `✅` |
| R12.6 | A feed never runs twice at once: a trigger for a feed that is already running is skipped and logged | A manual run started during the cron run of the same feed logs "already running" and writes nothing | `⬜` |
| R12.7 | Each feed's `retire-guard` is tuned from the logged retirement counts of real releases (first run: ~0.0005% per feed, against the 2% allowed) | After 3 real releases, every feed's guard is set from its logged counts | `⬜` |

#### R13: Admin API `🟡` · `hub`

| ID | Requirement | Acceptance criterion | Status |
|----|-------------|----------------------|--------|
| R13.1 | `POST /admin/updates` runs all feeds, and `/admin/updates/{id}` runs one | Returns 2xx, and the run appears in the logs | `✅` |
| R13.2 | The admin API accepts only Cognito **access** tokens from the `updater-admin` group: create the user pool, an admin app client and the group, and set `COGNITO_ISSUER_URI` and `COGNITO_CLIENT_IDS` (`prod` profile) | 200 for a member, 403 for a non-member, 401 with no token. ID tokens are rejected | `🟡` decoder built and tested (`CognitoJwtDecoderConfig`), but the pool isn't created and it isn't live |
| R13.3 | The dev token and stub decoder are removed once no environment uses them | `StubJwtDecoderConfig` and the `updater.admin.dev-token` / `stub-auth` properties are gone | `⬜` |

#### R14: Data the dictionary service needs `🟡` · spokes as listed

| ID | Requirement | Acceptance criterion | Status |
|----|-------------|----------------------|--------|
| R14.1 | X5: `pg_trgm`, two functions and two trigram indexes on `name_entry` (spec in the updater's PLAN), for R3.2 | A name search takes 3–40 ms on the full load | `⬜` |
| R14.2 | X2 (`jmdict`, `tatoeba`): kuromoji + ICU multi-fields, plus a keyword subfield on `glosses`, for R1.4 and R4.3 | The ES mapping has the fields, and an exact gloss query matches | `⬜` |
| R14.3 | X3 (`jmdict`): store `<field>` and `<dial>` tags with order columns, for R8.3 | Rows exist after a re-import, in JMdict order | `⬜` |
| R14.4 | X6 (new spoke): word-level JLPT, for R8.4 | Vocab rows carry a JLPT level from the chosen list | `⬜` blocked by O3 |
| R14.5 | X8: keep upstream order for X3's new tables | Order columns exist | `✅` kanji and names checked; X3's tables get order columns as part of R14.3 |
| R14.6 | X10 (`jmdict`): store POS and misc as JMdict codes, for R7.5 | Rows hold `n`, `v1`, not expanded text | `⬜` decision pending |
| R14.7 | New spoke `furigana`: import JmdictFurigana, keyed by `ent_seq` plus the writing and reading, for R9 | Every common vocab writing that contains kanji has segments | `⬜` (licence check against U12 first) |
| R14.8 | X12 (`tatoeba`): `headwords` and `exampleFor` keyword arrays on the `example-sentence` document, parsed from `index_words`, for R4.2 (D97) | Seed sentence 170682 has `headwords` 最近, 猫, は, 鼠, を, 食べる | `⬜` |

#### R15: Deployment `⬜` · all backend repos · hosting deferred until everything is containerized (O4)

| ID | Requirement | Acceptance criterion | Status |
|----|-------------|----------------------|--------|
| R15.1 | Each backend builds a container image, and CI runs `./mvnw verify` before building it | A merge to `main` produces a tagged image only if verify passes | `🟡` (the updater has a `Dockerfile`; the dictionary service doesn't) |
| R15.2 | The updater runs as **exactly one** instance (the cron runs in-process) | The deployment's instance count is fixed at 1 | `⬜` |
| R15.3 | Secrets (`DB_*`, `ES_*`, `COGNITO_*`) come from the platform's secret store, never from images or repos | No secret appears in an image or a repo | `⬜` |
| R15.4 | Postgres has `dictionary_reader` with read-only grants, created by infra from `docker/postgres/dictionary_reader.sql` with a real password. ES has one write key (updater) and one read-only key (service) | N1.6 passes. The updater's key can write | `⬜` |
| R15.5 | The updater's memory is sized from a measured JMdict run (the JVM takes 75% of the container) | A full load completes with no out-of-memory error at the chosen size | `⬜` |
| R15.6 | The updater's network allows outbound HTTPS to EDRDG, GitHub (KanjiVG) and Tatoeba, and inbound only from admin callers. `updater.kanjivg.url` is pinned, since GitHub can rate-limit (once seen as a 403) | An admin call from outside the allowed sources is refused. A full load succeeds | `⬜` |

### 2.2 Stretch

- **S8: Bulk upserts** (`jmdict`): `SEQUENCE` ids and batch statements cut the ~10 min JMdict run.
  _Why stretch:_ a nightly run has time.
- **S9: Link `example_sentence.index_words` to vocab ids** (`tatoeba`). _Why stretch:_ R4.2 works
  on headwords.
- **S10: Cleanup of long-retired, unreferenced radicals** (`kanji`). _Why stretch:_ harmless rows.
- **S11: Admin UI for feeds.** _Why stretch:_ the secured `/admin` API and curl are enough.
- **S12: More than one updater instance** (ShedLock or an external schedule). _Why stretch:_ one is
  enough.
- **S25: Local ES security** (`xpack.security.enabled=true`) so the dictionary service gets a
  read-only ES user locally too. _Why stretch:_ production keys already enforce it (R15.4).
- **S26: A config server** to replace the duplicated `local` / `docker` property files.
  _Why stretch:_ two files are manageable.
- **S27: Update container diagram V03**: Postgres (not MySQL), the local stacks, and the separate
  observability ES. _Why stretch:_ the docs already say this in words.
- **S28: Updater housekeeping**: remove `hibernate.dialect` (detected automatically) and upgrade
  to Boot 4.1.2. _Why stretch:_ nothing is broken.

### 2.3 Out-of-Scope

- **OUT4: Sources that aren't openly licensed** (e.g. Jreibun). U12.
- **OUT5: AWS OpenSearch.** The 9.x client rejects it (U15, D57).
- **OUT6: Serving reads.** The updater writes only. The dictionary service reads.

### 2.4 Non-functional

| ID | Requirement | Acceptance criterion |
|----|-------------|----------------------|
| N2.1 | Upstream releases are live within 48 h | Release date to first successful run is ≤ 48 h |
| N2.2 | A failed or partial run never breaks reads or retires entries | Kill a run midway: the API keeps serving, and the retired count is unchanged |
| N2.3 | A full load finishes in under 30 min (now ~16 min) | Timed on the production-sized instance |
| N2.4 | A failed run alerts the developer | A forced failure produces an alert within 1 h |
| N2.5 | Integration tests cover persist and index round-trips for each spoke (`✅` `*ModuleIT`) | `./mvnw verify` is green on CI |
| N2.6 | Observability data goes to its own ES, never the search cluster (U16) | No observability index exists on the search cluster |

---

## 3. User service (_repo not created_): Stretch for the first release

Lists live on the device (D94), so the first release needs no user service and no learner
accounts.

### 3.1 In-Scope

None for the first release. R16 moved to S22. R17–R19 moved to the front-end (section 4).

### 3.2 Stretch

- **S22: Accounts and list sync** (was R16): learners sign in with Cognito (email + password) and
  their device lists sync to the user service. Spring Boot + Postgres, in its own database and
  login, never touching dictionary tables. When it's built, these come with it:
  - every endpoint requires a valid access token;
  - a user can delete their account and all its data;
  - a user sees only their own lists;
  - data is encrypted in transit and at rest (was N3.1);
  - daily backups are kept 7 days (was N3.2);
  - it has integration tests (was N3.4).
  _Why stretch:_ local lists cover one device.
- **S13: Achievements.** Badges and streaks. _Why stretch:_ it doesn't help a learner look up words.
- **S14: Payments or subscriptions.** _Why stretch:_ the app is free for now.
- **S15: Community features** (shared lists, comments). _Why stretch:_ moderation is a lot of work
  for one developer.
- **S16: Synced settings** across devices (comes with S22). _Why stretch:_ settings on the device
  are enough.

### 3.3 Out-of-Scope

- **OUT7: Server-side search history.** History stays on the device (R22).
- **OUT8: Our own password storage.** Cognito owns credentials.

### 3.4 Non-functional

The requirements N3.1, N3.2 and N3.4 moved into S22. N3.3 moved to the front-end as N4.4.

---

## 4. Front-end (_repo not created_)

### 4.1 In-Scope

#### R20: Search and entry pages `⬜`

| ID | Requirement | Acceptance criterion | Status |
|----|-------------|----------------------|--------|
| R20.1 | The search box calls `/api/v1/search` when the learner presses Enter or the search button, not while typing. Results are grouped by type and paged | Typing makes no request. Enter on `taberu` shows 食べる first | `⬜` |
| R20.2 | Detail pages for words, kanji, names and sentences | Each has a shareable URL that loads directly | `⬜` |
| R20.3 | Word pages show example sentences (R4) | 食べる's page lists sentences that contain it | `⬜` |
| R20.4 | Errors and slow responses each show a clear state | A forced 500 shows an error message, not a blank page | `⬜` |
| R20.5 | `#tag` words in the search box are sent as the `tags=` parameter (R7.5) | `#slang やば` sends `q=やば&tags=slang` | `⬜` |
| R20.6 | An empty result explains how the input was read (R7.6) and suggests other types or spellings | `tabenakatta` with no results shows "searched 食べる (negative + past) from たべなかった" | `⬜` |

#### R21: Kanji tools `⬜`

| ID | Requirement | Acceptance criterion | Status |
|----|-------------|----------------------|--------|
| R21.1 | A radical picker, grouped by stroke count (R2.4), runs a radical search (R2.2) | Picking 化 and 木 shows 休 and 体 | `⬜` |
| R21.2 | Kanji pages show the stroke order from the KanjiVG paths | The strokes draw in order | `⬜` |

#### R22: Search history on the device `⬜`

| ID | Requirement | Acceptance criterion | Status |
|----|-------------|----------------------|--------|
| R22.1 | The newest searches are kept in browser storage, can be cleared, and can be turned off | No network request contains the history. "Clear" empties it | `⬜` |

#### R17: Lists on the device `⬜` (moved from the user service, D94)

| ID | Requirement | Acceptance criterion | Status |
|----|-------------|----------------------|--------|
| R17.1 | Create, rename and delete lists, stored in IndexedDB | A renamed list keeps its entries after a page reload | `⬜` |
| R17.2 | Add and remove vocabulary, kanji, example sentences and names, stored by dictionary id (`ent_seq`, character, or sentence id). One list can mix types | A list shows each added entry once, in the order added | `⬜` |
| R17.3 | Lists never leave the device, except as the learner's own export or backup file | No network request carries list contents. Lookups send only dictionary ids | `⬜` |
| R17.4 | Entries that later retire still resolve (D76) | A retired entry in a list still opens its detail | `⬜` |
| R17.5 | At most 100 lists of 5,000 entries each | Adding the 5,001st entry shows a message, and the list is unchanged | `⬜` |
| R17.6 | Protection against data loss: request persistent storage, offer a one-click backup of all lists to a JSON file and a restore from it, and remind the learner when there has been no backup for 30 days | Back up, clear site data, restore: the lists are identical. The reminder appears after 30 days | `⬜` |

#### R18: Export `⬜` (moved from the user service)

| ID | Requirement | Acceptance criterion | Status |
|----|-------------|----------------------|--------|
| R18.1 | Export a list as an Anki deck (`.apkg`), built in the browser. For each entry type, the learner picks which fields go on the front and the back. The choice is remembered on the device | A mixed list imports into Anki desktop with one note per entry, carrying exactly the chosen fields | `⬜` |
| R18.2 | Export a list as CSV or TSV (UTF-8, header row), with the same field picker | It opens in a spreadsheet with Japanese text intact and one column per chosen field | `⬜` |

Fields offered per type:
- **Vocabulary:** word, furigana, readings, meanings, POS, common, JLPT (once X6 lands).
- **Kanji:** character, on and kun readings, meanings, strokes, grade, jōyō, stroke-order image.
- **Sentences:** Japanese, furigana, English.
- **Names:** writing, reading, type, translation.

#### R19: Import `⬜` (moved from the user service)

| ID | Requirement | Acceptance criterion | Status |
|----|-------------|----------------------|--------|
| R19.1 | Import CSV, TSV or an Anki deck into a new list, in the browser, matching rows to dictionary entries through the dictionary API | A file of 100 known words gives a list of 100 entries | `⬜` |
| R19.2 | Rows that match no entry, or more than one, are reported, not silently dropped | The import report lists each unmatched or ambiguous row with its line number | `⬜` |
| R19.3 | Files over 5 MB or 5,000 rows, or of the wrong format, are rejected before any lookups | A 6 MB file shows a reason and makes no API calls | `⬜` |

#### R23: Lists UI `⬜`

| ID | Requirement | Acceptance criterion | Status |
|----|-------------|----------------------|--------|
| R23.1 | _Moved to Stretch (S22): sign-in screens_ | — | — |
| R23.2 | Manage lists, and add an entry from any detail page (R17) | "Add to list" on a word, kanji, name or sentence page saves it | `⬜` |
| R23.3 | Import and export from a list page (R18, R19), showing unmatched rows | The import report lists unmatched rows | `⬜` |

#### R24: Learning extras `⬜`

| ID | Requirement | Acceptance criterion | Status |
|----|-------------|----------------------|--------|
| R24.1 | Furigana above kanji on word and sentence pages (R9), with an on/off toggle | Toggling hides and shows the readings | `⬜` |
| R24.2 | Word of the day on the home page (R10) | The home page shows the day's word | `⬜` |

#### R25: About page `⬜`

| ID | Requirement | Acceptance criterion | Status |
|----|-------------|----------------------|--------|
| R25.1 | An about page credits every data source with its licence, plus contact information | It lists EDRDG (JMdict, JMnedict, KanjiDic2, RADKFILE), KanjiVG, Tatoeba and JmdictFurigana, with licence links | `⬜` |

#### R26: Cross-links between pages `⬜`

| ID | Requirement | Acceptance criterion | Status |
|----|-------------|----------------------|--------|
| R26.1 | A word page lists each kanji in its writing, linked to the kanji page | 日本語 links 日, 本 and 語 | `⬜` |
| R26.2 | A kanji page lists common words that use it (R1.5) | 猫's page lists 猫 | `⬜` needs R1.5 |
| R26.3 | A name page links each kanji in its writings | 田中 links 田 and 中 | `⬜` |
| R26.4 | A sentence page links each of its words to the word page (R4.5) | Clicking 食べ in seed sentence 170682 opens 食べる | `⬜` needs R4.5 |

### 4.2 Stretch

- **S17: Mobile app** (React Native or Flutter), keeping lists in SQLite on the device. It's the
  home for offline mode (S18). _Why stretch:_ the web SPA works on phones first.
- **S18: Offline mode** in the mobile app: a downloadable SQLite copy of the dictionary.
  _Why stretch:_ too large for a browser, and the data export needs design.
- **S19: Onboarding tour and preferences page.** _Why stretch:_ not needed to search.
- **S20: Handwriting input, OCR and mastery-map UIs** for S3, S4 and S6.
- **S21: Immersion resource recommendations** by level. _Why stretch:_ there is no source yet.
- **S24: Japanese UI language.** The UI strings are kept in one file so it can be added later.
  _Why stretch:_ the dictionary data is English glosses.

### 4.3 Out-of-Scope

- **OUT9: Server-side rendering.** A static SPA on S3 + CloudFront is the chosen host.
- **OUT10: Search as you type.** Search runs on Enter (R20.1).

### 4.4 Non-functional

| ID | Requirement | Acceptance criterion |
|----|-------------|----------------------|
| N4.1 | WCAG 2.1 AA | Search, results and lists work keyboard-only. Contrast is ≥ 4.5:1. Japanese text has `lang="ja"`. No axe violations at AA |
| N4.2 | Works on the latest two versions of Chrome, Firefox, Safari and Edge, from 360 px wide | A manual check at 360 px shows no horizontal scroll |
| N4.3 | Fast first load | The search page is interactive in < 3 s on a throttled 4G profile |
| N4.4 | Exporting is quick (was N3.3) | A 1,000-entry list exports in < 10 s in the browser |
| N4.5 | The UI is English only, with every string in one place | No UI string literal outside the strings file (lint rule) |

---

## 5. Whole application: non-functional

| ID | Requirement | Acceptance criterion |
|----|-------------|----------------------|
| N5.1 | 99% monthly availability for the front-end and the dictionary service | Uptime check: ≤ 7.3 h downtime per month |
| N5.2 | Abusive traffic is rate-limited at the edge (D8), sized for fewer than 100 daily users | Requests over the limit get 429 |
| N5.3 | Data licences are honoured: only open sources (U12), with attribution (R25.1) | Every source in the about page is listed with its licence |
| N5.4 | Every repo follows the docs system (D78, U10) | Each repo has CLAUDE.md, README and docs within budget |
| N5.5 | Unverified code is visible as unverified | Features built without Docker carry `🔵` until checked on seed or full data |
| N5.6 | Hosting stays within the budget | The monthly AWS + ES bill is ≤ $200 (tentative, O4) |

_Skipped areas:_
- Usability: covered by the front-end requirements and N4.1.
- Data retention: the server keeps no user data in the first release (D94).

## Open Questions

- **Hosting shape** (Fargate + Elastic Cloud, or one box with Compose) and the final budget.
  Decided after every application runs in a container (O4). It affects R15, N5.1, N5.6 and U15.
- **Rate limit numbers** for N5.2. Decide at deploy time (O4).
- **Observability design** (N2.4, N2.6; U16 is decided): OpenTelemetry vs Elastic APM, log
  shipping, JSON logs, dashboards. Needs its own design session, with the updater.
- **JmdictFurigana licence and format**: check it against U12 before R14.7.
- **Front-end framework:** React is assumed. Confirm when the repo is created.
- **O1** (tokenizer), **O2** (normalization), **O3** (JLPT source), **X10** (POS codes): already
  open in `docs/PLAN.md` and the updater's PLAN.
