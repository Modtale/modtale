# Public content cache invalidation

Public API responses opt into shared caching only after controller authorization and
public-response checks. Eligible anonymous successful responses use
`public, max-age=0, s-maxage=300, must-revalidate` and an environment-specific
`modtale-api-{api-host}` cache tag. Requests with an authenticated principal,
Authorization, API key, Cookie, or session, cookie-setting responses, and content
errors/404s use `no-store`. The advice does not grant any project/profile permission.

Project publication, metadata/media/team/version/comment changes and removals use
the existing project cache-eviction path. Metadata edits evict both the previous
and current route handle. Download/view-counter flushes, favorites, and comment
votes do not trigger edge purges.
News publishes/unpublishes trigger invalidation after successful persistence;
private draft saves do not. Creator profile/name/avatar/banner/connection-visibility
edits, organization metadata/role/member edits, accepted organization invitations,
OAuth visible-connection/avatar changes, account deletion/recovery, and administrative
public profile changes also clear locally embedded creator fields and invalidate.
External wiki upstream edits have no incoming mutation callback here, so their
freshness is bounded by the wiki TTL rather than promised immediate invalidation.

The exact public `/api/v1/tags` and `/api/v1/analytics/platform/stats` endpoints also
receive the 300-second API edge policy. Stats use the existing five-minute
`platformStats` origin cache, so those two layers can compound to roughly ten minutes.
Tags return a build-time static list from `ValidationService`, with no origin cache
layer. The separately declared `allTags` cache retains the default 60-minute policy
but is not used by that endpoint; other stable-metadata caches are not covered by
the five-minute project/wiki origin-cache bound.

## Disabled-by-default deployment configuration

- `PUBLIC_CACHE_PURGE_ENABLED=false` by default
- `CLOUDFLARE_ZONE_ID=ff20a05c676ca023b19d20e177054945`
- `CLOUDFLARE_CACHE_PURGE_TOKEN`: existing, zone-scoped Cache Purge credential
- `FRONTEND_URL` / `BACKEND_URL`: exactly one approved environment pair:
  `https://modtale.net` / `https://api.modtale.net`, or
  `https://dev.modtale.net` / `https://dev.api.modtale.net`

Purge destinations and tags are derived exclusively from these approved values.
No request host, media URL, profile name, tokenized URL, query, or user-supplied domain
is sent to the purge API. The client never follows redirects and never logs tokens,
request headers, Cloudflare response bodies, or exception messages.

Enabling this feature requires an operator-authorized runtime secret binding and
the minimum existing zone-specific Cache Purge scope. Do not create a credential,
grant IAM access, or transfer a deployment secret automatically. Preview and local
environments must leave it disabled; configuration rejects production/development
host mismatches and every other zone/host. No migration or new service is required.

## Behavior, limits, and failure recovery

The purge request contains only the public HTML and API tags for the same environment.
It preserves immutable assets and other environments. Cloudflare currently supports
tag purge on Free with 5 requests/minute and a 25-request burst bucket:
https://developers.cloudflare.com/cache/how-to/purge-cache/
https://developers.cloudflare.com/cache/how-to/purge-cache/purge-by-tags/

The first post-write attempt is synchronous with a two-second HTTP/connect timeout,
so it does not depend on request-throttled Cloud Run granting idle CPU. Subsequent
changes coalesce for 120 seconds per instance into a constant-size pending revision.
Incoming content reads and the scheduled task both attempt due pending purges.
Multiple backend instances/environments can still share Cloudflare's account limit;
HTTP failures, malformed responses, `success:false`, and 429s retain pending work
with exponential backoff and Retry-After handling. Content writes remain successful
when a purge fails. Pending work is process-local, so restarts discard it.

This is a best-effort freshness mechanism, not an immediate global revocation
guarantee. Per-instance public project caches and weighted wiki caches expire after
five minutes; API edges also expire after five minutes, and public HTML has its own
bounded TTL. These layers can compound when another instance still serves an old
public snapshot. Permission/authentication cache policy is unchanged. Do not claim
removed content disappears instantly, especially with purging disabled or during
rate limits/outages. Cloudflare cache rules must respect origin headers and must not
force a longer edge TTL or cache credentialed/no-store responses.

Previously cached API responses have no new API tag. Updating a cache rule or
deploying new response headers does not prove those old objects have disappeared.
After verifying the new origin headers, use an explicitly authorized one-time purge
scoped to the approved API hostname, or account for the previous cache TTL during
rollout. Do not substitute a zone-wide purge or purge a user-supplied hostname.

A successful purge response confirms acceptance only. After authorized deployment,
verify ordinary affected public URLs show current content or the expected 404 and
that `CF-Cache-Status` no longer reports the old cached HIT; check private/credentialed
variants remain BYPASS/no-store. Do not use cache-busting queries for verification.
