export const getSteppedWidth = (width: number) => {
    if (width <= 64) return 64;
    if (width <= 128) return 128;
    if (width <= 256) return 256;
    if (width <= 640) return 640;
    if (width <= 1280) return 1280;
    return 1920;
};

const isLocalEnvironment = () => {
    if (typeof window === 'undefined') return false;
    const hostname = window.location.hostname;
    return hostname === 'localhost' || hostname === '127.0.0.1' || hostname.startsWith('192.168.');
};

export const getCloudflareUrl = (url: string, width: number, quality: number) => {
    if (!url || url.includes('.svg') || url.startsWith('blob:')) {
        return url;
    }

    if (isLocalEnvironment()) {
        return url;
    }

    const steppedWidth = getSteppedWidth(width);
    const isAbsolute = url.startsWith('http');

    let absoluteUrl = url;
    if (!isAbsolute) {
        const origin = typeof window !== 'undefined' ? window.location.origin : 'https://modtale.net';
        absoluteUrl = `${origin}${url.startsWith('/') ? '' : '/'}${url}`;
    }

    const origin = typeof window !== 'undefined' ? window.location.origin : '';

    return `${origin}/cdn-cgi/image/width=${steppedWidth},quality=${quality},format=auto,onerror=redirect/${absoluteUrl}`;
};