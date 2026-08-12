import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { ProjectMetaSections } from '@/modules/project/components/ProjectMetaSections';
import type { Project, ProjectDependency } from '@/types';

const project = {
    id: 'project-1',
    title: 'Skyforge',
    description: 'A project for testing metadata rendering.',
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

    it('groups supported versions into expandable version families', async () => {
        const baseVersion = project.versions![0];
        const groupedProject = {
            ...project,
            versions: [
                {
                    ...baseVersion,
                    id: 'version-0-5-4',
                    gameVersion: '0.5.4',
                    gameVersions: ['0.5.4']
                },
                {
                    ...baseVersion,
                    id: 'version-0-5-3',
                    gameVersion: '0.5.3',
                    gameVersions: ['0.5.3']
                },
                {
                    ...baseVersion,
                    id: 'version-0-4-9',
                    gameVersion: '0.4.9',
                    gameVersions: ['0.4.9']
                },
                {
                    ...baseVersion,
                    id: 'version-legacy-date',
                    gameVersion: '2026.03.26-89796E57B',
                    gameVersions: ['2026.03.26-89796E57B']
                }
            ]
        } as Project;

        await act(async () => {
            root.render(
                <MemoryRouter>
                    <ProjectMetaSections
                        project={groupedProject}
                        dependencies={[]}
                        depMeta={{}}
                    />
                </MemoryRouter>
            );
        });

        expect(container.textContent).toContain('0.5.x');
        expect(container.textContent).toContain('0.4.9');
        expect(container.textContent).toContain('2026.03.26-89796E57B');
        expect(container.textContent).not.toContain('0.5.4');
        expect(container.textContent).not.toContain('0.5.3');
        expect((container.textContent || '').indexOf('0.5.x')).toBeLessThan(
            (container.textContent || '').indexOf('2026.03.26-89796E57B')
        );

        const expandButton = container.querySelector('button[aria-label="Expand 0.5.x versions"]') as HTMLButtonElement;

        await act(async () => {
            expandButton.click();
        });

        expect(container.textContent).toContain('0.5.4');
        expect(container.textContent).toContain('0.5.3');
    });
});
