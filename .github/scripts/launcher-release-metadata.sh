#!/usr/bin/env bash
set -euo pipefail

case "$GITHUB_REF_NAME" in
  main) channel=stable ;;
  develop) channel=develop ;;
  *) echo '::error::Launcher releases must run from main or develop.' >&2; exit 1 ;;
esac

version="${INPUT_VERSION:-}"
if [ -z "$version" ]; then
  # Keep the three numeric installer components within Windows MSI limits.
  version="0.$((2 + GITHUB_RUN_NUMBER / 65536)).$((GITHUB_RUN_NUMBER % 65536))"
fi
if ! [[ "$version" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]]; then
  echo '::error::Launcher version must contain three numeric components, for example 0.2.0.' >&2
  exit 1
fi
IFS=. read -r major minor patch <<< "$version"
if (( ${#major} > 3 || ${#minor} > 3 || ${#patch} > 5 )) || (( major > 255 || minor > 255 || patch > 65535 )); then
  echo '::error::Launcher version exceeds native installer limits (255.255.65535).' >&2
  exit 1
fi
if [ "$channel" = develop ]; then
  version="$version-develop.$GITHUB_RUN_NUMBER.${GITHUB_RUN_ATTEMPT:-1}"
  tag="launcher-develop-v$version"
else
  tag="launcher-v$version"
fi
{
  echo "channel=$channel"
  echo "tag=$tag"
  echo "version=$version"
} >> "$GITHUB_OUTPUT"
