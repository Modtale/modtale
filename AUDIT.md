# Codebase audit — 2026-09-06

The audit covers the tracked monorepo snapshot at `b54cb0cf` and the changes on `codebase-audit`. Work was performed in a separate checkout because the original `launcher` branch was being edited concurrently. Subsequent changes on that other branch are outside this snapshot.

## Review coverage

| Area | Review and improvements |
| --- | --- |
| Backend security | Authentication filters, session identity, MFA entry points, API-key scopes, CSRF, CORS, cookie configuration, signing defaults, error responses, and one-use tokens |
| Backend services | Project and organization access boundaries, upload/archive validation, storage and download flows, external wiki integration, social-preview image fetching, and detached status persistence |
| Frontend | API and cookie handling, session restoration, OAuth error rendering, prefetch lifetime/concurrency, modal scroll ownership, markdown/SSR serialization boundaries, type diagnostics, browser imports, and production build |
| Launcher | Session/settings/cache persistence, archive and override destinations, download response ownership and interrupted transfers, update downloads, provider verification, existing UI tests, and packaging configuration |
| Supporting code | Fixture generation/loading/validation, shared fixture paths and collection definitions, test deduplication, component selection, preview orchestration, dependency installation, and contributor instructions |

This was a source and automated-test audit, not a production penetration test or proof that every defect has been eliminated. Small, targeted changes were preferred over wholesale rewrites of established services and UI components.

## Implemented findings

### Authentication and security

- Disabled the unused legacy form-login endpoint, which could authenticate outside the application's MFA flow. A test builds the real security filter chain and checks that the password-login filter is absent while CSRF remains active.
- API-key authentication rejects blank, invalid, and orphaned credentials instead of falling back to browser-session identity. Successful API authentication uses a new security context rather than mutating the shared session context.
- Stable account IDs no longer fall back to usernames when the original account disappears. This prevents an old principal from resolving to an account that later reuses the name.
- Credentialed CORS is confined to trusted frontend origins. Third-party API-key clients keep noncredentialed access, and unrelated Cloud Run sites are not trusted as previews.
- Session mutations, including account and API-key operations, require CSRF tokens in preview environments as well as production. Only explicitly identified public POST operations and API-key requests are exempt.
- Added a noncacheable CSRF-token endpoint for trusted frontends whose API cookies reside on another host. Concurrent client refreshes share one request.
- Session restoration queries the API instead of assuming the absence of JavaScript-readable cookies means the user is signed out.
- Localhost checks compare parsed hosts, and permissive Cloud Run substring checks were removed. Provider/method normalization is independent of the server locale.
- Removed publicly known fallback signing secrets. An unset `PRE_AUTH_SECRET` creates a random per-process secret; explicitly configured secrets remain supported across replicas. Pre-auth signature comparison uses `MessageDigest.isEqual`.
- Internal server errors return public fallback messages instead of raw exception details. Server logs retain diagnostic exceptions.
- One-use download tokens are claimed with atomic map removal, so concurrent requests cannot both consume them. Dependency selections are copied when tokens are issued.

CORS and CSRF changes follow the [Spring Framework CORS guidance](https://docs.spring.io/spring-framework/reference/web/webmvc-cors.html) and [Spring Security CSRF documentation](https://www.springframework.org/spring-security/reference/servlet/exploits/csrf.html).

### Data integrity and resource handling

- Launcher settings, installed-project records, sessions, and disk API cache share a JSON writer that writes and flushes a temporary file before replacing the prior document. Serialization-failure tests verify preservation of existing data and temporary-file cleanup. Filesystems without atomic moves use a replacement fallback.
- Locked modpack paths reject dot-segment aliases, and override destinations reject existing symbolic links. Tests exercise traversal and writes through links outside the instance.
- Download response bodies close on HTTP failures as well as success. Failed transfers remove incomplete temporary files. Installer updates finish downloading before replacing an existing installer.
- Social-preview images are fetched only from known site origins, the configured storage origin, or restricted local asset routes. Redirects are disabled, downloads are capped at 10 MiB, and raster dimensions are checked against a 16-million-pixel limit before decoding.
- Null or corrupt detached-status snapshots return an empty history instead of preventing startup.
- All mock fixture files are parsed before a template database connection or collection deletion. Fixture paths and collection names are shared, and path handling supports spaces and encoded filesystem characters.

### Frontend and maintainability

- Cookie parsing preserves embedded equals signs and handles malformed encoding without throwing.
- OAuth errors are no longer URL-decoded twice.
- Project prefetching has a 50-entry cache, one-minute lifetime, eight-request concurrency limit, and request timeout.
- Sign-in, mobile filters, and project previews use the existing shared scroll-lock hook. The last lock restores the previous overflow style.
- Frontend tests use a repository-owned launcher rather than rewriting Vitest's installed executable. An isolated `npm ci` and complete test run verified the replacement.
- Browser-import integration checks have individual HTTP deadlines and a longer cold-compilation allowance; their test names now identify the source and import correctly.
- Fixed stale framework badges, repository structure, contributor links, preview instructions, and documented verification commands and signing-secret behavior.

### CI

- PR test runs retain ownership instead of trusting a queued push run that might itself defer to the PR. This removes a double-skip race.
- Test-orchestration changes select all components while avoiding unnecessary native packaging.
- CI runs frontend type checks plus dependency-free workflow and fixture-script regression tests.
- Removed obsolete deduplication inputs from the test and Lighthouse workflows.

## Verification

Final local verification on the audit branch:

| Check | Result |
| --- | --- |
| Backend `./gradlew test statusServiceJar` | Passed; 598 tests passed, one opt-in live contract skipped; detached status JAR built |
| Launcher `./gradlew test` | Passed; 248 tests passed, seven opt-in live/browser/performance/snapshot tests skipped |
| Frontend `npm test` | 341 tests passed across 68 files |
| Frontend `npm run check` | Zero errors, zero warnings; 106 informational hints remain |
| Frontend `npm run build` | Passed; existing chunk-size and mixed static/dynamic import notices remain |
| Workflow and fixture tests | Six tests passed |
| Frontend and mock-db dependency installation/audit | Clean installs; npm reported zero known vulnerabilities at audit time |
| Shell and fixture scripts | Syntax checks passed |
| Patch whitespace | `git diff --check` passed |

GitHub's [Tests run for the final code commit](https://github.com/Modtale/modtale/actions/runs/34079703140) also passed. Its launcher job was correctly skipped because that commit changed backend/frontend code; the complete launcher suite was separately run locally.

The initial frontend suite intermittently exceeded its five-second browser-import deadline while Java tests and type checking ran concurrently. It passed independently; the integration-specific deadline and bounded HTTP requests address that observed load sensitivity without raising unit-test timeouts.

## Operational boundaries and follow-ups

- Configure the same private `PRE_AUTH_SECRET` on every backend replica. The generated local fallback changes after a restart and is not shared across instances. Existing deployment configuration already supplies the secret.
- Rate limiting still relies on upstream forwarding-header sanitization. The application trusts `CF-Connecting-IP`/`X-Forwarded-For`, and forwarded-header processing is enabled. Verify that deployment ingress prevents arbitrary clients from supplying trusted address headers; this audit did not change live ingress or proxy configuration.
- Social-preview rendering now deliberately omits arbitrary external image hosts. Use the configured storage origin for project assets that should appear in social previews.
- Production OAuth flows, real Mongo/R2 operations, the proprietary Warden service, native installer packaging on Windows/macOS, and opt-in live-provider/browser/performance tests were not exercised. No production database refresh or manual production deployment was performed.
- Informational TypeScript hints and large frontend chunks remain candidates for a separate measured cleanup/performance pass. No unsupported claim of complete CVE coverage is made for the Java dependency graph.
