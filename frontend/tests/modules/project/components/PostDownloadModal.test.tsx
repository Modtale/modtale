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
});
