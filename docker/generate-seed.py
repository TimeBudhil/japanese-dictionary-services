"""Regenerates the committed seed data from a full-load dev stack (D19, D32).

Prerequisite: this repo's stack is up in empty mode and the updater has run a full
load into it (see docs/CONTEXT.md, "Running locally"; D74). The script copies
a fixed list of real rows out of that stack, so the seed always matches the
updater's real schema and data shapes:

    python docker/generate-seed.py

Writes:
    docker/postgres/seed/seed.sql
    docker/elasticsearch/seed/vocab.ndjson
    docker/elasticsearch/seed/example-sentence.ndjson

Edit the lists below to change what is seeded, then rerun the script, and commit
the regenerated files.
"""

import json
import subprocess
import urllib.request
from pathlib import Path

PG_CONTAINER = "dictionary-postgres"
PG_USER = "dictionary_owner"
PG_DB = "dictionary"
ES_URL = "http://localhost:9201"

# JMdict entries, each picked for an edge case.
VOCAB = {
    1582710: "日本: multi-reading (にほん / にっぽん), common",
    1464530: "日本語: multi-reading, common",
    1000430: "あの: kana-only entry (no kanji writing), common",
    1000420: "彼の / あの: same kana as 1000430 (two exact hits for あの)",
    1358280: "食べる: ichidan (る) verb",
    1343950: "書く: godan (う) verb, many senses",
    1467640: "猫: kanji + hiragana + katakana readings",
    2839132: "猫タワー: non-common, prefix hit for 猫",
    1206730: "学校: two kanji writings (学校 / 學校)",
    1012840: "やばい: slang misc tag",
    2561870: "a gloss longer than 255 characters (text column)",
}

# Kanji with their radicals (RADKFILE) and stroke paths (KanjiVG).
KANJI = ["日", "本", "語", "食", "書", "猫", "学", "校"]

NAMES = {
    5002725: "さくら: kana-only name (empty kanji array)",
    5543705: "田中: name with kanji",
}

# Tatoeba sentences whose index_words hit seeded vocab (猫, 食べる). Ids are the
# Japanese Tatoeba sentence ids, which the updater keys on.
SENTENCES = [170682, 207310]

ROOT = Path(__file__).resolve().parent


def query(sql: str) -> list[dict]:
    """Runs `sql` in the dev Postgres and returns its rows as dicts."""
    wrapped = f"select coalesce(json_agg(t), '[]') from ({sql}) t"
    out = subprocess.run(
        ["docker", "exec", PG_CONTAINER, "psql", "-U", PG_USER, "-d", PG_DB, "-At", "-c", wrapped],
        check=True, capture_output=True, encoding="utf-8",
    ).stdout
    return json.loads(out)


def lit(value) -> str:
    """SQL literal for a JSON value (arrays become text[], cast on insert)."""
    if value is None:
        return "null"
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, (int, float)):
        return str(value)
    if isinstance(value, list):
        return "array[" + ", ".join(lit(v) for v in value) + "]::text[]" if value else "'{}'"
    return "'" + str(value).replace("'", "''") + "'"


def inserts(table: str, rows: list[dict], columns: list[str]) -> list[str]:
    lines = [f"-- {table}: {len(rows)} rows"]
    for row in rows:
        values = ", ".join(lit(row[c]) for c in columns)
        lines.append(f"insert into {table} ({', '.join(columns)}) values ({values});")
    return lines + [""]


def ids(values) -> str:
    return ", ".join(str(v) if isinstance(v, int) else lit(v) for v in values)


def es_docs(index: str, doc_ids) -> list[str]:
    body = json.dumps({"ids": [str(i) for i in doc_ids]}).encode()
    request = urllib.request.Request(f"{ES_URL}/{index}/_mget", body, {"Content-Type": "application/json"})
    docs = json.load(urllib.request.urlopen(request))["docs"]
    missing = [d["_id"] for d in docs if not d.get("found")]
    if missing:
        raise SystemExit(f"{index}: ids not in ES: {missing}")
    lines = []
    for doc in docs:
        lines.append(json.dumps({"index": {"_id": doc["_id"]}}, ensure_ascii=False))
        lines.append(json.dumps(doc["_source"], ensure_ascii=False))
    return lines


def main() -> None:
    v = ids(VOCAB)
    entries = query(f"select ent_seq, common from vocab_entry where ent_seq in ({v}) order by ent_seq")
    if len(entries) != len(VOCAB):
        raise SystemExit("some VOCAB ids are missing from Postgres: run a full load first")
    readings = query(f"select id, ent_seq, text, kanji from reading where ent_seq in ({v}) order by id")
    senses = query(f"select id, ent_seq from sense where ent_seq in ({v}) order by id")
    s = f"select id from sense where ent_seq in ({v})"
    glosses = query(f"select sense_id, gloss_order, gloss from sense_gloss where sense_id in ({s}) order by 1, 2")
    pos = query(f"select sense_id, pos_order, pos from sense_pos where sense_id in ({s}) order by 1, 2")
    misc = query(f"select sense_id, misc_order, misc from sense_misc where sense_id in ({s}) order by 1, 2")

    k = ids(KANJI)
    kanji_cols = ["character", "codepoint", "onyomi", "kunyomi", "nanori", "meanings", "stroke_count",
                  "grade", "joyo", "jlpt", "frequency", "classical_radical", "stroke_paths"]
    kanji = query(f"select {', '.join(kanji_cols)} from kanji where character in ({k}) order by character")
    kanji_radical = query(f"select kanji, radical from kanji_radical where kanji in ({k}) order by 1, 2")
    radicals = query("select character, stroke_count from radical where character in "
                     f"(select radical from kanji_radical where kanji in ({k})) order by character")

    names = query("select ent_seq, kanji, readings, name_types, translations from name_entry "
                  f"where ent_seq in ({ids(NAMES)}) order by ent_seq")
    sentences = query("select id, source, japanese, english, index_words "
                      f"from example_sentence where id in ({ids(SENTENCES)}) order by id")

    sql = [
        "-- Seed data for local dev (D19, D32). GENERATED by docker/generate-seed.py",
        "-- from a full updater load; do not edit by hand. Run by init/01-seed-data.sh after schema.sql.",
        "--",
        "-- Edge cases covered:",
        *[f"--   vocab {i}: {why}" for i, why in VOCAB.items()],
        f"--   kanji {''.join(KANJI)} with radicals and stroke paths",
        *[f"--   name {i}: {why}" for i, why in NAMES.items()],
        f"--   sentences {', '.join(map(str, SENTENCES))}: index_words hit seeded vocab (猫, 食べる)",
        "",
        "begin;",
        "",
        *inserts("vocab_entry", entries, ["ent_seq", "common"]),
        *inserts("reading", readings, ["id", "ent_seq", "text", "kanji"]),
        *inserts("sense", senses, ["id", "ent_seq"]),
        *inserts("sense_gloss", glosses, ["sense_id", "gloss_order", "gloss"]),
        *inserts("sense_pos", pos, ["sense_id", "pos_order", "pos"]),
        *inserts("sense_misc", misc, ["sense_id", "misc_order", "misc"]),
        *inserts("radical", radicals, ["character", "stroke_count"]),
        *inserts("kanji", kanji, kanji_cols),
        *inserts("kanji_radical", kanji_radical, ["kanji", "radical"]),
        *inserts("name_entry", names, ["ent_seq", "kanji", "readings", "name_types", "translations"]),
        *inserts("example_sentence", sentences, ["id", "source", "japanese", "english", "index_words"]),
    ]
    sql += [
        "",
        "-- Identity columns: continue after the seeded ids.",
        "select setval(pg_get_serial_sequence('reading', 'id'), (select max(id) from reading));",
        "select setval(pg_get_serial_sequence('sense', 'id'), (select max(id) from sense));",
        "",
        "commit;",
        "",
    ]
    (ROOT / "postgres/seed/seed.sql").write_text("\n".join(sql), encoding="utf-8", newline="\n")

    seed_dir = ROOT / "elasticsearch/seed"
    seed_dir.mkdir(exist_ok=True)
    for index, doc_ids in [("vocab", VOCAB), ("example-sentence", SENTENCES)]:
        lines = es_docs(index, doc_ids)
        (seed_dir / f"{index}.ndjson").write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")

    print(f"vocab {len(entries)} (readings {len(readings)}, senses {len(senses)}, glosses {len(glosses)}), "
          f"kanji {len(kanji)}, radicals {len(radicals)}, names {len(names)}, sentences {len(sentences)}")


if __name__ == "__main__":
    main()
