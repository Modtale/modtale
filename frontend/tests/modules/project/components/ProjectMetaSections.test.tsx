import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { ProjectMetaSections } from '@/modules/project/components/ProjectMetaSections';
import type { Project, ProjectDependency } from '@/types';

const project = {
    id: 'project-1',
    title: 'Skyforge',
    authorId: 'author-1',
    author: 'Builder',
    imageUrl: '/icon.png',
    classification: 'PLUGIN',
    tags: [],
    downloadCount: 0,
    favoriteCount: 0,
    updatedAt: '2026-06-01T00:00:00Z',
    versions: [{
        id: 'version-1',
        versionNumber: '1.0.0',
        gameVersion: '2026.03.11',
        gameVersions: ['2026.03.11'],
        fileUrl: '/files/skyforge.jar',
        downloadCount: 0,
        releaseDate: '2026-06-01T00:00:00Z'
    }]
} as Project;

describe('ProjectMetaSections dependencies', () => {
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

    it('uses dependency payload metadata for the dependency card and link', async () => {
        const dependencies: ProjectDependency[] = [{
            projectId: 'dep-1',
            projectTitle: 'Stored Dependency Title',
            versionNumber: '2.0.0',
            icon: '/uploads/dependency.png',
            title: 'Dependency Display',
            slug: 'dependency-display',
            classification: 'PLUGIN'
        }];

        await act(async () => {
            root.render(
                <MemoryRouter>
                    <ProjectMetaSections
                        project={project}
                        dependencies={dependencies}
                        depMeta={{}}
                    />
                </MemoryRouter>
            );
        });

        expect(container.textContent).toContain('Dependency Display');
        expect(container.textContent).toContain('Required');
        expect(container.querySelector('a')?.getAttribute('href')).toBe('/mod/dependency-display');
        expect(container.querySelector('img')?.getAttribute('alt')).toBe('Dependency Display Icon');
    });
});
