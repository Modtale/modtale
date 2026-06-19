import { act } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createRoot, type Root } from 'react-dom/client';

import { GalleryCarousel } from '@/modules/project/components/GalleryCarousel';
import { GalleryCarouselViewer } from '@/modules/project/components/GalleryCarouselViewer';

describe('GalleryCarousel', () => {
    let container: HTMLDivElement;
    let root: Root;
    let originalScrollIntoView: typeof HTMLElement.prototype.scrollIntoView;

    beforeEach(() => {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        originalScrollIntoView = HTMLElement.prototype.scrollIntoView;
        HTMLElement.prototype.scrollIntoView = vi.fn();
    });

    afterEach(async () => {
        await act(async () => {
            root.unmount();
        });
        vi.useRealTimers();
        container.remove();
        HTMLElement.prototype.scrollIntoView = originalScrollIntoView;
    });

    it('renders nothing when a project has no gallery images', async () => {
        await act(async () => {
            root.render(<GalleryCarousel images={[]} title="Skyforge" />);
        });

        expect(container.querySelector('[aria-label="Skyforge gallery"]')).toBeNull();
    });

    it('advances through gallery images from the main controls', async () => {
        await act(async () => {
            root.render(<GalleryCarousel images={['/one.png', '/two.png']} title="Skyforge" />);
        });

        expect(container.querySelector('img[alt="Skyforge gallery image 1"]')?.getAttribute('src')).toBe('/one.png');

        const nextButton = container.querySelector('button[aria-label="Next gallery image"]') as HTMLButtonElement;
        await act(async () => {
            nextButton.click();
        });

        expect(container.querySelector('img[alt="Skyforge gallery image 2"]')?.getAttribute('src')).toBe('/two.png');
    });

    it('auto-advances through gallery images on a slow timer', async () => {
        vi.useFakeTimers();

        await act(async () => {
            root.render(<GalleryCarousel images={['/one.png', '/two.png']} title="Skyforge" />);
        });

        expect(container.querySelector('img[alt="Skyforge gallery image 1"]')?.getAttribute('src')).toBe('/one.png');

        await act(async () => {
            vi.advanceTimersByTime(7999);
        });

        expect(container.querySelector('img[alt="Skyforge gallery image 1"]')?.getAttribute('src')).toBe('/one.png');

        await act(async () => {
            vi.advanceTimersByTime(1);
        });

        expect(container.querySelector('img[alt="Skyforge gallery image 2"]')?.getAttribute('src')).toBe('/two.png');
    });

    it('keeps only the main arrows and removes thumbnail rail arrows', async () => {
        await act(async () => {
            root.render(<GalleryCarousel images={['/one.png', '/two.png']} title="Skyforge" />);
        });

        expect(container.querySelector('button[aria-label="Previous gallery image"]')).not.toBeNull();
        expect(container.querySelector('button[aria-label="Next gallery image"]')).not.toBeNull();
        expect(container.querySelector('button[aria-label="Previous gallery thumbnail"]')).toBeNull();
        expect(container.querySelector('button[aria-label="Next gallery thumbnail"]')).toBeNull();
    });

    it('renders captions from the gallery caption map', async () => {
        await act(async () => {
            root.render(<GalleryCarousel images={['/one.png']} captions={{ '/one.png': 'First build screenshot' }} title="Skyforge" />);
        });

        expect(container.textContent).toContain('First build screenshot');
    });

    it('renders youtube gallery entries as embedded videos', async () => {
        await act(async () => {
            root.render(<GalleryCarousel images={['https://www.youtube.com/watch?v=dQw4w9WgXcQ']} title="Skyforge" />);
        });

        const iframe = container.querySelector('iframe[title="Skyforge gallery video 1"]') as HTMLIFrameElement;

        expect(iframe).not.toBeNull();
        expect(iframe.getAttribute('src')).toBe('https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ');
    });

    it('supports a controlled active index for the gallery popup', async () => {
        const onActiveIndexChange = vi.fn();

        await act(async () => {
            root.render(
                <GalleryCarouselViewer
                    images={['/one.png', '/two.png']}
                    title="Skyforge"
                    activeIndex={1}
                    onActiveIndexChange={onActiveIndexChange}
                    autoAdvance={false}
                />
            );
        });

        expect(container.querySelector('img[alt="Skyforge gallery image 2"]')?.getAttribute('src')).toBe('/two.png');

        const previousButton = container.querySelector('button[aria-label="Previous gallery image"]') as HTMLButtonElement;
        await act(async () => {
            previousButton.click();
        });

        expect(onActiveIndexChange).toHaveBeenCalledWith(0);
    });

    it('can invert keyboard direction for the gallery popup without changing controls', async () => {
        const onActiveIndexChange = vi.fn();

        await act(async () => {
            root.render(
                <GalleryCarouselViewer
                    images={['/one.png', '/two.png', '/three.png']}
                    title="Skyforge"
                    activeIndex={1}
                    onActiveIndexChange={onActiveIndexChange}
                    autoAdvance={false}
                    keyboardNavigationDirection="inverted"
                />
            );
        });

        const mediaRegion = container.querySelector('section[aria-label="Skyforge gallery"] > div') as HTMLDivElement;
        await act(async () => {
            mediaRegion.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
        });

        expect(onActiveIndexChange).toHaveBeenCalledWith(0);
    });
});
