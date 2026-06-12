import React, { act } from 'react';
import { readFileSync } from 'node:fs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createRoot } from 'react-dom/client';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { HelmetProvider } from 'react-helmet-async';
import { SSRProvider } from '@/context/SSRContext';
import { MobileProvider } from '@/context/MobileContext';
import { Home } from '@/modules/home/views/Home';
import { Browse } from '@/modules/discovery/views/Browse';
import { ProjectDetails } from '@/modules/project/views/ProjectDetails';
import { discoveryClient } from '@/modules/discovery/api/discoveryClient';
import { projectClient } from '@/modules/project/api/projectClient';
import type { Project } from '@/types';

vi.mock('@/modules/discovery/api/discoveryClient', () => ({
    discoveryClient: {
        searchProjects: vi.fn(),
        getGameVersions: vi.fn()
    }
}));

vi.mock('@/modules/project/api/projectClient', () => ({
    projectClient: {
        getProject: vi.fn(),
        trackView: vi.fn(),
        getUserProfile: vi.fn(),
        getOrgMembers: vi.fn(),
        getUsersBatch: vi.fn(),
        getDependencyMeta: vi.fn(),
        getMetaGameVersionCatalog: vi.fn(),
        followUser: vi.fn(),
        unfollowUser: vi.fn()
    }
}));

const mockedDiscoveryClient = vi.mocked(discoveryClient);
const mockedProjectClient = vi.mocked(projectClient);

const project = {
    id: 'project-1',
    slug: 'skyforge-1',
    title: 'Skyforge Utilities',
    description: 'Optimization and quality-of-life utilities for ambitious Hytale servers.',
    authorId: 'user-1',
    author: 'Ada',
    imageUrl: '/assets/favicon.svg',
    bannerUrl: '/assets/favicon.svg',
    classification: 'PLUGIN',
    downloadCount: 120000,
    favoriteCount: 4000,
    updatedAt: '2026-06-01T00:00:00Z',
    createdAt: '2026-01-01T00:00:00Z',
    about: 'A fast project page with **markdown** and release metadata.',
    tags: ['performance'],
    categories: ['Utilities'],
    versions: [
        {
            id: 'version-1',
            versionNumber: '1.0.0',
            gameVersions: ['2026.03.11'],
            downloadCount: 1200,
            releaseDate: '2026-06-01T00:00:00Z',
            channel: 'RELEASE',
            dependencies: []
        }
    ],
    comments: [],
    teamMembers: [],
    galleryImages: [],
    allowComments: true,
    allowModpacks: true
} as any satisfies Project;

const page = {
    content: Array.from({ length: 12 }, (_, index) => ({
        ...project,
        id: `project-${index + 1}`,
        slug: `skyforge-${index + 1}`,
        title: `Skyforge Utilities ${index + 1}`,
        downloadCount: 120000 + index
    })),
    totalPages: 1,
    totalElements: 12
};

class MockObserver {
    observe() {}
    unobserve() {}
    disconnect() {}
}

const renderWithProviders = (ui: React.ReactNode, route: string, ssrData: any) => (
    <SSRProvider data={ssrData} initialPath={route}>
        <HelmetProvider>
            <MobileProvider>
                <MemoryRouter initialEntries={[route]}>
                    {ui}
                </MemoryRouter>
            </MobileProvider>
        </HelmetProvider>
    </SSRProvider>
);

const renderCriticalView = async (ui: React.ReactNode) => {
    const container = document.createElement('div');
    document.body.appendChild(container);
    const root = createRoot(container);

    await act(async () => {
        root.render(ui);
    });
    const text = container.textContent || '';

    await act(async () => {
        root.unmount();
    });
    container.remove();
    return text;
};

const setViewport = (width: number, height: number) => {
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: width });
    Object.defineProperty(window, 'innerHeight', { configurable: true, value: height });
    window.dispatchEvent(new Event('resize'));
};

describe('critical page render budgets', () => {
    beforeEach(() => {
        vi.stubGlobal('IntersectionObserver', MockObserver);
        vi.stubGlobal('ResizeObserver', MockObserver);
        mockedProjectClient.trackView.mockResolvedValue(undefined);
        mockedProjectClient.getUserProfile.mockResolvedValue({ id: 'user-1', username: 'Ada', avatarUrl: '', likedProjectIds: [] } as any);
        mockedProjectClient.getOrgMembers.mockResolvedValue([]);
        mockedProjectClient.getUsersBatch.mockResolvedValue([]);
        mockedProjectClient.getDependencyMeta.mockResolvedValue({ icon: '', title: 'Dependency' } as any);
        mockedProjectClient.getMetaGameVersionCatalog.mockResolvedValue({ orderedVersions: ['2026.03.11'] } as any);
        mockedDiscoveryClient.searchProjects.mockResolvedValue(page);
        mockedDiscoveryClient.getGameVersions.mockResolvedValue(['2026.03.11']);
    });

    afterEach(() => {
        vi.unstubAllGlobals();
        vi.clearAllMocks();
    });

    it('renders critical home, browse, and project content immediately from existing data', async () => {
        const homeText = await renderCriticalView(renderWithProviders(<Home />, '/', {
            homeProjects: page.content,
            stats: { totalProjects: 2842, totalDownloads: 92841653, totalUsers: 49713 }
        }));

        const browseText = await renderCriticalView(renderWithProviders(
            <Browse likedProjectIds={[]} onToggleFavorite={vi.fn()} isLoggedIn={false} />,
            '/mods',
            { browseData: page }
        ));

        const projectText = await renderCriticalView(renderWithProviders(
            <Routes>
                <Route
                    path="/mod/:id"
                    element={(
                        <ProjectDetails
                            currentUser={null}
                            isLiked={() => false}
                            onToggleFavorite={vi.fn()}
                            onDownload={vi.fn()}
                            downloadedSessionIds={new Set()}
                            onRefresh={async () => {}}
                        />
                    )}
                />
            </Routes>,
            '/mod/skyforge-1',
            project
        ));

        expect(homeText).toContain('Community');
        expect(homeText).toContain('Discover Projects');
        expect(homeText).not.toContain('Loading');

        expect(browseText).toContain('Skyforge Utilities 1');
        expect(browseText).not.toContain('No matches found');
        expect(mockedProjectClient.getMetaGameVersionCatalog).not.toHaveBeenCalled();

        expect(projectText).toContain('Skyforge Utilities');
        expect(projectText).toContain('A fast project page');
        expect(projectText).not.toContain('Loading');
        expect(mockedProjectClient.getProject).not.toHaveBeenCalled();
    });

    it('renders SSR critical home and browse content across common viewport shapes without extra browse fetches', async () => {
        const viewports = [
            { name: 'mobile portrait', width: 390, height: 844 },
            { name: 'tablet portrait', width: 768, height: 1024 },
            { name: 'desktop landscape', width: 1366, height: 768 },
            { name: 'wide desktop', width: 1920, height: 1080 }
        ];

        for (const viewport of viewports) {
            setViewport(viewport.width, viewport.height);

            const homeText = await renderCriticalView(renderWithProviders(<Home />, '/', {
                homeProjects: page.content,
                stats: { totalProjects: 2842, totalDownloads: 92841653, totalUsers: 49713 }
            }));

            const browseText = await renderCriticalView(renderWithProviders(
                <Browse likedProjectIds={[]} onToggleFavorite={vi.fn()} isLoggedIn={false} />,
                '/mods',
                { browseData: page }
            ));

            expect(homeText, viewport.name).toContain('Discover Projects');
            expect(homeText, viewport.name).not.toContain('Loading');
            expect(browseText, viewport.name).toContain('Skyforge Utilities 1');
            expect(browseText, viewport.name).not.toContain('No matches found');
        }

        expect(mockedDiscoveryClient.searchProjects).not.toHaveBeenCalled();
    });

    it('keeps heavy diagnostics and account overlays out of the anonymous critical import path', () => {
        const errorTrackingSource = readFileSync('src/utils/errorTracking.ts', 'utf8');
        const appSource = readFileSync('src/App.tsx', 'utf8');
        const navbarSource = readFileSync('src/modules/core/components/Navbar.tsx', 'utf8');

        expect(errorTrackingSource).not.toMatch(/^import\s+\*\s+as\s+Sentry\s+from\s+['"]@sentry\/react['"]/m);
        expect(errorTrackingSource).toContain("import('@sentry/react')");

        expect(appSource).not.toMatch(/^import\s+\{\s*StatusModal\s*\}/m);
        expect(appSource).not.toMatch(/^import\s+\{\s*Onboarding\s*\}/m);
        expect(appSource).toContain("import('@/components/ui/StatusModal')");
        expect(appSource).toContain("import('@/modules/user/components/Onboarding')");

        expect(navbarSource).not.toMatch(/^import\s+\{\s*SignInModal\s*\}/m);
        expect(navbarSource).not.toMatch(/^import\s+\{\s*NotificationMenu\s*\}/m);
        expect(navbarSource).toContain("import('@/modules/auth/components/SignInModal.tsx')");
        expect(navbarSource).toContain("import('@/modules/user/components/NotificationMenu')");
    });
});
