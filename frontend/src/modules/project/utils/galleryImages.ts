import type { GalleryImage } from '@/types';
import { getYouTubeEmbedUrl, getYouTubeThumbnailUrl, getYouTubeVideoId } from '@/utils/youtube';

export { getYouTubeEmbedUrl, getYouTubeThumbnailUrl, getYouTubeVideoId } from '@/utils/youtube';

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

const YOUTUBE_IFRAME_ALLOW = 'accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share';

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

const collapseSnippetText = (value: string) => value.replace(/\s+/g, ' ').trim();

const escapeMarkdownImageAlt = (value: string) => collapseSnippetText(value)
    .replace(/\\/g, '\\\\')
    .replace(/\[/g, '\\[')
    .replace(/]/g, '\\]');

const escapeMarkdownUrl = (value: string) => value
    .replace(/\s/g, '%20')
    .replace(/\)/g, '%29');

const escapeHtmlAttribute = (value: string) => collapseSnippetText(value)
    .replace(/&/g, '&amp;')
    .replace(/"/g, '&quot;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');

export const getGalleryEmbedSnippet = (
    image: ResolvedGalleryImage,
    fallbackTitle = 'Project gallery media'
) => {
    const label = image.caption || fallbackTitle || (image.type === 'youtube' ? 'Project gallery video' : 'Project gallery image');

    if (image.type === 'youtube' && image.youtubeVideoId) {
        return `<iframe src="${getYouTubeEmbedUrl(image.youtubeVideoId)}" title="${escapeHtmlAttribute(label)}" allow="${YOUTUBE_IFRAME_ALLOW}" allowfullscreen></iframe>`;
    }

    return `![${escapeMarkdownImageAlt(label)}](${escapeMarkdownUrl(image.url)})`;
};
