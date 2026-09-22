import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { DownloadModalSkeleton } from '@/modules/project/components/dialogs/DownloadModal';
import { HistoryModalSkeleton } from '@/modules/project/components/dialogs/HistoryModal';
import { ProjectGallerySkeleton } from '@/modules/project/components/ProjectGallerySkeleton';

const originalRects = Object.getOwnPropertyDescriptor(Range.prototype, 'getClientRects');
const originalScroll = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'scrollIntoView');
let root: Root;
let container: HTMLDivElement;

beforeEach(() => {
    vi.stubGlobal('ResizeObserver', class { observe() {} disconnect() {} });
    Object.defineProperty(Range.prototype, 'getClientRects', { configurable: true, value: () => [] });
    Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', { configurable: true, value: () => {} });
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
});
afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
    if (originalRects) Object.defineProperty(Range.prototype, 'getClientRects', originalRects);
    else Reflect.deleteProperty(Range.prototype, 'getClientRects');
    if (originalScroll) Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', originalScroll);
    else Reflect.deleteProperty(HTMLElement.prototype, 'scrollIntoView');
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
});

describe('Project loading dialog dismissal', () => {
    it.each([
        ['downloads', DownloadModalSkeleton, 'Close downloads'],
        ['history', HistoryModalSkeleton, 'Close Changelog'],
        ['gallery', ProjectGallerySkeleton, 'Close gallery'],
    ] as const)('keeps %s dismissible while content is inert', async (_name, Fixture, closeLabel) => {
        const onClose = vi.fn();
        await act(async () => root.render(<Fixture onClose={onClose} />));
        const close = document.querySelector<HTMLButtonElement>(`button[aria-label="${closeLabel}"]`)!;
        expect(close).not.toBeNull();
        expect(close.closest('[inert], [aria-hidden="true"]')).toBeNull();
        expect(document.querySelector('[aria-busy="true"] [inert]')).not.toBeNull();
        expect(document.body.style.overflow).toBe('hidden');
        await act(async () => close.click());
        expect(onClose).toHaveBeenCalledTimes(1);
        await act(async () => root.unmount());
        expect(document.body.style.overflow).toBe('');
        root = createRoot(container);
    });

    it('keeps the full changelog action usable on the download skeleton', async () => {
        const onViewHistory = vi.fn();
        await act(async () => root.render(<DownloadModalSkeleton onViewHistory={onViewHistory} />));

        const historyButton = Array.from(document.querySelectorAll('button'))
            .find(button => button.textContent?.includes('View Full Changelog')) as HTMLButtonElement;
        await act(async () => historyButton.click());

        expect(onViewHistory).toHaveBeenCalledTimes(1);
    });
});
