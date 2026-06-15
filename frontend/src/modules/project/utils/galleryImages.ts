import type { GalleryImage } from '@/types';

export type GalleryImageInput = string | GalleryImage;

export interface ResolvedGalleryImage {
    url: string;
    caption: string;
}

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
        .map((image) => {
            const url = getGalleryImageUrl(image);
            return url ? { url, caption: getGalleryImageCaption(image, captions) } : null;
        })
        .filter((image): image is ResolvedGalleryImage => Boolean(image))
);
