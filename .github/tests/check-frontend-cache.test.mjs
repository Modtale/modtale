import { test } from 'node:test';
import assert from 'node:assert/strict';
import { checkFrontendCache, validateHtmlResponse, validateRenderedHtml } from '../scripts/check-frontend-cache.mjs';

const html = '<!doctype html><title>Modtale fixture</title><main>Public shell</main>';
const response = (edge = 'public, max-age=300, stale-while-revalidate=60', headers = {}) => new Response(html, { headers: { 'content-type': 'text/html', 'cache-control': 'public, max-age=0, must-revalidate', 'cdn-cache-control': edge, ...headers } });
const appShell = heading => `<html><head><title>Modtale fixture</title></head><body><astro-island component-url="/_astro/App.fixture.js" component-export="default" ssr client="load"><div><h1>${heading}</h1><p>Rendered route content</p></div></astro-island></body></html>`;

test('accepts actual div-based SSR shells only for their matching known route headings', () => {
  for (const [path, heading] of [
    ['/terms', 'Terms of Service'], ['/privacy', 'Privacy Policy'], ['/dashboard', 'Your Projects'],
    ['/reset-password?token=cache-smoke-sentinel', 'Invalid Request'], ['/reset-password', 'Invalid Request'],
    ['/reset-password?token=cache-smoke-sentinel', 'Reset Password'],
  ]) validateRenderedHtml(appShell(heading), { path });
  assert.throws(() => validateRenderedHtml(appShell('Privacy Policy'), { path: '/terms' }), /shell\/heading/);
  assert.throws(() => validateRenderedHtml(appShell('Unrelated content'), { path: '/reset-password' }), /shell\/heading/);
  for (const path of ['/', '/mods', '/news', '/mod/sky', '/mod/sky/wiki', '/news/update', '/creator/ada', '/launcher']) {
    assert.throws(() => validateRenderedHtml(appShell('Terms of Service'), { path }), /shell\/heading|bootstrap/);
  }
});

test('rejects blank titles, empty main landmarks, inert markup and unrendered App islands', () => {
  for (const body of [
    '<title> </title><main>Public content</main>', '<title>&nbsp;</title><main>Public content</main>',
    '<title>Modtale</title><main>  </main>', '<title>Modtale</title><main><svg><title>Icon</title></svg></main>',
    '<title>Modtale</title><main><!-- Public content --><script>"Public content"</script><style>.content{display:block}</style></main>',
    '<title>Modtale</title><main><template>Public content</template></main>', '<title>Modtale</title><main>',
  ]) assert.throws(() => validateRenderedHtml(body, { path: '/' }), /title|blank|shell/);
  for (const body of [
    '<title>Modtale</title><div><h1>Terms of Service</h1></div>',
    appShell('Terms of Service').replace(' ssr ', ' '),
    appShell('Terms of Service').replace('component-export="default"', 'component-export="Other"'),
    appShell('Terms of Service').replace('component-url="/_astro/App.fixture.js"', 'component-url="/_astro/Other.fixture.js"'),
    appShell('Terms of Service').replace('<h1>Terms of Service</h1>', '<script>"<h1>Terms of Service</h1>"</script>'),
    appShell('Terms of Service').replace('<h1>Terms of Service</h1>', '').replace('</astro-island>', '</astro-island><h1>Terms of Service</h1>'),
    appShell('').replace('<p>Rendered route content</p>', ''),
  ]) assert.throws(() => validateRenderedHtml(body, { path: '/terms' }), /shell\/heading/);
  validateRenderedHtml(appShell('Terms of Service').replace('component-export="default" ssr client="load"', 'ssr client="load" component-export="default"'), { path: '/terms' });
});

test('public project/wiki/creator div layouts require a rendered heading matching real public bootstrap data', () => {
  const bootstrapShell = (heading, data) => appShell(heading).replace('</head>', `<script>window.INITIAL_DATA = ${JSON.stringify(data).replace(/</g, '\\u003c')};</script></head>`);
  const project = { id: 'sky', title: 'Sky & Tools', status: 'PUBLISHED' };
  for (const path of ['/mod/sky', '/modpack/sky', '/world/sky', '/mod/sky/wiki', '/mod/sky/wiki/start']) {
    validateRenderedHtml(bootstrapShell('Sky &amp; Tools', project), { path });
    for (const data of [null, {}, { ...project, status: 'UNLISTED' }, { ...project, title: '' }, { ...project, id: '' }]) {
      assert.throws(() => validateRenderedHtml(bootstrapShell('Sky &amp; Tools', data), { path }), /bootstrap/);
    }
    assert.throws(() => validateRenderedHtml(bootstrapShell('Project title', project), { path }), /shell\/heading/);
  }
  validateRenderedHtml(bootstrapShell('ada', { id: 'ada-id', username: 'ada' }), { path: '/creator/ada' });
  assert.throws(() => validateRenderedHtml(bootstrapShell('Creator name', { id: 'ada-id', username: 'ada' }), { path: '/creator/ada' }), /shell\/heading/);
  assert.throws(() => validateRenderedHtml(bootstrapShell('ada', { id: 'ada-id' }), { path: '/creator/ada' }), /bootstrap/);
});

test('div-based shells still fail revision, cookie, public cache and private-cache gates', async () => {
  const env = { FRONTEND_PUBLIC_URL: 'https://dev.modtale.net', GITHUB_SHA: 'fixture' };
  for (const [path, badHeaders, message] of [
    ['/terms', { 'x-modtale-revision': 'other' }, /revision/],
    ['/terms', { 'set-cookie': 'fixture=value' }, /cookie/],
    ['/terms', { 'cdn-cache-control': 'no-store' }, /opt in/],
    ['/dashboard', { 'cdn-cache-control': 'public, max-age=300' }, /no-store/],
    ['/reset-password', { 'cf-cache-status': 'HIT' }, /shared cache/],
  ]) {
    await assert.rejects(checkFrontendCache(env, { request: async url => {
      const privateRoute = /^\/(login|dashboard|reset-password)/.test(url.pathname);
      const headers = { 'content-type': 'text/html', 'x-modtale-revision': 'fixture', 'cache-control': privateRoute ? 'no-store' : 'max-age=0, must-revalidate', 'cdn-cache-control': privateRoute ? 'no-store' : url.search ? 'max-age=0' : 'public, max-age=300' };
      const heading = { '/terms': 'Terms of Service', '/privacy': 'Privacy Policy', '/dashboard': 'Your Projects', '/reset-password': 'Reset Password' }[url.pathname];
      return new Response(heading ? appShell(heading) : html, { headers: { ...headers, ...(url.pathname === path ? badHeaders : {}) } });
    }}), message);
  }
});

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
