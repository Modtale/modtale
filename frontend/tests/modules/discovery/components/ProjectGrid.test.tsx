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

describe('ProjectGrid fast render', () => {
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

    it('renders all cards immediately without waiting for image preloads', async () => {
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

        const cards = Array.from(container.querySelectorAll('[data-project-id]'));
        expect(cards).toHaveLength(3);
    });
});
