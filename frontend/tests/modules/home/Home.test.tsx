import React, { act } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { HelmetProvider } from 'react-helmet-async';
import { SSRProvider } from '@/context/SSRContext';
import { Home } from '@/modules/home/views/Home';
import { api } from '@/utils/api';

vi.mock('@/utils/api', () => ({
    api: {
        get: vi.fn(),
        post: vi.fn(),
        put: vi.fn(),
        delete: vi.fn()
    }
}));

vi.mock('@/modules/home/components/HeroMarquee', () => ({
    MarqueeColumn: ({ projects }: { projects: Array<unknown> }) => (
        <div data-testid="marquee-column" data-project-count={projects.length} />
    )
}));

vi.mock('@/modules/home/components/FeaturePreviews', () => ({
    TrendingProjectsSection: () => <div data-testid="trending-projects-section" />,
    NewReleasesSection: () => <div data-testid="new-releases-section" />,
    DirectDownloadsSection: () => <div data-testid="direct-downloads-section" />,
    SmartDependenciesSection: () => <div data-testid="smart-dependencies-section" />,
    ProjectAnalyticsSection: () => <div data-testid="project-analytics-section" />,
    CommunityThreadsSection: () => <div data-testid="community-threads-section" />,
    RealTimeAlertsSection: () => <div data-testid="real-time-alerts-section" />,
    AccountPreferencesSection: () => <div data-testid="account-preferences-section" />
}));

const mockedApi = vi.mocked(api);

class MockObserver {
    static instances: MockObserver[] = [];
    callback: ResizeObserverCallback | IntersectionObserverCallback;

    constructor(callback: ResizeObserverCallback | IntersectionObserverCallback) {
        this.callback = callback;
        MockObserver.instances.push(this);
    }

    observe() {}
    unobserve() {}
    disconnect() {}

    trigger() {
        this.callback([{ isIntersecting: true }] as never, this as never);
    }

    static triggerAll() {
        for (const instance of MockObserver.instances) {
            instance.trigger();
        }
    }

    static reset() {
        MockObserver.instances = [];
    }
}

describe('Home fallback requests', () => {
    let container: HTMLDivElement;
    let root: Root;
    const originalInnerWidth = window.innerWidth;
    const originalInnerHeight = window.innerHeight;

    beforeEach(() => {
        vi.useFakeTimers();
        vi.clearAllMocks();
        MockObserver.reset();

        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);

        vi.stubGlobal('IntersectionObserver', MockObserver as any);
        vi.stubGlobal('ResizeObserver', MockObserver as any);
        vi.stubGlobal('requestAnimationFrame', ((callback: FrameRequestCallback) => window.setTimeout(() => callback(performance.now()), 0)) as any);
        vi.stubGlobal('cancelAnimationFrame', ((handle: number) => window.clearTimeout(handle)) as any);
        mockedApi.get.mockResolvedValue({ data: { content: [] } } as any);
    });

    afterEach(async () => {
        await act(async () => {
            root.unmount();
        });
        container.remove();
        vi.unstubAllGlobals();
        Object.defineProperty(window, 'innerWidth', { configurable: true, writable: true, value: originalInnerWidth });
        Object.defineProperty(window, 'innerHeight', { configurable: true, writable: true, value: originalInnerHeight });
        vi.useRealTimers();
    });

    it('renders the hero immediately when SSR projects are already available', async () => {
        Object.defineProperty(window, 'innerWidth', { configurable: true, writable: true, value: 1450 });
        Object.defineProperty(window, 'innerHeight', { configurable: true, writable: true, value: 900 });

        const originalClientWidth = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'clientWidth');
        const originalClientHeight = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'clientHeight');
        const originalScrollWidth = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'scrollWidth');
        const originalScrollHeight = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'scrollHeight');
        const originalGetBoundingClientRect = HTMLElement.prototype.getBoundingClientRect;

        const restoreMeasurements = () => {
            if (originalClientWidth) Object.defineProperty(HTMLElement.prototype, 'clientWidth', originalClientWidth);
            if (originalClientHeight) Object.defineProperty(HTMLElement.prototype, 'clientHeight', originalClientHeight);
            if (originalScrollWidth) Object.defineProperty(HTMLElement.prototype, 'scrollWidth', originalScrollWidth);
            if (originalScrollHeight) Object.defineProperty(HTMLElement.prototype, 'scrollHeight', originalScrollHeight);
            HTMLElement.prototype.getBoundingClientRect = originalGetBoundingClientRect;
        };

        Object.defineProperty(HTMLElement.prototype, 'clientWidth', {
            configurable: true,
            get() {
                if (this instanceof HTMLElement) {
                    if (this.className.includes('max-w-[112rem]')) return 1330;
                    if (this.getAttribute('aria-label') === 'Primary Actions') return 520;
                    if (this.className.includes('home-hero-copy')) return 640;
                }

                return 400;
            }
        });

        Object.defineProperty(HTMLElement.prototype, 'clientHeight', {
            configurable: true,
            get() {
                return 56;
            }
        });

        Object.defineProperty(HTMLElement.prototype, 'scrollWidth', {
            configurable: true,
            get() {
                return this.clientWidth;
            }
        });

        Object.defineProperty(HTMLElement.prototype, 'scrollHeight', {
            configurable: true,
            get() {
                return this.clientHeight;
            }
        });

        HTMLElement.prototype.getBoundingClientRect = function getBoundingClientRect() {
            if (this instanceof HTMLElement && this.className.includes('home-hero-copy')) {
                return {
                    x: 0,
                    y: 0,
                    top: 0,
                    left: 80,
                    bottom: 520,
                    right: 700,
                    width: 620,
                    height: 520,
                    toJSON() {
                        return {};
                    }
                } as DOMRect;
            }

            if (this instanceof HTMLElement && this.className.includes('home-hero-desktop-marquee')) {
                return {
                    x: 0,
                    y: 0,
                    top: 32,
                    left: 780,
                    bottom: 760,
                    right: 1280,
                    width: 500,
                    height: 728,
                    toJSON() {
                        return {};
                    }
                } as DOMRect;
            }

            return {
                x: 0,
                y: 0,
                top: 0,
                left: 0,
                bottom: 56,
                right: 400,
                width: 400,
                height: 56,
                toJSON() {
                    return {};
                }
            } as DOMRect;
        };

        try {
        await act(async () => {
            root.render(
                <SSRProvider
                    data={{
                        homeProjects: [
                            {
                                id: 'project-1',
                                title: 'Skyforge Utilities',
                                authorId: 'user-1',
                                author: 'Ada',
                                imageUrl: '/images/skyforge-icon.png',
                                bannerUrl: '/images/skyforge-banner.png',
                                downloadCount: 1200
                            }
                        ],
                        stats: { totalProjects: 2842, totalDownloads: 92841653, totalUsers: 49713 }
                    }}
                    initialPath="/"
                >
                    <HelmetProvider>
                        <MemoryRouter initialEntries={['/']}>
                            <Home />
                        </MemoryRouter>
                    </HelmetProvider>
                </SSRProvider>
            );
        });

        expect(container.textContent).toContain('The Hytale');
        expect(container.querySelector('.home-hero-desktop-marquee')).toBeTruthy();
        expect(container.querySelector('[data-testid="marquee-column"]')).toBeTruthy();
        } finally {
            restoreMeasurements();
        }
    });

    it('renders a desktop marquee skeleton while hero projects are still loading on wide screens', async () => {
        Object.defineProperty(window, 'innerWidth', { configurable: true, writable: true, value: 1450 });
        Object.defineProperty(window, 'innerHeight', { configurable: true, writable: true, value: 900 });

        mockedApi.get
            .mockImplementationOnce(() => new Promise(() => {}))
            .mockResolvedValueOnce({ data: { content: [] } } as any)
            .mockResolvedValueOnce({ data: { totalProjects: 0, totalDownloads: 0, totalUsers: 0 } } as any);

        const originalClientWidth = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'clientWidth');
        const originalClientHeight = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'clientHeight');
        const originalScrollWidth = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'scrollWidth');
        const originalScrollHeight = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'scrollHeight');
        const originalGetBoundingClientRect = HTMLElement.prototype.getBoundingClientRect;

        const restoreMeasurements = () => {
            if (originalClientWidth) Object.defineProperty(HTMLElement.prototype, 'clientWidth', originalClientWidth);
            if (originalClientHeight) Object.defineProperty(HTMLElement.prototype, 'clientHeight', originalClientHeight);
            if (originalScrollWidth) Object.defineProperty(HTMLElement.prototype, 'scrollWidth', originalScrollWidth);
            if (originalScrollHeight) Object.defineProperty(HTMLElement.prototype, 'scrollHeight', originalScrollHeight);
            HTMLElement.prototype.getBoundingClientRect = originalGetBoundingClientRect;
        };

        Object.defineProperty(HTMLElement.prototype, 'clientWidth', {
            configurable: true,
            get() {
                if (this instanceof HTMLElement) {
                    if (this.className.includes('max-w-[112rem]')) return 1330;
                    if (this.getAttribute('aria-label') === 'Primary Actions') return 520;
                    if (this.className.includes('home-hero-copy')) return 640;
                }

                return 400;
            }
        });

        Object.defineProperty(HTMLElement.prototype, 'clientHeight', {
            configurable: true,
            get() {
                return 56;
            }
        });

        Object.defineProperty(HTMLElement.prototype, 'scrollWidth', {
            configurable: true,
            get() {
                return this.clientWidth;
            }
        });

        Object.defineProperty(HTMLElement.prototype, 'scrollHeight', {
            configurable: true,
            get() {
                return this.clientHeight;
            }
        });

        HTMLElement.prototype.getBoundingClientRect = function getBoundingClientRect() {
            return {
                x: 0,
                y: 0,
                top: 0,
                left: 0,
                bottom: 56,
                right: 400,
                width: 400,
                height: 56,
                toJSON() {
                    return {};
                }
            } as DOMRect;
        };

        try {
            await act(async () => {
                root.render(
                    <SSRProvider data={{ homeProjects: [], stats: { totalProjects: 0, totalDownloads: 0, totalUsers: 0 } }} initialPath="/">
                        <HelmetProvider>
                            <MemoryRouter initialEntries={['/']}>
                                <Home />
                            </MemoryRouter>
                        </HelmetProvider>
                    </SSRProvider>
                );
            });

            const heroSection = container.querySelector('section.home-hero');
            const heroGrid = container.querySelector('.home-hero-grid');
            const heroCopy = container.querySelector('.home-hero-copy');
            const heroPrimary = container.querySelector('.home-hero-copy-primary');
            const heroActions = container.querySelector('[aria-label="Primary Actions"]');
            const heroMarquee = container.querySelector('.home-hero-desktop-marquee');

            expect(heroSection?.className).toContain('home-hero-loading-state');
            expect(heroGrid?.className).toContain('home-hero-grid-loading-state');
            expect(heroCopy?.className).toContain('home-hero-copy-loading-state');
            expect(heroPrimary?.className).toContain('home-hero-copy-primary-loading-state');
            expect(heroActions?.className).toContain('home-hero-actions-loading-state');
            expect(heroMarquee?.className).toContain('home-hero-desktop-marquee-loading-state');
            expect(container.querySelector('[data-testid="home-hero-marquee-skeleton"]')).toBeTruthy();

            await act(async () => {
                await vi.runOnlyPendingTimersAsync();
            });

            await act(async () => {
                MockObserver.triggerAll();
                window.dispatchEvent(new Event('resize'));
                await vi.runAllTimersAsync();
            });

            expect(container.querySelector('.home-hero-copy-desktop')).toBeTruthy();
            expect(container.querySelector('.home-hero-desktop-marquee')).toBeTruthy();
            expect(container.querySelector('[data-testid="home-hero-marquee-skeleton"]')).toBeTruthy();
            expect(container.querySelector('[data-testid="marquee-column"]')).toBeFalsy();
        } finally {
            restoreMeasurements();
        }
    });

    it('keeps the wide desktop hero split while replacing skeleton cards with fetched marquee cards', async () => {
        Object.defineProperty(window, 'innerWidth', { configurable: true, writable: true, value: 1450 });
        Object.defineProperty(window, 'innerHeight', { configurable: true, writable: true, value: 900 });

        let resolveTrendingProjects: (value: { data: { content: Array<Record<string, unknown>> } }) => void = () => {};
        const trendingProjectsPromise = new Promise<{ data: { content: Array<Record<string, unknown>> } }>((resolve) => {
            resolveTrendingProjects = resolve;
        });

        mockedApi.get
            .mockImplementationOnce(() => trendingProjectsPromise as never)
            .mockResolvedValueOnce({ data: { content: [] } } as any)
            .mockResolvedValueOnce({ data: { totalProjects: 0, totalDownloads: 0, totalUsers: 0 } } as any);

        const originalClientWidth = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'clientWidth');
        const originalClientHeight = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'clientHeight');
        const originalScrollWidth = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'scrollWidth');
        const originalScrollHeight = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'scrollHeight');
        const originalGetBoundingClientRect = HTMLElement.prototype.getBoundingClientRect;

        const restoreMeasurements = () => {
            if (originalClientWidth) Object.defineProperty(HTMLElement.prototype, 'clientWidth', originalClientWidth);
            if (originalClientHeight) Object.defineProperty(HTMLElement.prototype, 'clientHeight', originalClientHeight);
            if (originalScrollWidth) Object.defineProperty(HTMLElement.prototype, 'scrollWidth', originalScrollWidth);
            if (originalScrollHeight) Object.defineProperty(HTMLElement.prototype, 'scrollHeight', originalScrollHeight);
            HTMLElement.prototype.getBoundingClientRect = originalGetBoundingClientRect;
        };

        Object.defineProperty(HTMLElement.prototype, 'clientWidth', {
            configurable: true,
            get() {
                if (this instanceof HTMLElement) {
                    if (this.className.includes('max-w-[112rem]')) return 1330;
                    if (this.getAttribute('aria-label') === 'Primary Actions') return 520;
                    if (this.className.includes('home-hero-copy')) return 640;
                }

                return 400;
            }
        });

        Object.defineProperty(HTMLElement.prototype, 'clientHeight', {
            configurable: true,
            get() {
                return 56;
            }
        });

        Object.defineProperty(HTMLElement.prototype, 'scrollWidth', {
            configurable: true,
            get() {
                return this.clientWidth;
            }
        });

        Object.defineProperty(HTMLElement.prototype, 'scrollHeight', {
            configurable: true,
            get() {
                return this.clientHeight;
            }
        });

        HTMLElement.prototype.getBoundingClientRect = function getBoundingClientRect() {
            if (this instanceof HTMLElement && this.className.includes('home-hero-copy')) {
                return {
                    x: 0,
                    y: 0,
                    top: 0,
                    left: 80,
                    bottom: 520,
                    right: 700,
                    width: 620,
                    height: 520,
                    toJSON() {
                        return {};
                    }
                } as DOMRect;
            }

            if (this instanceof HTMLElement && this.className.includes('home-hero-desktop-marquee')) {
                return {
                    x: 0,
                    y: 0,
                    top: 32,
                    left: 780,
                    bottom: 760,
                    right: 1280,
                    width: 500,
                    height: 728,
                    toJSON() {
                        return {};
                    }
                } as DOMRect;
            }

            return {
                x: 0,
                y: 0,
                top: 0,
                left: 0,
                bottom: 56,
                right: 400,
                width: 400,
                height: 56,
                toJSON() {
                    return {};
                }
            } as DOMRect;
        };

        try {
            await act(async () => {
                root.render(
                    <SSRProvider data={{ homeProjects: [], stats: { totalProjects: 0, totalDownloads: 0, totalUsers: 0 } }} initialPath="/">
                        <HelmetProvider>
                            <MemoryRouter initialEntries={['/']}>
                                <Home />
                            </MemoryRouter>
                        </HelmetProvider>
                    </SSRProvider>
                );
            });

            expect(container.querySelector('.home-hero-copy-desktop')).toBeTruthy();
            expect(container.querySelector('[data-testid="home-hero-marquee-skeleton"]')).toBeTruthy();

            await act(async () => {
                resolveTrendingProjects({
                    data: {
                        content: [
                            {
                                id: 'project-1',
                                title: 'Skyforge Utilities',
                                authorId: 'user-1',
                                author: 'Ada',
                                imageUrl: '/images/skyforge-icon.png',
                                bannerUrl: '/images/skyforge-banner.png',
                                downloadCount: 1200
                            }
                        ]
                    }
                });
                await trendingProjectsPromise;
                await vi.runAllTimersAsync();
            });

            const heroSection = container.querySelector('section.home-hero');
            const heroGrid = container.querySelector('.home-hero-grid');
            const heroCopy = container.querySelector('.home-hero-copy');

            expect(heroSection?.className).not.toContain('home-hero-loading-state');
            expect(heroGrid?.className).toContain('lg:grid-cols-2');
            expect(heroGrid?.className).not.toContain('lg:grid-cols-1');
            expect(heroCopy?.className).toContain('home-hero-copy-desktop');
            expect(heroCopy?.className).toContain('lg:text-left');
            expect(heroCopy?.className).toContain('lg:mx-0');
            expect(heroCopy?.className).not.toContain('mx-auto');
            expect(container.querySelector('[data-testid="home-hero-marquee-skeleton"]')).toBeFalsy();
            expect(container.querySelector('[data-testid="marquee-column"]')).toBeTruthy();
        } finally {
            restoreMeasurements();
        }
    });

    it('keeps skeleton cards in the wide desktop marquee slot when no marquee projects are available', async () => {
        Object.defineProperty(window, 'innerWidth', { configurable: true, writable: true, value: 1450 });
        Object.defineProperty(window, 'innerHeight', { configurable: true, writable: true, value: 900 });

        mockedApi.get
            .mockResolvedValueOnce({ data: { content: [] } } as any)
            .mockResolvedValueOnce({ data: { content: [] } } as any)
            .mockResolvedValueOnce({ data: { totalProjects: 0, totalDownloads: 0, totalUsers: 0 } } as any);

        const originalClientWidth = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'clientWidth');
        const originalClientHeight = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'clientHeight');
        const originalScrollWidth = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'scrollWidth');
        const originalScrollHeight = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'scrollHeight');
        const originalGetBoundingClientRect = HTMLElement.prototype.getBoundingClientRect;

        const restoreMeasurements = () => {
            if (originalClientWidth) Object.defineProperty(HTMLElement.prototype, 'clientWidth', originalClientWidth);
            if (originalClientHeight) Object.defineProperty(HTMLElement.prototype, 'clientHeight', originalClientHeight);
            if (originalScrollWidth) Object.defineProperty(HTMLElement.prototype, 'scrollWidth', originalScrollWidth);
            if (originalScrollHeight) Object.defineProperty(HTMLElement.prototype, 'scrollHeight', originalScrollHeight);
            HTMLElement.prototype.getBoundingClientRect = originalGetBoundingClientRect;
        };

        Object.defineProperty(HTMLElement.prototype, 'clientWidth', {
            configurable: true,
            get() {
                if (this instanceof HTMLElement) {
                    if (this.className.includes('max-w-[112rem]')) return 1330;
                    if (this.getAttribute('aria-label') === 'Primary Actions') return 520;
                    if (this.className.includes('home-hero-copy')) return 640;
                }

                return 400;
            }
        });

        Object.defineProperty(HTMLElement.prototype, 'clientHeight', {
            configurable: true,
            get() {
                return 56;
            }
        });

        Object.defineProperty(HTMLElement.prototype, 'scrollWidth', {
            configurable: true,
            get() {
                return this.clientWidth;
            }
        });

        Object.defineProperty(HTMLElement.prototype, 'scrollHeight', {
            configurable: true,
            get() {
                return this.clientHeight;
            }
        });

        HTMLElement.prototype.getBoundingClientRect = function getBoundingClientRect() {
            return {
                x: 0,
                y: 0,
                top: 0,
                left: 0,
                bottom: 56,
                right: 400,
                width: 400,
                height: 56,
                toJSON() {
                    return {};
                }
            } as DOMRect;
        };

        try {
            await act(async () => {
                root.render(
                    <SSRProvider data={{ homeProjects: [], stats: { totalProjects: 0, totalDownloads: 0, totalUsers: 0 } }} initialPath="/">
                        <HelmetProvider>
                            <MemoryRouter initialEntries={['/']}>
                                <Home />
                            </MemoryRouter>
                        </HelmetProvider>
                    </SSRProvider>
                );
            });

            await act(async () => {
                await vi.runAllTimersAsync();
            });

            const heroSection = container.querySelector('section.home-hero');
            const heroGrid = container.querySelector('.home-hero-grid');
            const heroCopy = container.querySelector('.home-hero-copy');
            const heroMarquee = container.querySelector('.home-hero-desktop-marquee');

            expect(heroSection?.className).not.toContain('home-hero-loading-state');
            expect(heroGrid?.className).toContain('lg:grid-cols-2');
            expect(heroGrid?.className).not.toContain('lg:grid-cols-1');
            expect(heroCopy?.className).toContain('home-hero-copy-desktop');
            expect(heroCopy?.className).toContain('lg:text-left');
            expect(heroCopy?.className).toContain('lg:mx-0');
            expect(heroMarquee).toBeTruthy();
            expect(container.querySelector('[data-testid="home-hero-marquee-skeleton"]')).toBeTruthy();
            expect(container.querySelector('[data-testid="marquee-column"]')).toBeFalsy();
        } finally {
            restoreMeasurements();
        }
    });

    it('uses a dynamic viewport height for the mobile hero container', async () => {
        await act(async () => {
            root.render(
                <SSRProvider
                    data={{
                        homeProjects: [],
                        stats: { totalProjects: 0, totalDownloads: 0, totalUsers: 0 }
                    }}
                    initialPath="/"
                >
                    <HelmetProvider>
                        <MemoryRouter initialEntries={['/']}>
                            <Home />
                        </MemoryRouter>
                    </HelmetProvider>
                </SSRProvider>
            );
        });

        const heroSection = container.querySelector('section.home-hero');
        expect(heroSection?.className).toContain('min-h-[100dvh]');
        expect(heroSection?.className).not.toContain('min-h-[100vh]');
    });

    it('fetches popular marquee projects via sort', async () => {
        mockedApi.get
            .mockResolvedValueOnce({ data: { content: [] } } as any)
            .mockResolvedValueOnce({ data: { content: [] } } as any)
            .mockResolvedValueOnce({ data: { totalProjects: 0, totalDownloads: 0, totalUsers: 0 } } as any);

        await act(async () => {
            root.render(
                <SSRProvider data={{ homeProjects: [], stats: { totalProjects: 0, totalDownloads: 0, totalUsers: 0 } }} initialPath="/">
                    <HelmetProvider>
                        <MemoryRouter initialEntries={['/']}>
                            <Home />
                        </MemoryRouter>
                    </HelmetProvider>
                </SSRProvider>
            );
        });

        await act(async () => {
            await vi.advanceTimersByTimeAsync(7000);
        });

        expect(mockedApi.get).toHaveBeenCalledWith('/projects', {
            params: { size: 16, sort: 'popular', view: 'marquee' },
            timeout: 1800,
        });
        expect(mockedApi.get).toHaveBeenCalledWith('/projects', {
            params: { size: 12, sort: 'newest' },
            timeout: 1800,
        });
        expect(mockedApi.get).toHaveBeenCalledWith('/analytics/platform/stats', { timeout: 1800 });
    });

    it('loads additional popular marquee pages in the background after the initial seed', async () => {
        await act(async () => {
            root.render(
                <SSRProvider
                    data={{
                        homeMarqueeProjects: [
                            {
                                id: 'project-1',
                                title: 'Skyforge Utilities',
                                authorId: 'user-1',
                                author: 'Ada',
                                imageUrl: '/images/skyforge-icon.png',
                                bannerUrl: '/images/skyforge-banner.png',
                                downloadCount: 1200
                            }
                        ],
                        homeProjects: [],
                        homeTrendingProjects: [
                            {
                                id: 'project-2',
                                title: 'Skyforge Core',
                                authorId: 'user-1',
                                author: 'Ada',
                                imageUrl: '/images/skyforge-core-icon.png',
                                bannerUrl: '/images/skyforge-core-banner.png',
                                downloadCount: 900
                            }
                        ],
                        homeNewestProjects: [],
                        stats: { totalProjects: 2842, totalDownloads: 92841653, totalUsers: 49713 }
                    }}
                    initialPath="/"
                >
                    <HelmetProvider>
                        <MemoryRouter initialEntries={['/']}>
                            <Home />
                        </MemoryRouter>
                    </HelmetProvider>
                </SSRProvider>
            );
        });

        expect(mockedApi.get).not.toHaveBeenCalled();

        await act(async () => {
            await vi.advanceTimersByTimeAsync(200);
        });

        expect(mockedApi.get).toHaveBeenCalledWith('/projects', {
            params: { size: 16, sort: 'popular', view: 'marquee', page: 1 },
            timeout: 1800,
        });
    });

    it('uses newest SSR projects as the initial marquee seed instead of immediately refetching marquee data', async () => {
        await act(async () => {
            root.render(
                <SSRProvider
                    data={{
                        homeProjects: [],
                        homeTrendingProjects: [],
                        homeNewestProjects: [
                            {
                                id: 'project-1',
                                title: 'Skyforge Utilities',
                                authorId: 'user-1',
                                author: 'Ada',
                                imageUrl: '/images/skyforge-icon.png',
                                bannerUrl: '/images/skyforge-banner.png',
                                downloadCount: 1200
                            }
                        ],
                        stats: { totalProjects: 2842, totalDownloads: 92841653, totalUsers: 49713 }
                    }}
                    initialPath="/"
                >
                    <HelmetProvider>
                        <MemoryRouter initialEntries={['/']}>
                            <Home />
                        </MemoryRouter>
                    </HelmetProvider>
                </SSRProvider>
            );
        });

        expect(mockedApi.get).not.toHaveBeenCalledWith('/projects', expect.objectContaining({
            params: expect.objectContaining({ sort: 'popular', view: 'marquee' }),
            timeout: 1800,
        }));
        expect(mockedApi.get).not.toHaveBeenCalled();
    });

    it('keeps the desktop hero layout on wide screens after a same-width fit failure', async () => {
        Object.defineProperty(window, 'innerWidth', { configurable: true, writable: true, value: 1400 });

        const originalClientWidth = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'clientWidth');
        const originalClientHeight = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'clientHeight');
        const originalScrollWidth = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'scrollWidth');
        const originalScrollHeight = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'scrollHeight');
        const originalOffsetTop = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'offsetTop');
        const originalGetBoundingClientRect = HTMLElement.prototype.getBoundingClientRect;

        const restoreMeasurements = () => {
            if (originalClientWidth) Object.defineProperty(HTMLElement.prototype, 'clientWidth', originalClientWidth);
            if (originalClientHeight) Object.defineProperty(HTMLElement.prototype, 'clientHeight', originalClientHeight);
            if (originalScrollWidth) Object.defineProperty(HTMLElement.prototype, 'scrollWidth', originalScrollWidth);
            if (originalScrollHeight) Object.defineProperty(HTMLElement.prototype, 'scrollHeight', originalScrollHeight);
            if (originalOffsetTop) Object.defineProperty(HTMLElement.prototype, 'offsetTop', originalOffsetTop);
            HTMLElement.prototype.getBoundingClientRect = originalGetBoundingClientRect;
        };

        Object.defineProperty(HTMLElement.prototype, 'clientWidth', {
            configurable: true,
            get() {
                if (this instanceof HTMLElement) {
                    if (this.className.includes('max-w-[112rem]')) return 1300;
                    if (this.getAttribute('aria-label') === 'Primary Actions') return 480;
                    if (this.className.includes('home-hero-copy')) return 640;
                }

                return 400;
            }
        });

        Object.defineProperty(HTMLElement.prototype, 'clientHeight', {
            configurable: true,
            get() {
                return 56;
            }
        });

        Object.defineProperty(HTMLElement.prototype, 'scrollWidth', {
            configurable: true,
            get() {
                return this.clientWidth;
            }
        });

        Object.defineProperty(HTMLElement.prototype, 'scrollHeight', {
            configurable: true,
            get() {
                if (this instanceof HTMLElement && this.tagName === 'A') {
                    return this.className.includes('sm:px-10') ? 96 : 56;
                }

                return this.clientHeight;
            }
        });

        Object.defineProperty(HTMLElement.prototype, 'offsetTop', {
            configurable: true,
            get() {
                if (this instanceof HTMLElement && this.tagName === 'A') {
                    return this.className.includes('sm:px-10') ? 20 : 0;
                }

                return 0;
            }
        });

        HTMLElement.prototype.getBoundingClientRect = function getBoundingClientRect() {
            return {
                x: 0,
                y: 0,
                top: 0,
                left: 0,
                bottom: 56,
                right: 400,
                width: 400,
                height: 56,
                toJSON() {
                    return {};
                }
            } as DOMRect;
        };

        try {
            await act(async () => {
                root.render(
                    <SSRProvider data={{
                        homeProjects: [
                            {
                                id: 'project-1',
                                title: 'Skyforge Utilities',
                                authorId: 'user-1',
                                author: 'Ada',
                                imageUrl: '/images/skyforge-icon.png',
                                bannerUrl: '/images/skyforge-banner.png',
                                downloadCount: 1200
                            }
                        ],
                        stats: { totalProjects: 0, totalDownloads: 0, totalUsers: 0 }
                    }} initialPath="/">
                        <HelmetProvider>
                            <MemoryRouter initialEntries={['/']}>
                                <Home />
                            </MemoryRouter>
                        </HelmetProvider>
                    </SSRProvider>
                );
            });

            await act(async () => {
                await vi.runAllTimersAsync();
            });

            expect(container.querySelector('.home-hero-copy-desktop')).toBeTruthy();

            await act(async () => {
                MockObserver.triggerAll();
                window.dispatchEvent(new Event('resize'));
                await vi.runAllTimersAsync();
            });

            expect(container.querySelector('.home-hero-copy-desktop')).toBeTruthy();

            Object.defineProperty(window, 'innerWidth', { configurable: true, writable: true, value: 1450 });
            Object.defineProperty(HTMLElement.prototype, 'clientWidth', {
                configurable: true,
                get() {
                    if (this instanceof HTMLElement) {
                        if (this.className.includes('max-w-[112rem]')) return 1330;
                        if (this.getAttribute('aria-label') === 'Primary Actions') return 520;
                        if (this.className.includes('home-hero-copy')) return 640;
                    }

                    return 400;
                }
            });

            await act(async () => {
                MockObserver.triggerAll();
                window.dispatchEvent(new Event('resize'));
                await vi.runAllTimersAsync();
            });

            expect(container.querySelector('.home-hero-copy-desktop')).toBeTruthy();

            const heroCopy = container.querySelector('.home-hero-copy');
            expect(heroCopy?.className).toContain('lg:text-left');
            expect(heroCopy?.className).toContain('lg:mx-0');
            expect(heroCopy?.className).not.toContain('lg:mx-auto');
            expect(heroCopy?.className).toContain('lg:items-start');

            const heroPrimary = container.querySelector('.home-hero-copy-primary');
            expect(heroPrimary?.className).toContain('lg:items-start');

            const heroActions = container.querySelector('[aria-label="Primary Actions"]');
            expect(heroActions?.className).toContain('lg:items-start');
        } finally {
            restoreMeasurements();
        }
    });

    it('keeps the hero in the mobile layout on short desktop viewports so it stays inside the viewport', async () => {
        Object.defineProperty(window, 'innerWidth', { configurable: true, writable: true, value: 1180 });
        Object.defineProperty(window, 'innerHeight', { configurable: true, writable: true, value: 692 });

        const originalClientWidth = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'clientWidth');
        const originalClientHeight = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'clientHeight');
        const originalScrollWidth = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'scrollWidth');
        const originalScrollHeight = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'scrollHeight');
        const originalGetBoundingClientRect = HTMLElement.prototype.getBoundingClientRect;

        const restoreMeasurements = () => {
            if (originalClientWidth) Object.defineProperty(HTMLElement.prototype, 'clientWidth', originalClientWidth);
            if (originalClientHeight) Object.defineProperty(HTMLElement.prototype, 'clientHeight', originalClientHeight);
            if (originalScrollWidth) Object.defineProperty(HTMLElement.prototype, 'scrollWidth', originalScrollWidth);
            if (originalScrollHeight) Object.defineProperty(HTMLElement.prototype, 'scrollHeight', originalScrollHeight);
            HTMLElement.prototype.getBoundingClientRect = originalGetBoundingClientRect;
        };

        Object.defineProperty(HTMLElement.prototype, 'clientWidth', {
            configurable: true,
            get() {
                if (this instanceof HTMLElement) {
                    if (this.className.includes('max-w-[112rem]')) return 1100;
                    if (this.className.includes('home-hero-copy')) return 560;
                }

                return 400;
            }
        });

        Object.defineProperty(HTMLElement.prototype, 'clientHeight', {
            configurable: true,
            get() {
                return 56;
            }
        });

        Object.defineProperty(HTMLElement.prototype, 'scrollWidth', {
            configurable: true,
            get() {
                return this.clientWidth;
            }
        });

        Object.defineProperty(HTMLElement.prototype, 'scrollHeight', {
            configurable: true,
            get() {
                return this.clientHeight;
            }
        });

        HTMLElement.prototype.getBoundingClientRect = function getBoundingClientRect() {
            return {
                x: 0,
                y: 0,
                top: 0,
                left: 0,
                bottom: 56,
                right: 400,
                width: 400,
                height: 56,
                toJSON() {
                    return {};
                }
            } as DOMRect;
        };

        try {
            await act(async () => {
                root.render(
                    <SSRProvider
                        data={{
                            homeProjects: [
                                {
                                    id: 'project-1',
                                    title: 'Skyforge Utilities',
                                    authorId: 'user-1',
                                    author: 'Ada',
                                    imageUrl: '/images/skyforge-icon.png',
                                    bannerUrl: '/images/skyforge-banner.png',
                                    downloadCount: 1200
                                }
                            ],
                            stats: { totalProjects: 2842, totalDownloads: 92841653, totalUsers: 49713 }
                        }}
                        initialPath="/"
                    >
                        <HelmetProvider>
                            <MemoryRouter initialEntries={['/']}>
                                <Home />
                            </MemoryRouter>
                        </HelmetProvider>
                    </SSRProvider>
                );
            });

            await act(async () => {
                await vi.runAllTimersAsync();
            });

            expect(container.querySelector('.home-hero-copy-desktop')).toBeFalsy();
            expect(container.querySelector('.home-hero-desktop-marquee')).toBeFalsy();
            expect(container.querySelector('[data-testid="marquee-column"]')).toBeFalsy();

            const heroSection = container.querySelector('section.home-hero');
            const heroCopy = container.querySelector('.home-hero-copy');
            const heroPrimary = container.querySelector('.home-hero-copy-primary');
            const heroActions = container.querySelector('[aria-label="Primary Actions"]');

            expect(heroSection?.className).toContain('home-hero-desktop-stacked');
            expect(heroSection?.className).toContain('lg:min-h-[calc(100dvh-6rem)]');
            expect(heroCopy?.className).not.toContain('lg:text-left');
            expect(heroCopy?.className).not.toContain('lg:items-start');
            expect(heroPrimary?.className).not.toContain('lg:items-start');
            expect(heroActions?.className).not.toContain('lg:items-start');
        } finally {
            restoreMeasurements();
        }
    });
});
