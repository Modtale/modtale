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

    const isLocal = isLocalEnvironment();
    const cloudflareOrigin = 'https://modtale.net';
    let canUseCloudflareProxy = !isLocal;

    if (url.startsWith('http')) {
        try {
            const srcHost = new URL(url).hostname;
            const isFirstPartyCdn = srcHost === 'cdn.modtale.net';

            if (typeof window !== 'undefined') {
                const appHost = window.location.hostname;
                const isSameHost = srcHost === appHost;
                const isSubdomainOfAppHost = srcHost.endsWith(`.${appHost}`);

                if (!isSameHost && !isSubdomainOfAppHost && !isFirstPartyCdn) return url;
                canUseCloudflareProxy = !isLocal || isFirstPartyCdn;
            } else {
                canUseCloudflareProxy = isFirstPartyCdn;
            }
        } catch {
            return url;
        }
    } else if (isLocal) {
        return url;
    }

    if (!canUseCloudflareProxy) {
        return url;
    }

    const steppedWidth = getSteppedWidth(width);
    let absoluteUrl = url;
    if (!url.startsWith('http')) {
        const origin = typeof window !== 'undefined' ? window.location.origin : 'https://modtale.net';
        absoluteUrl = `${origin}${url.startsWith('/') ? '' : '/'}${url}`;
    }

    const origin = typeof window !== 'undefined' && !isLocal
        ? window.location.origin
        : cloudflareOrigin;

    return `${origin}/cdn-cgi/image/width=${steppedWidth},quality=${quality},format=auto,onerror=redirect/${absoluteUrl}`;
};
