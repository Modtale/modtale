# Cache and build-cost rollout

## Checklist and acceptance

- [x] Replace registry image cache pulls with per-component GitHub Actions Buildx caches; component checks and exact-image scan must pass before its single publish
- [x] Fix partial/failed homepage SSR readiness and preserve client recovery
- [x] Use Cloudflare-specific edge freshness/SWR separately from browser revalidation
- [x] Public news/generic pages have bounded policies, identical crawler/human HTML, and private/account/token/error responses remain no-store
- [x] Bound anonymous API and per-instance public-data staleness; opt-in content purge stays disabled until credential setup is separately authorized
- [ ] Test the final develop tree; verify the exact pushed commit, CI and deployed revision
- [ ] Test frontend-only scale-to-zero on develop with the backend temporarily warm, then restore the backend's previous staging minimum
- [ ] Merge develop into each existing branch without force, preserving branch-specific work; retest conflict resolutions and verify exact-head deployments
- [ ] Keep the production backend warm and frontend minimum at 1 until measured cold paths meet acceptance

## Trial scope

Use the existing `modtale-frontend-dev` service and `dev.modtale.net` zone cache,
with `modtale-backend-dev` kept warm only during the trial. Do not change or write
live user data. Public reads, unauthenticated login/dashboard shells and synthetic
local fixture failures can be exercised without a test account. Authenticated
cross-user leakage and content mutation tests need separately approved disposable
accounts/fixtures; mark them not run until available.

Capture warm origin and edge HTML TTFB, cached HIT, a confirmed zero-instance cold
MISS, expiry/revalidation, post-deployment purge, LCP, interactive data-ready time,
errors, SEO and authoritative 404 behavior. Use normal URLs and cache headers,
not cache-busting URLs. A just-deployed service is already probed and cannot by
itself establish a zero-instance cold start. Stop on WAF/security challenges;
do not weaken security or disguise clients to obtain cache measurements.

Provisional performance gate: no more than 250 ms p95 TTFB regression against the
warm baseline, LCP at most 2.5 seconds on the chosen representative profile, no
blank cached pages/errors, correct SEO/404s and no private or token data in shared
responses. A small smoke sample is diagnostic, not a statistically reliable p95.
If cold misses are noticeable or evidence is incomplete, production stays warm.

## Bounded freshness and invalidation

Deployment invalidation must verify the new origin revision before purging and
then verify ordinary public URLs show the same revision. Immutable versioned
assets remain cached. Public-content mutation purging is opt-in and disabled by
default. It must use only configured Modtale HTML/API cache tags and never
user-supplied purge URLs. Cloudflare accepts purge by tag on this Free zone, with
an account-wide 5/minute refill and 25-request burst limit.

A public-data response can still be stale in another origin instance after an
edge purge. Nested public origin DTO/view caches can each retain data for five minutes.
Together with a five-minute API edge layer and ten-minute HTML/five-minute SWR,
a theoretical worst-case chain can approach 30 minutes for a delayed update.
Short origin, edge and HTML lifetimes bound this delay but do not
provide globally immediate unpublication. Until runtime purge is enabled, edits
and removals rely on those bounded lifetimes. Private authorization and responses
must never rely on cache expiration for protection.

## Production guardrails

Keep production backend min=1. Keep production frontend min=1 pending the trial;
service-level and revision-level settings and CI must agree. Previews remain
scale-to-zero. No key/permission/quota changes, budget exports, new paid services
or preview deletions are part of this rollout.

Conditional next stage: if the measured cold entry paths miss the gate, trial
Cloudflare-hosted finite public pages and generic app shells while preserving
public project/wiki/news SEO SSR and backend authorization. The Node wrapper's
filesystem, compression, CSS and video handling requires explicit compatibility
work; a static build still served from zero-instance Cloud Run does not remove
cold misses. No wholesale backend rewrite is warranted by these changes.

## Rollback

Restore frontend minimum 1 at service and revision levels and keep backend warm.
Redeploy the previous tested image/commit if needed, then purge only affected HTML
and verify its revision. Revert code with normal commits; never reset remote
branches or overwrite feature work. Capture original Cloudflare rule bodies
before editing and restore those exact rules if their trial fails.

## Evidence log

- 2026-10-02: fresh five-branch inventory: develop/main85ad382312193f11757e3215e30faf0d07beb8ea; monetization3d4054f607ac770c38e36c9cb28876fd581ac5a9; modjama66239f9b68a978c532d854dcba5e324045d887b; warden-v3630c60d9b58f2597a5e3b2c41b6084bf74c4b441
- Baseline repository/mock script tests:15passed
- Zone Free Website and no-impact nonexistent-tag purge probe accepted200/success=true; this verifies capability, not real content eviction
- Baseline prod frontend00915-mrf and backend00787-5zm both service/revision min1; dev frontend00869-7pr/backend01488-s8p min0
- Live dev HTML lacked a cache rule; production anonymous catalog forced3600s regardless of origin policy. Trial corrections must preserve all Cookie, Authorization, Origin and API-key exclusions

- Integrated local validation: 33 repository tests, 681 frontend tests, 75 production SSR fixture checks, Astro check (zero errors/warnings) and frontend build passed
- Backend local full run: 701 passed, 37 skipped, one pre-existing source-DNS-dependent ExternalDependencyArtifactServiceTest failure; new invalidation/privacy focused checks passed. Exact-head CI container build/scan and full backend tests remain required
- Baseline cold staging /terms: recent active=0/idle=0 metric samples, then AUTOSCALING startup and200 response with Cloud Run request latency2.665s. Browser TTFB/LCP were unavailable through the supported cloud-browser API; no production scaling decision follows from this small sample alone
