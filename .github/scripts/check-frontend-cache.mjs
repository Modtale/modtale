import { pathToFileURL } from 'node:url';
import { performance } from 'node:perf_hooks';

const DEFAULT_CASES = [
  { path: '/', cache: 'public' },
  { path: '/mods', cache: 'public' },
  { path: '/mods?q=cache-smoke', cache: 'fresh' },
  { path: '/mods?page=1', cache: 'fresh' },
  { path: '/terms', cache: 'public' },
  { path: '/privacy', cache: 'public' },
  { path: '/launcher', cache: 'public' },
  { path: '/news', cache: 'public' },
  { path: '/login', cache: 'private' },
  { path: '/dashboard', cache: 'private' },
  { path: '/reset-password?token=cache-smoke-sentinel', cache: 'private' },
];

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

export function validateHtmlResponse(response, { path, cache, revision }) {
  assert(response.status === 200, `${path}: expected 200, received ${response.status}`);
  assert((response.headers.get('content-type') || '').includes('text/html'), `${path}: HTML missing`);
  assert(!response.headers.has('set-cookie'), `${path}: HTML sets a cookie`);
  if (revision) assert(response.headers.get('x-modtale-revision') === revision, `${path}: wrong deployed revision`);
  const browser = response.headers.get('cache-control') || '';
  const edge = response.headers.get('cloudflare-cdn-cache-control') || response.headers.get('cdn-cache-control') || '';
  if (cache === 'private') {
    assert(/\bno-store\b/i.test(browser) && /\bno-store\b/i.test(edge), `${path}: private/token HTML is not no-store`);
  } else {
    assert((cache === 'fresh' && /\bno-store\b/i.test(browser)) || (/\bmax-age=0\b/i.test(browser) && /\bmust-revalidate\b/i.test(browser)), `${path}: browser revalidation missing`);
    if (cache === 'public') {
      assert(/\bpublic\b/i.test(edge) && /\bmax-age=[1-9]\d*\b/i.test(edge), `${path}: successful public HTML did not opt in`);
      assert(!/\b(?:s-maxage|must-revalidate|proxy-revalidate|no-store|private)\b/i.test(edge), `${path}: conflicting public CDN directives`);
    } else {
      assert(/\b(?:no-store|no-cache|max-age=0)\b/i.test(edge), `${path}: search/filter response has shared freshness`);
    }
  }
  const cacheStatus = response.headers.get('cf-cache-status');
  if (cache === 'private') assert(!['HIT', 'STALE', 'UPDATING', 'REVALIDATED'].includes(cacheStatus), `${path}: private HTML served from shared cache`);
  return { browser, edge, cacheStatus, age: response.headers.get('age'), revision: response.headers.get('x-modtale-revision') };
}

export async function checkFrontendCache(env, { request = fetch, now = () => performance.now() } = {}) {
  const base = new URL(env.FRONTEND_PUBLIC_URL);
  assert(base.protocol === 'https:' || ['localhost', '127.0.0.1'].includes(base.hostname), 'Expected HTTPS or a local fixture');
  assert(!base.username && !base.password && !base.search && !base.hash, 'Base URL must not contain credentials, query or fragment');
  const cases = [...DEFAULT_CASES];
  for (const [key, prefix] of [['CACHE_SMOKE_PROJECT_PATH', /^\/(mod|modpack|world)\/[^/?#]+$/], ['CACHE_SMOKE_WIKI_PATH', /^\/(mod|modpack|world)\/[^/?#]+\/wiki(?:\/[^?#]+)?$/], ['CACHE_SMOKE_NEWS_PATH', /^\/news\/[^/?#]+$/], ['CACHE_SMOKE_CREATOR_PATH', /^\/creator\/[^/?#]+$/]]) {
    if (env[key]) {
      assert(prefix.test(env[key]), `Unexpected ${key}`);
      cases.push({ path: env[key], cache: 'public' });
    }
  }
  const results = [];
  // One ordinary read per route, plus one repeat each for home/news. This is a
  // bounded smoke check, not a load test or statistically reliable percentile.
  for (const item of [...cases, cases[0], cases[7]]) {
    const url = new URL(item.path, base);
    const start = now();
    const response = await request(url, { signal: AbortSignal.timeout(30000), redirect: 'manual' });
    const ttfbMs = now() - start;
    const headers = validateHtmlResponse(response, { ...item, revision: env.GITHUB_SHA });
    const body = await response.text();
    assert(/<title>[^<]+<\/title>/i.test(body) && /<main[\s>]/i.test(body), `${item.path}: rendered shell/SEO title missing`);
    results.push({ path: item.path, status: response.status, ttfbMs: Math.round(ttfbMs), totalMs: Math.round(now() - start), ...headers });
  }
  const report = { sampledAt: new Date().toISOString(), baseUrl: base.origin, results, confirmedEdgeHit: results.some(r => r.cacheStatus === 'HIT'), authenticatedLeakageTest: 'not run: no authorized disposable account', coldStartVerified: false, lcpMeasured: false };
  if (env.CACHE_SMOKE_REQUIRE_HIT === 'true') assert(report.confirmedEdgeHit, 'No actual Cloudflare HIT was observed');
  return report;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  console.log(JSON.stringify(await checkFrontendCache(process.env), null, 2));
}
