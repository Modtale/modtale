#!/usr/bin/env bash
set -euo pipefail
component="${1:?component required}"
tag="${2:?image tag required}"
shift 2
: "${PROJECT_ID:?PROJECT_ID required}"
image="gcr.io/$PROJECT_ID/modtale-$component"
gcloud auth configure-docker gcr.io --quiet
docker pull "$image:$tag" || true
if [[ "$tag" != latest ]]; then docker pull "$image:latest" || true; fi
DOCKER_BUILDKIT=1 docker build \
  --cache-from "$image:$tag" --cache-from "$image:latest" \
  --build-arg BUILDKIT_INLINE_CACHE=1 -t "$image:$tag" "$@" .
docker push "$image:$tag"
