import fs from 'node:fs';

const videoTypes = new Map([['.mp4', 'video/mp4'], ['.webm', 'video/webm'], ['.ogv', 'video/ogg']]);

export function serveVideo(req, res, filePath, extension, stat) {
    const type = videoTypes.get(extension.toLowerCase());
    if (!type) return false;

    const modified = stat.mtime.toUTCString();
    const etag = `"${stat.size.toString(16)}-${stat.mtimeMs.toString(16)}"`;
    res.setHeader('Content-Type', type);
    res.setHeader('Accept-Ranges', 'bytes');
    res.setHeader('ETag', etag);
    res.setHeader('Last-Modified', modified);
    res.setHeader('Cache-Control', 'public, max-age=0, must-revalidate');
    res.setHeader('X-Content-Type-Options', 'nosniff');

    const noneMatch = req.headers['if-none-match'];
    if (noneMatch
        ? noneMatch.split(',').some(value => value.trim().replace(/^W\//, '') === etag || value.trim() === '*')
        : req.headers['if-modified-since'] && Date.parse(req.headers['if-modified-since']) >= Date.parse(modified)) {
        res.statusCode = 304;
        res.end();
        return true;
    }

    let start = 0;
    let end = stat.size - 1;
    const ifRange = req.headers['if-range'];
    const range = req.method === 'GET' && (!ifRange || ifRange === etag || Date.parse(ifRange) === Date.parse(modified))
        ? req.headers.range : undefined;
    // Ignore malformed/multipart ranges; browsers use a single byte range for media.
    const match = range && /^bytes=(\d*)-(\d*)$/.exec(range);
    if (match && (match[1] || match[2])) {
        start = match[1] ? Number(match[1]) : Math.max(0, stat.size - Number(match[2]));
        end = match[1] && match[2] ? Math.min(Number(match[2]), end) : end;
        if (!Number.isSafeInteger(start) || !Number.isSafeInteger(end) || start > end || start >= stat.size) {
            res.statusCode = 416;
            res.setHeader('Content-Range', `bytes */${stat.size}`);
            res.setHeader('Content-Length', 0);
            res.end();
            return true;
        }
        res.statusCode = 206;
        res.setHeader('Content-Range', `bytes ${start}-${end}/${stat.size}`);
    } else {
        res.statusCode = 200;
    }
    res.setHeader('Content-Length', Math.max(0, end - start + 1));
    if (req.method === 'HEAD' || stat.size === 0) {
        res.end();
        return true;
    }
    const stream = fs.createReadStream(filePath, { start, end });
    res.once('close', () => stream.destroy());
    stream.once('error', error => res.destroy(error));
    stream.pipe(res);
    return true;
}
