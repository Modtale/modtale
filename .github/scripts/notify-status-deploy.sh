#!/usr/bin/env bash
set -euo pipefail

if [ -z "${STATUS_DISCORD_WEBHOOK_URL:-}" ]; then
  echo "STATUS_DISCORD_WEBHOOK_URL is not configured; skipping status deploy notification."
  exit 0
fi

if [ -z "${COMPONENT_NAME:-}" ] || [ -z "${SERVICE_NAME:-}" ] || [ -z "${DEPLOYED_REVISION:-}" ]; then
  echo "COMPONENT_NAME, SERVICE_NAME, and DEPLOYED_REVISION are required." >&2
  exit 1
fi

clean_frontend_url="${FRONTEND_DOMAIN:-https://modtale.net}"
clean_frontend_url="${clean_frontend_url%/}"
status_page_url="${clean_frontend_url}/status"
avatar_url="${clean_frontend_url}/assets/favicon.png"
service_url="${SERVICE_URL:-$status_page_url}"

wait_for_full_traffic() {
  local service_json
  local ready_revision
  local traffic_percent

  for attempt in $(seq 1 30); do
    service_json=$(gcloud run services describe "$SERVICE_NAME" --region "$REGION" --format=json)
    ready_revision=$(jq -r '.status.latestReadyRevisionName // ""' <<< "$service_json")
    traffic_percent=$(jq -r --arg revision "$DEPLOYED_REVISION" '[.status.traffic[]? | select(.revisionName == $revision) | (.percent // 0)] | add // 0' <<< "$service_json")

    if [ "$ready_revision" = "$DEPLOYED_REVISION" ] && [ "$traffic_percent" = "100" ]; then
      return 0
    fi

    echo "Waiting for $SERVICE_NAME revision $DEPLOYED_REVISION to serve all traffic ($attempt/30). Current ready revision: ${ready_revision:-none}; traffic: ${traffic_percent:-0}%."
    sleep 10
  done

  echo "$SERVICE_NAME revision $DEPLOYED_REVISION did not reach 100% traffic in time." >&2
  return 1
}

wait_for_full_traffic

commit_short="${GITHUB_SHA:0:7}"
commit_url="${GITHUB_SERVER_URL}/${GITHUB_REPOSITORY}/commit/${GITHUB_SHA}"
timestamp="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"

payload=$(jq -n \
  --arg username "Modtale Status" \
  --arg avatarUrl "$avatar_url" \
  --arg content "**Production deploy complete:** ${COMPONENT_NAME} revision \`${DEPLOYED_REVISION}\` is serving all traffic." \
  --arg title "${COMPONENT_NAME} production revision live" \
  --arg url "$status_page_url" \
  --arg service "$SERVICE_NAME" \
  --arg serviceUrl "$service_url" \
  --arg revision "$DEPLOYED_REVISION" \
  --arg actor "$GITHUB_ACTOR" \
  --arg commit "$commit_short" \
  --arg commitUrl "$commit_url" \
  --arg timestamp "$timestamp" \
  '{
    username: $username,
    avatar_url: $avatarUrl,
    content: $content,
    embeds: [{
      title: $title,
      url: $url,
      color: 3447003,
      fields: [
        { name: "Service", value: $service, inline: true },
        { name: "Revision", value: $revision, inline: true },
        { name: "Endpoint", value: $serviceUrl, inline: true },
        { name: "Commit", value: ("[\($commit)](\($commitUrl))"), inline: true },
        { name: "Triggered by", value: $actor, inline: true }
      ],
      timestamp: $timestamp
    }]
  }')

curl --fail --silent --show-error \
  -H "Content-Type: application/json" \
  -d "$payload" \
  "$STATUS_DISCORD_WEBHOOK_URL"

echo "Sent status deploy notification for $COMPONENT_NAME revision $DEPLOYED_REVISION."
