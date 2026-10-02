import fs from 'node:fs/promises';
import path from 'node:path';
import { performance } from 'node:perf_hooks';
import { pathToFileURL } from 'node:url';

const TARGETS = Object.freeze({
  dev: { origin: 'https://dev.api.modtale.net', site: 'https://dev.modtale.net' },
  production: { origin: 'https://api.modtale.net', site: 'https://modtale.net' },
});
export const MAX_REQUESTS = 12;
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
    { id: 'origin_projects', path: '/api/v1/projects', kind: 'origin', headers: { Origin: fixed.site } },
    { id: 'cookie_projects', path: '/api/v1/projects', kind: 'credential', headers: { Cookie: 'SESSION=modtale-cache-diagnostic-invalid-session' } },
    { id: 'authorization_projects', path: '/api/v1/projects', kind: 'credential', headers: { Authorization: 'Bearer modtale-cache-diagnostic-invalid-token' } },
    { id: 'modtale_key_projects', path: '/api/v1/projects', kind: 'credential', headers: { 'X-Modtale-Key': 'modtale-cache-diagnostic-invalid-key' } },
    { id: 'legacy_key_projects', path: '/api/v1/projects', kind: 'credential', headers: { 'X-API-Key': 'modtale-cache-diagnostic-invalid-key' } },
    { id: 'missing_project', path: MISSING_PATH, kind: 'missing' },
  ]);
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

export function publicProjectFixture(body) {
  try {
    const value = JSON.parse(body);
    if (!Array.isArray(value?.content)) return null;
    for (const project of value.content.slice(0, 20)) {
      if (!project || !['PUBLISHED', 'ARCHIVED'].includes(project.status)) continue;
      const segment = project.slug || project.id;
      if (typeof segment !== 'string' || !/^[A-Za-z0-9][A-Za-z0-9-]{0,127}$/.test(segment)) continue;
      const prefixes = { PLUGIN: 'mod', DATA: 'mod', ART: 'mod', MODPACK: 'modpack', SAVE: 'world' };
      if (Object.hasOwn(prefixes, project.classification)) return `/${prefixes[project.classification]}/${segment}`;
    }
  } catch { /* Optional fixture evidence must not widen or fail the diagnostic. */ }
  return null;
}

function isApplicationProblem(response, body) {
  if (!/^(?:application\/json|application\/problem\+json)(?:;|$)/i.test(response.headers.get('content-type') || '')) return false;
  try {
    const value = JSON.parse(body);
    // ErrorMessageUtils emits these fields for API-key and Spring-security errors.
    // A generic JSON or HTML403 is not evidence that the application rejected a credential.
    return value && value.type === 'about:blank' && value.status === response.status
      && value.title === TITLES[response.status] && typeof value.detail === 'string' && value.detail.length > 0
      && value.error === value.detail && value.message === value.detail;
  } catch { return false; }
}

export function blockedReason(response, body) {
  if (response.status === 429) return 'rate_limited_no_retry';
  if (/challenge/i.test(response.headers.get('cf-mitigated') || '')) return 'waf_challenge';
  if (/\/cdn-cgi\/challenge-platform\/|challenges\.cloudflare\.com\/turnstile\//i.test(response.headers.get('location') || '')) return 'waf_challenge_redirect';
  if (/<title[^>]*>\s*(?:Just a moment|Attention Required)[^<]*<\/title>|cf-chl-(?:widget|managed|captcha)|\/cdn-cgi\/challenge-platform\//i.test(body)) return 'waf_challenge_markup';
  if (response.status >= 400 && /\b(?:error\s*(?:code)?\s*[:=]?\s*1010|cloudflare\s+(?:error\s*)?1010)\b/i.test(body)) return 'waf_1010';
  if ([401, 403].includes(response.status) && !isApplicationProblem(response, body)) return 'unrecognized_access_denial';
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
    status: response.status, ttfbMs: Math.round(ttfbMs), totalMs: Math.round(totalMs),
    cacheStatus: CACHE_STATES.has(cacheStatus) ? cacheStatus : null,
    malformedCacheStatusObserved: rawCacheStatus !== null && !CACHE_STATES.has(cacheStatus),
    ageSeconds: /^\d+$/.test(age || '') && Number.isSafeInteger(Number(age)) ? Number(age) : null,
    malformedAgeObserved: age !== null && (!/^\d+$/.test(age) || !Number.isSafeInteger(Number(age))),
    cacheControl: cachePolicy(response.headers.get('cache-control') || ''),
    cdnPolicyHeaderObserved: cdnHeader !== null,
    cdnCacheControl: cachePolicy(cdnHeader || ''),
    contentType: ['application/json', 'application/problem+json', 'text/html', 'text/plain'].includes(type) ? type : 'other',
    revision: /^[a-f0-9]{40}$/.test(revision || '') ? revision : null,
    setCookieObserved: response.headers.has('set-cookie'),
  };
}

function requireNoSharedCache(item) {
  requireCondition(!SHARED_CACHE_STATES.has(item.cacheStatus) && !(item.ageSeconds > 0), 'excluded_response_used_shared_cache');
  requireCondition(!item.malformedAgeObserved, 'unrecognized_cache_age');
  requireCondition(!item.malformedCacheStatusObserved, 'unrecognized_cache_status');
  requireCondition(['BYPASS', 'DYNAMIC'].includes(item.cacheStatus), 'edge_bypass_not_observed');
}
function requireNoStore(item) {
  requireCondition(item.cacheControl['no-store'] === true, 'credential_or_error_missing_no_store');
  if (item.cdnPolicyHeaderObserved) requireCondition(item.cdnCacheControl['no-store'] === true, 'cdn_policy_overrides_no_store');
  requireNoSharedCache(item);
}
function requirePublicPolicy(item) {
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
}

export function validateObservation(response, body, item) {
  const reason = blockedReason(response, body);
  if (reason) throw new DiagnosticFailure(reason, true);
  requireCondition(response.status < 300 || response.status >= 400, 'redirect_not_followed');
  if (response.status >= 400) {
    requireCondition(isApplicationProblem(response, body), 'unrecognized_application_error_contract');
    if (item.kind === 'missing') requireCondition([404, 410].includes(response.status), 'missing_fixture_not_authoritatively_missing');
    requireNoStore(item);
    return 'application_error_no_store';
  }
  requireCondition(response.status === 200 && item.kind !== 'missing', 'unexpected_success_status');
  requireCondition(item.contentType === 'application/json', 'successful_api_response_not_json');
  if (item.kind === 'credential') {
    requireNoStore(item);
    return 'synthetic_credential_no_store';
  }
  requirePublicPolicy(item);
  if (item.kind === 'origin') requireNoSharedCache(item);
  return item.kind === 'origin' ? 'origin_excluded_from_shared_cache' : 'public_bounded_300';
}

export async function runApiHeaderDiagnostic(input, { request = fetch, now = () => performance.now(), date = () => new Date() } = {}) {
  const fixed = validateInputs({ API_DIAGNOSTIC_TARGET: input.target });
  const cases = requestCases(fixed);
  requireCondition(cases.length === MAX_REQUESTS, 'request_plan_budget_invalid');
  const report = { target: fixed.target, origin: fixed.origin, sampledAt: date().toISOString(), status: 'running',
    maxRequests: MAX_REQUESTS, requestCount: 0, observations: [], publicSuccessCount: 0, applicationErrorCount: 0,
    confirmedPublicHit: false, authenticatedCrossUserCoverage: false,
    limitations: ['Headers/Age alone do not prove the active Cloudflare rule TTL; reconcile live rule configuration separately.',
      'Only fixed synthetic invalid credentials are tested; no real authentication or cross-user private data coverage.',
      'No response bodies, arbitrary header values, credential values, cookies, or tokens are retained.',
      'No retries, redirects, cache-busting, writes, purges, user-agent overrides, or WAF bypass.'] };
  try {
    for (const item of cases) {
      requireCondition(report.requestCount < MAX_REQUESTS, 'request_budget_exceeded');
      const url = new URL(item.path, fixed.origin);
      requireCondition(url.origin === fixed.origin && !url.search && !url.hash && !url.username && !url.password, 'request_outside_fixed_scope');
      const start = now();
      const startedAt = date().toISOString();
      const options = { method: 'GET', redirect: 'manual', signal: AbortSignal.timeout(15000) };
      if (item.headers) options.headers = { ...item.headers };
      report.requestCount++;
      const response = await request(url, options);
      const ttfb = now() - start;
      const body = await readBoundedBody(response);
      const result = observation(response, item, startedAt, ttfb, now() - start);
      report.observations.push(result);
      result.result = validateObservation(response, body, result);
      if (result.result === 'public_bounded_300') {
        report.publicSuccessCount++;
        if (result.cacheStatus === 'HIT') report.confirmedPublicHit = true;
        if (item.id === 'anonymous_projects') report.publicProjectFixturePath = publicProjectFixture(body);
      }
      if (result.result === 'application_error_no_store') report.applicationErrorCount++;
    }
    report.status = report.publicSuccessCount === 6 ? 'complete' : 'limited';
    if (report.status === 'limited') report.errorCode = 'public_policy_not_fully_observed';
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
