#!/bin/bash
# Seed mode (default): creates the schema and loads the seed data (D19, D59).
# Empty mode (DICTIONARY_SEED=false): does nothing, so the database stays empty for a
# full load. The updater's Flyway then creates the schema itself (D74); it cannot run
# on tables that schema.sql created, because there is no Flyway history for them.
# Runs once, on first start of an empty volume (docker-entrypoint-initdb.d).
set -euo pipefail

if [ "${DICTIONARY_SEED:-true}" = "false" ]; then
  echo "DICTIONARY_SEED=false: empty database for a full load (the updater's Flyway creates the schema)"
  exit 0
fi

for file in /seed/schema.sql /seed/seed.sql; do
  echo "running $file"
  psql -v ON_ERROR_STOP=1 -q --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -f "$file"
done
