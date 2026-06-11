import { act } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createRoot, type Root } from 'react-dom/client';
import { ProjectGrid } from '@/modules/discovery/components/ProjectGrid';

vi.mock('@/modules/project/components/ProjectCard', () => ({
    ProjectCard: ({ project }: any) => (
        <div data-project-id={project.id}>
            {project.title}
        </div>
    )
}));

describe('ProjectGrid progressive reveal', () => {
    let container: HTMLDivElement;
    let root: Root;
    let createdImages: Array<{ onload: null | (() => void); onerror: null | (() => void); complete: boolean; naturalWidth: number; src: string }>;

    beforeEach(() => {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        createdImages = [];

        class MockImage {
            onload: null | (() => void) = null;
            onerror: null | (() => void) = null;
            complete = false;
            naturalWidth = 0;
            src = '';

            constructor() {
                createdImages.push(this);
            }
        }

        vi.stubGlobal('Image', MockImage as any);
    });

    afterEach(async () => {
        await act(async () => {
            root.unmount();
        });
        vi.unstubAllGlobals();
        container.remove();
    });

    it('loads and reveals cards one at a time as each icon preload completes', async () => {
        const items = [
            { id: 'project-1', title: 'Project 1' },
            { id: 'project-2', title: 'Project 2' },
            { id: 'project-3', title: 'Project 3' }
        ] as any;

        await act(async () => {
            root.render(
                <ProjectGrid
                    items={[]}
                    loading={false}
                    viewStyle="grid"
                    itemsPerPage={12}
                    likedProjectIds={[]}
                    onToggleFavorite={vi.fn()}
                    isLoggedIn={false}
                />
            );
        });

        await act(async () => {
            root.render(
                <ProjectGrid
                    items={items}
                    loading={false}
                    viewStyle="grid"
                    itemsPerPage={12}
                    likedProjectIds={[]}
                    onToggleFavorite={vi.fn()}
                    isLoggedIn={false}
                />
            );
        });

        let cards = Array.from(container.querySelectorAll('[data-project-id]'));
        expect(cards).toHaveLength(0);
        expect(createdImages).toHaveLength(1);

        await act(async () => {
            createdImages[0]?.onload?.();
        });

        cards = Array.from(container.querySelectorAll('[data-project-id]'));
        expect(cards).toHaveLength(1);
        expect(createdImages).toHaveLength(2);

        await act(async () => {
            createdImages[1]?.onload?.();
        });

        cards = Array.from(container.querySelectorAll('[data-project-id]'));
        expect(cards).toHaveLength(2);
        expect(createdImages).toHaveLength(3);

        await act(async () => {
            createdImages[2]?.onload?.();
        });

        cards = Array.from(container.querySelectorAll('[data-project-id]'));
        expect(cards).toHaveLength(3);
    });
});
