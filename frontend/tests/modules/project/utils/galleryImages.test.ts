import { describe, expect, it } from 'vitest';

import { getYouTubeVideoId, resolveGalleryImages } from '@/modules/project/utils/galleryImages';

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
});
