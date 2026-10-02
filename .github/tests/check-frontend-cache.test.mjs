import { test } from 'node:test';
import assert from 'node:assert/strict';
import { checkFrontendCache, validateHtmlResponse } from '../scripts/check-frontend-cache.mjs';

const html = '<!doctype html><title>Modtale fixture</title><main>Public shell</main>';
const response = (edge = 'public, max-age=300, stale-while-revalidate=60', headers = {}) => new Response(html, { headers: { 'content-type': 'text/html', 'cache-control': 'public, max-age=0, must-revalidate', 'cdn-cache-control': edge, ...headers } });

test('separates edge max-age/SWR from browser must-revalidate', () => {
  const result = validateHtmlResponse(response(), { path: '/', cache: 'public' });
  assert.match(result.edge, /max-age=300/);
});
test('rejects conflicting shared directives and release mismatch', () => {
  assert.throws(() => validateHtmlResponse(response('public, s-maxage=300, stale-while-revalidate=60'), { path: '/', cache: 'public' }), /opt in|conflicting/);
  assert.throws(() => validateHtmlResponse(response(), { path: '/', cache: 'public', revision: 'expected' }), /revision/);
});
test('requires no-store for token/private HTML and no shared hits', () => {
  const safe = response('private, no-store', { 'cache-control': 'private, no-store', 'cf-cache-status': 'DYNAMIC' });
  validateHtmlResponse(safe, { path: '/reset-password?token=sentinel', cache: 'private' });
  assert.throws(() => validateHtmlResponse(response(), { path: '/dashboard', cache: 'private' }), /no-store/);
  assert.throws(() => validateHtmlResponse(response('no-store', { 'cache-control': 'no-store', 'cf-cache-status': 'HIT' }), { path: '/dashboard', cache: 'private' }), /shared cache/);
});
test('never treats 5xx, cookie-setting HTML, or a fresh search result as passing', () => {
  assert.throws(() => validateHtmlResponse(new Response(html, { status: 503 }), { path: '/', cache: 'public' }), /503/);
  assert.throws(() => validateHtmlResponse(response(undefined, { 'set-cookie': 'fixture=value' }), { path: '/', cache: 'public' }), /cookie/);
  assert.throws(() => validateHtmlResponse(response(), { path: '/mods?q=test', cache: 'fresh' }), /shared freshness/);
});
test('bounded smoke reports measurement gaps rather than inventing cold/LCP/auth results', async () => {
  let count = 0;
  const report = await checkFrontendCache({ FRONTEND_PUBLIC_URL: 'https://dev.modtale.net', GITHUB_SHA: 'fixture' }, { request: async url => {
    count++;
    const privateRoute = /^\/(login|dashboard|reset-password)/.test(url.pathname);
    return response(privateRoute ? 'private, no-store' : url.search ? 'public, max-age=0' : undefined, { 'cache-control': privateRoute ? 'private, no-store' : 'public, max-age=0, must-revalidate', 'x-modtale-revision': 'fixture' });
  }});
  assert.equal(count, 13);
  assert.equal(report.coldStartVerified, false);
  assert.equal(report.confirmedEdgeHit, false);
  assert.equal(report.lcpMeasured, false);
});
test('requires real HIT evidence when configured and rejects unrelated fixture paths', async () => {
  await assert.rejects(checkFrontendCache({ FRONTEND_PUBLIC_URL: 'https://dev.modtale.net', CACHE_SMOKE_REQUIRE_HIT: 'true' }, { request: async url => {
    const privateRoute = /^\/(login|dashboard|reset-password)/.test(url.pathname);
    return response(privateRoute ? 'no-store' : url.search ? 'public, max-age=0' : undefined, { 'cache-control': privateRoute ? 'no-store' : 'public, max-age=0, must-revalidate' });
  }}), /No actual Cloudflare HIT/);
  await assert.rejects(checkFrontendCache({ FRONTEND_PUBLIC_URL: 'https://dev.modtale.net', CACHE_SMOKE_PROJECT_PATH: '//other.example' }), /Unexpected/);
});

test('CI preserves warm production and sets both frontend minimum levels', async () => {
  const { readFile } = await import('node:fs/promises');
  const workflow = await readFile(new URL('../workflows/ci-cd.yml', import.meta.url), 'utf8');
  assert.match(workflow, /FRONTEND_ARGS\+=\(\"--min\" \"1\" \"--min-instances\" \"1\"\)/);
  assert.equal((workflow.match(/FRONTEND_ARGS\+=\(\"--min\" \"0\" \"--min-instances\" \"0\"\)/g) || []).length, 2);
});
