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
    const button = (text: string) => Array.from(container.querySelectorAll('button')).find(button => button.getAttribute('aria-label') === text || button.textContent === text)!;
    return { container, button, cleanup: async () => { await act(async () => root.unmount()); container.remove(); } };
}

it('opens a mod config modal from its row, saves per mod, and guards unsaved changes', async () => {
    const { container, button, cleanup } = await mount();
    try {
        expect(container.querySelector('.llp-toolbar')).toBeNull();
        expect(container.textContent).not.toContain('World settings');
        button('Configure Map').focus();
        await act(async () => button('Configure Map').click());
        const dialog = () => container.querySelector('[role=dialog]')!;
        const toggle = () => dialog().querySelector<HTMLInputElement>('[role=switch]')!;
        expect(dialog().textContent).toContain('Map');
        expect(document.activeElement).toBe(dialog());
        expect(container.querySelector('.llp-workspace')?.hasAttribute('inert')).toBe(true);
        expect(container.textContent).toContain('2 of 3 mods enabled');
        await act(async () => toggle().click());
        await act(async () => button('Done').click());
        expect(dialog().textContent).toContain('Save or reset your changes before closing.');
        await act(async () => button('Save changes').click());
        await act(async () => button('Done').click());
        expect(dialog()).toBeNull();
        expect(document.activeElement).toBe(button('Configure Map'));
        await act(async () => button('Configure Tools').click());
        expect(toggle().checked).toBe(true);
        await act(async () => button('Done').click());
        await act(async () => button('Configure Map').click());
        expect(toggle().checked).toBe(false);
        const number = dialog().querySelector<HTMLInputElement>('input[type=number]')!;
        await act(async () => {
            Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!.call(number, '-1');
            number.dispatchEvent(new Event('input', { bubbles: true }));
        });
        expect(number.getAttribute('aria-invalid')).toBe('true');
        expect(button('Save changes').disabled).toBe(true);
        await act(async () => button('Reset changes').click());
        expect(number.value).toBe('5');
        await act(async () => dialog().dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true })));
        expect(dialog()).toBeNull();
    } finally { await cleanup(); }
});

it('updates directly on a mod row without toggling the mod, and keeps progress across worlds', async () => {
    vi.useFakeTimers();
    const { container, button, cleanup } = await mount();
    try {
        await act(async () => button('Update Map').click());
        expect(container.textContent).toContain('2 of 3 mods enabled');
        await act(async () => vi.advanceTimersByTime(440));
        expect(container.querySelector('[role=progressbar]')?.getAttribute('aria-valuenow')).toBe('40');
        expect(button('Updating Map').disabled).toBe(true);
        await act(async () => button('Greenhaven').click());
        await act(async () => vi.advanceTimersByTime(660));
        expect(button('Map updated').disabled).toBe(true);
        expect(container.querySelector('[role=progressbar]')).toBeNull();
        await act(async () => button('Emberwild').click());
        expect(button('Map updated').disabled).toBe(true);
        expect(button('Update Tools').disabled).toBe(false);
        expect(container.textContent).toContain('2 of 3 mods enabled');
    } finally { await cleanup(); vi.useRealTimers(); }
});
