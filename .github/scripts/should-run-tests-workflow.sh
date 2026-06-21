#!/usr/bin/env bash
set -euo pipefail

event_name="${GITHUB_EVENT_NAME:-}"
repo="${GITHUB_REPOSITORY:-}"
repo_owner="${GITHUB_REPOSITORY_OWNER:-${repo%%/*}}"
ref_name="${GITHUB_REF_NAME:-}"
pr_action="${PR_ACTION:-}"
pr_head_repo="${PR_HEAD_REPO:-}"
pr_head_sha="${PR_HEAD_SHA:-}"
workflow_file="${WORKFLOW_FILE:-tests.yml}"

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

covering_push_run_for_pr_head() {
  gh api --method GET "repos/$repo/actions/workflows/$workflow_file/runs" \
    -f event=push \
    -f head_sha="$pr_head_sha" \
    --jq '.workflow_runs[] | select((.status != "completed") or (.conclusion != "cancelled" and .conclusion != "skipped")) | .html_url' |
    head -n 1
}

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
elif [[ "$event_name" == pull_request* ]]; then
  if [[ "$pr_action" == "opened" || "$pr_action" == "reopened" ]]; then
    if gh_available && [[ "$pr_head_repo" == "$repo" && -n "$pr_head_sha" ]]; then
      if push_run_url="$(covering_push_run_for_pr_head)" && [[ -n "$push_run_url" ]]; then
        should_run=false
        reason="Skipping PR workflow because an existing push run already covers this commit: $push_run_url"
      fi
    fi
  fi
fi

echo "$reason"

{
  echo "should_run=$should_run"
  echo "reason=$reason"
} >> "${GITHUB_OUTPUT:-/dev/stdout}"
