import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { PostDownloadModal } from '@/modules/project/components/dialogs/PostDownloadModal';

describe('PostDownloadModal prefab instructions', () => {
    let container: HTMLDivElement;
    let root: Root;

    beforeEach(() => {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
    });

    afterEach(async () => {
        await act(async () => root.unmount());
        container.remove();
        document.body.style.overflow = '';
    });

    it('routes prefab-tagged worlds through asset-pack and Paste Tool guidance', async () => {
        await act(async () => {
            root.render(
                <PostDownloadModal
                    isOpen
                    onClose={() => undefined}
                    classification="SAVE"
                    title="Castle Prefab"
                    tags={['Prefab', 'Structure']}
                />
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
                <PostDownloadModal
                    isOpen
                    onClose={() => undefined}
                    classification="SAVE"
                    title="Adventure World"
                    tags={['Adventure Map']}
                />
            );
        });

        expect(document.body.textContent).toContain('Hytale Saves directory');
        expect(document.body.textContent).toContain('select the world from the Singleplayer menu');
    });
});
