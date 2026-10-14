#!/usr/bin/env bash
set -euo pipefail

event_name="${GITHUB_EVENT_NAME:-}"
repo="${GITHUB_REPOSITORY:-}"
repo_owner="${GITHUB_REPOSITORY_OWNER:-${repo%%/*}}"
ref_name="${GITHUB_REF_NAME:-}"
head_sha="${GITHUB_SHA:-}"

should_run=true
reason="This workflow run owns the work."

gh_available() {
  command -v gh >/dev/null 2>&1 && [[ -n "${GH_TOKEN:-}" ]]
}

open_pr_can_own_tests() {
  local numbers number state mergeable pr_head
  numbers="$(gh api --method GET "repos/$repo/pulls" \
    -f state=open \
    -f head="$repo_owner:$ref_name" \
    --jq '.[].number')" || return 1
  while IFS= read -r number; do
    [[ -z "$number" ]] && continue
    [[ "$number" =~ ^[0-9]+$ ]] || return 1
    state="$(gh api "repos/$repo/pulls/$number" --jq '[.mergeable, .head.sha] | @tsv')" || return 1
    IFS=$'\t' read -r mergeable pr_head <<< "$state"
    if [[ "$mergeable" == "true" && "$pr_head" == "$head_sha" ]]; then
      echo true
      return 0
    fi
  done <<< "$numbers"
  echo false
}

# PR runs always own their tests. A queued push may itself skip because a PR
# exists, so its presence cannot prove that the commit has test coverage.
# Conflicted PRs do not trigger pull_request workflows. Unknown mergeability
# or a different head must therefore retain the push run's test coverage.
if [[ "$event_name" == "push" ]]; then
  if gh_available && [[ -n "$repo" && -n "$repo_owner" && -n "$ref_name" && "$head_sha" =~ ^[[:xdigit:]]{40}$ ]]; then
    if pr_can_own_tests="$(open_pr_can_own_tests)"; then
      if [[ "$pr_can_own_tests" == "true" ]]; then
        should_run=false
        reason="Skipping push workflow because an open, mergeable PR has this exact head; the pull_request run owns this commit."
      fi
    else
      echo "::warning::Could not check for open pull requests; running tests to avoid missing coverage."
    fi
  else
    echo "::warning::GitHub CLI, token, or commit context unavailable; running tests to avoid missing coverage."
  fi
fi

echo "$reason"

{
  echo "should_run=$should_run"
  echo "reason=$reason"
} >> "${GITHUB_OUTPUT:-/dev/stdout}"
