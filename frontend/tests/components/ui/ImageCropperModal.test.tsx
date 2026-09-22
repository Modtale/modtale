import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { describe, expect, it, vi } from 'vitest';
import { ImageCropperModal } from '@/components/ui/ImageCropperModal';

vi.mock('react-easy-crop', () => ({ default: () => <div data-testid="cropper" /> }));

describe('animated image uploads', () => {
    it.each([
        ['animation.gif', 'image/gif'],
        ['animation.GIF', ''],
        ['animation', 'image/gif'],
    ])('passes %s through unchanged without rasterizing it', async (name, type) => {
        const file = new File(['GIF89a-original-frames'], name, { type });
        const onCropComplete = vi.fn();
        const host = document.createElement('div');
        const root = createRoot(host);
        try {
            await act(async () => root.render(<ImageCropperModal imageSrc="blob:animation" sourceFile={file} aspect={3} onCancel={() => {}} onCropComplete={onCropComplete} />));
            expect(document.querySelector('[data-testid="cropper"]')).toBeNull();
            expect(document.querySelector('img[alt="Animated image preview"]')?.getAttribute('src')).toBe('blob:animation');
            const save = [...document.querySelectorAll('button')].find(button => button.textContent?.includes('Use GIF'))!;
            await act(async () => save.click());
            expect(onCropComplete).toHaveBeenCalledOnce();
            const uploaded = onCropComplete.mock.calls[0][0] as File;
            expect(uploaded.name).toMatch(/\.gif$/i);
            expect(uploaded.size).toBe(file.size);
            if (/\.gif$/i.test(name)) expect(uploaded).toBe(file);
        } finally {
            await act(async () => root.unmount());
        }
    });

    it('retains cropping controls for still images', async () => {
        const root = createRoot(document.createElement('div'));
        try {
            await act(async () => root.render(<ImageCropperModal imageSrc="blob:photo" sourceFile={new File([], 'photo.png', { type: 'image/png' })} aspect={1} onCancel={() => {}} onCropComplete={() => {}} />));
            expect(document.querySelector('[data-testid="cropper"]')).not.toBeNull();
            expect(document.body.textContent).toContain('Apply Crop');
        } finally {
            await act(async () => root.unmount());
        }
    });
});
