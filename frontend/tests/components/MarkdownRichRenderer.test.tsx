import { act } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createRoot, type Root } from 'react-dom/client';
import { MarkdownRichRenderer } from '@/components/ui/MarkdownRichRenderer';

vi.mock('@/components/ui/MarkdownSyntaxHighlighter', () => ({
    HighlightedCode: ({ content }: { content: string }) => <pre data-testid="highlighted-code">{content}</pre>
}));

describe('MarkdownRichRenderer', () => {
    let container: HTMLDivElement;
    let root: Root;

    beforeEach(() => {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
    });

    afterEach(async () => {
        await act(async () => {
            root.unmount();
        });
        container.remove();
    });

    it('preserves text-align center from inline styles', async () => {
        await act(async () => {
            root.render(<MarkdownRichRenderer content={'<p style="text-align: center;">Centered text</p>'} />);
        });

        const paragraph = container.querySelector('p');
        expect(paragraph).not.toBeNull();
        expect(paragraph?.style.textAlign).toBe('center');
        expect(paragraph?.textContent).toBe('Centered text');
    });

    it('does not preserve unrelated inline styles', async () => {
        await act(async () => {
            root.render(<MarkdownRichRenderer content={'<p style="color: red; text-align: center;">Centered text</p>'} />);
        });

        const paragraph = container.querySelector('p');
        expect(paragraph).not.toBeNull();
        expect(paragraph?.style.textAlign).toBe('center');
        expect(paragraph?.style.color).toBe('');
    });

    it('renders youtube iframes with a canonical nocookie embed source', async () => {
        await act(async () => {
            root.render(<MarkdownRichRenderer content={'<iframe src="https://www.youtube.com/watch?v=dQw4w9WgXcQ" title="Launch trailer"></iframe>'} />);
        });

        const iframe = container.querySelector('iframe');
        expect(iframe).not.toBeNull();
        expect(iframe?.getAttribute('src')).toBe('https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ');
        expect(iframe?.getAttribute('title')).toBe('Launch trailer');
        expect(iframe?.getAttribute('allow')).toContain('picture-in-picture');
    });

    it('removes non-youtube iframes', async () => {
        await act(async () => {
            root.render(<MarkdownRichRenderer content={'<iframe src="https://example.com/embed/unsafe"></iframe>'} />);
        });

        expect(container.querySelector('iframe')).toBeNull();
    });

    it('renders markdown images for externally hosted gallery media', async () => {
        await act(async () => {
            root.render(<MarkdownRichRenderer content={'![Gallery build](https://cdn.modtale.net/gallery/build.png)'} />);
        });

        const image = container.querySelector('img[alt="Gallery build"]') as HTMLImageElement | null;
        expect(image).not.toBeNull();
        expect(image?.getAttribute('src')).toBe('https://cdn.modtale.net/gallery/build.png');
    });

    it('copies markdown code block contents', async () => {
        const originalClipboard = navigator.clipboard;
        const writeText = vi.fn().mockResolvedValue(undefined);
        Object.defineProperty(navigator, 'clipboard', {
            configurable: true,
            value: { writeText }
        });

        try {
            await act(async () => {
                root.render(<MarkdownRichRenderer content={'```json\n{"ok": true}\n```'} />);
            });

            const copyButton = container.querySelector('button[title="Copy code"]') as HTMLButtonElement | null;
            expect(copyButton).not.toBeNull();

            await act(async () => {
                copyButton?.click();
            });

            expect(writeText).toHaveBeenCalledWith('{"ok": true}');
            expect(copyButton?.textContent).toContain('Copied');
        } finally {
            Object.defineProperty(navigator, 'clipboard', {
                configurable: true,
                value: originalClipboard
            });
        }
    });
});
