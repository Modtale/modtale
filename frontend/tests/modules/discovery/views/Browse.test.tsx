import React, { act } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { HelmetProvider } from 'react-helmet-async';
import { SSRProvider } from '@/context/SSRContext';
import { MobileProvider } from '@/context/MobileContext';
import { Browse } from '@/modules/discovery/views/Browse';
import { discoveryClient } from '@/modules/discovery/api/discoveryClient';

vi.mock('@/modules/discovery/api/discoveryClient', () => ({
    discoveryClient: {
        searchProjects: vi.fn(),
        getGameVersions: vi.fn()
    }
}));

vi.mock('@/modules/project/components/ProjectCard', () => ({
    ProjectCard: ({ project }: any) => <div data-project-id={project.id}>{project.title}</div>,
    ProjectCardSkeletons: () => <div data-testid="project-card-skeletons" />
}));

const mockedDiscoveryClient = vi.mocked(discoveryClient);

const settle = async (times = 8) => {
    for (let i = 0; i < times; i += 1) {
        await act(async () => {
            await Promise.resolve();
        });
    }
};

describe('Browse SSR fallback recovery', () => {
    let container: HTMLDivElement;
    let root: Root;

    beforeEach(() => {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        vi.clearAllMocks();

        class MockObserver {
            observe() {}
            unobserve() {}
            disconnect() {}
        }

        class MockImage {
            onload: null | (() => void) = null;
            onerror: null | (() => void) = null;
            complete = true;
            naturalWidth = 1;

            set src(_: string) {
                queueMicrotask(() => {
                    this.onload?.();
                });
            }
        }

        vi.stubGlobal('IntersectionObserver', MockObserver as any);
        vi.stubGlobal('ResizeObserver', MockObserver as any);
        vi.stubGlobal('Image', MockImage as any);
    });

    afterEach(async () => {
        await act(async () => {
            root.unmount();
        });
        vi.unstubAllGlobals();
        container.remove();
    });

    it('fetches client data when browse SSR payload is only a fallback placeholder', async () => {
        mockedDiscoveryClient.searchProjects.mockResolvedValue({
            content: [{
                id: 'project-1',
                slug: 'skyforge-1',
                title: 'Skyforge Utilities',
                imageUrl: '/assets/favicon.svg'
            }],
            totalPages: 1,
            totalElements: 1
        } as any);

        await act(async () => {
            root.render(
                <SSRProvider
                    data={{ browseData: { content: [], totalPages: 0, totalElements: 0 }, browseDataReady: false }}
                    initialPath="/mods"
                >
                    <HelmetProvider>
                        <MobileProvider>
                            <MemoryRouter initialEntries={['/mods']}>
                                <Browse likedProjectIds={[]} onToggleFavorite={vi.fn()} isLoggedIn={false} />
                            </MemoryRouter>
                        </MobileProvider>
                    </HelmetProvider>
                </SSRProvider>
            );
        });

        await settle(12);

        expect(mockedDiscoveryClient.searchProjects).toHaveBeenCalledTimes(1);
        expect(container.textContent).toContain('Skyforge Utilities');
        expect(container.textContent).not.toContain('No matches found');
    });

    it('keeps the My Favorites browse view wired to the favorites category request', async () => {
        mockedDiscoveryClient.searchProjects.mockResolvedValue({
            content: [{
                id: 'project-1',
                slug: 'skyforge-1',
                title: 'Skyforge Utilities',
                imageUrl: '/assets/favicon.svg'
            }],
            totalPages: 1,
            totalElements: 1
        } as any);

        await act(async () => {
            root.render(
                <SSRProvider data={{}} initialPath="/mods?category=favorites">
                    <HelmetProvider>
                        <MobileProvider>
                            <MemoryRouter initialEntries={['/mods?category=favorites']}>
                                <Browse likedProjectIds={['project-1']} onToggleFavorite={vi.fn()} isLoggedIn={true} />
                            </MemoryRouter>
                        </MobileProvider>
                    </HelmetProvider>
                </SSRProvider>
            );
        });

        await settle(12);

        expect(container.textContent).toContain('My Favorites');
        const browseLinks = Array.from(container.querySelectorAll('a')).map(link => link.textContent?.trim());
        expect(browseLinks.indexOf('Recently Updated')).toBeLessThan(browseLinks.indexOf('My Favorites'));
        expect(mockedDiscoveryClient.searchProjects).toHaveBeenCalledWith(
            expect.objectContaining({ category: 'favorites' }),
            expect.any(AbortSignal)
        );
        expect(container.textContent).toContain('Skyforge Utilities');
    });
});
