# HTML cache policy

The Astro frontend remains server-rendered. Public routes opt into a bounded CDN
policy; private, token-bearing, degraded, missing, and otherwise unapproved HTML
uses `no-store`.

| HTML | CDN fresh TTL | Stale while revalidating |
| --- | ---: | ---: |
| Complete home / default browse | 300 seconds | 300 seconds |
| Public project / creator / complete wiki / published news | 600 seconds | 300 seconds |
| Terms, privacy, API docs, launcher shell (no query) | 600 seconds | 300 seconds |

The home/browse TTL was reduced from 24 hours so ranking/stat changes and any
failed mutation purge cannot freeze listings for a day. These are conservative
starting values to tune against measured cache hit rates and origin costs.

Browser HTML uses `Cache-Control: public, max-age=0, must-revalidate`; edge HTML
uses `CDN-Cache-Control: public, max-age=<TTL>, stale-while-revalidate=300,
stale-if-error=0`. The edge header deliberately excludes `s-maxage` and
`must-revalidate`: Cloudflare treats those as prohibiting stale responses, which
would disable background revalidation. `stale-if-error=0` bounds failure behavior
rather than silently retaining unavailable or withdrawn content indefinitely.
Cloudflare Cache Rules must make HTML cache-eligible and respect origin cache
headers; origin headers cannot override a rule that forces an unrelated TTL.

All HTML carries `Cache-Tag: modtale-html-<hostname>` for existing deployment and
public-content mutation purges. Cache tags are supported on Cloudflare Free,
including purge by tag; the Free purge rate limit requires coalescing mutations.
Immutable JS/CSS/media are excluded from this HTML tag.

Homepage readiness is tracked per marquee, trending, newest, and stats request.
Valid empty lists/zero stats count as successful. Null, malformed, timed-out, and
non-2xx responses do not. Partial data is retained, missing sections recover on
the client, and partial HTML is never stored in a shared cache.

Project/creator/wiki HTML is identical for crawler and browser user agents.
Public archived projects remain available; unlisted projects remain accessible
but receive `no-store` and `noindex`. Only authoritative backend 404/410 responses
become missing-resource HTML statuses. Transient project/wiki/creator failures
remain client-recoverable and `no-store`. News failures use 503/no-store and an
unknown news slug uses 404/no-store.

## Layered freshness caveat

These are HTML lifetimes, not an end-to-end removal deadline. Nested public
origin view/DTO caches can each retain data for five minutes, an API edge layer
can add another five, and HTML may be fresh ten minutes plus five minutes of SWR.
A theoretical delayed-update chain can therefore approach 25–30 minutes.
Runtime content purging is disabled until the existing purge credential is
configured through a separately authorized user-operated setup. Deployment
purging is retained; neither TTL expiry nor a successful tag purge alone promises
globally immediate removal from every origin instance.

See [backend invalidation setup](../backend/CACHE_INVALIDATION.md) and
[rollout acceptance](../docs/cache-cost-rollout.md).

## Verification

Run normal frontend checks and the production SSR fixture:

```
npm run check
npm test
npm run build
npm run test:html-cache
```

The fixture builds into a temporary directory with a local mock API, then runs
actual production Astro response handlers. It verifies complete/partial/failed
and empty homepage data, bot/human equality, canonical redirects, missing versus
transient resources, news, public shells, and private/token response headers. It also validates wiki payload shapes,
retains valid partial wiki bootstrap data, and proves session-cookie/anonymous
HTML equality without forwarding cookies or authorization to the SSR API.
Header-variant comparisons warm lazy modules first to hold module state constant;
they compare full HTML without stripping or normalizing its contents.
The Docker build gate runs this fixture before pruning test dependencies.
The fixture does not change deployment state or require production credentials.

## References

- [Cloudflare origin cache control](https://developers.cloudflare.com/cache/concepts/cache-control/)
- [Separate CDN and browser policies](https://developers.cloudflare.com/cache/concepts/cdn-cache-control/)
- [Directives that disable stale-while-revalidate](https://developers.cloudflare.com/cache/concepts/revalidation/)
- [Purge by tag on all plans](https://developers.cloudflare.com/changelog/post/2025-04-01-purge-for-all/)
- [Current purge limits](https://developers.cloudflare.com/cache/how-to/purge-cache/)
