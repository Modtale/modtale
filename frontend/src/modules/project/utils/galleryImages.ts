import type { GalleryImage } from '@/types';

export type GalleryImageInput = string | GalleryImage;
export type GalleryMediaType = 'image' | 'youtube';

export interface ResolvedGalleryImage {
    url: string;
    caption: string;
    type: GalleryMediaType;
    youtubeVideoId?: string;
    embedUrl?: string;
    thumbnailUrl?: string;
}

const YOUTUBE_VIDEO_ID_PATTERN = /^[A-Za-z0-9_-]{11}$/;

export const getGalleryImageUrl = (image: GalleryImageInput | null | undefined) => {
    if (!image) return '';
    return typeof image === 'string' ? image : image.url || image.imageUrl || '';
};

export const getGalleryImageCaption = (
    image: GalleryImageInput | null | undefined,
    captions: Record<string, string> = {}
) => {
    const url = getGalleryImageUrl(image);
    if (!url) return '';
    if (image && typeof image === 'object' && image.caption) return image.caption;
    return captions[url] || '';
};

const getFirstPathSegment = (pathname: string) => pathname.replace(/^\/+/, '').split('/')[0] || '';

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
            candidate = getFirstPathSegment(parsed.pathname);
        } else if (host === 'youtube.com' || host === 'youtube-nocookie.com') {
            if (parsed.pathname === '/watch') {
                candidate = parsed.searchParams.get('v') || '';
            } else if (
                parsed.pathname.startsWith('/embed/')
                || parsed.pathname.startsWith('/shorts/')
                || parsed.pathname.startsWith('/live/')
            ) {
                candidate = getFirstPathSegment(parsed.pathname.replace(/^\/(embed|shorts|live)\//, ''));
            }
        }

        return YOUTUBE_VIDEO_ID_PATTERN.test(candidate) ? candidate : null;
    } catch {
        return null;
    }
};

export const getYouTubeEmbedUrl = (videoId: string) => `https://www.youtube-nocookie.com/embed/${videoId}`;
export const getYouTubeThumbnailUrl = (videoId: string) => `https://i.ytimg.com/vi/${videoId}/hqdefault.jpg`;

export const resolveGalleryImages = (
    images: GalleryImageInput[] = [],
    captions: Record<string, string> = {}
): ResolvedGalleryImage[] => (
    images
        .map((image): ResolvedGalleryImage | null => {
            const url = getGalleryImageUrl(image);
            if (!url) return null;

            const youtubeVideoId = getYouTubeVideoId(url);
            return {
                url,
                caption: getGalleryImageCaption(image, captions),
                type: youtubeVideoId ? 'youtube' : 'image',
                youtubeVideoId: youtubeVideoId || undefined,
                embedUrl: youtubeVideoId ? getYouTubeEmbedUrl(youtubeVideoId) : undefined,
                thumbnailUrl: youtubeVideoId ? getYouTubeThumbnailUrl(youtubeVideoId) : undefined
            };
        })
        .filter((image): image is ResolvedGalleryImage => Boolean(image))
);
