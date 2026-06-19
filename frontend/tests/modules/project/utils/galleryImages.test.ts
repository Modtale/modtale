import { describe, expect, it } from 'vitest';

import { getGalleryEmbedSnippet, getYouTubeVideoId, resolveGalleryImages } from '@/modules/project/utils/galleryImages';

describe('galleryImages utilities', () => {
    it('extracts youtube video ids from supported youtube url formats', () => {
        expect(getYouTubeVideoId('https://youtu.be/dQw4w9WgXcQ?si=test')).toBe('dQw4w9WgXcQ');
        expect(getYouTubeVideoId('https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=12')).toBe('dQw4w9WgXcQ');
        expect(getYouTubeVideoId('https://www.youtube.com/embed/dQw4w9WgXcQ')).toBe('dQw4w9WgXcQ');
        expect(getYouTubeVideoId('https://m.youtube.com/shorts/dQw4w9WgXcQ')).toBe('dQw4w9WgXcQ');
    });

    it('rejects non-youtube urls', () => {
        expect(getYouTubeVideoId('https://example.com/watch?v=dQw4w9WgXcQ')).toBeNull();
        expect(getYouTubeVideoId('not a url')).toBeNull();
    });

    it('resolves youtube gallery entries with embed and thumbnail metadata', () => {
        const [video] = resolveGalleryImages(
            ['https://www.youtube.com/watch?v=dQw4w9WgXcQ'],
            { 'https://www.youtube.com/watch?v=dQw4w9WgXcQ': 'Launch trailer' }
        );

        expect(video).toEqual({
            url: 'https://www.youtube.com/watch?v=dQw4w9WgXcQ',
            caption: 'Launch trailer',
            type: 'youtube',
            youtubeVideoId: 'dQw4w9WgXcQ',
            embedUrl: 'https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ',
            thumbnailUrl: 'https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg'
        });
    });

    it('builds markdown snippets for gallery image embeds', () => {
        const [image] = resolveGalleryImages(
            ['https://cdn.modtale.net/gallery/build one).png'],
            { 'https://cdn.modtale.net/gallery/build one).png': 'Build [overview]' }
        );

        expect(getGalleryEmbedSnippet(image)).toBe('![Build \\[overview\\]](https://cdn.modtale.net/gallery/build%20one%29.png)');
    });

    it('builds iframe snippets for youtube gallery embeds', () => {
        const [video] = resolveGalleryImages(
            ['https://youtu.be/dQw4w9WgXcQ'],
            { 'https://youtu.be/dQw4w9WgXcQ': 'Launch "Trailer"' }
        );

        expect(getGalleryEmbedSnippet(video)).toBe('<iframe src="https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ" title="Launch &quot;Trailer&quot;" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen></iframe>');
    });
});
