import assert from 'node:assert/strict';
import fs from 'node:fs';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';
import { ORIGIN, MAX_DOCUMENT_READS, MAX_BROWSER_REQUESTS_PER_PAGE, MAX_LIGHTHOUSE_PAGES,
  MISSING_PATH, LIGHTHOUSE_FLAGS, validateInputs, browserRequestDecision,
  challengeReason, summarizeLighthouse, sanitizeReport, diagnosticLighthouseConfig, runDiagnostic } from '../scripts/dev-responsiveness-diagnostic.mjs';

const sha = 'a'.repeat(40);
const input = (overrides = {}) => validateInputs({ DIAGNOSTIC_PHASE: 'warm', DIAGNOSTIC_EXPECTED_SHA: sha, ...overrides });
const html = '<html><head><title>Public test fixture</title><meta name="robots" content="noindex"><link rel="canonical" href="https://dev.modtale.net/"></head><body><main>Public content</main></body></html>';
function response(url, { status, headers, body } = {}) {
  const target = new URL(url);
  const privatePath = ['/login', '/dashboard', '/reset-password'].includes(target.pathname);
  const filtered = Boolean(target.search);
  const missing = target.pathname === MISSING_PATH;
  return new Response(body || html, { status: status ?? (missing ? 404 : 200), headers: {
    'content-type': 'text/html', 'x-modtale-revision': sha,
    'cache-control': privatePath || filtered ? 'no-store' : 'max-age=0, must-revalidate',
    'cloudflare-cdn-cache-control': privatePath || filtered || missing ? 'no-store' : 'public, max-age=60',
    'cf-cache-status': privatePath || filtered || missing ? 'DYNAMIC' : 'HIT', ...headers,
  } });
}

test('only warm/cold, a verified-format deployment SHA and bounded public fixture paths are accepted', () => {
  const value = input({ DIAGNOSTIC_PROJECT_PATH: '/mod/a-public-project', DIAGNOSTIC_WIKI_PATH: '/mod/a-public-project/wiki/start/guide', DIAGNOSTIC_NEWS_PATH: '/news/public-article' });
  assert.equal(value.lighthousePaths.length, MAX_LIGHTHOUSE_PAGES);
  for (const overrides of [
    { DIAGNOSTIC_PHASE: 'load' }, { DIAGNOSTIC_EXPECTED_SHA: 'HEAD' }, { DIAGNOSTIC_EXPECTED_SHA: 'a'.repeat(41) },
    { FRONTEND_PUBLIC_URL: 'https://modtale.net' }, { FRONTEND_PUBLIC_URL: 'http://127.0.0.1' },
    { DIAGNOSTIC_PROJECT_PATH: 'https://example.test/mod/a' }, { DIAGNOSTIC_PROJECT_PATH: '/mod/a?q=secret' },
    { DIAGNOSTIC_PROJECT_PATH: '/mod/%2e%2e' }, { DIAGNOSTIC_PROJECT_PATH: '/mod/a/../b' },
    { DIAGNOSTIC_PROJECT_PATH: '/mod/a; echo injected' }, { DIAGNOSTIC_WIKI_PATH: '/mod/a/wiki//b' },
    { DIAGNOSTIC_NEWS_PATH: '/news/a#fragment' }, { DIAGNOSTIC_NEWS_PATH: '/news/' },
    { DIAGNOSTIC_NEWS_PATH: `/news/${'a'.repeat(129)}` },
  ]) assert.throws(() => input(overrides));
});

test('first ordinary homepage request precedes all warming and cold collection, with no request headers/body', async () => {
  for (const phase of ['warm', 'cold']) {
    const order = [];
    const report = await runDiagnostic(input({ DIAGNOSTIC_PHASE: phase }), {
      request: async (url, options) => {
        order.push(new URL(url).pathname + new URL(url).search);
        assert.equal(new URL(url).origin, ORIGIN);
        assert.equal(options.redirect, 'manual');
        assert.equal(options.headers, undefined);
        assert.equal(options.body, undefined);
        return response(url);
      },
      collect: async value => { order.push('lighthouse'); assert.deepEqual(value.lighthousePaths, ['/']); return []; },
    });
    assert.equal(report.status, 'complete', report.error);
    assert.equal(order[0], '/');
    assert.equal(order.filter(x => x === 'lighthouse').length, 1);
    assert.equal(order.indexOf('lighthouse') === 1, phase === 'cold');
    assert.ok(report.documentReads.length <= MAX_DOCUMENT_READS);
    assert.equal(report.coldStartVerified, false);
    assert.match(report.dataReadyTiming, /^unavailable/);
    assert.match(report.authenticatedCrossUserTesting, /^unavailable/);
    assert.equal(report.missingPage.status, 404);
    assert.ok(report.documentReads.some(x => x.path === '/dashboard'));
    for (const [index, item] of report.cacheSmoke.results.entries()) {
      assert.equal(item.ttfbMs, report.documentReads[index + 1].ttfbMs);
    }
  }
});

test('the maximal fixture set still makes only 18 ordinary document reads and one collection', async () => {
  let collected = 0;
  const report = await runDiagnostic(input({ DIAGNOSTIC_PROJECT_PATH: '/mod/public', DIAGNOSTIC_WIKI_PATH: '/mod/public/wiki', DIAGNOSTIC_NEWS_PATH: '/news/article' }), {
    request: async url => response(url),
    collect: async value => { collected++; assert.equal(value.lighthousePaths.length, 4); return []; },
  });
  assert.equal(report.status, 'complete', report.error);
  assert.equal(report.documentReads.length, MAX_DOCUMENT_READS);
  assert.equal(collected, 1);
});

test('nested smoke TTFB is actual header timing, not body inspection delay', async () => {
  let count = 0;
  const report = await runDiagnostic(input(), {
    request: async url => {
      const normal = response(url);
      if (++count !== 2) return normal;
      return new Response(new ReadableStream({ start(controller) {
        setTimeout(() => { controller.enqueue(new TextEncoder().encode(html)); controller.close(); }, 30);
      } }), { status: normal.status, headers: normal.headers });
    }, collect: async () => [],
  });
  assert.equal(report.status, 'complete', report.error);
  assert.ok(report.documentReads[1].totalMs >= 20);
  assert.equal(report.cacheSmoke.results[0].ttfbMs, report.documentReads[1].ttfbMs);
  assert.equal(report.cacheSmoke.results[0].totalMs, report.documentReads[1].totalMs);
});

test('WAF/status and deployment identity failures stop at the first request with no Lighthouse retry', async () => {
  for (const options of [
    { headers: { 'cf-mitigated': 'challenge' } },
    { body: '<title>Just a moment...</title><main>Challenge</main>' },
    { status: 403 }, { status: 429 }, { status: 503 },
    { headers: { 'x-modtale-revision': 'b'.repeat(40) } },
    { status: 302, headers: { location: 'https://accounts.example.test/' } },
  ]) {
    let reads = 0;
    let collections = 0;
    const report = await runDiagnostic(input(), { request: async url => { reads++; return response(url, options); }, collect: async () => { collections++; return []; } });
    assert.notEqual(report.status, 'complete');
    assert.equal(reads, 1);
    assert.equal(collections, 0);
  }
});

test('browser guard blocks writes/auth and stops challenges, unexpected navigation and excess requests', () => {
  const paths = new Set(['/']);
  const get = { url: ORIGIN + '/', method: 'GET', mainDocument: true };
  assert.deepEqual(browserRequestDecision(get, paths, 1), { allow: true });
  assert.ok(browserRequestDecision({ ...get, method: 'POST' }, paths, 1).block);
  assert.ok(browserRequestDecision({ ...get, headers: { Authorization: 'fixture' } }, paths, 1).block);
  assert.ok(browserRequestDecision({ ...get, url: 'https://example.test/' }, paths, 1).stop);
  assert.ok(browserRequestDecision({ ...get, url: ORIGIN + '/login' }, paths, 1).stop);
  assert.ok(browserRequestDecision({ ...get, url: ORIGIN + '/cdn-cgi/challenge-platform/test' }, paths, 1).stop);
  assert.ok(browserRequestDecision(get, paths, MAX_BROWSER_REQUESTS_PER_PAGE + 1).stop);
  assert.ok(browserRequestDecision({ ...get, url: 'https://cdn.example.test/app.js', mainDocument: false }, paths, 1).allow);
  assert.match(challengeReason(ORIGIN, { 'CF-Mitigated': 'challenge' }), /Cloudflare/);
});

test('Lighthouse reports measured metrics and distinguishes missing console/network data from zero errors', () => {
  const result = { lhr: { requestedUrl: ORIGIN, audits: {
    'largest-contentful-paint': { numericValue: 1234 }, 'first-contentful-paint': { numericValue: 500 },
    'total-blocking-time': { numericValue: 10 }, 'cumulative-layout-shift': { numericValue: 0.02 },
    'server-response-time': { numericValue: 100 }, metrics: { details: { items: [{ timeToFirstByte: 160 }] } },
  } } };
  const report = summarizeLighthouse(result);
  assert.equal(report.metrics.lcpMs, 1234);
  assert.equal(report.metrics.navigationTtfbMs, 160);
  assert.equal(report.metrics.initialServerResponseMs, 100);
  assert.equal(report.javascriptErrors, null);
  assert.equal(report.networkErrors, null);
  assert.equal(report.seoAudits.canonical.unavailable, true);
});

test('fixed Lighthouse configuration disables extra source-map lookups without losing gatherer metadata', async () => {
  const marker = Symbol('SourceMaps');
  class SourceMaps {
    get meta() { return { symbol: marker }; }
    async getArtifact() { throw new Error('network source-map lookup must not run'); }
  }
  const config = diagnosticLighthouseConfig(SourceMaps);
  assert.equal(config.extends, 'lighthouse:default');
  assert.equal(config.artifacts[0].id, 'SourceMaps');
  assert.equal(typeof config.artifacts[0].gatherer, 'function');
  const gatherer = new config.artifacts[0].gatherer();
  assert.equal(gatherer.meta.symbol, marker);
  assert.deepEqual(await gatherer.getArtifact(), []);
});

test('artifacts omit raw headers/bodies and credential, query and signed URL data', () => {
  const clean = sanitizeReport({ requestHeaders: { Authorization: 'Bearer secret' }, cookies: ['secret'], postData: 'secret body',
    result: [{ url: 'https://cdn.example.test/file?X-Amz-Signature=secret&Expires=100', responseHeaders: { 'Set-Cookie': 'secret' } },
      { url: 'https://dev.modtale.net/mod/a?private=secret#secret' }, { text: 'Authorization: Bearer secret' },
      { text: 'Error reading https://example.test/a?token=secret' }], cacheStatus: 'HIT' });
  const encoded = JSON.stringify(clean);
  assert.ok(!encoded.includes('secret'));
  assert.ok(!encoded.includes('Signature'));
  assert.equal(clean.cacheStatus, 'HIT');
  assert.equal(clean.result[1].url, ORIGIN + '/mod/a');
  const measured = summarizeLighthouse({ lhr: { audits: { 'errors-in-console': { details: { items: [{ description: 'request body secret' }] } } } },
    artifacts: { ConsoleMessages: [{ level: 'error', eventType: 'consoleAPI', text: 'private request body secret', url: ORIGIN + '/app.js' }] } });
  assert.ok(!JSON.stringify(measured).includes('secret'));
  assert.equal(measured.consoleAuditErrorCount, 1);
  assert.equal(sanitizeReport({ url: 'data:text/plain,private-request-body', finalUrl: 'javascript:privateData()' }).url, null);
  assert.equal(sanitizeReport({ url: 'blob:private-request-body' }).url, null);
});

test('workflow is manual, develop-only, credential-free and standard-runner with pinned existing tools', () => {
  const workflow = fs.readFileSync(fileURLToPath(new URL('../workflows/dev-responsiveness-diagnostic.yml', import.meta.url)), 'utf8');
  assert.match(workflow, /workflow_dispatch:/);
  assert.ok(!/^  (push|pull_request|schedule):/m.test(workflow));
  assert.match(workflow, /github\.ref_name == 'develop'/);
  assert.match(workflow, /runs-on: ubuntu-latest/);
  assert.match(workflow, /node-version: 22\.23\.3/);
  assert.ok(!/DIAGNOSTIC_TOOL_ROOT: \$\{\{ runner\./.test(workflow));
  assert.match(workflow, /export DIAGNOSTIC_TOOL_ROOT="\$RUNNER_TEMP\/modtale-diagnostic-tools"/);
  assert.match(workflow, /contents: read/);
  assert.ok(!/secrets\.|id-token:|actions: write|contents: write|environment:|run deploy|gcloud/i.test(workflow));
  assert.match(workflow, /@lhci\/cli@0\.15\.1/);
  assert.match(workflow, /test -x \/usr\/bin\/google-chrome/);
  assert.match(workflow, /actions\/upload-artifact@v4/);
  assert.ok(!/lhci (autorun|upload|collect)|temporary-public-storage|psiApi/i.test(workflow));
  const harness = fs.readFileSync(fileURLToPath(new URL('../scripts/dev-responsiveness-diagnostic.mjs', import.meta.url)), 'utf8');
  assert.ok(!/GITHUB_STEP_SUMMARY|\.report\.html|\.devtools\.json|\.lhr\.json|summary\.txt/.test(harness));
  assert.equal(LIGHTHOUSE_FLAGS.disableStorageReset, true);
  assert.equal(LIGHTHOUSE_FLAGS.disableFullPageScreenshot, true);
  assert.equal(LIGHTHOUSE_FLAGS.usePassiveGathering, true);
  assert.ok(!LIGHTHOUSE_FLAGS.onlyAudits.includes('bf-cache'));
  assert.ok(!LIGHTHOUSE_FLAGS.onlyAudits.includes('paste-preventing-inputs'));
});
