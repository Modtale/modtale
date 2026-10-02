import assert from 'node:assert/strict';
import { execFile } from 'node:child_process';
import { once } from 'node:events';
import { mkdtemp, rm, symlink } from 'node:fs/promises';
import http from 'node:http';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { after, before, beforeEach, describe, it } from 'node:test';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { promisify } from 'node:util';

const root = fileURLToPath(new URL('../../', import.meta.url));
const run = promisify(execFile);
const project = { id: 'sky', slug: 'sky', title: 'Sky Tools', author: 'Ada', authorId: 'ada', classification: 'PLUGIN', status: 'PUBLISHED', downloadCount: 10, versions: [] };
const stats = { totalProjects: 12, totalDownloads: 345, totalUsers: 67 };
const news = { slug: 'update', title: 'A public update', description: 'New tools for creators', excerpt: 'New tools', author: 'Modtale Team', tags: ['Product'], publishedAt: '2026-09-01T12:00:00Z', updatedAt: '2026-09-01T12:00:00Z', readingTime: '1 min read', heroImage: '/assets/logo.svg', heroAlt: 'Logo', socialImage: '/assets/logo.svg', socialImageAlt: 'Logo', body: 'Public content' };
let backend;
let frontend;
let buildDir;
let origin;
let homeMode = 'success';
let newsMode = 'success';
let wikiOverrides = {};
let upstreamRequests = [];
const wikiMetadata = { index: { slug: 'home-1' }, pages: [{ slug: 'home-1', title: 'Welcome' }] };
const wikiPage = { slug: 'home-1', title: 'Welcome', content: 'Public wiki' };

const json = (res, data, status = 200) => {
    res.writeHead(status, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify(data));
};
const listen = async server => {
    server.listen(0, '127.0.0.1');
    await once(server, 'listening');
    return `http://127.0.0.1:${server.address().port}`;
};
const close = async server => {
    if (!server) return;
    server.closeAllConnections();
    await new Promise(resolve => server.close(resolve));
};
const get = async (route, headers = {}) => {
    const response = await fetch(`${origin}${route}`, { headers, redirect: 'manual' });
    return { response, html: await response.text() };
};
const wikiBootstrap = html => JSON.parse(html.match(/var bootstrap = ([\s\S]*?);/)?.[1] || 'null');
const bootstrap = html => JSON.parse(html.match(/window\.INITIAL_DATA = ([\s\S]*?);<\/script>/)?.[1] || 'null');
const assertPublic = (response, ttl = 600) => {
    assert.equal(response.status, 200);
    assert.equal(response.headers.get('Cache-Control'), 'public, max-age=0, must-revalidate');
    assert.equal(response.headers.get('CDN-Cache-Control'), `public, max-age=${ttl}, stale-while-revalidate=300, stale-if-error=0`);
    assert.equal(response.headers.get('Cache-Tag'), 'modtale-html-127.0.0.1');
};
const assertNoStore = response => {
    assert.match(response.headers.get('Cache-Control') || '', /no-store/);
    assert.equal(response.headers.get('CDN-Cache-Control'), 'no-store');
};

before(async () => {
    backend = http.createServer((req, res) => {
        const url = new URL(req.url, 'http://fixture');
        upstreamRequests.push({ path: url.pathname, cookie: req.headers.cookie, authorization: req.headers.authorization });
        if (url.pathname === '/api/v1/projects') {
            if (homeMode === 'all-fail' || (homeMode === 'partial' && url.searchParams.get('sort') === 'trending')) return json(res, { error: 'temporarily unavailable' }, 503);
            if (homeMode === 'malformed') return json(res, { content: null });
            return json(res, { content: homeMode === 'empty' ? [] : [project], totalPages: 1, totalElements: homeMode === 'empty' ? 0 : 1 });
        }
        if (url.pathname === '/api/v1/analytics/platform/stats') {
            if (homeMode === 'all-fail' || homeMode === 'partial') return json(res, {}, 503);
            return json(res, homeMode === 'empty' ? { totalProjects: 0, totalDownloads: 0, totalUsers: 0 } : stats);
        }
        if (url.pathname.startsWith('/api/v1/projects/')) {
            const key = url.pathname.split('/').at(-1);
            if (key === 'missing') return json(res, {}, 404);
            if (key === 'gone') return json(res, {}, 410);
            if (key === 'failure') return json(res, {}, 503);
            if (key === 'malformed') return json(res, { id: 'malformed' });
            if (key === 'slow') return; // Kept open until the SSR abort timeout.
            return json(res, { ...project, id: key, slug: key, classification: key === 'pack' ? 'MODPACK' : key === 'world' ? 'SAVE' : 'PLUGIN', status: key === 'unlisted' ? 'UNLISTED' : key === 'archived' ? 'ARCHIVED' : 'PUBLISHED' });
        }
        if (url.pathname.startsWith('/api/v1/user/profile/')) {
            const handle = url.pathname.split('/').at(-1);
            if (handle === 'missing') return json(res, {}, 404);
            if (handle === 'failure') return json(res, {}, 503);
            return json(res, { id: 'ada', username: 'ada', displayName: 'Ada', bio: 'A creator', avatarUrl: '/assets/favicon.svg', accountType: 'INDIVIDUAL' });
        }
        if (url.pathname.startsWith('/api/v1/wiki/')) {
            if (url.pathname.endsWith('/missing')) return json(res, {}, 404);
            if (url.pathname.endsWith('/failure')) return json(res, {}, 503);
            return json(res, { project, metadata: Object.hasOwn(wikiOverrides, 'metadata') ? wikiOverrides.metadata : wikiMetadata, page: Object.hasOwn(wikiOverrides, 'page') ? wikiOverrides.page : wikiPage, pageSlug: 'home-1' });
        }
        if (url.pathname === '/api/v1/news') {
            if (newsMode === 'failure') return json(res, {}, 503);
            if (newsMode === 'malformed') return json(res, { posts: [] });
            return json(res, [news]);
        }
        return json(res, {}, 404);
    });
    const apiOrigin = await listen(backend);
    buildDir = await mkdtemp(path.join(tmpdir(), 'modtale-html-cache-'));
    try {
        await run(process.execPath, [path.join(root, 'node_modules/astro/bin/astro.mjs'), 'build', '--outDir', buildDir], {
            cwd: root,
            env: { ...process.env, ASTRO_TELEMETRY_DISABLED: '1', PUBLIC_API_URL: `${apiOrigin}/api/v1`, SSR_API_URL: `${apiOrigin}/api/v1` },
            timeout: 120000,
            maxBuffer: 8 * 1024 * 1024,
        });
    } catch (error) {
        throw new Error(`Fixture build failed: ${error.stdout || ''}\n${error.stderr || ''}`, { cause: error });
    }
    await symlink(path.join(root, 'node_modules'), path.join(buildDir, 'node_modules'), 'dir');
    const { handler } = await import(pathToFileURL(path.join(buildDir, 'server/entry.mjs')).href);
    frontend = http.createServer((req, res) => {
        Promise.resolve(handler(req, res, () => { res.writeHead(404); res.end('Not found'); }))
            .catch(error => { res.writeHead(500); res.end(String(error)); });
    });
    origin = await listen(frontend);
}, { timeout: 120000 });

after(async () => { await close(frontend); await close(backend); if (buildDir) await rm(buildDir, { recursive: true, force: true }); });
beforeEach(() => { homeMode = 'success'; newsMode = 'success'; wikiOverrides = {}; upstreamRequests = []; });

describe('production SSR homepage caching', () => {
    it('caches a complete homepage for five minutes with project/stats bootstrap and SEO', async () => {
        const { response, html } = await get('/');
        assertPublic(response, 300);
        assert.equal(bootstrap(html).homeDataReady, true);
        assert.deepEqual(bootstrap(html).stats, stats);
        assert.match(html, /Sky Tools/);
        assert.match(html, /rel="canonical" href="https:\/\/modtale.net\/"/);
        assert.match(html, /application\/ld\+json/);
    });
    it('keeps all failed sections retryable and no-store instead of storing zeros', async () => {
        homeMode = 'all-fail';
        const { response, html } = await get('/');
        assertNoStore(response);
        const data = bootstrap(html);
        assert.equal(data.homeDataReady, false);
        assert.equal(data.stats, null);
        assert.deepEqual(data.homeSectionsReady, { marquee: false, trending: false, newest: false, stats: false });
    });
    it('retains useful partial results without claiming failed sections succeeded', async () => {
        homeMode = 'partial';
        const { response, html } = await get('/');
        assertNoStore(response);
        const data = bootstrap(html);
        assert.equal(data.homeDataReady, false);
        assert.deepEqual(data.homeSectionsReady, { marquee: true, trending: false, newest: true, stats: false });
        assert.deepEqual(data.homeNewestProjects, [project]);
    });
    it('caches authoritative empty results and zero stats as successful', async () => {
        homeMode = 'empty';
        const { response, html } = await get('/');
        assertPublic(response, 300);
        assert.equal(bootstrap(html).homeDataReady, true);
        assert.deepEqual(bootstrap(html).homeProjects, []);
    });
    it('does not cache malformed successful upstream list payloads', async () => {
        homeMode = 'malformed';
        const { response, html } = await get('/');
        assertNoStore(response);
        assert.equal(bootstrap(html).homeDataReady, false);
    });
    it('recovers on the next request after an upstream failure', async () => {
        homeMode = 'all-fail';
        assertNoStore((await get('/')).response);
        homeMode = 'success';
        const { response, html } = await get('/');
        assertPublic(response, 300);
        assert.equal(bootstrap(html).homeDataReady, true);
    });
    it('does not cache failed browse SSR', async () => {
        homeMode = 'all-fail';
        const { response, html } = await get('/mods');
        assertNoStore(response);
        assert.equal(bootstrap(html).browseDataReady, false);
    });
});

describe('production public detail and SEO behavior', () => {
    for (const route of ['/mod/sky', '/modpack/pack', '/world/world', '/mod/sky/download', '/mod/sky/changelog', '/mod/sky/gallery', '/mod/sky/wiki', '/creator/ada']) {
        it(`returns identical bot/human HTML and bounded CDN policy for ${route}`, async () => {
            await get(route); // Warm lazy modules before isolating user-agent variants.
            const human = await get(route, { 'User-Agent': 'Mozilla/5.0' });
            const bot = await get(route, { 'User-Agent': 'Googlebot' });
            assertPublic(human.response);
            assertPublic(bot.response);
            assert.equal(human.html, bot.html);
            assert.ok(bootstrap(human.html));
            assert.match(human.html, /rel="canonical"/);
        });
    }
    for (const [key, status] of [['missing', 404], ['gone', 410]]) {
        it(`uses authoritative project ${status} with unavailable bootstrap and noindex`, async () => {
            const { response, html } = await get(`/mod/${key}`);
            assert.equal(response.status, status);
            assertNoStore(response);
            assert.equal(bootstrap(html).__projectUnavailable, true);
            assert.match(html, /name="robots" content="noindex,follow"/);
        });
    }
    for (const key of ['failure', 'malformed', 'slow']) {
        it(`keeps ${key} project failure client-recoverable instead of fabricating 404`, async () => {
            const { response, html } = await get(`/mod/${key}`);
            assert.equal(response.status, 200);
            assertNoStore(response);
            assert.equal(bootstrap(html), null);
            assert.match(html, /__MODTALE_PROJECT_BOOTSTRAP/);
        });
    }
    for (const route of ['/modpack/missing', '/world/missing', '/project/missing/download']) {
        it(`uses authoritative 404 for ${route}`, async () => {
            const { response, html } = await get(route);
            assert.equal(response.status, 404);
            assertNoStore(response);
            assert.equal(bootstrap(html).__projectUnavailable, true);
        });
    }
    for (const malformed of [{}, [], { error: 'upstream failed' }]) {
        it(`does not cache malformed wiki metadata ${JSON.stringify(malformed)} and retains its valid page`, async () => {
            wikiOverrides = { metadata: malformed };
            const { response, html } = await get('/mod/sky/wiki');
            assert.equal(response.status, 200);
            assertNoStore(response);
            assert.equal(wikiBootstrap(html).metadataData, null);
            assert.deepEqual(wikiBootstrap(html).pageData, wikiPage);
        });
        it(`does not cache malformed wiki page ${JSON.stringify(malformed)} and retains valid metadata`, async () => {
            wikiOverrides = { page: malformed };
            const { response, html } = await get('/mod/sky/wiki');
            assert.equal(response.status, 200);
            assertNoStore(response);
            assert.deepEqual(wikiBootstrap(html).metadataData, wikiMetadata);
            assert.equal(wikiBootstrap(html).pageData, null);
        });
    }
    it('recovers after a malformed wiki bundle without permanently storing bad payloads', async () => {
        wikiOverrides = { metadata: {}, page: [] };
        assertNoStore((await get('/mod/sky/wiki')).response);
        wikiOverrides = {};
        const { response, html } = await get('/mod/sky/wiki');
        assertPublic(response);
        assert.deepEqual(wikiBootstrap(html).metadataData, wikiMetadata);
        assert.deepEqual(wikiBootstrap(html).pageData, wikiPage);
    });
    for (const route of ['/', '/mods', '/mod/sky', '/mod/sky/download', '/mod/sky/wiki', '/creator/ada', '/news', '/news/update', '/launcher']) {
        it(`returns exact anonymous/session-cookie HTML equality for ${route} without forwarding credentials`, async () => {
            // Keep lazy-module state constant while isolating request credentials.
            await get(route);
            const anonymous = await get(route);
            const withSession = await get(route, { Cookie: 'session=sentinel' });
            assertPublic(anonymous.response, route === '/' || route === '/mods' ? 300 : 600);
            assertPublic(withSession.response, route === '/' || route === '/mods' ? 300 : 600);
            assert.equal(anonymous.html, withSession.html);
            for (const request of upstreamRequests) {
                assert.equal(request.cookie, undefined);
                assert.equal(request.authorization, undefined);
            }
        });
    }
    for (const route of ['/', '/mods', '/mod/sky', '/creator/ada', '/news', '/news/update']) {
        it(`never forwards incoming Cookie or Authorization to public SSR API fetches for ${route}`, async () => {
            const { response } = await get(route, { Cookie: 'session=sentinel', Authorization: 'Bearer sentinel' });
            assertNoStore(response);
            assert.ok(upstreamRequests.length > 0);
            for (const request of upstreamRequests) {
                assert.equal(request.cookie, undefined);
                assert.equal(request.authorization, undefined);
            }
        });
    }
    it('keeps unlisted projects usable but no-store and noindex', async () => {
        const { response, html } = await get('/mod/unlisted');
        assert.equal(response.status, 200);
        assertNoStore(response);
        assert.equal(bootstrap(html).status, 'UNLISTED');
        assert.match(html, /name="robots" content="noindex,follow"/);
    });
    it('keeps archived projects cacheable', async () => { assertPublic((await get('/mod/archived')).response); });
    it('preserves canonical project redirects', async () => {
        const { response } = await get('/project/sky/download');
        assert.equal(response.status, 301);
        assert.equal(response.headers.get('Location'), '/mod/sky/download');
    });
    it('preserves canonical wiki redirects for humans and bots', async () => {
        for (const agent of ['Mozilla/5.0', 'Googlebot']) {
            const { response } = await get('/project/sky/wiki', { 'User-Agent': agent });
            assert.equal(response.status, 301);
            assert.equal(response.headers.get('Location'), '/mod/sky/wiki');
        }
    });
    it('does not revive a missing project from a stale wiki bundle', async () => {
        const { response, html } = await get('/mod/missing/wiki');
        assert.equal(response.status, 404);
        assertNoStore(response);
        assert.equal(bootstrap(html).__projectUnavailable, true);
        assert.doesNotMatch(html, /"content":"Public wiki"/);
    });
    it('returns authoritative missing wiki page status without caching', async () => {
        const { response, html } = await get('/mod/sky/wiki/missing');
        assert.equal(response.status, 404);
        assertNoStore(response);
        assert.equal(bootstrap(html).id, 'sky');
    });
    it('retains project bootstrap but allows recovery for transient wiki failures', async () => {
        const { response, html } = await get('/mod/sky/wiki/failure');
        assert.equal(response.status, 200);
        assertNoStore(response);
        assert.equal(bootstrap(html).id, 'sky');
    });
    it('does not turn creator failures into missing profiles', async () => {
        const { response } = await get('/creator/failure');
        assert.equal(response.status, 200);
        assertNoStore(response);
    });
    it('returns a real missing creator status', async () => {
        const { response, html } = await get('/creator/missing');
        assert.equal(response.status, 404);
        assertNoStore(response);
        assert.match(html, /name="robots" content="noindex,follow"/);
    });
});

describe('production news and public shells', () => {
    for (const route of ['/news', '/news/update']) {
        it(`caches published news ${route} with its bootstrap/SEO`, async () => {
            const { response, html } = await get(route);
            assertPublic(response);
            assert.match(html, /A public update/);
            assert.ok(bootstrap(html));
        });
    }
    it('returns real 404 for an unknown news slug', async () => {
        const { response } = await get('/news/missing');
        assert.equal(response.status, 404);
        assertNoStore(response);
        assert.equal(response.headers.get('X-Robots-Tag'), 'noindex');
    });
    for (const mode of ['failure', 'malformed']) {
        it(`never caches ${mode} news responses`, async () => {
            newsMode = mode;
            const { response } = await get('/news');
            assert.equal(response.status, 503);
            assertNoStore(response);
        });
    }
    for (const route of ['/terms', '/privacy', '/api-docs', '/api-docs/swagger', '/launcher']) {
        it(`explicitly caches the public generic shell ${route}`, async () => { assertPublic((await get(route)).response); });
    }
    for (const route of ['/login', '/dashboard', '/admin', '/upload', '/verify?token=secret', '/reset-password?token=secret', '/mfa', '/launcher/auth', '/mod/sky/edit', '/?code=secret', '/news?token=secret']) {
        it(`keeps private/account/token HTML ${route} no-store`, async () => { assertNoStore((await get(route)).response); });
    }
});
