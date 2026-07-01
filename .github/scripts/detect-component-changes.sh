#!/usr/bin/env bash
set -euo pipefail

default_branch="${DEFAULT_BRANCH:-main}"
event_name="${GITHUB_EVENT_NAME:-}"
head_sha="${PR_HEAD_SHA:-${GITHUB_SHA:-HEAD}}"
base_sha=""
range_label=""

is_zero_sha() {
  [[ -z "${1:-}" || "$1" =~ ^0+$ ]]
}

commit_exists() {
  git cat-file -e "$1^{commit}" 2>/dev/null
}

default_branch_exists() {
  git rev-parse --verify --quiet "origin/$default_branch^{commit}" >/dev/null
}

resolve_push_base() {
  local before_sha="${PUSH_BEFORE_SHA:-}"
  local head="$1"

  if ! is_zero_sha "$before_sha"; then
    if ! commit_exists "$before_sha"; then
      echo "::error::Push base commit '$before_sha' is missing from the checkout."
      exit 1
    fi

    printf '%s\n' "$before_sha"
    return
  fi

  if ! default_branch_exists; then
    git fetch --no-tags --prune origin "+refs/heads/$default_branch:refs/remotes/origin/$default_branch" >/dev/null 2>&1 || true
  fi

  if default_branch_exists; then
    git merge-base "$head" "origin/$default_branch"
    return
  fi

  if git rev-parse --verify --quiet "$head^" >/dev/null; then
    git rev-parse "$head^"
    return
  fi

  git hash-object -t tree /dev/null
}

if [[ "$event_name" == pull_request* ]]; then
  head_sha="${PR_HEAD_SHA:-$head_sha}"
  pr_base="${PR_BASE_SHA:-}"

  if is_zero_sha "$pr_base"; then
    echo "::error::Pull request base SHA was not provided."
    exit 1
  fi

  if ! commit_exists "$pr_base" || ! commit_exists "$head_sha"; then
    echo "::error::Required pull request commits are missing from the checkout."
    exit 1
  fi

  base_sha="$(git merge-base "$pr_base" "$head_sha")"
  range_label="$base_sha..$head_sha (PR merge base)"
else
  if ! commit_exists "$head_sha"; then
    echo "::error::Head commit '$head_sha' is missing from the checkout."
    exit 1
  fi

  base_sha="$(resolve_push_base "$head_sha")"
  range_label="$base_sha..$head_sha"
fi

changed_files="$(git diff --name-only --no-renames "$base_sha" "$head_sha")"

frontend=false
backend=false
launcher=false
launcher_build=false

while IFS= read -r path; do
  [[ -z "$path" ]] && continue

  case "$path" in
    frontend/*)
      frontend=true
      ;;
    backend/*)
      backend=true
      ;;
    launcher/*)
      launcher=true
      launcher_build=true
      ;;
    .github/workflows/launcher-release.yml)
      launcher=true
      ;;
  esac
done <<< "$changed_files"

{
  echo "Change detection range: $range_label"
  echo "Frontend changed: $frontend"
  echo "Backend changed: $backend"
  echo "Launcher changed: $launcher"
  echo "Launcher build needed: $launcher_build"
  echo "Changed files:"
  if [[ -n "$changed_files" ]]; then
    printf '%s\n' "$changed_files"
  else
    echo "(none)"
  fi
}

{
  echo "frontend=$frontend"
  echo "backend=$backend"
  echo "launcher=$launcher"
  echo "launcher_build=$launcher_build"
} >> "${GITHUB_OUTPUT:-/dev/stdout}"
