import React from 'react';
import { act } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';

import { PostDownloadModal } from '@/modules/project/components/dialogs/PostDownloadModal';

describe('PostDownloadModal', () => {
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
        document.body.style.overflow = '';
    });

    it('links users to the launcher download page', async () => {
        await act(async () => {
            root.render(
                <MemoryRouter>
                    <PostDownloadModal
                        isOpen={true}
                        onClose={vi.fn()}
                        classification="PLUGIN"
                        title="Skyforge"
                    />
                </MemoryRouter>
            );
        });

        expect(document.body.textContent).toContain('You can install mods automatically using the Modtale Launcher.');

        const launcherLink = document.body.querySelector('a[aria-label="Download Modtale Launcher"]');

        expect(launcherLink?.getAttribute('href')).toBe('/launcher');
    });

    it('routes prefab-tagged worlds through asset-pack and Paste Tool guidance', async () => {
        await act(async () => {
            root.render(
                <MemoryRouter>
                    <PostDownloadModal
                        isOpen
                        onClose={() => undefined}
                        classification="SAVE"
                        title="Castle Prefab"
                        tags={['Prefab', 'Structure']}
                    />
                </MemoryRouter>
            );
        });

        expect(document.body.textContent).toContain('inside a Hytale asset pack in your Mods directory');
        expect(document.body.textContent).toContain('load the prefab from the Prefab List');
        expect(document.body.textContent).toContain('use the Paste Tool to place it');
        expect(document.body.textContent).not.toContain('select the world from the Singleplayer menu');
    });

    it('keeps normal world-save installation guidance unchanged', async () => {
        await act(async () => {
            root.render(
                <MemoryRouter>
                    <PostDownloadModal
                        isOpen
                        onClose={() => undefined}
                        classification="SAVE"
                        title="Adventure World"
                        tags={['Adventure Map']}
                    />
                </MemoryRouter>
            );
        });

        expect(document.body.textContent).toContain('Hytale Saves directory');
        expect(document.body.textContent).toContain('select the world from the Singleplayer menu');
    });
});
