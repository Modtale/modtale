import { test } from 'node:test';
import assert from 'node:assert/strict';
import { invalidateFrontend } from '../scripts/invalidate-frontend-cache.mjs';
const env = { CLOUDFLARE_CACHE_PURGE_TOKEN: 'test', FRONTEND_PUBLIC_URL: 'https://modtale.net', FRONTEND_ORIGIN_URL: 'https://origin.example', GITHUB_SHA: 'new-revision' };
const good = () => new Response('html', { headers: { 'x-modtale-revision': env.GITHUB_SHA } });
test('purges only environment HTML after origin readiness, then warms real public URLs', async () => {
  const calls = [];
  await invalidateFrontend(env, { request: async (url, options) => {
    calls.push([url, options]);
    return options?.method === 'POST' ? Response.json({ success: true }) : good();
  }});
  assert.equal(calls[0][0], env.FRONTEND_ORIGIN_URL);
  assert.deepEqual(JSON.parse(calls[1][1].body), { tags: ['modtale-html-modtale.net'] });
  assert.deepEqual(JSON.parse(calls[2][1].body), { files: ['https://modtale.net/', 'https://modtale.net/launcher'] });
  assert.deepEqual(calls.slice(3).map(c => c[0]), ['https://modtale.net/', 'https://modtale.net/launcher']);
});
test('never purges when origin is stale', async () => {
  let purged = false;
  await assert.rejects(invalidateFrontend(env, { wait: async () => {}, request: async (_, options) => {
    purged ||= options?.method === 'POST';
    return new Response('old');
  }}), /revision not visible/);
  assert.equal(purged, false);
});
test('failed purge fails deployment rather than silently leaving old HTML', async () => {
  await assert.rejects(invalidateFrontend(env, { request: async (_, options) => options?.method === 'POST' ? Response.json({ success: false }, { status: 403 }) : good() }), /purge failed/);
});
test('public cache must actually expose the new release', async () => {
  await assert.rejects(invalidateFrontend(env, { wait: async () => {}, request: async (url, options) => options?.method === 'POST' ? Response.json({ success: true }) : url === env.FRONTEND_ORIGIN_URL ? good() : new Response('old') }), /revision not visible/);
});
