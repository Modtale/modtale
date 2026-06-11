import { act } from 'react';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { createRoot, type Root } from 'react-dom/client';
import { MarkdownRichRenderer } from '@/components/ui/MarkdownRichRenderer';

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
});
