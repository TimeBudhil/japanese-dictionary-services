#!/bin/sh
# Save and restore the dev stack's data volumes (D71), so a full load (about 16 min)
# is done once and restored in about 30 s.
#
#   sh docker/snapshot.sh save [name]      # default name: full
#   sh docker/snapshot.sh restore [name]
#   sh docker/snapshot.sh list
#
# save:    stops the stack, copies both volumes (Postgres + Elasticsearch) into
#          docker/snapshots/<name>/ as .tar.gz files, then starts the stack again.
# restore: DELETES the current volumes (docker compose down -v), recreates them from
#          the snapshot, then starts the stack.
#
# A snapshot is raw database files, so it only works with the same images it was saved
# with (postgres:16, the same Elasticsearch version). `restore` refuses a snapshot made
# with other images. Take a new snapshot after a schema change or an image upgrade.
# Snapshots are large and local: docker/snapshots/ is in .gitignore.
set -eu

cd "$(dirname "$0")/.."
PROJECT="${COMPOSE_PROJECT_NAME:-$(basename "$PWD")}"
VOLUMES="dictionary-postgres-data dictionary-elasticsearch-data"
SNAPSHOTS="docker/snapshots"
# Any image that has tar + gzip. postgres:16 is already pulled for the stack.
TOOL_IMAGE="postgres:16"

# Git Bash on Windows rewrites "/backup"-style arguments into Windows paths; stop it,
# and give docker a host path it understands (pwd -W exists only in Git Bash).
export MSYS_NO_PATHCONV=1
host_path() { (cd "$1" && (pwd -W 2>/dev/null || pwd)); }

images() { docker compose config --images | sort; }

save() {
  name="$1"
  dir="$SNAPSHOTS/$name"
  mkdir -p "$dir"
  host_dir="$(host_path "$dir")"

  echo "stopping the stack (consistent files)"
  docker compose stop
  for volume in $VOLUMES; do
    echo "saving $volume"
    docker run --rm --entrypoint tar \
      -v "${PROJECT}_${volume}:/data:ro" -v "$host_dir:/backup" \
      "$TOOL_IMAGE" czf "/backup/$volume.tar.gz" -C /data .
  done
  images > "$dir/images.txt"
  date -u +%Y-%m-%dT%H:%M:%SZ > "$dir/created.txt"

  echo "starting the stack"
  docker compose up -d
  echo "snapshot '$name' saved in $dir"
}

restore() {
  name="$1"
  dir="$SNAPSHOTS/$name"
  [ -d "$dir" ] || { echo "no snapshot '$name' in $SNAPSHOTS (try: list)"; exit 1; }
  if ! images | cmp -s - "$dir/images.txt"; then
    echo "snapshot '$name' was made with other images:"
    cat "$dir/images.txt"
    echo "the stack now uses:"
    images
    exit 1
  fi
  host_dir="$(host_path "$dir")"

  echo "deleting the current volumes"
  docker compose down -v
  for volume in $VOLUMES; do
    echo "restoring $volume"
    docker volume create \
      --label "com.docker.compose.project=$PROJECT" \
      --label "com.docker.compose.volume=$volume" \
      "${PROJECT}_${volume}" > /dev/null
    docker run --rm --entrypoint tar \
      -v "${PROJECT}_${volume}:/data" -v "$host_dir:/backup:ro" \
      "$TOOL_IMAGE" xzf "/backup/$volume.tar.gz" -C /data
  done

  # The volumes are not empty, so the Postgres init scripts do not run, and
  # elasticsearch-init skips the existing indexes (and their seed files).
  docker compose up -d
  echo "snapshot '$name' restored (made $(cat "$dir/created.txt"))"
}

list() {
  [ -d "$SNAPSHOTS" ] || { echo "no snapshots"; return; }
  for dir in "$SNAPSHOTS"/*/; do
    [ -f "$dir/created.txt" ] || continue
    echo "$(basename "$dir")  $(cat "$dir/created.txt")  $(du -sh "$dir" | cut -f1)"
  done
}

case "${1:-}" in
  save) save "${2:-full}" ;;
  restore) restore "${2:-full}" ;;
  list) list ;;
  *) echo "usage: sh docker/snapshot.sh save|restore [name] | list"; exit 1 ;;
esac
