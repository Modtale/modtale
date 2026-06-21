import { act, createRef } from 'react';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';

import { ActionBar } from '@/modules/project/components/ActionBar';
import type { Project } from '@/types';

const project = {
    id: 'project-1',
    slug: 'skyforge',
    title: 'Skyforge',
    description: 'A project with a gallery.',
    authorId: 'author-1',
    author: 'Builder',
    imageUrl: '/icon.png',
    classification: 'PLUGIN',
    downloadCount: 42,
    favoriteCount: 7,
    updatedAt: '2026-06-11T12:00:00Z',
    comments: [],
    versions: [{
        id: 'version-1',
        versionNumber: '1.0.0',
        gameVersion: '2026.03.11',
        gameVersions: ['2026.03.11'],
        fileUrl: '/files/skyforge.jar',
        downloadCount: 3,
        releaseDate: '2026-06-01T00:00:00Z'
    }],
    galleryImages: ['/one.png']
} as Project;

describe('ActionBar gallery action', () => {
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

    const renderActionBar = async (showGalleryButton?: boolean) => {
        await act(async () => {
            root.render(
                <MemoryRouter initialEntries={['/mod/skyforge']}>
                    <ActionBar
                        project={project}
                        projectUrl="/mod/skyforge"
                        links={[]}
                        commentsRef={createRef<HTMLDivElement>()}
                        {...(showGalleryButton === undefined ? {} : { showGalleryButton })}
                    />
                </MemoryRouter>
            );
        });
    };

    it('shows the Gallery button by default', async () => {
        await renderActionBar();

        expect(container.textContent).toContain('Gallery');
    });

    it('hides the Gallery button when the project uses the inline carousel', async () => {
        await renderActionBar(false);

        expect(container.textContent).not.toContain('Gallery');
    });
});
