import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import zlib from 'node:zlib';
import { fileURLToPath } from 'node:url';
import { handler } from '../dist/server/entry.mjs';

const clientRoot = path.resolve(fileURLToPath(new URL('../dist/client/', import.meta.url)));
const port = Number(process.env.PORT) || 5173;
const host = process.env.HOST || '0.0.0.0';
const logContext = {
    service: process.env.LOG_SERVICE_NAME || process.env.K_SERVICE || 'modtale-frontend',
    component: process.env.LOG_COMPONENT || 'frontend',
    environment: process.env.LOG_ENVIRONMENT || process.env.NODE_ENV || 'local',
    branch: process.env.LOG_BRANCH,
    revision: process.env.K_REVISION,
};

const withoutEmptyValues = (entry) => Object.fromEntries(
    Object.entries(entry).filter(([, value]) => value !== undefined && value !== null && value !== '')
);

const serializeError = (error) => {
    if (!(error instanceof Error)) return { message: String(error) };
    return withoutEmptyValues({
        name: error.name,
        message: error.message,
        stack: error.stack,
    });
};

const writeLog = (severity, message, fields = {}) => {
    const entry = withoutEmptyValues({
        severity,
        message,
        ...logContext,
        ...fields,
    });
    const line = JSON.stringify(entry);
    if (severity === 'ERROR' || severity === 'CRITICAL') {
        console.error(line);
    } else {
        console.log(line);
    }
};

const mimeTypes = new Map([
    ['.css', 'text/css; charset=utf-8'],
    ['.gif', 'image/gif'],
    ['.html', 'text/html; charset=utf-8'],
    ['.ico', 'image/x-icon'],
    ['.jpg', 'image/jpeg'],
    ['.jpeg', 'image/jpeg'],
    ['.js', 'text/javascript; charset=utf-8'],
    ['.json', 'application/json; charset=utf-8'],
    ['.map', 'application/json; charset=utf-8'],
    ['.png', 'image/png'],
    ['.svg', 'image/svg+xml; charset=utf-8'],
    ['.txt', 'text/plain; charset=utf-8'],
    ['.webp', 'image/webp'],
    ['.woff2', 'font/woff2'],
    ['.xml', 'application/xml; charset=utf-8'],
]);

const compressibleTypes = [
    'application/javascript',
    'application/json',
    'application/ld+json',
    'application/xml',
    'image/svg+xml',
    'text/css',
    'text/html',
    'text/javascript',
    'text/plain',
    'text/xml',
];

const getContentType = (filePath) => mimeTypes.get(path.extname(filePath).toLowerCase()) || 'application/octet-stream';

const inlinedStylesheetCache = new Map();

const isCompressible = (contentType) => {
    const normalized = String(contentType || '').split(';', 1)[0].trim().toLowerCase();
    return compressibleTypes.includes(normalized);
};

const isHtmlContent = (contentType) => String(contentType || '').split(';', 1)[0].trim().toLowerCase() === 'text/html';

const getStylesheetForInlining = (href) => {
    let pathname;
    try {
        pathname = decodeURIComponent(new URL(href, 'http://localhost').pathname);
    } catch {
        return null;
    }

    const filePath = path.resolve(clientRoot, path.normalize(`.${pathname}`));
    if (!filePath.startsWith(clientRoot + path.sep) || path.extname(filePath).toLowerCase() !== '.css') {
        return null;
    }

    if (!inlinedStylesheetCache.has(filePath)) {
        inlinedStylesheetCache.set(filePath, fs.readFileSync(filePath, 'utf8'));
    }

    return inlinedStylesheetCache.get(filePath);
};

const getPriorityFontPreloads = (stylesheet) => {
    const fontUrls = new Set();
    const fontUrlPattern = /url\((['"]?)(\/_astro\/inter-latin-wght-normal\.[^)'" ]+\.woff2)\1\)/g;
    let match;

    while ((match = fontUrlPattern.exec(stylesheet)) !== null) {
        fontUrls.add(match[2]);
    }

    return Array.from(fontUrls)
        .map((href) => `<link rel="preload" as="font" type="font/woff2" href="${href}" crossorigin>`)
        .join('');
};

const inlineStylesheets = (body) => {
    const html = body.toString('utf8');
    let replaced = false;
    const earlyHeadAssets = [];
    const nextHtml = html.replace(/<link\s+rel="stylesheet"\s+href="([^"]+\.css)"\s*>/g, (tag, href) => {
        const stylesheet = getStylesheetForInlining(href);
        if (!stylesheet) return tag;

        replaced = true;
        earlyHeadAssets.push(
            `${getPriorityFontPreloads(stylesheet)}<style data-inlined-stylesheet="${href}">${stylesheet.replace(/<\/style/gi, '<\\/style')}</style>`
        );
        return '';
    });

    if (!replaced) return body;

    const assetsHtml = earlyHeadAssets.join('');
    const withEarlyAssets = nextHtml.includes('<meta name="viewport"')
        ? nextHtml.replace(/(<meta name="viewport"[^>]*>)/, `$1${assetsHtml}`)
        : nextHtml.replace('</head>', `${assetsHtml}</head>`);

    return Buffer.from(withEarlyAssets, 'utf8');
};

const chooseEncoding = (req) => {
    const acceptEncoding = String(req.headers['accept-encoding'] || '');
    if (/\bbr\b/.test(acceptEncoding)) return 'br';
    if (/\bgzip\b/.test(acceptEncoding)) return 'gzip';
    return null;
};

const appendVaryAcceptEncoding = (res) => {
    const current = res.getHeader('Vary');
    if (!current) {
        res.setHeader('Vary', 'Accept-Encoding');
        return;
    }

    const values = Array.isArray(current) ? current.join(',') : String(current);
    if (!values.toLowerCase().split(',').map((value) => value.trim()).includes('accept-encoding')) {
        res.setHeader('Vary', `${values}, Accept-Encoding`);
    }
};

const compressBody = (body, encoding) => {
    if (encoding === 'br') {
        return zlib.brotliCompressSync(body, {
            params: {
                [zlib.constants.BROTLI_PARAM_QUALITY]: 5,
            },
        });
    }

    return zlib.gzipSync(body, { level: 6 });
};

const sendStaticFile = (req, res, filePath, pathname, stat) => {
    const contentType = getContentType(filePath);
    const encoding = req.method !== 'HEAD' && isCompressible(contentType) ? chooseEncoding(req) : null;
    const body = req.method === 'HEAD' ? Buffer.alloc(0) : fs.readFileSync(filePath);
    const responseBody = encoding ? compressBody(body, encoding) : body;

    res.statusCode = 200;
    res.setHeader('Content-Type', contentType);
    res.setHeader('Last-Modified', stat.mtime.toUTCString());
    res.setHeader('X-Content-Type-Options', 'nosniff');
    res.setHeader(
        'Cache-Control',
        pathname.startsWith('/_astro/')
            ? 'public, max-age=31536000, immutable'
            : 'public, max-age=0, must-revalidate'
    );

    if (encoding) {
        res.setHeader('Content-Encoding', encoding);
        appendVaryAcceptEncoding(res);
    }

    res.setHeader('Content-Length', responseBody.length);
    res.end(responseBody);
};

const tryServeStatic = (req, res) => {
    if (!['GET', 'HEAD'].includes(req.method || 'GET')) return false;

    let pathname;
    try {
        const url = new URL(req.url || '/', `http://${req.headers.host || 'localhost'}`);
        pathname = decodeURIComponent(url.pathname);
    } catch {
        res.writeHead(400);
        res.end('Bad request.');
        return true;
    }

    const normalizedPath = path.normalize(`.${pathname}`);
    const filePath = path.resolve(clientRoot, normalizedPath);
    if (!filePath.startsWith(clientRoot + path.sep) && filePath !== clientRoot) return false;

    try {
        const stat = fs.statSync(filePath);
        if (!stat.isFile()) return false;
        sendStaticFile(req, res, filePath, pathname, stat);
        return true;
    } catch {
        return false;
    }
};

const sendBufferedResponse = (req, res, original, chunks, callback) => {
    try {
        let body = Buffer.concat(chunks);
        const statusCode = res.statusCode || 200;
        const contentType = res.getHeader('Content-Type');
        const canHaveBody = req.method !== 'HEAD' && statusCode !== 204 && statusCode !== 304;
        if (canHaveBody && isHtmlContent(contentType) && body.includes('home-hero')) {
            body = inlineStylesheets(body);
        }

        const encoding = canHaveBody && body.length > 1024 && !res.getHeader('Content-Encoding') && isCompressible(contentType)
            ? chooseEncoding(req)
            : null;

        if (encoding) {
            body = compressBody(body, encoding);
            res.setHeader('Content-Encoding', encoding);
            appendVaryAcceptEncoding(res);
            res.removeHeader('Content-Length');
        }

        if (canHaveBody) {
            res.setHeader('Content-Length', body.length);
        } else {
            body = Buffer.alloc(0);
            res.removeHeader('Content-Length');
        }

        original.writeHead.call(res, statusCode);
        original.end.call(res, body, callback);
    } catch (error) {
        original.writeHead.call(res, 500, { 'Content-Type': 'text/plain; charset=utf-8' });
        original.end.call(res, 'Internal Server Error', callback);
        writeLog('ERROR', 'Failed to buffer frontend response.', { error: serializeError(error) });
    }
};

const withCompression = async (req, res) => {
    const original = {
        write: res.write,
        end: res.end,
        writeHead: res.writeHead,
    };
    const chunks = [];

    res.writeHead = function writeHead(statusCode, reasonOrHeaders, headers) {
        this.statusCode = statusCode;

        const headerMap = typeof reasonOrHeaders === 'string' ? headers : reasonOrHeaders;
        if (headerMap) {
            for (const [key, value] of Object.entries(headerMap)) {
                this.setHeader(key, value);
            }
        }

        return this;
    };

    res.write = function write(chunk, encoding, callback) {
        if (chunk) {
            chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk, encoding));
        }

        if (typeof callback === 'function') callback();
        return true;
    };

    res.end = function end(chunk, encoding, callback) {
        if (chunk) {
            chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk, encoding));
        }

        const done = typeof encoding === 'function' ? encoding : callback;
        sendBufferedResponse(req, res, original, chunks, done);
        return res;
    };

    await handler(req, res, () => {
        if (!res.writableEnded) {
            res.statusCode = 404;
            res.setHeader('Content-Type', 'text/plain; charset=utf-8');
            res.end('Not found');
        }
    });
};

const server = http.createServer(async (req, res) => {
    try {
        if (tryServeStatic(req, res)) return;
        await withCompression(req, res);
    } catch (error) {
        if (!res.headersSent) {
            res.writeHead(500, { 'Content-Type': 'text/plain; charset=utf-8' });
        }
        res.end('Internal Server Error');
        writeLog('ERROR', 'Unhandled frontend request failure.', { error: serializeError(error) });
    }
});

server.listen(port, host, () => {
    const displayHost = host === '0.0.0.0' ? 'localhost' : host;
    writeLog('INFO', 'Frontend server listening.', { host: displayHost, port });
});
