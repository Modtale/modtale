import { act } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createRoot, type Root } from 'react-dom/client';

import { Settings } from '@/modules/project/tabs/Settings';
import { Permission } from '@/modules/permissions/permissions';
import type { MetadataFormData } from '@/modules/project/components/FormShared';

describe('Project settings tab', () => {
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

    it('does not show gallery carousel placement guidance', async () => {
        const projectData = {
            id: 'project-1',
            title: 'Sky Tools',
            slug: 'sky-tools',
            status: 'DRAFT',
            allowModpacks: true,
            allowComments: true,
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
            license: 'MIT',
            customLicenseOpenSource: false
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

        expect(container.textContent).not.toContain('Gallery Carousel');
        expect(container.textContent).not.toContain('{{gallery-carousel}}');
        expect(container.textContent).not.toContain('inline carousel');
        expect(markDirty).not.toHaveBeenCalled();
        expect(setProjectData).not.toHaveBeenCalled();
    });
});
