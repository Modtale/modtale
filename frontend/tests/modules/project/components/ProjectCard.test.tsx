import React from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { ProjectCard } from '@/modules/project/components/ProjectCard';
import type { Project } from '@/types';

vi.mock('@/components/ui/OptimizedImage', () => ({
    OptimizedImage: ({ className = '', alt = '' }: { className?: string; alt?: string }) => (
        <img alt={alt} className={className} />
    )
}));

vi.mock('@/utils/prefetch', () => ({
    prefetchProject: vi.fn()
}));

describe('ProjectCard banner fade', () => {
    let container: HTMLDivElement;
    let root: Root;
    let originalRequestIdleCallback: typeof window.requestIdleCallback;
    let originalCancelIdleCallback: typeof window.cancelIdleCallback;

    const baseProject: Project = {
        id: 'project-1',
        title: 'Skyforge',
        description: 'A polished project card test fixture.',
        authorId: 'author-1',
        author: 'Builder',
        imageUrl: '/icon.png',
        classification: 'PLUGIN',
        downloadCount: 42,
        favoriteCount: 7,
        updatedAt: '2026-06-11T12:00:00Z',
        comments: [],
        versions: [],
        galleryImages: []
    };

    beforeEach(() => {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        originalRequestIdleCallback = window.requestIdleCallback;
        originalCancelIdleCallback = window.cancelIdleCallback;

        window.requestIdleCallback = ((cb: () => void) => {
            cb();
            return 1;
        }) as typeof window.requestIdleCallback;
        window.cancelIdleCallback = vi.fn() as typeof window.cancelIdleCallback;
    });

    afterEach(async () => {
        await act(async () => {
            root.unmount();
        });
        container.remove();
        window.requestIdleCallback = originalRequestIdleCallback;
        window.cancelIdleCallback = originalCancelIdleCallback;
    });

    it('keeps the fade when no banner is set', async () => {
        await act(async () => {
            root.render(
                <MemoryRouter>
                    <ProjectCard
                        project={baseProject}
                        isFavorite={false}
                        onToggleFavorite={vi.fn()}
                        isLoggedIn={false}
                    />
                </MemoryRouter>
            );
        });

        expect(container.querySelector('div[class*="bg-gradient-to-t"]')).not.toBeNull();
    });

    it('removes the fade when a banner is set', async () => {
        await act(async () => {
            root.render(
                <MemoryRouter>
                    <ProjectCard
                        project={{ ...baseProject, bannerUrl: '/banner.png' }}
                        isFavorite={false}
                        onToggleFavorite={vi.fn()}
                        isLoggedIn={false}
                    />
                </MemoryRouter>
            );
        });

        const bannerImage = container.querySelector('img[alt=""]');
        expect(bannerImage).not.toBeNull();
        expect(bannerImage?.className).not.toContain('opacity-80');
        expect(container.querySelector('div[class*="bg-gradient-to-t"]')).toBeNull();
    });

    it('optimistically updates the displayed favorite count when favorite state changes', async () => {
        await act(async () => {
            root.render(
                <MemoryRouter>
                    <ProjectCard
                        project={baseProject}
                        isFavorite={false}
                        onToggleFavorite={vi.fn()}
                        isLoggedIn={true}
                    />
                </MemoryRouter>
            );
        });

        expect(container.textContent).toContain('7');

        await act(async () => {
            root.render(
                <MemoryRouter>
                    <ProjectCard
                        project={baseProject}
                        isFavorite={true}
                        onToggleFavorite={vi.fn()}
                        isLoggedIn={true}
                    />
                </MemoryRouter>
            );
        });

        expect(container.textContent).toContain('8');
    });

    it('uses the latest favorite handler after rerendering with unchanged project data', async () => {
        const oldToggle = vi.fn();
        const newToggle = vi.fn();

        await act(async () => {
            root.render(
                <MemoryRouter>
                    <ProjectCard
                        project={baseProject}
                        isFavorite={false}
                        onToggleFavorite={oldToggle}
                        isLoggedIn={true}
                    />
                </MemoryRouter>
            );
        });

        await act(async () => {
            root.render(
                <MemoryRouter>
                    <ProjectCard
                        project={baseProject}
                        isFavorite={false}
                        onToggleFavorite={newToggle}
                        isLoggedIn={true}
                    />
                </MemoryRouter>
            );
        });

        const favoriteButton = container.querySelector('button[aria-label="7 favorites"]') as HTMLButtonElement | null;
        expect(favoriteButton).not.toBeNull();

        await act(async () => {
            favoriteButton?.click();
        });

        expect(oldToggle).not.toHaveBeenCalled();
        expect(newToggle).toHaveBeenCalledWith('project-1');
    });
});
