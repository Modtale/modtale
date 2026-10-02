import { request as httpRequest, type ClientRequest } from 'node:http';
import { request as httpsRequest } from 'node:https';

export type PublicJsonResult = { data: any | null; status: number };
export const PUBLIC_SSR_MAX_RESPONSE_BYTES = 8 * 1024 * 1024;

// Keep this transport out of shared/client modules. Node's fetch adds
// Sec-Fetch-Mode: cors, which correctly makes the API treat it as a browser
// request and issue a CSRF cookie. These reads are anonymous server requests:
// accept no caller headers, URL credentials, cookies, or redirects.
export const fetchPublicJson = (resource: string, timeoutMs = 1500): Promise<PublicJsonResult> => new Promise(resolve => {
    let request: ClientRequest | undefined;
    let status = 0;
    let settled = false;
    let chunks: Buffer[] = [];
    let timeoutHandle: ReturnType<typeof setTimeout> | undefined;
    const finish = (data: any = null) => {
        if (settled) return;
        settled = true;
        clearTimeout(timeoutHandle);
        chunks = [];
        resolve({ data, status });
    };
    const stop = () => {
        finish();
        request?.destroy();
    };

    try {
        const url = new URL(resource);
        if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password
            || !Number.isFinite(timeoutMs) || timeoutMs <= 0) {
            finish();
            return;
        }
        const send = url.protocol === 'https:' ? httpsRequest : httpRequest;
        request = send(url, {
            method: 'GET',
            headers: { Accept: 'application/json', 'Accept-Encoding': 'identity' },
        }, response => {
            status = response.statusCode ?? 0;
            response.on('error', stop);
            response.on('aborted', stop);
            if (status < 200 || status >= 300
                || Number(response.headers['content-length']) > PUBLIC_SSR_MAX_RESPONSE_BYTES) {
                stop();
                return;
            }
            let bytes = 0;
            response.on('data', (chunk: Buffer) => {
                if (settled) return;
                bytes += chunk.length;
                if (bytes > PUBLIC_SSR_MAX_RESPONSE_BYTES) {
                    stop();
                    return;
                }
                chunks.push(chunk);
            });
            response.on('end', () => {
                if (settled) return;
                try {
                    finish(JSON.parse(Buffer.concat(chunks, bytes).toString('utf8').replace(/^\uFEFF/, '')));
                } catch {
                    finish();
                }
            });
        });
        request.on('error', stop);
        // One deadline covers connection, headers, and the entire body, rather
        // than an idle-socket timeout that a trickling response could extend.
        timeoutHandle = setTimeout(stop, timeoutMs);
        request.end();
    } catch {
        stop();
    }
});
