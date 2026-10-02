#!/usr/bin/env bash
set -euo pipefail

component="${1:?component required}"
tag="${2:?image tag required}"
shift 2
: "${PROJECT_ID:?PROJECT_ID required}"

case "$component" in
  backend|frontend|status) ;;
  *) echo "::error::Unsupported image component: $component" >&2; exit 1 ;;
esac
if [[ ! "$tag" =~ ^[a-zA-Z0-9_][a-zA-Z0-9_.-]{0,127}$ ]]; then
  echo "::error::Invalid image tag: $tag" >&2
  exit 1
fi

# Only accept inputs that cannot override the release tag or publish before the
# scan. The trusted workflow supplies public URLs as build args, never secrets.
build_args=()
while (($#)); do
  case "$1" in
    --build-arg|-f|--file)
      if (($# < 2)); then
        echo "::error::$1 requires a value" >&2
        exit 1
      fi
      build_args+=("$1" "$2")
      shift 2
      ;;
    --build-arg=*|--file=*) build_args+=("$1"); shift ;;
    *) echo "::error::Unsupported build option: $1" >&2; exit 1 ;;
  esac
done

# Shell steps do not receive these GitHub cache credentials automatically. The
# workflow exposes them through ghaction-github-runtime; v1 is no longer usable.
: "${ACTIONS_RUNTIME_TOKEN:?GitHub Actions cache runtime token required}"
: "${ACTIONS_RESULTS_URL:?GitHub Actions cache v2 results URL required}"
command -v trivy >/dev/null || { echo "::error::Trivy must be installed before building" >&2; exit 1; }

image="gcr.io/$PROJECT_ID/modtale-$component"
cache="type=gha,version=2,scope=modtale-$component"
gcloud auth configure-docker gcr.io --quiet

# Build once, including the Dockerfile's test/typecheck gates, then load locally
# for inspection. Cache misses/eviction are safe; failed optional cache exports
# cannot turn a successful application build into a failed deployment.
docker buildx build \
  --pull --load --platform linux/amd64 \
  --cache-from "$cache" \
  --cache-to "$cache,mode=max,ignore-error=true,timeout=5m" \
  -t "$image:$tag" "${build_args[@]}" .

# Scan the exact image just loaded, with no fallback to an old registry tag.
# Fixed CRITICAL OS/library vulnerabilities and scanner errors block publish.
image_id="$(docker image inspect --format '{{.Id}}' "$image:$tag")"
trivy image --config /dev/null --ignorefile /dev/null --disable-telemetry \
  --image-src docker --scanners vuln --severity CRITICAL \
  --ignore-unfixed --exit-code 1 --timeout 10m "$image_id"
if [[ "$(docker image inspect --format '{{.Id}}' "$image:$tag")" != "$image_id" ]]; then
  echo "::error::Release image changed after vulnerability scanning" >&2
  exit 1
fi
docker push "$image:$tag"
if [[ "$component" == status && "$tag" != latest ]]; then
  docker tag "$image_id" "$image:latest"
  docker push "$image:latest"
fi
