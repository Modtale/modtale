#!/usr/bin/env bash
set -euo pipefail

event_name="${GITHUB_EVENT_NAME:-}"
repo="${GITHUB_REPOSITORY:-}"
repo_owner="${GITHUB_REPOSITORY_OWNER:-${repo%%/*}}"
ref_name="${GITHUB_REF_NAME:-}"

should_run=true
reason="This workflow run owns the work."

gh_available() {
  command -v gh >/dev/null 2>&1 && [[ -n "${GH_TOKEN:-}" ]]
}

open_pr_count_for_branch() {
  gh api --method GET "repos/$repo/pulls" \
    -f state=open \
    -f head="$repo_owner:$ref_name" \
    --jq 'length'
}

# PR runs always own their tests. A queued push may itself skip because a PR
# exists, so its presence cannot prove that the commit has test coverage.
if [[ "$event_name" == "push" ]]; then
  if gh_available && [[ -n "$repo" && -n "$repo_owner" && -n "$ref_name" ]]; then
    if open_pr_count="$(open_pr_count_for_branch)"; then
      if [[ "$open_pr_count" =~ ^[0-9]+$ && "$open_pr_count" -gt 0 ]]; then
        should_run=false
        reason="Skipping push workflow because this branch has an open PR; the pull_request run owns this commit."
      fi
    else
      echo "::warning::Could not check for open pull requests; running tests to avoid missing coverage."
    fi
  else
    echo "::warning::GitHub CLI or token unavailable; running tests to avoid missing coverage."
  fi
fi

echo "$reason"

{
  echo "should_run=$should_run"
  echo "reason=$reason"
} >> "${GITHUB_OUTPUT:-/dev/stdout}"
