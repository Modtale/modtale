import React, { act } from 'react';
import { createRoot } from 'react-dom/client';
import { expect, it, vi } from 'vitest';
import { LauncherLibraryPreview } from '@/modules/home/components/LauncherLibraryPreview';
import type { Project } from '@/types';

const projects = ['Map', 'Tools', 'Herbs'].map((title, index) => ({ id: String(index), title, author: 'Creator', classification: 'PLUGIN' })) as Project[];

async function mount() {
    const container = document.createElement('div');
    document.body.append(container);
    const root = createRoot(container);
    await act(async () => root.render(<LauncherLibraryPreview projects={projects} />));
    const button = (text: string, scope: ParentNode = container) => Array.from(scope.querySelectorAll('button')).find(button => button.textContent === text)!;
    return { container, button, cleanup: async () => { await act(async () => root.unmount()); container.remove(); } };
}

it('saves, resets, and validates settings independently for each world across view changes', async () => {
    const { container, button, cleanup } = await mount();
    try {
        await act(async () => button('Config editor').click());
        const editors = container.querySelectorAll('.llp-config');
        const pvp = (index: number) => editors[index].querySelector<HTMLInputElement>('[role=switch]')!;
        await act(async () => pvp(0).click());
        expect(button('Save changes', editors[0]).disabled).toBe(false);
        await act(async () => button('Save changes', editors[0]).click());
        await act(async () => pvp(0).click());
        await act(async () => button('Reset changes', editors[0]).click());
        expect(pvp(0).checked).toBe(true);
        await act(async () => button('Greenhaven', container.querySelector('.llp-world-picker')!).click());
        expect(pvp(1).checked).toBe(false);
        await act(async () => button('Library').click());
        await act(async () => button('Config editor').click());
        await act(async () => button('Emberwild', container.querySelector('.llp-world-picker')!).click());
        expect(pvp(0).checked).toBe(true);
        const loss = editors[0].querySelector<HTMLInputElement>('input[type=number]')!;
        await act(async () => {
            Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!.call(loss, '101');
            loss.dispatchEvent(new Event('input', { bubbles: true }));
        });
        expect(loss.getAttribute('aria-invalid')).toBe('true');
        expect(button('Save changes', editors[0]).disabled).toBe(true);
        await act(async () => button('Reset changes', editors[0]).click());
        expect(loss.value).toBe('');
        const search = editors[0].querySelector<HTMLInputElement>('input:not([type])')!;
        await act(async () => {
            Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!.call(search, 'unknown setting');
            search.dispatchEvent(new Event('input', { bubbles: true }));
        });
        expect(editors[0].textContent).toContain('No matching settings.');
    } finally { await cleanup(); }
});

it('simulates updates without re-queuing installed versions on a subsequent check', async () => {
    vi.useFakeTimers();
    const { container, button, cleanup } = await mount();
    try {
        await act(async () => button('Updates').click());
        const updates = container.querySelector('.llp-updates')!;
        expect(updates.textContent).toContain('2 updates available');
        await act(async () => button('Update', updates).click());
        await act(async () => vi.advanceTimersByTime(440));
        expect(updates.querySelector('[role=progressbar]')?.getAttribute('aria-valuenow')).toBe('40');
        await act(async () => vi.advanceTimersByTime(660));
        expect(updates.textContent).toContain('1 update available');
        expect(button('Updated', updates).disabled).toBe(true);
        await act(async () => button('Update', updates).click());
        await act(async () => vi.advanceTimersByTime(1100));
        expect(updates.textContent).toContain('Your library is up to date.');
        await act(async () => button('Check for updates', updates).click());
        await act(async () => vi.advanceTimersByTime(800));
        expect(updates.textContent).toContain('No updates queued');
        expect(updates.querySelectorAll('.llp-complete')).toHaveLength(2);
    } finally { await cleanup(); vi.useRealTimers(); }
});
