#!/bin/sh
# One-shot ES init: create each index from mappings/<index>.json if it does not
# exist yet, then bulk-load seed/<index>.ndjson if that file exists.
# Mappings mirror the updater's Spring Data documents (VocabDocument,
# ExampleSentenceDocument), except number_of_replicas=0 so a single node is green.
set -eu

ES_URL="${ES_URL:-http://localhost:9201}"

for mapping in /es-init/mappings/*.json; do
  index="$(basename "$mapping" .json)"

  status="$(curl -s -o /dev/null -w '%{http_code}' "$ES_URL/$index")"
  if [ "$status" = "200" ]; then
    echo "index $index exists, skipping create"
  else
    echo "creating index $index"
    curl -sf -X PUT "$ES_URL/$index" -H 'Content-Type: application/json' --data-binary "@$mapping"
    echo
  fi

  seed="/es-init/seed/$index.ndjson"
  if [ -f "$seed" ]; then
    echo "bulk-loading $seed"
    curl -sf -X POST "$ES_URL/$index/_bulk?refresh=true" -H 'Content-Type: application/x-ndjson' --data-binary "@$seed" \
      | grep -q '"errors":false' || { echo "bulk load of $index reported errors"; exit 1; }
  fi
done

echo "elasticsearch init done"
