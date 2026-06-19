#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
collection_dir="${COLLECTION_DIR:-$repo_root/mock-db/collections}"
mongo_uri="${MONGODB_URI:-mongodb://localhost:27017}"
db_name="${MONGODB_DATABASE_NAME:-modtale-mock}"

if ! command -v mongoimport >/dev/null 2>&1; then
  echo "mongoimport is required. Install MongoDB Database Tools first." >&2
  exit 1
fi

if [ ! -d "$collection_dir" ]; then
  echo "Mock DB collection directory not found: $collection_dir" >&2
  exit 1
fi

echo "Importing mock database into '$db_name' from $collection_dir"

for file in "$collection_dir"/*.json; do
  [ -e "$file" ] || {
    echo "No JSON fixtures found in $collection_dir" >&2
    exit 1
  }

  collection="$(basename "$file" .json)"
  echo " - $collection"
  mongoimport \
    --uri "$mongo_uri" \
    --db "$db_name" \
    --collection "$collection" \
    --drop \
    --jsonArray \
    --file "$file"
done

echo "Mock database import complete."
