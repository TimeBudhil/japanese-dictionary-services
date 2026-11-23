# CLAUDE.md — japanese-dictionary-services

The read-only dictionary API. Its data is written by the updater in
`../japanese-dictionary-updater-component`, which has its own `CLAUDE.md` with the same rules.

## Reading

- **Start of a session:** read "Next session" at the top of `docs/PLAN.md`.
- **How something works** (architecture, data caveats, module conventions, glossary): `docs/CONTEXT.md`.
- **What the whole app must do** (all four applications): `written_reports/REQUIREMENTS.md`.
- **Running, loading data, regenerating seed files:** `README.md`.
- **An id that `docs/PLAN.md` doesn't list, or the user asks about the past:** `docs/HISTORY.md`.
  It is the archive; leave it closed otherwise.

## The docs

One purpose per file, each with a budget. Before adding past a budget, cut first.

| File | Holds | Budget |
|---|---|---|
| `CLAUDE.md` | These rules and pointers | 80 lines |
| `README.md` | How to restart and run | 100 lines |
| `docs/CONTEXT.md` | What the code can't say: why, rules, caveats | 8 KB |
| `docs/PLAN.md` | Next session, remaining tasks, decisions in force, open decisions | 12 KB |
| `docs/HISTORY.md` | Done tasks, superseded decisions, verification notes | none |
| `written_reports/REQUIREMENTS.md` | App-wide requirements, by application, then In/Stretch/Out/NFR | none |

- **README stays a lightweight index:** how to run, plus a link to every doc with its path.
  A new doc gets a row in README's docs table, and a step in "Coming back after a while" if a
  returning reader needs it. Section order is fixed: "The docs" first, then "Coming back after
  a while", then "Run it" last.
- **This repo holds the app-wide docs** (requirements, diagrams) for all four applications (D92).
- **Requirements name the owning module** of every dictionary-service feature, and the spoke
  or `hub` for updater features. Change a status tag in the same change as the code.
- **Built without Docker:** tag the requirement `🔵 built, unverified` until it's checked on
  seed or full-load data.

- **A fact** about how things work goes to CONTEXT.md. **A task, status or decision** goes to PLAN.md.
- **Point to the code** for anything it already says (schema, config, versions, class layout):
  name the file and add only what the file doesn't show.
- **Write sentences that read on their own.** Ids go at the end as a reference, like "(D12)".
- **Decisions** get the next free `D<n>`, one line in PLAN.md's table. Ids are permanent.
- **Done or superseded items** move to HISTORY.md under a dated heading, word for word, and
  leave PLAN.md. HISTORY.md is append-only.
- **Cross-repo decisions** live in the repo whose code or data they change. The other repo links
  to it. Updater tasks this repo waits on are `X<n>`, tracked in the updater's `docs/PLAN.md`.
- **Code comments** explain only what the code can't: a non-obvious reason or a gotcha.
- Bump `_Last updated_` in each doc you change.

## Working

- **Open decisions** (`O<n>`): give your advice, then let the user choose with a question.
- **Update the docs along with the code**, in the same change.
- **The user makes every commit.** At the end of each unit of work, stop with a suggested commit
  message, wait for "committed", then check `git status` before continuing.
- **End every task** with:
  - `Docs:` and the files updated, or why none needed it.
  - `Choices I made:` the small implementation choices, in the chat only. They go into the docs
    only when the user says "log that".
- **End of a session:** rewrite "Next session" (15 lines max) so the next session can start from
  it alone.
- **When the user corrects how docs are written,** add the correction to this file.
