import React, { act } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createRoot, type Root } from 'react-dom/client';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router-dom';
import { api } from '@/utils/api';
import { InlineDependencyUI, InlineModpackBuilderUI, NewReleasesSection, ProjectAnalyticsSection, TrendingProjectsSection } from '@/modules/home/components/FeaturePreviews';

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

    it('balances multiline body copy for feature preview text', async () => {
        await act(async () => {
            root.render(
                <MemoryRouter>
                    <ProjectAnalyticsSection />
                </MemoryRouter>
            );
        });

        const bodyCopy = Array.from(container.querySelectorAll('p'))
            .find((paragraph) => paragraph.textContent?.includes('Keep tabs on how your Hytale uploads perform'));

        expect(bodyCopy?.className).toContain('[text-wrap:balance]');
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

    it('uses real project data for every dependency preview entry', () => {
        const dependencyProjects = [
            {
                id: 'arcane-toolkit',
                title: 'Arcane Toolkit',
                author: 'Ada',
                imageUrl: '/images/arcane.png',
                classification: 'PLUGIN',
                versions: [{ versionNumber: '2.4.0', releaseDate: '2026-04-10T00:00:00Z' }]
            },
            {
                id: 'worldedit-plus',
                title: 'WorldEdit Plus',
                author: 'Grace',
                imageUrl: '/images/worldedit.png',
                classification: 'MODPACK',
                versions: [{ versionNumber: '1.8.2', releaseDate: '2026-03-10T00:00:00Z' }]
            },
            {
                id: 'biome-painter',
                title: 'Biome Painter',
                author: 'Lin',
                imageUrl: '/images/biome.png',
                classification: 'DATA',
                versions: [{ versionNumber: '3.0.1', releaseDate: '2026-05-10T00:00:00Z' }]
            }
        ] as any;

        const markup = renderToStaticMarkup(<InlineDependencyUI projects={dependencyProjects} />);

        expect(markup).toContain('Arcane Toolkit');
        expect(markup).toContain('WorldEdit Plus');
        expect(markup).toContain('Biome Painter');
        expect(markup).toContain('by Ada');
        expect(markup).toContain('v2.4.0');
        expect(markup).not.toContain('Hytale Core Library');
        expect(markup).not.toContain('MathLib');
    });
});


describe('Modpack builder preview', () => {
    const projects = [
        { id: 'arcane', slug: 'arcane', title: 'Arcane Toolkit', author: 'Ada', imageUrl: '/images/arcane.png', classification: 'PLUGIN', versions: [
            { versionNumber: '1.0.0', releaseDate: '2026-01-01' },
            { versionNumber: '2.4.0', releaseDate: '2026-08-01' }
        ] },
        { id: 'biomes', title: 'Biome Painter', author: 'Lin', classification: 'DATA', versions: [{ versionNumber: '3.1', releaseDate: '2026-08-01' }] }
    ] as any;

    it('renders real artwork, authors, latest versions and links without fabricated warnings', () => {
        const markup = renderToStaticMarkup(<MemoryRouter><InlineModpackBuilderUI projects={projects} /></MemoryRouter>);
        expect(markup).toContain('Arcane Toolkit');
        expect(markup).toContain('images/arcane.png');
        expect(markup).toContain('by Ada');
        expect(markup).toContain('v2.4.0');
        expect(markup).toContain('href="/mod/arcane"');
        expect(markup).not.toContain('Hytale Core Library');
        expect(markup).not.toContain('WeatherFX');
        expect(markup).not.toContain('QuestAPI');
    });

    it('deduplicates entries and excludes packs and projects that disallow inclusion', () => {
        const markup = renderToStaticMarkup(<MemoryRouter><InlineModpackBuilderUI projects={[
            projects[0], projects[0],
            { ...projects[1], title: 'Not allowed', allowModpacks: false },
            { ...projects[1], title: 'Another pack', classification: 'MODPACK' }
        ]} /></MemoryRouter>);
        expect((markup.match(/aria-pressed=/g) || [])).toHaveLength(1);
        expect(markup).not.toContain('Not allowed');
        expect(markup).not.toContain('Another pack');
    });

    it('shows loading and unavailable states instead of fake projects', () => {
        const render = (loading: boolean) => renderToStaticMarkup(<MemoryRouter><InlineModpackBuilderUI loading={loading} /></MemoryRouter>);
        expect(render(true)).toContain('Loading projects');
        expect(render(false)).toContain('Projects are unavailable');
        expect(render(false)).not.toContain('Skylands Expansion');
    });

    it('fetches missing release data and handles unavailable versions', async () => {
        const get = vi.spyOn(api, 'get')
            .mockResolvedValueOnce({ data: { versions: [{ versionNumber: '4.2.1', releaseDate: '2026-09-01' }] } })
            .mockRejectedValueOnce(new Error('Unavailable'));
        const container = document.createElement('div');
        const root = createRoot(container);
        try {
            await act(async () => root.render(<MemoryRouter><InlineModpackBuilderUI projects={projects.map((project: any) => ({ ...project, versions: [] }))} /></MemoryRouter>));
            expect(get).toHaveBeenCalledWith('/projects/arcane/versions', { timeout: 1800 });
            expect(get).toHaveBeenCalledTimes(2);
            expect(container.textContent).toContain('v4.2.1');
            expect(container.textContent).toContain('Latest release');
            expect(container.textContent).not.toContain('v1.0.0');
        } finally {
            await act(async () => root.unmount());
            get.mockRestore();
        }
    });

    it('updates the selected count and preserves selection while searching', async () => {
        const container = document.createElement('div');
        document.body.appendChild(container);
        const root = createRoot(container);
        try {
            await act(async () => root.render(<MemoryRouter><InlineModpackBuilderUI projects={projects} /></MemoryRouter>));
            expect(container.querySelector('[aria-live]')?.textContent).toBe('2projects');
            await act(async () => (container.querySelector('button') as HTMLButtonElement).click());
            expect(container.querySelector('[aria-live]')?.textContent).toBe('1project');
            expect(container.querySelector('button')?.getAttribute('aria-pressed')).toBe('false');
            const input = container.querySelector('input')!;
            const search = async (value: string) => act(async () => {
                Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!.call(input, value);
                input.dispatchEvent(new Event('input', { bubbles: true }));
            });
            await search('Lin');
            expect(container.textContent).not.toContain('Arcane Toolkit');
            expect(container.textContent).toContain('Biome Painter');
            await search('no-such-project');
            expect(container.textContent).toContain('No matching projects');
            await search('');
            expect(container.querySelector('button')?.getAttribute('aria-pressed')).toBe('false');
            await act(async () => (container.querySelector('button') as HTMLButtonElement).click());
            expect(container.querySelector('[aria-live]')?.textContent).toBe('2projects');
        } finally {
            await act(async () => root.unmount());
            container.remove();
        }
    });
});
