import React from 'react';
import { act } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createRoot, type Root } from 'react-dom/client';

import { ViewDetails } from '@/modules/project/tabs/ViewDetails';
import type { Project } from '@/types';

vi.mock('@/components/ui/MarkdownRenderer', () => ({
    MarkdownRenderer: ({ content }: { content: string }) => <div data-testid="project-description">{content}</div>
}));

vi.mock('@/modules/project/components/GalleryCarousel', () => ({
    GalleryCarousel: () => <div data-testid="gallery-carousel">Gallery carousel</div>
}));

vi.mock('@/modules/project/components/CommentSection', () => ({
    CommentSection: () => <div data-testid="comment-section" />
}));

const baseProject = {
    id: 'project-1',
    title: 'Sky Tools',
    about: 'Project description',
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

describe('ViewDetails gallery carousel marker', () => {
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

    const renderViewDetails = async (project: Project) => {
        await act(async () => {
            root.render(
                <ViewDetails
                    project={project}
                    currentUser={null}
                    canEdit={false}
                    commentsRef={React.createRef<HTMLDivElement>()}
                    setProject={vi.fn()}
                    setStatusModal={vi.fn()}
                    onRefresh={vi.fn()}
                    depMeta={{}}
                />
            );
        });
    };

    it('does not render the carousel when the description has no marker', async () => {
        await renderViewDetails(baseProject);

        const gallery = container.querySelector('[data-testid="gallery-carousel"], [aria-label="Sky Tools gallery"]');
        const description = container.querySelector('[data-testid="project-description"]');

        expect(gallery).toBeNull();
        expect(description).not.toBeNull();
        expect(description?.textContent).toContain('Project description');
    });

    it('renders the carousel where the description marker is placed', async () => {
        await renderViewDetails({
            ...baseProject,
            about: 'Intro copy\n\n{{gallery-carousel}}\n\nOutro copy'
        });

        const gallery = container.querySelector('[data-testid="gallery-carousel"], [aria-label="Sky Tools gallery"]');
        const descriptions = container.querySelectorAll('[data-testid="project-description"]');

        expect(gallery).not.toBeNull();
        expect(descriptions).toHaveLength(2);
        expect(descriptions[0].textContent).toContain('Intro copy');
        expect(descriptions[1].textContent).toContain('Outro copy');
        expect(descriptions[0].compareDocumentPosition(gallery!) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
        expect(gallery!.compareDocumentPosition(descriptions[1]) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    });

    it('strips the marker text when rendering the inline carousel', async () => {
        await renderViewDetails({
            ...baseProject,
            about: 'Intro\n\n{{gallery-carousel}}\n\nOutro'
        });

        expect(container.textContent).toContain('Intro');
        expect(container.textContent).toContain('Outro');
        expect(container.textContent).not.toContain('{{gallery-carousel}}');
        expect(container.querySelector('[data-testid="gallery-carousel"], [aria-label="Sky Tools gallery"]')).not.toBeNull();
    });
});
