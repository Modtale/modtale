import { pathToFileURL } from 'node:url';

export async function invalidateFrontend(env, { request = fetch, wait = ms => new Promise(r => setTimeout(r, ms)) } = {}) {
  const { CLOUDFLARE_CACHE_PURGE_TOKEN: token, FRONTEND_PUBLIC_URL: publicUrl,
    FRONTEND_ORIGIN_URL: originUrl, GITHUB_SHA: revision } = env;
  if (!token || !publicUrl || !originUrl || !revision) throw new Error('Missing frontend cache deployment configuration');
  const host = new URL(publicUrl).hostname;
  if (!['modtale.net', 'dev.modtale.net'].includes(host)) throw new Error('Unexpected frontend hostname');
  async function verify(url, attempts) {
    for (let i = 0; i < attempts; i++) {
      const response = await request(url, { signal: AbortSignal.timeout(30000) });
      await response.arrayBuffer();
      if (response.ok && response.headers.get('x-modtale-revision') === revision) return;
      if (i + 1 < attempts) await wait(5000);
    }
    throw new Error(`Deployed revision not visible at ${url}`);
  }
  // Never evict good HTML until the new origin can actually serve the release.
  await verify(originUrl, 12);
  const response = await request('https://api.cloudflare.com/client/v4/zones/ff20a05c676ca023b19d20e177054945/purge_cache', {
    method: 'POST', signal: AbortSignal.timeout(30000),
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ tags: [`modtale-html-${host}`] }),
  });
  const result = await response.json();
  if (!response.ok || !result.success) throw new Error(`Frontend HTML purge failed (${response.status})`);
  // Use ordinary public URLs: cache-busting queries would hide stale-cache bugs.
  await verify(new URL('/', publicUrl).href, 12);
  await verify(new URL('/launcher', publicUrl).href, 12);
  console.log(`Public frontend verified at revision ${revision}; immutable assets remain cached.`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  await invalidateFrontend(process.env);
}
