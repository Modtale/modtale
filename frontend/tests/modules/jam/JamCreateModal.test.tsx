import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { JamCreateModal } from '@/modules/jam/components/JamCreateModal';
import { api } from '@/utils/api';

const input = (element: HTMLInputElement, value: string) => {
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!.call(element, value);
    element.dispatchEvent(new Event('input', { bubbles: true }));
};

describe('jam creation modal', () => {
    let root: Root;
    let container: HTMLDivElement;
    beforeEach(() => {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
    });
    afterEach(async () => { await act(async () => root.unmount()); container.remove(); });

    it('locks the background, supports Escape, and starts each mount with clean fields', async () => {
        const close = vi.fn();
        await act(async () => root.render(<JamCreateModal onClose={close} onCreated={vi.fn()} />));
        expect(document.querySelector('[role="dialog"]')?.getAttribute('aria-modal')).toBe('true');
        expect(document.body.style.overflow).toBe('hidden');
        await act(async () => input(document.querySelector('#jam-title')!, 'My Summer Jam'));
        expect((document.querySelector('#jam-slug') as HTMLInputElement).value).toBe('my-summer-jam');
        await act(async () => document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' })));
        expect(close).toHaveBeenCalledOnce();
        await act(async () => root.render(null));
        expect(document.body.style.overflow).not.toBe('hidden');
        await act(async () => root.render(<JamCreateModal onClose={close} onCreated={vi.fn()} />));
        expect((document.querySelector('#jam-title') as HTMLInputElement).value).toBe('');
    });

    it('keeps a customized URL when the title changes and deduplicates repeated submissions', async () => {
        let resolve!: (value: any) => void;
        const post = vi.spyOn(api, 'post').mockImplementation(() => new Promise(done => { resolve = done; }));
        const created = vi.fn();
        await act(async () => root.render(<JamCreateModal onClose={vi.fn()} onCreated={created} />));
        await act(async () => input(document.querySelector('#jam-title')!, 'My Summer Jam'));
        await act(async () => input(document.querySelector('#jam-slug')!, 'https://modtale.net/jam/Custom-Jam?tab=rules'));
        await act(async () => input(document.querySelector('#jam-title')!, 'Renamed Summer Jam'));
        expect((document.querySelector('#jam-slug') as HTMLInputElement).value).toBe('custom-jam');
        const form = document.querySelector('form')!;
        await act(async () => {
            form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
            form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
        });
        expect(post).toHaveBeenCalledOnce();
        expect(post).toHaveBeenCalledWith('/modjams', expect.objectContaining({ title: 'Renamed Summer Jam', slug: 'custom-jam' }));
        expect((document.querySelector('[aria-label="Close jam creation"]') as HTMLButtonElement).disabled).toBe(true);
        await act(async () => resolve({ data: { id: 'jam-1', slug: 'custom-jam' } }));
        expect(created).toHaveBeenCalledWith({ id: 'jam-1', slug: 'custom-jam' });
    });

    it('shows a failed create inline without losing the fields', async () => {
        vi.spyOn(api, 'post').mockRejectedValue({ response: { data: { message: 'That URL is already taken.' } } });
        await act(async () => root.render(<JamCreateModal onClose={vi.fn()} onCreated={vi.fn()} />));
        await act(async () => input(document.querySelector('#jam-title')!, 'My Summer Jam'));
        await act(async () => document.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })));
        expect(document.querySelector('[role="alert"]')?.textContent).toContain('already taken');
        expect((document.querySelector('#jam-title') as HTMLInputElement).value).toBe('My Summer Jam');
    });
});
