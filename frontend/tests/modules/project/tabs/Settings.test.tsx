import { act } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createRoot, type Root } from 'react-dom/client';

import { Settings } from '@/modules/project/tabs/Settings';
import { Permission } from '@/modules/permissions/permissions';
import type { MetadataFormData } from '@/modules/project/components/FormShared';

describe('Project settings gallery carousel option', () => {
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

    it('toggles the single gallery carousel setting in project editor state', async () => {
        const projectData = {
            id: 'project-1',
            title: 'Sky Tools',
            slug: 'sky-tools',
            status: 'DRAFT',
            allowModpacks: true,
            allowComments: true,
            galleryCarouselEnabled: false,
            hmWikiEnabled: false
        } as any;
        const metaData: MetadataFormData = {
            title: 'Sky Tools',
            slug: 'sky-tools',
            summary: 'Summary',
            description: 'Description',
            tags: [],
            links: {},
            repositoryUrl: '',
            iconFile: null,
            iconPreview: null,
            license: 'MIT'
        };
        const setProjectData = vi.fn();
        const markDirty = vi.fn();

        await act(async () => {
            root.render(
                <Settings
                    projectData={projectData}
                    metaData={metaData}
                    setMetaData={vi.fn()}
                    setProjectData={setProjectData}
                    readOnly={false}
                    hasProjectPermission={(permission) => permission === Permission.PROJECT_EDIT_METADATA}
                    slugError={null}
                    handleSlugChange={vi.fn()}
                    getUrlPrefix={() => 'https://modtale.net/project/'}
                    markDirty={markDirty}
                    isLoading={false}
                />
            );
        });

        const heading = Array.from(container.querySelectorAll('h3')).find((node) => node.textContent === 'Gallery Carousel');
        const toggle = heading?.closest('.flex')?.querySelector('button');

        expect(toggle).toBeDefined();
        await act(async () => {
            toggle?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
        });

        expect(markDirty).toHaveBeenCalledTimes(1);
        expect(setProjectData).toHaveBeenCalledTimes(1);
        const updater = setProjectData.mock.calls[0][0];
        expect(updater(projectData).galleryCarouselEnabled).toBe(true);
    });
});
