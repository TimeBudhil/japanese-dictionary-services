#!/bin/bash
# Creates the read-only role this service connects as (D23).
# Runs once, on first start of an empty volume (docker-entrypoint-initdb.d), in both
# modes. In empty mode no table exists yet: the default privileges below cover the
# tables the updater's Flyway creates later, as dictionary_owner.
set -euo pipefail

psql -v ON_ERROR_STOP=1 \
     --username "$POSTGRES_USER" \
     --dbname "$POSTGRES_DB" \
     -v reader_password="$DB_READER_PASSWORD" <<'EOSQL'
CREATE ROLE dictionary_reader LOGIN PASSWORD :'reader_password';

GRANT CONNECT ON DATABASE dictionary TO dictionary_reader;
GRANT USAGE ON SCHEMA public TO dictionary_reader;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO dictionary_reader;

-- Tables the owner (or the updater, connected as the owner) creates later.
ALTER DEFAULT PRIVILEGES FOR ROLE dictionary_owner IN SCHEMA public
    GRANT SELECT ON TABLES TO dictionary_reader;
EOSQL
