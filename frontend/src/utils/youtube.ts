const YOUTUBE_VIDEO_ID_PATTERN = /^[A-Za-z0-9_-]{11}$/;

const firstPathSegment = (pathname: string) => pathname.replace(/^\/+/, '').split('/')[0] || '';

export const getYouTubeVideoId = (url: string | null | undefined) => {
    if (!url) return null;

    try {
        const parsed = new URL(url);
        if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') return null;

        let host = parsed.hostname.toLowerCase();
        if (host.startsWith('www.')) host = host.slice(4);
        if (host.startsWith('m.')) host = host.slice(2);

        let candidate = '';
        if (host === 'youtu.be') {
            candidate = firstPathSegment(parsed.pathname);
        } else if (host === 'youtube.com' || host === 'youtube-nocookie.com') {
            if (parsed.pathname === '/watch') {
                candidate = parsed.searchParams.get('v') || '';
            } else if (
                parsed.pathname.startsWith('/embed/')
                || parsed.pathname.startsWith('/shorts/')
                || parsed.pathname.startsWith('/live/')
            ) {
                candidate = firstPathSegment(parsed.pathname.replace(/^\/(embed|shorts|live)\//, ''));
            }
        }

        return YOUTUBE_VIDEO_ID_PATTERN.test(candidate) ? candidate : null;
    } catch {
        return null;
    }
};

export const getYouTubeEmbedUrl = (videoId: string) => `https://www.youtube-nocookie.com/embed/${videoId}`;
export const getYouTubeThumbnailUrl = (videoId: string) => `https://i.ytimg.com/vi/${videoId}/hqdefault.jpg`;
