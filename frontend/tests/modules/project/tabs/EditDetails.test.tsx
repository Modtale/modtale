import React from 'react';
import { act } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createRoot, type Root } from 'react-dom/client';

import { EditDetails } from '@/modules/project/tabs/EditDetails';
import { Permission } from '@/modules/permissions/permissions';
import type { MetadataFormData } from '@/modules/project/components/FormShared';
import type { Project } from '@/types';

vi.mock('@/components/ui/MarkdownRenderer', () => ({
    MarkdownRenderer: ({ content }: { content: string }) => <div data-testid="project-description">{content}</div>
}));

vi.mock('@/modules/project/components/GalleryCarousel', () => ({
    GalleryCarousel: () => <div data-testid="gallery-carousel">Gallery carousel</div>
}));

const metaData = {
    title: 'Sky Tools',
    slug: 'sky-tools',
    summary: 'Summary',
    description: 'Intro copy\n\n{{gallery-carousel}}\n\nOutro copy',
    tags: [],
    links: {},
    repositoryUrl: '',
    iconFile: null,
    iconPreview: null,
    license: 'MIT'
} satisfies MetadataFormData;

const projectData = {
    id: 'project-1',
    title: 'Sky Tools',
    about: 'Description',
    authorId: 'user-1',
    author: 'Ada',
    imageUrl: '/icon.png',
    description: 'Summary',
    classification: 'PLUGIN',
    tags: [],
    downloadCount: 0,
    favoriteCount: 0,
    updatedAt: '2026-06-01T00:00:00Z',
    galleryImages: ['/one.png'],
    galleryImageCaptions: {}
} as Project;

describe('EditDetails gallery carousel preview', () => {
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

    const renderEditDetails = async (overrides: {
        metaData?: MetadataFormData;
        projectData?: Project;
    } = {}) => {
        await act(async () => {
            root.render(
                <EditDetails
                    metaData={overrides.metaData || metaData}
                    projectData={overrides.projectData || projectData}
                    setMetaData={vi.fn()}
                    readOnly={false}
                    hasProjectPermission={(permission) => permission === Permission.PROJECT_EDIT_METADATA}
                    editorMode="preview"
                    setEditorMode={vi.fn()}
                    markDirty={vi.fn()}
                />
            );
        });
    };

    it('renders the gallery carousel at the description marker in preview mode', async () => {
        await renderEditDetails();

        const gallery = container.querySelector('[data-testid="gallery-carousel"]');
        const descriptions = container.querySelectorAll('[data-testid="project-description"]');

        expect(gallery).not.toBeNull();
        expect(descriptions).toHaveLength(2);
        expect(descriptions[0].textContent).toContain('Intro copy');
        expect(descriptions[1].textContent).toContain('Outro copy');
        expect(descriptions[0].compareDocumentPosition(gallery!) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
        expect(gallery!.compareDocumentPosition(descriptions[1]) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
        expect(container.textContent).toContain('Leave it out');
    });

    it('does not render the carousel in preview mode when there is no marker', async () => {
        await renderEditDetails({
            metaData: {
                ...metaData,
                description: 'Intro copy\n\nOutro copy'
            }
        });

        const descriptionText = Array.from(container.querySelectorAll('[data-testid="project-description"]'))
            .map((node) => node.textContent || '')
            .join('');

        expect(container.textContent).toContain('Intro copy');
        expect(container.textContent).toContain('Outro copy');
        expect(descriptionText).toContain('Intro copy');
        expect(container.querySelector('[data-testid="gallery-carousel"]')).toBeNull();
    });
});
