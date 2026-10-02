import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import { once } from 'node:events';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';
import { MAX_REQUESTS, MAX_BODY_BYTES, REQUEST_TIMEOUT_MS, MISSING_PATH, validateInputs, requestCases,
  blockedReason, publicProjectFixture, requestWithoutBrowserHeaders, runApiHeaderDiagnostic } from '../scripts/api-cache-header-diagnostic.mjs';

const publicPolicy = 'public, max-age=0, s-maxage=300, must-revalidate';
const sha = 'a'.repeat(40);
const projects = { content: [{ id: 'project-id', slug: 'public-fixture', classification: 'PLUGIN', status: 'PUBLISHED', title: 'not retained', description: 'not retained' }] };
const TITLES = { 401: 'Unauthorized', 403: 'Forbidden', 404: 'Not Found', 500: 'Internal Server Error' };
function problem(status, detail = 'Application error, not retained') {
  return { type: 'about:blank', title: TITLES[status], status, detail, error: detail, message: detail };
}
function fixture(url, options, overrides = {}) {
  const excluded = options.headers !== undefined;
  const bootstrap = new URL(url).pathname === '/api/v1/auth/csrf';
  const missing = new URL(url).pathname === MISSING_PATH;
  const status = overrides.status ?? (missing ? 404 : 200);
  const body = overrides.body ?? (status >= 400 ? problem(status) : bootstrap ? { token: 'ephemeral-csrf-not-retained' } : projects);
  return new Response(typeof body === 'string' ? body : JSON.stringify(body), { status, headers: {
    'content-type': status >= 400 ? 'application/problem+json' : 'application/json',
    'cache-control': excluded || missing || status >= 400 ? 'private, no-store' : publicPolicy,
    'cf-cache-status': excluded || missing || status >= 400 ? 'DYNAMIC' : 'HIT',
    'x-modtale-revision': sha,
    ...(excluded && status === 200 ? { 'set-cookie': 'XSRF-TOKEN=ephemeral-csrf-not-retained; Path=/; Secure; SameSite=None' } : {}),
    ...overrides.headers,
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
  assert.equal(cases.length, MAX_REQUESTS - 2);
  assert.equal(cases.filter(item => item.kind === 'public').length, 6);
  assert.equal(cases.filter(item => item.kind === 'credential').length, 4);
  assert.ok(cases.every(item => item.path.startsWith('/api/v1/') && !/[?#%]/.test(item.path)));
});

test('at most 25 bounded ordinary GETs, no redirects/cache busting or real auth; summaries retain no raw data', async () => {
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
    assert.equal(good.publicSuccessCount, 8);
    assert.equal(good.catalogSuccessCount, 6);
    assert.equal(good.projectSuccessCount, 2);
    assert.equal(good.applicationErrorCount, 1);
    assert.equal(good.publicProjectFixturePath, '/mod/public-fixture');
    assert.equal(good.authenticatedCrossUserCoverage, false);
    assert.equal(good.confirmedPublicHit, true);
    assert.equal(good.csrfBootstrapVerified, true);
    assert.ok(goodCalls.slice(0, 6).every(([, options]) => options.headers === undefined));
    assert.ok(!/invalid-token|invalid-key|invalid-session|not retained|ephemeral-csrf-not-retained|responseHeaders|requestHeaders/.test(JSON.stringify(good)));
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

test('every credential, browser, empty-header and bootstrap request must bypass shared cache', async () => {
  for (const failureCase of requestCases(input()).filter(item => !['public', 'missing'].includes(item.kind)).map(item => item.id)) {
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

test('excluded successes must issue XSRF specifically; bootstrap token must match its cookie', async () => {
  for (const item of requestCases(input()).filter(item => !['public', 'missing'].includes(item.kind))) {
    for (const badHeaders of [
      { 'cache-control': publicPolicy },
      { 'set-cookie': 'SESSION=not-an-xsrf-cookie; Secure' },
      { 'set-cookie': 'XSRF-TOKEN=; Max-Age=0' },
    ]) {
      let count = 0;
      const report = await runApiHeaderDiagnostic(input(), { request: async (url, options) => {
        const current = requestCases(input())[count++];
        return fixture(url, options, current.id === item.id ? { headers: badHeaders } : {});
      } });
      assert.equal(report.status, 'failed', item.id);
      assert.equal(report.errorCode, badHeaders['cache-control']
        ? 'credential_or_error_missing_no_store' : 'excluded_success_missing_csrf_cookie', item.id);
    }
  }
  const mismatch = await runApiHeaderDiagnostic(input(), { request: async (url, options) => fixture(url, options,
    new URL(url).pathname === '/api/v1/auth/csrf' ? { body: { token: 'different-secret-token' } } : {}) });
  assert.equal(mismatch.errorCode, 'bootstrap_token_cookie_mismatch');
  assert.ok(!JSON.stringify(mismatch).includes('different-secret-token'));
  const unavailable = await runApiHeaderDiagnostic(input(), { request: async (url, options) => fixture(url, options,
    new URL(url).pathname === '/api/v1/auth/csrf' ? { status: 500 } : {}) });
  assert.equal(unavailable.status, 'limited');
  assert.equal(unavailable.errorCode, 'csrf_bootstrap_not_observed');
  assert.equal(unavailable.csrfBootstrapVerified, false);
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
    { status: 403, body: 'Invalid CORS request', headers: { 'content-type': 'text/plain' } },
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
    [MISSING_PATH, '/api/v1/auth/csrf'].includes(new URL(url).pathname) ? {} : { status: 500 }) });
  assert.equal(limited.status, 'limited');
  assert.equal(limited.errorCode, 'public_policy_not_fully_observed');
  assert.equal(limited.publicSuccessCount, 0);
  assert.equal(limited.requestCount, MAX_REQUESTS - 2);
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


test('empty-header probes are presence-based and the fixed time budget fits the workflow', () => {
  const cases = requestCases(input());
  const empty = cases.filter(item => item.kind === 'empty_header');
  assert.equal(empty.length, 7);
  assert.deepEqual(empty.map(item => Object.keys(item.headers)[0]),
    ['Cookie', 'Authorization', 'Referer', 'X-Modtale-Key', 'X-API-Key', 'Sec-Fetch-Site', 'Sec-Fetch-Unknown']);
  assert.ok(empty.every(item => Object.values(item.headers)[0] === ''));
  assert.ok(MAX_REQUESTS * REQUEST_TIMEOUT_MS < 5 * 60 * 1000);
});

test('native transport preserves empty headers and never adds fetch metadata or follows redirects', async () => {
  const observed = [];
  const server = http.createServer((request, response) => {
    observed.push({ url: request.url, headers: request.headers });
    response.writeHead(302, { Location: '/not-followed', 'Set-Cookie': ['one=secret-a', 'two=secret-b'] });
    response.end('redirect body');
  });
  server.listen(0, '127.0.0.1');
  await once(server, 'listening');
  const url = new URL(`http://127.0.0.1:${server.address().port}/fixed`);
  try {
    for (const headers of [undefined, { Origin: '', Cookie: '', 'Sec-Fetch-Unknown': '' }]) {
      const response = await requestWithoutBrowserHeaders(url,
        { method: 'GET', redirect: 'manual', headers, signal: AbortSignal.timeout(1000) }, http.request);
      assert.equal(response.status, 302);
      assert.deepEqual(response.headers.getSetCookie(), ['one=secret-a', 'two=secret-b']);
      assert.equal(await new Response(response.body).text(), 'redirect body');
    }
    assert.equal(observed.length, 2);
    assert.ok(observed.every(item => item.url === '/fixed'));
    assert.equal(observed[0].headers['sec-fetch-mode'], undefined);
    assert.equal(observed[0].headers['user-agent'], undefined);
    assert.equal(observed[0].headers.origin, undefined);
    assert.equal(observed[0].headers.cookie, undefined);
    assert.equal(observed[1].headers.origin, '');
    assert.equal(observed[1].headers.cookie, '');
    assert.equal(observed[1].headers['sec-fetch-unknown'], '');
  } finally { server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); }
});

test('native request deadline also aborts a response body that stalls after headers', async () => {
  const server = http.createServer((request, response) => { response.writeHead(200); response.write('{'); });
  server.listen(0, '127.0.0.1');
  await once(server, 'listening');
  try {
    const response = await requestWithoutBrowserHeaders(new URL(`http://127.0.0.1:${server.address().port}/fixed`),
      { method: 'GET', signal: AbortSignal.timeout(200) }, http.request);
    await assert.rejects(new Response(response.body).text());
  } finally { server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); }
});


function originOnlyFixture(url, options, overrides = {}) {
  const response = fixture(url, options, overrides);
  response.headers.delete('cf-cache-status');
  response.headers.delete('cf-ray');
  return response;
}

test('DNS-only staging validates origin policy without claiming edge verification', async () => {
  const report = await runApiHeaderDiagnostic(input(), { request: async (url, options) => originOnlyFixture(url, options) });
  assert.equal(report.status, 'complete', report.errorCode);
  assert.equal(report.requestCount, MAX_REQUESTS);
  assert.equal(report.publicSuccessCount, 8);
  assert.equal(report.originPolicyVerified, true);
  assert.equal(report.csrfBootstrapVerified, true);
  assert.equal(report.verificationScope, 'origin_only');
  assert.equal(report.edgeVerificationRequired, false);
  assert.equal(report.edgeVerified, false);
  assert.equal(report.confirmedPublicHit, false);
  assert.ok(report.observations.every(item => item.cloudflareHeadersObserved === false));
  assert.match(report.limitations[0], /does not verify Cloudflare edge caching/);
});

test('staging with Cloudflare headers still enforces edge validation and remains origin-scoped', async () => {
  const observed = await runApiHeaderDiagnostic(input(), { request: async (url, options) => fixture(url, options) });
  assert.equal(observed.status, 'complete');
  assert.equal(observed.confirmedPublicHit, true);
  assert.equal(observed.edgeVerified, false);
  assert.equal(observed.verificationScope, 'origin_only');
  const partial = await runApiHeaderDiagnostic(input(), { request: async (url, options) => {
    const response = originOnlyFixture(url, options);
    response.headers.set('cf-ray', 'unretained-fixture-ray');
    return response;
  } });
  assert.equal(partial.errorCode, 'edge_cache_status_not_observed');
  assert.ok(!JSON.stringify(partial).includes('unretained-fixture-ray'));
  for (const headers of [
    { 'cf-cache-status': 'HIT' }, { age: '1' },
    { 'cf-cache-status': 'MISS' }, { 'cf-cache-status': '' }, { age: 'invalid' },
  ]) {
    const report = await runApiHeaderDiagnostic(input(), { request: async (url, options) => {
      const response = originOnlyFixture(url, options);
      if (options.headers?.Origin) for (const [name, value] of Object.entries(headers)) response.headers.set(name, value);
      return response;
    } });
    assert.equal(report.status, 'failed');
    assert.equal(report.edgeVerified, false);
  }
});

test('origin-only staging still rejects missing no-store, missing cookies and bad bootstrap tokens', async () => {
  for (const overrides of [
    { headers: { 'cache-control': publicPolicy } },
    { headers: { 'set-cookie': 'SESSION=not-an-xsrf-cookie' } },
  ]) {
    const report = await runApiHeaderDiagnostic(input(), { request: async (url, options) => originOnlyFixture(url, options,
      options.headers?.Origin ? overrides : {}) });
    assert.equal(report.status, 'failed');
    assert.equal(report.originPolicyVerified, false);
  }
  const report = await runApiHeaderDiagnostic(input(), { request: async (url, options) => originOnlyFixture(url, options,
    new URL(url).pathname === '/api/v1/auth/csrf' ? { body: { token: 'mismatch' } } : {}) });
  assert.equal(report.errorCode, 'bootstrap_token_cookie_mismatch');
  assert.equal(report.originPolicyVerified, false);
});

test('production cannot complete without a public HIT or silently become origin-only', async () => {
  const missingHeaders = await runApiHeaderDiagnostic({ ...input('production'), requireEdge: false }, {
    request: async (url, options) => originOnlyFixture(url, options),
  });
  assert.equal(missingHeaders.status, 'failed');
  assert.equal(missingHeaders.errorCode, 'edge_cache_status_not_observed');
  assert.equal(missingHeaders.edgeVerificationRequired, true);
  for (const state of ['MISS', 'BYPASS', 'DYNAMIC', 'EXPIRED']) {
    const noHit = await runApiHeaderDiagnostic(input('production'), { request: async (url, options) => fixture(url, options,
      options.headers || new URL(url).pathname === MISSING_PATH ? {} : { headers: { 'cf-cache-status': state } }) });
    assert.equal(noHit.status, 'limited');
    assert.equal(noHit.errorCode, 'catalog_cache_hit_not_observed');
    assert.equal(noHit.requestCount, MAX_REQUESTS);
    assert.equal(noHit.originPolicyVerified, true);
    assert.equal(noHit.edgeVerified, false);
  }
  const good = await runApiHeaderDiagnostic(input('production'), { request: async (url, options) => fixture(url, options) });
  assert.equal(good.status, 'complete');
  assert.equal(good.verificationScope, 'origin_and_cloudflare_edge');
  assert.equal(good.edgeVerificationRequired, true);
  assert.equal(good.originPolicyVerified, true);
  assert.equal(good.edgeVerified, true);
  assert.equal(good.confirmedPublicHit, true);
  const missingBypass = await runApiHeaderDiagnostic(input('production'), { request: async (url, options) => {
    const response = fixture(url, options);
    if (options.headers?.Origin) response.headers.delete('cf-cache-status');
    return response;
  } });
  assert.equal(missingBypass.errorCode, 'edge_bypass_not_observed');
});


test('both production cache-rule groups require a HIT from a bounded validated fixture', async () => {
  const calls = [];
  const good = await runApiHeaderDiagnostic(input('production'), { request: async (url, options) => {
    calls.push(new URL(url));
    return fixture(url, options);
  } });
  assert.equal(good.status, 'complete');
  assert.equal(good.confirmedCatalogHit, true);
  assert.equal(good.confirmedProjectHit, true);
  assert.equal(good.projectFixtureAvailable, true);
  assert.equal(good.catalogSuccessCount, 6);
  assert.equal(good.projectSuccessCount, 2);
  assert.equal(calls.length, MAX_REQUESTS);
  assert.deepEqual(calls.slice(-2).map(url => url.pathname), ['/api/v1/projects/public-fixture', '/api/v1/projects/public-fixture']);
  assert.ok(calls.every(url => url.origin === input('production').origin && !url.search && !url.hash));
  const projectMiss = await runApiHeaderDiagnostic(input('production'), { request: async (url, options) => fixture(url, options,
    new URL(url).pathname === '/api/v1/projects/public-fixture' ? { headers: { 'cf-cache-status': 'MISS' } } : {}) });
  assert.equal(projectMiss.status, 'limited');
  assert.equal(projectMiss.errorCode, 'project_cache_hit_not_observed');
  assert.equal(projectMiss.confirmedCatalogHit, true);
  assert.equal(projectMiss.confirmedProjectHit, false);
  assert.equal(projectMiss.edgeVerified, false);
});

test('absent or unsafe public fixture limits coverage and never widens request scope', async () => {
  for (const target of ['dev', 'production']) {
    for (const body of [
      { content: [] },
      { content: [{ ...projects.content[0], slug: 'https://attacker.test' }] },
      { content: [{ ...projects.content[0], status: 'DRAFT' }] },
    ]) {
      const calls = [];
      const report = await runApiHeaderDiagnostic(input(target), { request: async (url, options) => {
        calls.push(String(url));
        return fixture(url, options, new URL(url).pathname === '/api/v1/projects' && !options.headers ? { body } : {});
      } });
      assert.equal(report.status, 'limited');
      assert.equal(report.errorCode, 'public_project_fixture_not_observed');
      assert.equal(report.requestCount, MAX_REQUESTS - 2);
      assert.equal(report.projectFixtureAvailable, false);
      assert.equal(report.projectSuccessCount, 0);
      assert.equal(report.edgeVerified, false);
      assert.ok(calls.every(url => !url.includes('attacker')));
    }
  }
  const gone = await runApiHeaderDiagnostic(input('production'), { request: async (url, options) => fixture(url, options,
    new URL(url).pathname === '/api/v1/projects/public-fixture' ? { status: 404 } : {}) });
  assert.equal(gone.status, 'limited');
  assert.equal(gone.errorCode, 'project_policy_not_fully_observed');
  assert.equal(gone.edgeVerified, false);
});


// Captured from local real ApiKeyAuthFilter -> GlobalExceptionHandler MVC output.
const mvcKeyDenial = {
  detail: 'Authentication is required to perform this action: Invalid API Key.',
  instance: '/api/v1/projects', status: 401, title: 'Unauthorized',
  error: 'Authentication is required to perform this action: Invalid API Key.',
  message: 'Authentication is required to perform this action: Invalid API Key.',
};
// Captured from local ProjectMapper.toSummaryDTO(publicProject) Jackson output.
const publicSummaryDto = {
  id: 'fixture-id', slug: 'fixture-slug', title: 'Fixture', classification: 'PLUGIN', downloadCount: 0, favoriteCount: 0,
};

test('real MVC API-key rejection with omitted type and exact instance is recognized', async () => {
  for (const target of ['dev', 'production']) {
    const report = await runApiHeaderDiagnostic(input(target), { request: async (url, options) => fixture(url, options,
      options.headers && Object.hasOwn(options.headers, 'X-Modtale-Key')
        ? { status: 401, body: mvcKeyDenial, headers: { 'content-type': 'application/problem+json' } } : {}) });
    assert.equal(report.status, 'complete', report.errorCode);
    assert.equal(report.applicationErrorCount, 3);
    assert.ok(!JSON.stringify(report).includes('Authentication is required'));
  }
  for (const body of [
    { ...mvcKeyDenial, instance: '/api/v1/other' },
    { ...mvcKeyDenial, instance: undefined },
    { ...mvcKeyDenial, type: 'https://unrecognized.test/problem' },
    { ...mvcKeyDenial, type: null },
    { ...mvcKeyDenial, status: 403 },
    { ...mvcKeyDenial, title: 'Forbidden' },
    { ...mvcKeyDenial, message: 'unrelated' },
    { ...mvcKeyDenial, properties: { error: 'conflicting' } },
  ]) {
    const report = await runApiHeaderDiagnostic(input(), { request: async (url, options) => fixture(url, options,
      options.headers?.['X-Modtale-Key'] ? { status: 401, body } : {}) });
    assert.equal(report.status, 'blocked');
    assert.equal(report.errorCode, 'unrecognized_access_denial');
  }
});

test('bare Jackson SecurityConfig denial is recognized only with exact nested contract', async () => {
  for (const [status, detail] of [
    [401, 'You need to sign in before performing this action. If you were already signed in, your session may have expired.'],
    [403, 'You do not have permission to perform this action with the current account or API key.'],
  ]) {
    const body = { detail, instance: null, properties: { error: detail, message: detail }, status, title: TITLES[status], type: null };
    const report = await runApiHeaderDiagnostic(input(), { request: async (url, options) => fixture(url, options,
      options.headers?.Authorization ? { status, body, headers: { 'content-type': 'application/json' } } : {}) });
    assert.equal(report.status, 'complete', report.errorCode);
    for (const invalid of [
      { ...body, detail: 'generic denial', properties: { error: 'generic denial', message: 'generic denial' } },
      { ...body, instance: '/unexpected' },
      { ...body, properties: { error: detail } },
      { ...body, error: detail },
      { ...body, properties: { error: detail, message: detail, unexpected: true } },
    ]) {
      const blocked = await runApiHeaderDiagnostic(input(), { request: async (url, options) => fixture(url, options,
        options.headers?.Authorization ? { status, body: invalid, headers: { 'content-type': 'application/json' } } : {}) });
      assert.equal(blocked.status, 'blocked');
      assert.equal(blocked.errorCode, 'unrecognized_access_denial');
    }
    const waf = await runApiHeaderDiagnostic(input(), { request: async (url, options) => fixture(url, options,
      { status, body, headers: { 'content-type': 'application/json', 'cf-mitigated': 'challenge' } }) });
    assert.equal(waf.status, 'blocked');
    assert.equal(waf.errorCode, 'waf_challenge');
    assert.equal(waf.requestCount, 1);
  }
});

test('source-proven public summary DTO permits missing status only in a verified anonymous catalog', async () => {
  const body = JSON.stringify({ content: [publicSummaryDto] });
  assert.equal(publicProjectFixture(body), null);
  assert.equal(publicProjectFixture(body, { verifiedAnonymousCatalog: true }), '/mod/fixture-slug');
  assert.equal(publicProjectFixture(JSON.stringify({ content: [{ ...publicSummaryDto, slug: undefined }] }),
    { verifiedAnonymousCatalog: true }), '/mod/fixture-id');
  for (const override of [
    { status: 'DRAFT' }, { status: 'PRIVATE' }, { status: 'UNLISTED' }, { status: null }, { status: 'UNKNOWN' },
    { canEdit: true }, { isOwner: false }, { versions: [] }, { title: undefined }, { id: undefined },
    { downloadCount: undefined }, { favoriteCount: -1 }, { classification: '__proto__' },
    { slug: 'https://attacker.test' }, { slug: 'secret?token=value' }, { slug: '../private' },
  ]) {
    assert.equal(publicProjectFixture(JSON.stringify({ content: [{ ...publicSummaryDto, ...override }] }),
      { verifiedAnonymousCatalog: true }), null);
  }
  const paths = [];
  const report = await runApiHeaderDiagnostic(input('production'), { request: async (url, options) => {
    paths.push(new URL(url).pathname);
    return fixture(url, options, new URL(url).pathname === '/api/v1/projects' && !options.headers
      ? { body: { content: [publicSummaryDto] } } : {});
  } });
  assert.equal(report.status, 'complete', report.errorCode);
  assert.equal(report.projectFixtureAvailable, true);
  assert.equal(report.publicProjectFixturePath, '/mod/fixture-slug');
  assert.deepEqual(paths.slice(-2), ['/api/v1/projects/fixture-slug', '/api/v1/projects/fixture-slug']);
  assert.equal(paths.length, MAX_REQUESTS);
});
