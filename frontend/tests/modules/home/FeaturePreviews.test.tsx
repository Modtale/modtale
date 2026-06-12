import React, { act } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createRoot, type Root } from 'react-dom/client';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router-dom';
import { NewReleasesSection, ProjectAnalyticsSection, TrendingProjectsSection } from '@/modules/home/components/FeaturePreviews';

vi.mock('@/components/ui/charts/LineChart', () => ({
    LineChart: () => <div data-testid="line-chart" />
}));

vi.mock('@/modules/project/components/ProjectCard', async () => {
    const actual = await vi.importActual<typeof import('@/modules/project/components/ProjectCard')>('@/modules/project/components/ProjectCard');
    return {
        ...actual,
        ProjectCard: ({ project }: any) => <div data-testid="project-card">{project.title}</div>
    };
});

describe('FeaturePreviews ProjectAnalyticsSection', () => {
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

    it('renders the conversion rate card by default or when showConversionRate is true', async () => {
        await act(async () => {
            root.render(
                <MemoryRouter>
                    <ProjectAnalyticsSection />
                </MemoryRouter>
            );
        });

        expect(container.textContent).toContain('Conversion Rate');
        expect(container.textContent).toContain('Downloads');
        expect(container.textContent).toContain('Views');
        
        const gridContainer = container.querySelector('.grid');
        expect(gridContainer?.className).toContain('md:grid-cols-3');
    });

    it('does not render the conversion rate card when showConversionRate is false', async () => {
        await act(async () => {
            root.render(
                <MemoryRouter>
                    <ProjectAnalyticsSection showConversionRate={false} />
                </MemoryRouter>
            );
        });

        expect(container.textContent).not.toContain('Conversion Rate');
        expect(container.textContent).toContain('Downloads');
        expect(container.textContent).toContain('Views');

        const gridContainer = container.querySelector('.grid');
        expect(gridContainer?.className).not.toContain('md:grid-cols-3');
    });
});

describe('FeaturePreviews project sections', () => {
    const project = {
        id: 'project-1',
        title: 'Skyforge Utilities',
        authorId: 'user-1',
        author: 'Ada',
        classification: 'PLUGIN',
        downloadCount: 1200,
        favoriteCount: 45,
        comments: [],
        versions: [],
        galleryImages: []
    } as any;

    it('renders project card skeletons while trending projects are loading', () => {
        const markup = renderToStaticMarkup(
            <MemoryRouter>
                <TrendingProjectsSection projects={[]} loading={true} />
            </MemoryRouter>
        );

        expect(markup).toContain('Trending');
        expect(markup).toContain('aria-hidden="true"');
        expect(markup).not.toContain('data-testid="project-card"');
    });

    it('renders project card skeletons while new releases are loading', () => {
        const markup = renderToStaticMarkup(
            <MemoryRouter>
                <NewReleasesSection projects={[]} loading={true} />
            </MemoryRouter>
        );

        expect(markup).toContain('New Releases');
        expect(markup).toContain('aria-hidden="true"');
        expect(markup).not.toContain('data-testid="project-card"');
    });

    it('renders project cards when trending projects are available', () => {
        const markup = renderToStaticMarkup(
            <MemoryRouter>
                <TrendingProjectsSection projects={[project]} />
            </MemoryRouter>
        );

        expect(markup).toContain('data-testid="project-card"');
        expect(markup).toContain('Skyforge Utilities');
    });
});
