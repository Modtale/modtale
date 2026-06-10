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
    MarqueeColumn: () => <div data-testid="marquee-column" />,
    MarqueeRow: () => <div data-testid="marquee-row" />
}));

vi.mock('@/modules/home/components/FeaturePreviews', () => ({
    InlineDependencyUI: () => <div />,
    InlineDownloadUI: () => <div />,
    InlineNotificationUI: () => <div />
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
        vi.useRealTimers();
    });

    it('renders hero marquee content immediately when SSR projects are already available', async () => {
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

        expect(container.querySelector('[data-testid="marquee-column"], [data-testid="marquee-row"]')).toBeTruthy();
    });

    it('fetches trending home projects via category instead of an invalid sort value', async () => {
        mockedApi.get
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
            params: { size: 16, sort: 'relevance', category: 'trending' }
        });
        expect(mockedApi.get).toHaveBeenCalledWith('/analytics/platform/stats');
    });

    it('does not bounce back into the desktop hero layout after a same-width desktop fit failure', async () => {
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

            expect(container.querySelector('.home-hero-copy-desktop')).toBeFalsy();

            await act(async () => {
                MockObserver.triggerAll();
                window.dispatchEvent(new Event('resize'));
                await vi.runAllTimersAsync();
            });

            expect(container.querySelector('.home-hero-copy-desktop')).toBeFalsy();

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
        } finally {
            restoreMeasurements();
        }
    });
});
