import fs from 'node:fs/promises';
import https from 'node:https';
import { Readable } from 'node:stream';
import path from 'node:path';
import { performance } from 'node:perf_hooks';
import { pathToFileURL } from 'node:url';

const TARGETS = Object.freeze({
  dev: { origin: 'https://dev.api.modtale.net', site: 'https://dev.modtale.net', requireEdge: false },
  production: { origin: 'https://api.modtale.net', site: 'https://modtale.net', requireEdge: true },
});
export const MAX_REQUESTS = 25;
export const REQUEST_TIMEOUT_MS = 10000;
export const MAX_BODY_BYTES = 512 * 1024;
export const MISSING_PATH = '/api/v1/projects/modtale-cache-diagnostic-fixed-missing-project';
const SHARED_CACHE_STATES = new Set(['HIT', 'STALE', 'UPDATING', 'REVALIDATED']);
const CACHE_STATES = new Set([...SHARED_CACHE_STATES, 'MISS', 'BYPASS', 'DYNAMIC', 'EXPIRED', 'NONE', 'UNKNOWN']);
const TITLES = { 400: 'Bad Request', 401: 'Unauthorized', 403: 'Forbidden', 404: 'Not Found', 410: 'Gone', 500: 'Internal Server Error', 502: 'Bad Gateway', 503: 'Service Unavailable', 504: 'Gateway Timeout' };

class DiagnosticFailure extends Error {
  constructor(code, blocked = false) { super(code); this.code = code; this.blocked = blocked; }
}
function requireCondition(condition, code) { if (!condition) throw new DiagnosticFailure(code); }

export function validateInputs(env) {
  const target = env.API_DIAGNOSTIC_TARGET ?? 'dev';
  requireCondition(Object.hasOwn(TARGETS, target), 'invalid_fixed_target');
  return Object.freeze({ target, ...TARGETS[target] });
}

export function requestCases(input) {
  const fixed = validateInputs({ API_DIAGNOSTIC_TARGET: input.target });
  const publicCases = ['projects', 'tags', 'analytics/platform/stats'].map((route, index) => ({
    id: ['projects', 'tags', 'stats'][index], path: `/api/v1/${route}`, kind: 'public',
  }));
  return Object.freeze([
    ...publicCases.map(item => ({ ...item, id: `anonymous_${item.id}` })),
    ...publicCases.map(item => ({ ...item, id: `repeat_${item.id}` })),
    { id: 'origin_projects', path: '/api/v1/projects', kind: 'browser', headers: { Origin: fixed.site } },
    { id: 'referer_projects', path: '/api/v1/projects', kind: 'browser', headers: { Referer: `${fixed.site}/` } },
    { id: 'sec_fetch_projects', path: '/api/v1/projects', kind: 'browser', headers: { 'Sec-Fetch-Site': 'same-site' } },
    { id: 'sec_fetch_unknown_projects', path: '/api/v1/projects', kind: 'browser', headers: { 'Sec-Fetch-Unknown': 'diagnostic' } },
    { id: 'cookie_projects', path: '/api/v1/projects', kind: 'credential', headers: { Cookie: 'SESSION=modtale-cache-diagnostic-invalid-session' } },
    { id: 'authorization_projects', path: '/api/v1/projects', kind: 'credential', headers: { Authorization: 'Bearer modtale-cache-diagnostic-invalid-token' } },
    { id: 'modtale_key_projects', path: '/api/v1/projects', kind: 'credential', headers: { 'X-Modtale-Key': 'modtale-cache-diagnostic-invalid-key' } },
    { id: 'legacy_key_projects', path: '/api/v1/projects', kind: 'credential', headers: { 'X-API-Key': 'modtale-cache-diagnostic-invalid-key' } },
    // Empty Origin is rejected by Spring CORS before token issuance; verify that
    // case in backend tests and edge configuration, never bypass its generic403.
    ...['Cookie', 'Authorization', 'Referer', 'X-Modtale-Key', 'X-API-Key', 'Sec-Fetch-Site', 'Sec-Fetch-Unknown']
      .map(name => ({ id: `empty_${name.toLowerCase().replaceAll('-', '_')}_projects`, path: '/api/v1/projects',
        kind: 'empty_header', headers: { [name]: '' } })),
    { id: 'missing_project', path: MISSING_PATH, kind: 'missing' },
    { id: 'csrf_bootstrap', path: '/api/v1/auth/csrf', kind: 'bootstrap', headers: { Origin: fixed.site } },
  ]);
}

// Node fetch adds Sec-Fetch-Mode: cors even for server requests. Use the native
// HTTPS client so public probes actually have no browser or credential headers.
// It does not follow redirects, maintain a cookie jar, decompress, or retry.
export function requestWithoutBrowserHeaders(url, options, transport = https.request) {
  return new Promise((resolve, reject) => {
    const request = transport(url, {
      method: options.method, headers: options.headers, signal: options.signal,
    }, response => {
      const headers = new Headers();
      for (let index = 0; index < response.rawHeaders.length; index += 2) {
        headers.append(response.rawHeaders[index], response.rawHeaders[index + 1]);
      }
      resolve({ status: response.statusCode, headers, body: Readable.toWeb(response) });
    });
    request.on('error', reject);
    request.end();
  });
}

function csrfCookies(response) {
  const cookies = typeof response.headers.getSetCookie === 'function'
    ? response.headers.getSetCookie() : [response.headers.get('set-cookie') || ''];
  return cookies.map(cookie => /^XSRF-TOKEN=([^;]+)/.exec(cookie)?.[1]).filter(Boolean);
}

function cachePolicy(raw = '') {
  const policy = Object.create(null);
  const seen = new Set();
  for (const item of raw.split(',')) {
    const token = item.trim().toLowerCase();
    const separator = token.indexOf('=');
    const key = separator === -1 ? token : token.slice(0, separator).trim();
    const rawValue = separator === -1 ? undefined : token.slice(separator + 1).trim();
    const value = /^"\d+"$/.test(rawValue || '') ? rawValue.slice(1, -1) : rawValue;
    if (seen.has(key)) policy.invalid = true;
    seen.add(key);
    if (['public', 'private', 'no-store', 'no-cache', 'must-revalidate', 'proxy-revalidate', 'immutable'].includes(key) && value === undefined) policy[key] = true;
    else if (['max-age', 's-maxage', 'stale-while-revalidate', 'stale-if-error'].includes(key) && /^\d+$/.test(value || '')) {
      const number = Number(value);
      if (Number.isSafeInteger(number)) policy[key] = number;
    }
  }
  return policy;
}

export function publicProjectFixture(body, { verifiedAnonymousCatalog = false } = {}) {
  try {
    const value = JSON.parse(body);
    if (!Array.isArray(value?.content)) return null;
    for (const project of value.content.slice(0, 20)) {
      if (!project || typeof project !== 'object' || Array.isArray(project)) continue;
      if (Object.hasOwn(project, 'status')) {
        if (!['PUBLISHED', 'ARCHIVED'].includes(project.status)) continue;
      } else {
        // ProjectMapper.toSummaryDTO(false) omits status and management fields.
        // Only the already-verified, query-free anonymous catalog can establish
        // public scope: its repository query filters PUBLISHED/ARCHIVED projects.
        if (!verifiedAnonymousCatalog || typeof project.id !== 'string' || !project.id
          || typeof project.title !== 'string' || !project.title
          || !Number.isInteger(project.downloadCount) || project.downloadCount < 0
          || !Number.isInteger(project.favoriteCount) || project.favoriteCount < 0
          || ['canEdit', 'isOwner', 'versions'].some(name => Object.hasOwn(project, name))) continue;
      }
      const segment = project.slug || project.id;
      if (typeof segment !== 'string' || !/^[A-Za-z0-9][A-Za-z0-9-]{0,127}$/.test(segment)) continue;
      const prefixes = { PLUGIN: 'mod', DATA: 'mod', ART: 'mod', MODPACK: 'modpack', SAVE: 'world' };
      if (Object.hasOwn(prefixes, project.classification)) return `/${prefixes[project.classification]}/${segment}`;
    }
  } catch { /* Optional fixture evidence must not widen or fail the diagnostic. */ }
  return null;
}

function isApplicationProblem(response, body, item) {
  if (!/^(?:application\/json|application\/problem\+json)(?:;|$)/i.test(response.headers.get('content-type') || '')) return false;
  try {
    const value = JSON.parse(body);
    if (!value || Array.isArray(value) || value.status !== response.status
      || value.title !== TITLES[response.status] || typeof value.detail !== 'string' || !value.detail) return false;
    const type = (response.headers.get('content-type') || '').split(';')[0].toLowerCase();
    const flattened = value.error === value.detail && value.message === value.detail && !Object.hasOwn(value, 'properties');
    // Existing canonical ProblemDetail responses remain supported.
    if (value.type === 'about:blank' && flattened) return true;
    // Real ApiKeyAuthFilter -> GlobalExceptionHandler -> Spring MVC serialization
    // flattens extension fields, omits the default type, and sets instance to the
    // request path. Require that exact shape and the actual scoped request path.
    if (!Object.hasOwn(value, 'type') && type === 'application/problem+json'
      && typeof item?.path === 'string' && value.instance === item.path && flattened) return true;
    // Direct ErrorMessageUtils.writeJsonError uses a bare Jackson3 ObjectMapper:
    // type and instance are null and extension fields stay under properties.
    // Accept only the two source-defined Spring Security denial messages here.
    const directDetails = {
      401: 'You need to sign in before performing this action. If you were already signed in, your session may have expired.',
      403: 'You do not have permission to perform this action with the current account or API key.',
    };
    return type === 'application/json' && value.type === null && value.instance === null
      && value.detail === directDetails[response.status]
      && !Object.hasOwn(value, 'error') && !Object.hasOwn(value, 'message')
      && value.properties && !Array.isArray(value.properties)
      && Object.keys(value.properties).length === 2
      && value.properties.error === value.detail && value.properties.message === value.detail;
  } catch { return false; }
}

export function blockedReason(response, body, item) {
  if (response.status === 429) return 'rate_limited_no_retry';
  if (/challenge/i.test(response.headers.get('cf-mitigated') || '')) return 'waf_challenge';
  if (/\/cdn-cgi\/challenge-platform\/|challenges\.cloudflare\.com\/turnstile\//i.test(response.headers.get('location') || '')) return 'waf_challenge_redirect';
  if (/<title[^>]*>\s*(?:Just a moment|Attention Required)[^<]*<\/title>|cf-chl-(?:widget|managed|captcha)|\/cdn-cgi\/challenge-platform\//i.test(body)) return 'waf_challenge_markup';
  if (response.status >= 400 && /\b(?:error\s*(?:code)?\s*[:=]?\s*1010|cloudflare\s+(?:error\s*)?1010)\b/i.test(body)) return 'waf_1010';
  if ([401, 403].includes(response.status) && !isApplicationProblem(response, body, item)) return 'unrecognized_access_denial';
  return null;
}

async function readBoundedBody(response) {
  if (!response.body) return '';
  const reader = response.body.getReader();
  const chunks = [];
  let bytes = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) return Buffer.concat(chunks).toString('utf8');
      bytes += value.byteLength;
      if (bytes > MAX_BODY_BYTES) {
        await reader.cancel();
        throw new DiagnosticFailure('response_body_budget_exceeded', true);
      }
      chunks.push(Buffer.from(value));
    }
  } finally { reader.releaseLock(); }
}

function observation(response, item, startedAt, ttfbMs, totalMs) {
  const rawCacheStatus = response.headers.get('cf-cache-status');
  const cacheStatus = (rawCacheStatus || '').toUpperCase();
  const age = response.headers.get('age');
  const revision = response.headers.get('x-modtale-revision');
  const type = (response.headers.get('content-type') || '').split(';')[0].toLowerCase();
  const cdnHeader = response.headers.has('cloudflare-cdn-cache-control')
    ? response.headers.get('cloudflare-cdn-cache-control') : response.headers.get('cdn-cache-control');
  return {
    case: item.id, path: item.path, kind: item.kind, startedAt,
    publicCacheGroup: item.kind === 'public' ? item.cacheGroup || 'catalog' : null,
    status: response.status, ttfbMs: Math.round(ttfbMs), totalMs: Math.round(totalMs),
    cacheStatus: CACHE_STATES.has(cacheStatus) ? cacheStatus : null,
    cloudflareHeadersObserved: rawCacheStatus !== null || response.headers.has('cf-ray'),
    malformedCacheStatusObserved: rawCacheStatus !== null && !CACHE_STATES.has(cacheStatus),
    ageSeconds: /^\d+$/.test(age || '') && Number.isSafeInteger(Number(age)) ? Number(age) : null,
    malformedAgeObserved: age !== null && (!/^\d+$/.test(age) || !Number.isSafeInteger(Number(age))),
    cacheControl: cachePolicy(response.headers.get('cache-control') || ''),
    cdnPolicyHeaderObserved: cdnHeader !== null,
    cdnCacheControl: cachePolicy(cdnHeader || ''),
    contentType: ['application/json', 'application/problem+json', 'text/html', 'text/plain'].includes(type) ? type : 'other',
    revision: /^[a-f0-9]{40}$/.test(revision || '') ? revision : null,
    setCookieObserved: response.headers.has('set-cookie'),
    csrfCookieObserved: csrfCookies(response).length > 0,
  };
}

function requireNoSharedCache(item, requireEdge) {
  requireCondition(!SHARED_CACHE_STATES.has(item.cacheStatus) && !(item.ageSeconds > 0), 'excluded_response_used_shared_cache');
  requireCondition(!item.malformedAgeObserved, 'unrecognized_cache_age');
  requireCondition(!item.malformedCacheStatusObserved, 'unrecognized_cache_status');
  if (requireEdge || item.cloudflareHeadersObserved) {
    requireCondition(['BYPASS', 'DYNAMIC'].includes(item.cacheStatus), 'edge_bypass_not_observed');
  }
}
function requireNoStore(item, requireEdge) {
  requireCondition(item.cacheControl['no-store'] === true, 'credential_or_error_missing_no_store');
  if (item.cdnPolicyHeaderObserved) requireCondition(item.cdnCacheControl['no-store'] === true, 'cdn_policy_overrides_no_store');
  requireNoSharedCache(item, requireEdge);
}
function requirePublicPolicy(item, requireEdge) {
  const policy = item.cacheControl;
  requireCondition(!policy.invalid && policy.public && policy['max-age'] === 0 && policy['must-revalidate']
    && Number.isInteger(policy['s-maxage']) && policy['s-maxage'] > 0 && policy['s-maxage'] <= 300
    && !policy.private && !policy['no-store'] && !policy['stale-while-revalidate'] && !policy['stale-if-error'], 'public_success_missing_bounded_300_policy');
  requireCondition(!item.setCookieObserved, 'anonymous_public_response_sets_cookie');
  const cdn = item.cdnCacheControl;
  if (item.cdnPolicyHeaderObserved) {
    const ttl = cdn['s-maxage'] ?? cdn['max-age'];
    requireCondition(!cdn.invalid && cdn.public && Number.isInteger(ttl) && ttl <= 300 && !cdn.private && !cdn['no-store']
      && !cdn['stale-while-revalidate'] && !cdn['stale-if-error'], 'cdn_policy_exceeds_public_bound');
  }
  requireCondition(item.ageSeconds === null || item.ageSeconds <= 300, 'legacy_cache_age_exceeds_300');
  requireCondition(!item.malformedAgeObserved, 'unrecognized_cache_age');
  requireCondition(!item.malformedCacheStatusObserved, 'unrecognized_cache_status');
  requireCondition(!['STALE', 'UPDATING'].includes(item.cacheStatus), 'public_api_response_served_stale');
  if (requireEdge || item.cloudflareHeadersObserved) {
    requireCondition(item.cacheStatus !== null, 'edge_cache_status_not_observed');
  }
}

export function validateObservation(response, body, item, requireEdge = true) {
  const reason = blockedReason(response, body, item);
  if (reason) throw new DiagnosticFailure(reason, true);
  requireCondition(response.status < 300 || response.status >= 400, 'redirect_not_followed');
  if (response.status >= 400) {
    requireCondition(isApplicationProblem(response, body, item), 'unrecognized_application_error_contract');
    if (item.kind === 'missing') requireCondition([404, 410].includes(response.status), 'missing_fixture_not_authoritatively_missing');
    requireNoStore(item, requireEdge);
    return 'application_error_no_store';
  }
  requireCondition(response.status === 200 && item.kind !== 'missing', 'unexpected_success_status');
  requireCondition(item.contentType === 'application/json', 'successful_api_response_not_json');
  if (item.kind !== 'public') {
    requireNoStore(item, requireEdge);
    requireCondition(item.csrfCookieObserved, 'excluded_success_missing_csrf_cookie');
    if (item.kind === 'bootstrap') {
      let token;
      try { token = JSON.parse(body)?.token; } catch { /* Report only the fixed error code. */ }
      requireCondition(typeof token === 'string' && token.length > 0 && csrfCookies(response).includes(token),
        'bootstrap_token_cookie_mismatch');
      return 'csrf_bootstrap_no_store';
    }
    return 'excluded_success_no_store_with_csrf';
  }
  requirePublicPolicy(item, requireEdge);
  return 'public_bounded_300';
}

export async function runApiHeaderDiagnostic(input, { request = requestWithoutBrowserHeaders, now = () => performance.now(), date = () => new Date() } = {}) {
  const fixed = validateInputs({ API_DIAGNOSTIC_TARGET: input.target });
  const cases = [...requestCases(fixed)];
  requireCondition(cases.length === MAX_REQUESTS - 2, 'request_plan_budget_invalid');
  const report = { target: fixed.target, origin: fixed.origin, sampledAt: date().toISOString(), status: 'running',
    verificationScope: fixed.requireEdge ? 'origin_and_cloudflare_edge' : 'origin_only',
    edgeVerificationRequired: fixed.requireEdge, originPolicyVerified: false, edgeVerified: false,
    maxRequests: MAX_REQUESTS, requestCount: 0, observations: [], publicSuccessCount: 0, catalogSuccessCount: 0, projectSuccessCount: 0, applicationErrorCount: 0,
    confirmedPublicHit: false, confirmedCatalogHit: false, confirmedProjectHit: false, projectFixtureAvailable: false, csrfBootstrapVerified: false, authenticatedCrossUserCoverage: false,
    limitations: [fixed.requireEdge
      ? 'Production completion requires the origin policy matrix, excluded-request edge bypass, and a public Cloudflare HIT for both catalog and project-page rules.'
      : 'Dev is an origin-only check for the DNS-only staging deployment; completion does not verify Cloudflare edge caching. Any observed Cloudflare headers are still validated.',
      'Headers/Age alone do not prove the active Cloudflare rule TTL; reconcile live rule configuration separately.',
      'The two project reads use only a validated public slug/id from the first catalog response; an absent fixture leaves coverage limited.',
      'Only fixed synthetic invalid credentials are tested; no real authentication or cross-user private data coverage.',
      'Empty Origin is checked by backend tests and edge-rule inspection, not live probing, because Spring CORS rejects it.',
      'No response bodies, arbitrary header values, credential values, cookies, or tokens are retained.',
      'No retries, redirects, cache-busting, writes, purges, user-agent overrides, or WAF bypass.'] };
  try {
    for (const item of cases) {
      requireCondition(report.requestCount < MAX_REQUESTS, 'request_budget_exceeded');
      const url = new URL(item.path, fixed.origin);
      requireCondition(url.origin === fixed.origin && !url.search && !url.hash && !url.username && !url.password, 'request_outside_fixed_scope');
      const start = now();
      const startedAt = date().toISOString();
      const options = { method: 'GET', redirect: 'manual', signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS) };
      if (item.headers) options.headers = { ...item.headers };
      report.requestCount++;
      const response = await request(url, options);
      const ttfb = now() - start;
      const body = await readBoundedBody(response);
      const result = observation(response, item, startedAt, ttfb, now() - start);
      report.observations.push(result);
      result.result = validateObservation(response, body, result, fixed.requireEdge);
      if (result.result === 'public_bounded_300') {
        report.publicSuccessCount++;
        const projectRead = result.publicCacheGroup === 'project';
        if (projectRead) report.projectSuccessCount++;
        else report.catalogSuccessCount++;
        if (result.cacheStatus === 'HIT') {
          report.confirmedPublicHit = true;
          if (projectRead) report.confirmedProjectHit = true;
          else report.confirmedCatalogHit = true;
        }
        if (item.id === 'anonymous_projects') {
          report.publicProjectFixturePath = publicProjectFixture(body, { verifiedAnonymousCatalog: true });
          report.projectFixtureAvailable = report.publicProjectFixturePath !== null;
          if (report.projectFixtureAvailable) {
            const segment = report.publicProjectFixturePath.split('/').at(-1);
            for (const prefix of ['anonymous', 'repeat']) cases.push({
              id: `${prefix}_public_project`, path: `/api/v1/projects/${segment}`, kind: 'public', cacheGroup: 'project',
            });
          }
        }
      }
      if (result.result === 'application_error_no_store') report.applicationErrorCount++;
      if (result.result === 'csrf_bootstrap_no_store') report.csrfBootstrapVerified = true;
    }
    report.originPolicyVerified = report.catalogSuccessCount === 6 && report.projectSuccessCount === 2 && report.csrfBootstrapVerified;
    report.edgeVerified = fixed.requireEdge && report.originPolicyVerified && report.confirmedCatalogHit && report.confirmedProjectHit;
    report.status = report.originPolicyVerified && (!fixed.requireEdge || report.edgeVerified) ? 'complete' : 'limited';
    if (report.status === 'limited') report.errorCode = report.catalogSuccessCount !== 6
      ? 'public_policy_not_fully_observed' : !report.csrfBootstrapVerified
        ? 'csrf_bootstrap_not_observed' : !report.projectFixtureAvailable
          ? 'public_project_fixture_not_observed' : report.projectSuccessCount !== 2
            ? 'project_policy_not_fully_observed' : !report.confirmedCatalogHit
              ? 'catalog_cache_hit_not_observed' : 'project_cache_hit_not_observed';
  } catch (error) {
    report.status = error instanceof DiagnosticFailure && error.blocked ? 'blocked' : 'failed';
    report.errorCode = error instanceof DiagnosticFailure ? error.code : 'request_failed_no_retry';
  }
  return report;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const input = validateInputs(process.env);
  if (process.argv[2] === 'validate') console.log(JSON.stringify(input));
  else {
    requireCondition(!process.argv[2], 'arbitrary_commands_not_supported');
    const report = await runApiHeaderDiagnostic(input);
    await fs.mkdir('api-cache-diagnostic-output', { recursive: true });
    await fs.writeFile(path.join('api-cache-diagnostic-output', 'report.json'), JSON.stringify(report, null, 2));
    console.log(JSON.stringify(report, null, 2));
    process.exitCode = report.status === 'complete' ? 0 : 1;
  }
}
