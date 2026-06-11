import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createImage, getCroppedImg } from '@/utils/canvasUtils';

type ImageListener = (event: Event) => void;

class MockImage {
    width = 640;
    height = 320;
    private listeners: Record<string, ImageListener[]> = {};
    public crossOrigin: string | null = null;

    addEventListener(type: string, listener: ImageListener) {
        this.listeners[type] ??= [];
        this.listeners[type].push(listener);
    }

    setAttribute(name: string, value: string) {
        if (name === 'crossOrigin') {
            this.crossOrigin = value;
        }
    }

    set src(value: string) {
        const eventType = value.includes('fail') ? 'error' : 'load';
        queueMicrotask(() => {
            for (const listener of this.listeners[eventType] ?? []) {
                listener(new Event(eventType));
            }
        });
    }
}

describe('canvas utils', () => {
    const originalImage = globalThis.Image;

    beforeEach(() => {
        Object.defineProperty(globalThis, 'Image', {
            configurable: true,
            value: MockImage
        });
    });

    afterEach(() => {
        vi.restoreAllMocks();
        Object.defineProperty(globalThis, 'Image', {
            configurable: true,
            value: originalImage
        });
    });

    it('creates images with cross-origin enabled', async () => {
        const image = await createImage('https://example.com/icon.png') as unknown as MockImage;

        expect(image).toBeInstanceOf(MockImage);
        expect(image.crossOrigin).toBe('anonymous');
    });

    it('rejects when the image cannot be loaded', async () => {
        await expect(createImage('https://example.com/fail.png')).rejects.toBeInstanceOf(Event);
    });

    it('crops an image into a png file using the requested canvas transforms', async () => {
        const context = {
            clearRect: vi.fn(),
            translate: vi.fn(),
            scale: vi.fn(),
            drawImage: vi.fn()
        };
        const canvas = {
            width: 0,
            height: 0,
            getContext: vi.fn(() => context),
            toBlob: vi.fn((callback: BlobCallback) => {
                callback(new Blob(['png-bytes'], { type: 'image/png' }));
            })
        };
        const originalCreateElement = document.createElement.bind(document);

        vi.spyOn(document, 'createElement').mockImplementation(((tagName: string) => {
            if (tagName === 'canvas') {
                return canvas as unknown as HTMLCanvasElement;
            }
            return originalCreateElement(tagName);
        }) as typeof document.createElement);

        const file = await getCroppedImg(
            'https://example.com/icon.png',
            { x: 12, y: -8, zoom: 1.5 },
            { width: 320, height: 180 },
            'cover.png'
        );

        expect(file.name).toBe('cover.png');
        expect(file.type).toBe('image/png');
        expect(canvas.width).toBe(320);
        expect(canvas.height).toBe(180);
        expect(context.clearRect).toHaveBeenCalledWith(0, 0, 320, 180);
        expect(context.translate).toHaveBeenNthCalledWith(1, 160, 90);
        expect(context.translate).toHaveBeenNthCalledWith(2, 12, -8);
        expect(context.scale).toHaveBeenCalledWith(1.5, 1.5);
        expect(context.drawImage).toHaveBeenCalledWith(expect.any(MockImage), -320, -160);
    });

    it('throws when no 2d context is available', async () => {
        const canvas = {
            getContext: vi.fn(() => null)
        };
        const originalCreateElement = document.createElement.bind(document);

        vi.spyOn(document, 'createElement').mockImplementation(((tagName: string) => {
            if (tagName === 'canvas') {
                return canvas as unknown as HTMLCanvasElement;
            }
            return originalCreateElement(tagName);
        }) as typeof document.createElement);

        await expect(
            getCroppedImg('https://example.com/icon.png', { x: 0, y: 0, zoom: 1 }, { width: 100, height: 100 }, 'icon.png')
        ).rejects.toThrow('No 2d context');
    });

    it('rejects when the canvas cannot produce a blob', async () => {
        const canvas = {
            width: 0,
            height: 0,
            getContext: vi.fn(() => ({
                clearRect: vi.fn(),
                translate: vi.fn(),
                scale: vi.fn(),
                drawImage: vi.fn()
            })),
            toBlob: vi.fn((callback: BlobCallback) => callback(null))
        };
        const originalCreateElement = document.createElement.bind(document);

        vi.spyOn(document, 'createElement').mockImplementation(((tagName: string) => {
            if (tagName === 'canvas') {
                return canvas as unknown as HTMLCanvasElement;
            }
            return originalCreateElement(tagName);
        }) as typeof document.createElement);

        await expect(
            getCroppedImg('https://example.com/icon.png', { x: 0, y: 0, zoom: 1 }, { width: 100, height: 100 }, 'icon.png')
        ).rejects.toThrow('Canvas is empty');
    });
});
