// @vitest-environment node
import http, { type IncomingMessage, type ServerResponse } from 'node:http';
import { once } from 'node:events';
import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import { fetchPublicJson, PUBLIC_SSR_MAX_RESPONSE_BYTES } from '@/utils/publicSsr.server';

const page = { content: [{ id: 'sky', title: 'Sky Tools', status: 'PUBLISHED' }] };
let origin: string;
let requests: IncomingMessage[] = [];
let handle: (req: IncomingMessage, res: ServerResponse) => void;
const server = http.createServer((req, res) => {
    requests.push(req);
    handle(req, res);
});

beforeAll(async () => {
    server.listen(0, '127.0.0.1');
    await once(server, 'listening');
    const address = server.address();
    if (!address || typeof address === 'string') throw new Error('Missing fixture address');
    origin = `http://127.0.0.1:${address.port}`;
});
afterAll(async () => {
    server.closeAllConnections();
    await new Promise<void>(resolve => server.close(() => resolve()));
});
beforeEach(() => {
    requests = [];
    handle = (_req, res) => res.end(JSON.stringify(page));
});

describe('anonymous server-only public JSON transport', () => {
    it('reads local HTTP JSON without browser metadata or credentials', async () => {
        expect(await fetchPublicJson(`${origin}/api/v1/projects?size=12`)).toEqual({ data: page, status: 200 });
        expect(requests).toHaveLength(1);
        expect(requests[0].method).toBe('GET');
        expect(requests[0].url).toBe('/api/v1/projects?size=12');
        expect(Object.keys(requests[0].headers).sort()).toEqual(['accept', 'accept-encoding', 'connection', 'host']);
        expect(requests[0].headers.accept).toBe('application/json');
        expect(requests[0].headers['accept-encoding']).toBe('identity');
    });
    it('never stores or forwards upstream cookies on a subsequent request', async () => {
        handle = (_req, res) => {
            res.setHeader('Set-Cookie', 'XSRF-TOKEN=sentinel; Path=/');
            res.end('{}');
        };
        await fetchPublicJson(origin);
        await fetchPublicJson(origin);
        expect(requests).toHaveLength(2);
        for (const request of requests) expect(request.headers.cookie).toBeUndefined();
    });
    it.each([301, 302, 303, 307, 308])('does not follow %s redirects or return their payloads', async status => {
        handle = (_req, res) => {
            res.writeHead(status, { Location: `${origin}/redirect-target` });
            res.end(JSON.stringify(page));
        };
        expect(await fetchPublicJson(origin)).toEqual({ data: null, status });
        expect(requests).toHaveLength(1);
    });
    it.each([404, 410, 429, 500, 503])('preserves upstream status %s without fabricated data', async status => {
        handle = (_req, res) => { res.writeHead(status); res.end(JSON.stringify(page)); };
        expect(await fetchPublicJson(origin)).toEqual({ data: null, status });
    });
    it.each(['{broken', '', '<html>not JSON</html>'])('keeps malformed success data retryable: %j', async body => {
        handle = (_req, res) => res.end(body);
        expect(await fetchPublicJson(origin)).toEqual({ data: null, status: 200 });
    });
    it('preserves UTF-8 JSON and the optional BOM accepted by fetch', async () => {
        const data = { title: 'Sky café 🌤️' };
        handle = (_req, res) => res.end(`\uFEFF${JSON.stringify(data)}`);
        expect(await fetchPublicJson(origin)).toEqual({ data, status: 200 });
    });
    it('treats an empty 204 as absent data with its status intact', async () => {
        handle = (_req, res) => { res.writeHead(204); res.end(); };
        expect(await fetchPublicJson(origin)).toEqual({ data: null, status: 204 });
    });
    it('distinguishes a connection failure from authoritative 404', async () => {
        handle = req => req.socket.destroy();
        expect(await fetchPublicJson(origin)).toEqual({ data: null, status: 0 });
    });
    it('times out and closes a request waiting for headers', async () => {
        let closed!: Promise<unknown>;
        handle = req => { closed = new Promise(resolve => req.once('close', resolve)); };
        expect(await fetchPublicJson(origin, 50)).toEqual({ data: null, status: 0 });
        await closed;
    });
    it('preserves received status when the response body times out', async () => {
        let closed!: Promise<unknown>;
        handle = (req, res) => {
            closed = new Promise(resolve => req.once('close', resolve));
            res.writeHead(200);
            res.write('{');
        };
        expect(await fetchPublicJson(origin, 50)).toEqual({ data: null, status: 200 });
        await closed;
    });
    it('uses a total deadline even while body bytes keep arriving', async () => {
        let closed!: Promise<unknown>;
        handle = (req, res) => {
            closed = new Promise(resolve => req.once('close', resolve));
            res.write('{');
            const interval = setInterval(() => res.write(' '), 5);
            req.on('close', () => clearInterval(interval));
        };
        expect(await fetchPublicJson(origin, 50)).toEqual({ data: null, status: 200 });
        await closed;
    });
    it('preserves status on a truncated response body', async () => {
        handle = (_req, res) => {
            res.writeHead(200, { 'Content-Length': 100 });
            res.end('{');
        };
        expect(await fetchPublicJson(origin, 50)).toEqual({ data: null, status: 200 });
    });
    it('rejects an oversized declared body without waiting for body bytes', async () => {
        handle = (_req, res) => {
            res.writeHead(200, { 'Content-Length': PUBLIC_SSR_MAX_RESPONSE_BYTES + 1 });
            res.flushHeaders();
        };
        expect(await fetchPublicJson(origin)).toEqual({ data: null, status: 200 });
    });
    it('bounds chunked response bytes even without a Content-Length', async () => {
        handle = (_req, res) => {
            res.writeHead(200);
            res.write('"');
            res.end(`${'x'.repeat(PUBLIC_SSR_MAX_RESPONSE_BYTES)}"`);
        };
        expect(await fetchPublicJson(origin)).toEqual({ data: null, status: 200 });
    });
    it('accepts a valid response exactly at the byte limit', async () => {
        const data = 'x'.repeat(PUBLIC_SSR_MAX_RESPONSE_BYTES - 2);
        handle = (_req, res) => res.end(JSON.stringify(data));
        const result = await fetchPublicJson(origin);
        expect(result.status).toBe(200);
        expect(result.data).toBe(data);
    });
    it.each(['data:application/json,{}', 'file:///etc/passwd', 'ftp://example.invalid/file', 'not a URL'])('rejects unsupported or malformed URL %s', async url => {
        expect(await fetchPublicJson(url)).toEqual({ data: null, status: 0 });
        expect(requests).toHaveLength(0);
    });
    it.each(['user', 'user:password', ':password'])('rejects URL credentials %s before making a request', async credentials => {
        expect(await fetchPublicJson(origin.replace('://', `://${credentials}@`))).toEqual({ data: null, status: 0 });
        expect(requests).toHaveLength(0);
    });
    it.each([0, -1, Infinity, NaN])('rejects an invalid deadline %s before making a request', async timeout => {
        expect(await fetchPublicJson(origin, timeout)).toEqual({ data: null, status: 0 });
        expect(requests).toHaveLength(0);
    });
});
