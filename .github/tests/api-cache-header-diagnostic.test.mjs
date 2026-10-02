import assert from 'node:assert/strict';
import fs from 'node:fs';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';
import { MAX_REQUESTS, MAX_BODY_BYTES, MISSING_PATH, validateInputs, requestCases,
  blockedReason, publicProjectFixture, runApiHeaderDiagnostic } from '../scripts/api-cache-header-diagnostic.mjs';

const publicPolicy = 'public, max-age=0, s-maxage=300, must-revalidate';
const sha = 'a'.repeat(40);
const projects = { content: [{ id: 'project-id', slug: 'public-fixture', classification: 'PLUGIN', status: 'PUBLISHED', title: 'not retained', description: 'not retained' }] };
const TITLES = { 401: 'Unauthorized', 403: 'Forbidden', 404: 'Not Found', 500: 'Internal Server Error' };
function problem(status, detail = 'Application error, not retained') {
  return { type: 'about:blank', title: TITLES[status], status, detail, error: detail, message: detail };
}
function fixture(url, options, overrides = {}) {
  const credential = options.headers && !options.headers.Origin;
  const missing = new URL(url).pathname === MISSING_PATH;
  const status = overrides.status ?? (missing ? 404 : 200);
  const body = overrides.body ?? (status >= 400 ? problem(status) : projects);
  return new Response(typeof body === 'string' ? body : JSON.stringify(body), { status, headers: {
    'content-type': status >= 400 ? 'application/problem+json' : 'application/json',
    'cache-control': credential || missing || status >= 400 ? 'private, no-store' : publicPolicy,
    'cf-cache-status': credential || options.headers?.Origin || missing || status >= 400 ? 'DYNAMIC' : 'HIT',
    'x-modtale-revision': sha, ...overrides.headers,
  } });
}
const input = (target = 'dev') => validateInputs({ API_DIAGNOSTIC_TARGET: target });

test('only exact fixed dev/production choices are accepted and dev is the default', () => {
  assert.equal(validateInputs({}).origin, 'https://dev.api.modtale.net');
  assert.equal(input('production').origin, 'https://api.modtale.net');
  for (const value of ['', 'prod', 'https://evil.test', 'https://api.modtale.net', '__proto__', 'dev; echo bad']) {
    assert.throws(() => input(value), /invalid_fixed_target/);
  }
  const cases = requestCases(input());
  assert.equal(cases.length, MAX_REQUESTS);
  assert.equal(cases.filter(item => item.kind === 'public').length, 6);
  assert.equal(cases.filter(item => item.kind === 'credential').length, 4);
  assert.ok(cases.every(item => item.path.startsWith('/api/v1/') && !/[?#%]/.test(item.path)));
});

test('at most12ordinaryGETs, no redirects/cache busting or real auth; summaries retain no raw data', async () => {
  for (const target of ['dev', 'production']) {
    const calls = [];
    const report = await runApiHeaderDiagnostic({ ...input(target), origin: 'https://ignored-evil.test' }, {
      request: async (url, options) => {
        calls.push([new URL(url), options]);
        assert.equal(new URL(url).origin, input(target).origin);
        assert.equal(options.method, 'GET');
        assert.equal(options.redirect, 'manual');
        assert.equal(options.body, undefined);
        assert.equal(options.cache, undefined);
        assert.equal(options.headers?.['User-Agent'], undefined);
        assert.equal(options.headers?.['Cache-Control'], undefined);
        assert.equal(new URL(url).search, '');
        return fixture(url, options, { headers: { 'age': options.headers || new URL(url).pathname === MISSING_PATH ? '0' : '20', 'set-cookie': 'never-retain-session-value' } });
      },
    });
    // Anonymous Set-Cookie must fail rather than turn a personalized response into public evidence.
    assert.equal(report.status, 'failed');
    assert.equal(report.errorCode, 'anonymous_public_response_sets_cookie');
    assert.equal(calls.length, 1);
    assert.equal(report.requestCount, 1);
    assert.ok(!JSON.stringify(report).includes('never-retain-session-value'));

    const goodCalls = [];
    const good = await runApiHeaderDiagnostic(input(target), { request: async (url, options) => {
      goodCalls.push([String(url), options]);
      return fixture(url, options);
    } });
    assert.equal(good.status, 'complete', good.errorCode);
    assert.equal(goodCalls.length, MAX_REQUESTS);
    assert.equal(good.requestCount, MAX_REQUESTS);
    assert.equal(good.publicSuccessCount, 6);
    assert.equal(good.applicationErrorCount, 1);
    assert.equal(good.publicProjectFixturePath, '/mod/public-fixture');
    assert.equal(good.authenticatedCrossUserCoverage, false);
    assert.equal(good.confirmedPublicHit, true);
    assert.ok(goodCalls.slice(0, 6).every(([, options]) => options.headers === undefined));
    assert.ok(!/invalid-token|invalid-key|invalid-session|not retained|responseHeaders|requestHeaders/.test(JSON.stringify(good)));
  }
});

test('source bound and legacy age, not pass-through origin headers alone, are checked', async () => {
  for (const headers of [
    { 'cache-control': 'public, max-age=3600' },
    { 'cache-control': 'public, max-age=0, s-maxage=301, must-revalidate' },
    { 'cache-control': publicPolicy + ', s-maxage=3600' },
    { 'cache-control': publicPolicy + ', stale-while-revalidate=600' },
    { 'age': '301' }, { 'age': 'untrusted-value-not-retained' },
    { 'cf-cache-status': 'unexpected-value-not-retained' },
    { 'cf-cache-status': 'STALE' }, { 'cf-cache-status': 'UPDATING' },
    { 'cdn-cache-control': 'public, max-age=3600' },
    { 'cloudflare-cdn-cache-control': 'public, s-maxage="3600"' },
    { 'cloudflare-cdn-cache-control': 'unrecognized-sensitive-value-not-retained' },
    { 'cloudflare-cdn-cache-control': '' },
  ]) {
    let count = 0;
    const report = await runApiHeaderDiagnostic(input(), { request: async (url, options) => {
      count++; return fixture(url, options, { headers });
    } });
    assert.equal(report.status, 'failed');
    assert.equal(count, 1);
    assert.ok(!JSON.stringify(report).includes('untrusted-value-not-retained'));
    assert.ok(!JSON.stringify(report).includes('unexpected-value-not-retained'));
    assert.ok(!JSON.stringify(report).includes('unrecognized-sensitive-value-not-retained'));
  }
});

test('all synthetic credentials require no-store and Origin cannot be a shared HIT', async () => {
  for (const failureCase of ['origin_projects', 'cookie_projects', 'authorization_projects', 'modtale_key_projects', 'legacy_key_projects']) {
    const cases = requestCases(input());
    let count = 0;
    const report = await runApiHeaderDiagnostic(input(), { request: async (url, options) => {
      const item = cases[count++];
      return fixture(url, options, item.id === failureCase ? { headers: { 'cf-cache-status': 'HIT', 'age': '10' } } : {});
    } });
    assert.equal(report.status, 'failed');
    assert.equal(report.errorCode, 'excluded_response_used_shared_cache');
    assert.equal(count, cases.findIndex(item => item.id === failureCase) + 1);
  }
  const report = await runApiHeaderDiagnostic(input(), { request: async (url, options) => fixture(url, options,
    options.headers?.Cookie ? { headers: { 'cache-control': publicPolicy } } : {}) });
  assert.equal(report.errorCode, 'credential_or_error_missing_no_store');
  for (const cacheStatus of ['MISS', 'EXPIRED', 'NONE']) {
    const inconclusive = await runApiHeaderDiagnostic(input(), { request: async (url, options) => fixture(url, options,
      options.headers?.Origin ? { headers: { 'cf-cache-status': cacheStatus, age: '0' } } : {}) });
    assert.equal(inconclusive.errorCode, 'edge_bypass_not_observed');
  }
  const cdnOverride = await runApiHeaderDiagnostic(input(), { request: async (url, options) => fixture(url, options,
    options.headers?.Cookie ? { headers: { 'cloudflare-cdn-cache-control': 's-maxage="3600"' } } : {}) });
  assert.equal(cdnOverride.errorCode, 'cdn_policy_overrides_no_store');
});

test('actual application401/403 contract is accepted but generic access denials/WAF stop immediately', async () => {
  for (const status of [401, 403]) {
    const report = await runApiHeaderDiagnostic(input(), { request: async (url, options) => fixture(url, options,
      options.headers?.Authorization || options.headers?.['X-Modtale-Key'] ? { status } : {}) });
    assert.equal(report.status, 'complete', report.errorCode);
    assert.equal(report.applicationErrorCount, 3);
  }
  for (const overrides of [
    { status: 403, body: 'error code: 1010', headers: { 'content-type': 'text/plain' } },
    { status: 403, body: { error: 'forbidden' }, headers: { 'content-type': 'application/json' } },
    { status: 200, body: '<title>Just a moment...</title>', headers: { 'content-type': 'text/html' } },
    { status: 403, body: problem(403), headers: { 'cf-mitigated': 'challenge' } },
    { status: 429, body: problem(403) },
    { status: 302, body: '', headers: { location: '/cdn-cgi/challenge-platform/start' } },
  ]) {
    let count = 0;
    const report = await runApiHeaderDiagnostic(input(), { request: async (url, options) => { count++; return fixture(url, options, overrides); } });
    assert.equal(report.status, 'blocked');
    assert.equal(count, 1);
    assert.equal(report.requestCount, 1);
  }
});

test('authoritative errors must be no-store, and the fixed missing fixture cannot silently exist', async () => {
  const limited = await runApiHeaderDiagnostic(input(), { request: async (url, options) => fixture(url, options,
    new URL(url).pathname === MISSING_PATH ? {} : { status: 500 }) });
  assert.equal(limited.status, 'limited');
  assert.equal(limited.errorCode, 'public_policy_not_fully_observed');
  assert.equal(limited.publicSuccessCount, 0);
  assert.equal(limited.requestCount, MAX_REQUESTS);
  const safe = await runApiHeaderDiagnostic(input(), { request: async (url, options) => fixture(url, options, { status: 500 }) });
  assert.equal(safe.status, 'failed');
  assert.equal(safe.errorCode, 'missing_fixture_not_authoritatively_missing');
  const unsafe = await runApiHeaderDiagnostic(input(), { request: async (url, options) => fixture(url, options,
    { status: 500, headers: { 'cache-control': publicPolicy } }) });
  assert.equal(unsafe.errorCode, 'credential_or_error_missing_no_store');
  const exists = await runApiHeaderDiagnostic(input(), { request: async (url, options) => fixture(url, options,
    new URL(url).pathname === MISSING_PATH ? { status: 200 } : {}) });
  assert.equal(exists.errorCode, 'unexpected_success_status');
});

test('timeouts/network failures, oversized bodies and redirects do not trigger retries or retain text', async () => {
  let count = 0;
  const failure = await runApiHeaderDiagnostic(input(), { request: async () => {
    count++; throw new Error('secret-cookie=do-not-retain');
  } });
  assert.equal(failure.errorCode, 'request_failed_no_retry');
  assert.equal(count, 1);
  assert.equal(failure.requestCount, 1);
  assert.ok(!JSON.stringify(failure).includes('do-not-retain'));
  const oversized = await runApiHeaderDiagnostic(input(), { request: async (url, options) => fixture(url, options, { body: 'x'.repeat(MAX_BODY_BYTES + 1) }) });
  assert.equal(oversized.status, 'blocked');
  assert.equal(oversized.errorCode, 'response_body_budget_exceeded');
  assert.equal(oversized.requestCount, 1);
  const redirect = await runApiHeaderDiagnostic(input(), { request: async (url, options) => fixture(url, options, { status: 302, body: '', headers: { location: 'https://evil.test' } }) });
  assert.equal(redirect.errorCode, 'redirect_not_followed');
});

test('optional project evidence is one validated public existing path, never a URL/body or inferred wiki', () => {
  assert.equal(publicProjectFixture(JSON.stringify(projects)), '/mod/public-fixture');
  assert.equal(publicProjectFixture(JSON.stringify({ content: [{ id: 'id', classification: 'SAVE', status: 'PUBLISHED' }] })), '/world/id');
  for (const overrides of [{ status: 'DRAFT' }, { slug: 'https://evil.test' }, { slug: 'token?secret=value' }, { classification: 'UNRECOGNIZED' }, { classification: '__proto__' }, { classification: 'constructor' }]) {
    assert.equal(publicProjectFixture(JSON.stringify({ content: [{ ...projects.content[0], ...overrides }] })), null);
  }
  assert.equal(publicProjectFixture('not-json'), null);
});

test('manual workflow has fixed choices, read-only permissions and no deployment/purge/secret/retry steps', () => {
  const workflow = fs.readFileSync(fileURLToPath(new URL('../workflows/api-cache-header-diagnostic.yml', import.meta.url)), 'utf8');
  assert.match(workflow, /workflow_dispatch:/);
  assert.match(workflow, /options: \[dev, production\]/);
  assert.match(workflow, /default: dev/);
  assert.match(workflow, /contents: read/);
  assert.match(workflow, /github\.ref == 'refs\/heads\/develop'/);
  assert.match(workflow, /persist-credentials: false/);
  assert.match(workflow, /timeout-minutes: 5/);
  assert.doesNotMatch(workflow, /secrets\.|id-token:|write\b|gcloud|purge_cache|deploy\b|curl|retry/);
  assert.equal(blockedReason(new Response('error code: 1010', { status: 403 }), 'error code: 1010'), 'waf_1010');
});
