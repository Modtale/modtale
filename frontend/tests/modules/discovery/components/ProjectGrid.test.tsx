import React from 'react';
import { describe, expect, it, vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { ProjectGrid } from '@/modules/discovery/components/ProjectGrid';

vi.mock('@/modules/project/components/ProjectCard', () => ({
    ProjectCard: ({ path, project }: any) => (
        <div data-testid="project-card" data-path={path} data-project-id={project.id}>
            {project.title}
        </div>
    ),
    ProjectCardSkeletons: () => <div data-testid="project-card-skeletons" />
}));

describe('ProjectGrid project links', () => {
    const baseProject = {
        id: 'project-1',
        slug: 'levelingcore',
        title: 'LevelingCore',
        authorId: 'author-1',
        author: 'AzureDoom',
        classification: 'PLUGIN',
        downloadCount: 0,
        favoriteCount: 0,
        comments: [],
        versions: [],
        galleryImages: []
    };

    it('passes the canonical slug path to browse cards', () => {
        const markup = renderToStaticMarkup(
            <ProjectGrid
                items={[baseProject as any]}
                loading={false}
                viewStyle="grid"
                itemsPerPage={12}
                likedProjectIds={[]}
                onToggleFavorite={vi.fn()}
                isLoggedIn={false}
            />
        );

        expect(markup).toContain('data-path="/mod/levelingcore"');
    });

    it('switches browse grid cards to three columns on mid-desktop viewports', () => {
        const markup = renderToStaticMarkup(
            <ProjectGrid
                items={[baseProject as any]}
                loading={false}
                viewStyle="grid"
                itemsPerPage={12}
                likedProjectIds={[]}
                onToggleFavorite={vi.fn()}
                isLoggedIn={false}
            />
        );

        expect(markup).toContain('min-[1440px]:grid-cols-3');
        expect(markup).not.toContain('min-[1800px]:grid-cols-3');
    });
});
