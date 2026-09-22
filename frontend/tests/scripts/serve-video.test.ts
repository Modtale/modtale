// @vitest-environment node
import { createServer, type Server } from 'node:http';
import { mkdtempSync, writeFileSync, statSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { afterAll, beforeAll, expect, it } from 'vitest';
import { serveVideo } from '../../scripts/serve-video.mjs';

let server: Server;
let base: string;
let directory: string;
const bytes = Buffer.from('0123456789abcdefghijklmnopqrstuvwxyz');
beforeAll(async () => {
    directory = mkdtempSync(join(tmpdir(), 'modtale-video-'));
    const file = join(directory, 'demo.mp4');
    writeFileSync(file, bytes);
    server = createServer((req, res) => { serveVideo(req, res, file, '.mp4', statSync(file)); });
    await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve));
    base = `http://127.0.0.1:${(server.address() as { port: number }).port}`;
});
afterAll(async () => {
    await new Promise<void>(resolve => server.close(() => resolve()));
    rmSync(directory, { recursive: true });
});
it('streams the original bytes and supplies media and cache headers', async () => {
    const response = await fetch(base);
    expect(response.headers.get('content-type')).toBe('video/mp4');
    expect(response.headers.get('accept-ranges')).toBe('bytes');
    expect(Buffer.from(await response.arrayBuffer())).toEqual(bytes);
});
it.each([
    ['bytes=0-3', 206, '0123', 'bytes 0-3/36'],
    ['bytes=30-', 206, 'uvwxyz', 'bytes 30-35/36'],
    ['bytes=-3', 206, 'xyz', 'bytes 33-35/36'],
    ['bytes=34-999', 206, 'yz', 'bytes 34-35/36'],
    ['bytes=99-', 416, '', 'bytes */36'],
    ['bytes=-0', 416, '', 'bytes */36'],
    ['bytes=8-2', 416, '', 'bytes */36'],
    ['bytes=0-1,3-4', 200, bytes.toString(), null],
    ['bytes=invalid', 200, bytes.toString(), null],
])('handles range %s', async (range, status, body, contentRange) => {
    const response = await fetch(base, { headers: { Range: range } });
    expect(response.status).toBe(status);
    expect(response.headers.get('content-range')).toBe(contentRange);
    expect(await response.text()).toBe(body);
});
it('returns the full length without a body for HEAD', async () => {
    const response = await fetch(base, { method: 'HEAD', headers: { Range: 'bytes=0-3' } });
    expect(response.status).toBe(200);
    expect(response.headers.get('content-length')).toBe('36');
    expect(await response.text()).toBe('');
});
it('revalidates cached media and respects If-Range', async () => {
    const initial = await fetch(base);
    await initial.arrayBuffer();
    const etag = initial.headers.get('etag')!;
    const cached = await fetch(base, { headers: { 'If-None-Match': etag } });
    expect(cached.status).toBe(304);
    expect(await cached.text()).toBe('');
    const partial = await fetch(base, { headers: { Range: 'bytes=0-3', 'If-Range': etag } });
    expect(partial.status).toBe(206);
    expect(await partial.text()).toBe('0123');
    const stale = await fetch(base, { headers: { Range: 'bytes=0-3', 'If-Range': '"old"' } });
    expect(stale.status).toBe(200);
    expect(await stale.text()).toBe(bytes.toString());
});
