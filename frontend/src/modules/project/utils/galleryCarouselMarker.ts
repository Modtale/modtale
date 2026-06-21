export const GALLERY_CAROUSEL_MARKER = '{{gallery-carousel}}';

const GALLERY_CAROUSEL_MARKER_PATTERN = '\\{\\{\\s*gallery-carousel\\s*\\}\\}';

const createGalleryCarouselMarkerRegex = () => new RegExp(GALLERY_CAROUSEL_MARKER_PATTERN, 'gi');

export type GalleryCarouselDescriptionPart =
    | { type: 'markdown'; content: string }
    | { type: 'gallery' };

export const countGalleryCarouselMarkers = (content = '') => (
    content.match(createGalleryCarouselMarkerRegex())?.length || 0
);

export const splitDescriptionByGalleryCarouselMarker = (content = ''): GalleryCarouselDescriptionPart[] => {
    const regex = createGalleryCarouselMarkerRegex();
    const parts: GalleryCarouselDescriptionPart[] = [];
    let lastIndex = 0;
    let galleryInserted = false;
    let match: RegExpExecArray | null;

    while ((match = regex.exec(content)) !== null) {
        const markdown = content.slice(lastIndex, match.index);
        if (markdown.trim()) {
            parts.push({ type: 'markdown', content: markdown });
        }

        if (!galleryInserted) {
            parts.push({ type: 'gallery' });
            galleryInserted = true;
        }

        lastIndex = match.index + match[0].length;
    }

    const remainingMarkdown = content.slice(lastIndex);
    if (remainingMarkdown.trim()) {
        parts.push({ type: 'markdown', content: remainingMarkdown });
    }

    return parts.length ? parts : [{ type: 'markdown', content }];
};
