import React, { act } from 'react';
import { createRoot } from 'react-dom/client';
import { expect, it } from 'vitest';
import { LauncherLibraryPreview } from '@/modules/home/components/LauncherLibraryPreview';
import type { Project } from '@/types';

it('keeps mod selections independent for each demo world', async () => {
    const container = document.createElement('div');
    document.body.append(container);
    const root = createRoot(container);
    const projects = ['Map', 'Tools', 'Herbs'].map((title, index) => ({ id: String(index), title, author: 'Creator', classification: 'PLUGIN' })) as Project[];
    const checkbox = (name: string) => Array.from(container.querySelectorAll('label')).find(label => label.textContent?.includes(name))!.querySelector('input')!;
    const world = (name: string) => Array.from(container.querySelectorAll('button')).find(button => button.textContent === name)!;
    try {
        await act(async () => root.render(<LauncherLibraryPreview projects={projects} />));
        expect(checkbox('Map').checked).toBe(true);
        expect(checkbox('Herbs').checked).toBe(false);
        await act(async () => checkbox('Herbs').click());
        expect(container.textContent).toContain('3 of 3 mods enabled');
        await act(async () => world('Greenhaven').click());
        expect(checkbox('Map').checked).toBe(false);
        expect(container.textContent).toContain('2 of 3 mods enabled');
        await act(async () => checkbox('Tools').click());
        expect(container.textContent).toContain('1 of 3 mods enabled');
        await act(async () => world('Emberwild').click());
        expect(checkbox('Tools').checked).toBe(true);
        expect(checkbox('Herbs').checked).toBe(true);
        expect(container.querySelector('video')).toBeNull();
    } finally {
        await act(async () => root.unmount());
        container.remove();
    }
});
