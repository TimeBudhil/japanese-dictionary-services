# CLAUDE.md — japanese-dictionary-services

The read-only dictionary API. Its data is written by the updater in
`../japanese-dictionary-updater-component`, which has its own `CLAUDE.md` with the same rules.

## Reading

Read as little as the task needs.
- **Start of a session:** "Next session" at the top of `docs/PLAN.md`.
- **What to build:** `written_reports/REQUIREMENTS.md`, **only the section or R-IDs** for the
  task (grep the id). Never read the whole file.
- **How something works** (architecture, data caveats, module conventions): `docs/CONTEXT.md`.
- **Running, loading data, regenerating seed files:** `README.md`.
- **An id PLAN.md doesn't list, or the user asks about the past:** `docs/HISTORY.md`, by grep
  only. It is the archive; leave it closed otherwise.

## The docs

One purpose per file, each with a budget. Before adding past a budget, cut first.

| File | Holds | Budget |
|---|---|---|
| `CLAUDE.md` | These rules and pointers | 80 lines |
| `README.md` | Index of the docs, return steps, how to run | 100 lines |
| `docs/PLAN.md` | Next session, build order, decisions in force, open decisions | 8 KB |
| `written_reports/REQUIREMENTS.md` | App-wide requirements; its rows and status tags are the task list (D95) | none |
| `docs/CONTEXT.md` | What the code can't say: why, rules, caveats | 8 KB |
| `docs/HISTORY.md` | Done work, superseded decisions, verification notes. Append-only | none |
| `written_reports/specs/` | Specs for work in progress, one `<YYYY-MM-DD>-<name>/` folder each with `spec.md` and `issues/`. Gitignored, never committed | none |

## Keeping the docs current

Update the docs in the same change as the code. After any change, apply every row that fits:

| When this happens | Update |
|---|---|
| A requirement gets built | Its status tag: `🔵` built without Docker, `✅` checked on seed or full data. No task copy elsewhere |
| A requirement is added, changed or moved between buckets | REQUIREMENTS.md, next free id; ids never change |
| A decision is made | One line in PLAN.md's table, next free `D<n>`; the options and reasoning go to HISTORY.md |
| A decision is superseded | Move its row to HISTORY.md word for word, dated |
| A question needs the user | `O<n>` in PLAN.md with your advice; when answered it becomes a `D<n>` |
| You learn how something works, or a data caveat | CONTEXT.md |
| Run steps, ports, scripts or seed files change | README.md "Run it" |
| A new doc or folder | A row in README.md "The docs", and a return step if a returning reader needs it |
| This repo needs updater work | An `X<n>` in the updater's PLAN.md, and "blocked by X<n>" on the requirement here. Build it in the same session when the row needs it (D96) |
| A spec is written | `written_reports/specs/<YYYY-MM-DD>-<name>/spec.md`, with its tickets in `issues/<NN>-<slug>.md` beside it |
| A session ends | Rewrite PLAN.md "Next session" (15 lines max) so the next session can start from it alone |

Bump `_Last updated_` in each doc you change.

## Writing

- **README stays a lightweight index.** Section order is fixed: "The docs", "Coming back after
  a while", "Run it".
- **This repo holds the app-wide docs** (requirements, diagrams) for all four applications (D92).
- **Requirements name the owning module** of each dictionary-service feature, and the spoke or
  `hub` for updater features.
- **Point to the code** for anything it already says (schema, config, versions, class layout).
- **Write sentences that read on their own.** Ids go at the end as a reference, like "(D12)".
- **Cross-repo decisions** live in the repo whose code or data they change; the other links to it.
- **Code comments** explain only what the code can't: a non-obvious reason or a gotcha.
- **No phases.** Name work by its R-ids; "Next session" says which R-ids it closes.

## Working

- **Open decisions:** give your advice, then let the user choose with a question.
- **The user makes every commit.** At the end of each unit of work, stop with a suggested commit
  message, wait for "committed", then check `git status` before continuing.
- **End every task** with:
  - `Requirements:` the R-IDs whose status changed, or "none".
  - `Docs:` the files updated, or why none needed it.
  - `Choices I made:` the small implementation choices, in the chat only. They go into the docs
    only when the user says "log that".
- **When the user corrects how docs are written,** add the correction to this file.
