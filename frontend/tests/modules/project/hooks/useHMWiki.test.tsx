import { act } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createRoot, type Root } from 'react-dom/client';
import { clearHMWikiCacheForTests, prefetchInitialWikiPage, useHMWiki } from '@/modules/project/hooks/useHMWiki';
import { projectClient } from '@/modules/project/api/projectClient';

vi.mock('@/modules/project/api/projectClient', () => ({
    projectClient: {
        getWikiData: vi.fn(),
        getWikiPage: vi.fn()
    }
}));

const mockedProjectClient = vi.mocked(projectClient);

const createDeferred = <T,>() => {
    let resolve!: (value: T) => void;
    let reject!: (reason?: unknown) => void;
    const promise = new Promise<T>((promiseResolve, promiseReject) => {
        resolve = promiseResolve;
        reject = promiseReject;
    });

    return { promise, resolve, reject };
};

const settle = async (times = 8) => {
    for (let i = 0; i < times; i += 1) {
        await act(async () => {
            await Promise.resolve();
        });
    }
};

type HookSnapshot = ReturnType<typeof useHMWiki>;

const Probe = ({
    projectId,
    pageSlug,
    enabled,
    onRender
}: {
    projectId?: string;
    pageSlug?: string;
    enabled?: boolean;
    onRender: (snapshot: HookSnapshot) => void;
}) => {
    const snapshot = useHMWiki(projectId, pageSlug, enabled);
    onRender(snapshot);

    return (
        <div
            id="probe"
            data-loading={String(snapshot.loading)}
            data-error={String(snapshot.error)}
            data-title={snapshot.data?.content?.title ?? ''}
        />
    );
};

describe('useHMWiki', () => {
    let container: HTMLDivElement;
    let root: Root;
    let latestSnapshot: HookSnapshot;

    beforeEach(() => {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        latestSnapshot = undefined as unknown as HookSnapshot;
        window.__MODTALE_WIKI_BOOTSTRAP = undefined;
        clearHMWikiCacheForTests();
        vi.clearAllMocks();
    });

    afterEach(async () => {
        await act(async () => {
            root.unmount();
        });
        vi.clearAllTimers();
        vi.useRealTimers();
        window.__MODTALE_WIKI_BOOTSTRAP = undefined;
        container.remove();
    });

    it('loads a newly selected page immediately before background prefetch work runs', async () => {
        vi.useFakeTimers();
        const pageRequests = new Map<string, ReturnType<typeof createDeferred<any>>>();
        mockedProjectClient.getWikiData.mockResolvedValue({
            index: { slug: 'intro' },
            pages: [
                { id: '1', slug: 'intro', title: 'Intro' },
                {
                    id: '2',
                    title: 'Guides',
                    children: [
                        { id: '3', slug: 'guides/install', title: 'Install' }
                    ]
                }
            ]
        });
        mockedProjectClient.getWikiPage.mockImplementation((_projectId, slug) => {
            const request = createDeferred<any>();
            pageRequests.set(slug, request);
            return request.promise;
        });

        await act(async () => {
            root.render(
                <Probe
                    projectId="project-1"
                    pageSlug="intro"
                    enabled={true}
                    onRender={(snapshot) => {
                        latestSnapshot = snapshot;
                    }}
                />
            );
        });
        await settle();

        expect(mockedProjectClient.getWikiData).toHaveBeenCalledWith('project-1');
        expect(mockedProjectClient.getWikiPage).toHaveBeenCalledTimes(1);
        expect(mockedProjectClient.getWikiPage).toHaveBeenCalledWith('project-1', 'intro');
        expect(latestSnapshot.loading).toBe(true);

        await act(async () => {
            pageRequests.get('intro')?.resolve({
                title: 'Intro',
                content: 'content for intro'
            });
            await pageRequests.get('intro')?.promise;
        });
        await settle();

        expect(latestSnapshot.loading).toBe(false);
        expect(latestSnapshot.data?.content?.title).toBe('Intro');
        expect(latestSnapshot.data?.pageCache).toHaveProperty('intro');
        expect(latestSnapshot.data?.pageCache).not.toHaveProperty('guides/install');

        await act(async () => {
            root.render(
                <Probe
                    projectId="project-1"
                    pageSlug="guides/install"
                    enabled={true}
                    onRender={(snapshot) => {
                        latestSnapshot = snapshot;
                    }}
                />
            );
        });
        await settle();

        expect(latestSnapshot.loading).toBe(true);
        expect(mockedProjectClient.getWikiPage).toHaveBeenCalledTimes(2);
        expect(mockedProjectClient.getWikiPage).toHaveBeenLastCalledWith('project-1', 'guides/install');

        await act(async () => {
            vi.advanceTimersByTime(1000);
        });
        await settle();

        expect(mockedProjectClient.getWikiPage).toHaveBeenCalledTimes(2);

        await act(async () => {
            pageRequests.get('guides/install')?.resolve({
                title: 'Install',
                content: 'content for guides/install'
            });
            await pageRequests.get('guides/install')?.promise;
        });
        await settle();

        expect(latestSnapshot.loading).toBe(false);
        expect(latestSnapshot.data?.content?.title).toBe('Install');
        expect(mockedProjectClient.getWikiPage).toHaveBeenCalledTimes(2);
    });

    it('fetches a selected page synchronously when it was not part of the prefetch set', async () => {
        const wikiDataRequest = createDeferred<any>();
        mockedProjectClient.getWikiData.mockReturnValue(wikiDataRequest.promise);
        mockedProjectClient.getWikiPage.mockImplementation(async (_projectId, slug) => ({
            title: slug === 'hidden/page' ? 'Hidden page' : 'Intro',
            content: `content for ${slug}`
        }));

        await act(async () => {
            root.render(
                <Probe
                    projectId="project-1"
                    pageSlug="hidden/page"
                    enabled={true}
                    onRender={(snapshot) => {
                        latestSnapshot = snapshot;
                    }}
                />
            );
        });
        await settle();

        expect(mockedProjectClient.getWikiData).toHaveBeenCalledWith('project-1');
        expect(mockedProjectClient.getWikiPage).toHaveBeenCalledWith('project-1', 'hidden/page');
        expect(latestSnapshot.data?.content?.title).toBe('Hidden page');
        expect(latestSnapshot.loading).toBe(false);

        await act(async () => {
            wikiDataRequest.resolve({
                index: { slug: 'intro' },
                pages: [
                    { id: '1', slug: 'intro', title: 'Intro' }
                ]
            });
            await wikiDataRequest.promise;
        });
        await settle();

        expect(latestSnapshot.loading).toBe(false);
        expect(latestSnapshot.data?.content?.title).toBe('Hidden page');
    });

    it('starts the observed root wiki page before index metadata resolves', async () => {
        const wikiDataRequest = createDeferred<any>();
        mockedProjectClient.getWikiData.mockReturnValue(wikiDataRequest.promise);
        mockedProjectClient.getWikiPage.mockImplementation(async (_projectId, slug) => ({
            title: slug === 'home-1' ? 'Home' : 'Other',
            content: `content for ${slug}`
        }));

        await act(async () => {
            root.render(
                <Probe
                    projectId="project-1"
                    pageSlug={undefined}
                    enabled={true}
                    onRender={(snapshot) => {
                        latestSnapshot = snapshot;
                    }}
                />
            );
        });
        await settle();

        expect(mockedProjectClient.getWikiData).toHaveBeenCalledWith('project-1');
        expect(mockedProjectClient.getWikiPage).toHaveBeenCalledWith('project-1', 'home-1');
        expect(latestSnapshot.loading).toBe(false);
        expect(latestSnapshot.data?.content?.title).toBe('Home');

        await act(async () => {
            wikiDataRequest.resolve({
                index: { slug: 'home-1' },
                pages: [
                    { id: '1', slug: 'home-1', title: 'Home' }
                ]
            });
            await wikiDataRequest.promise;
        });
        await settle();

        expect(mockedProjectClient.getWikiPage).toHaveBeenCalledTimes(1);
        expect(latestSnapshot.loading).toBe(false);
        expect(latestSnapshot.data?.mod?.index?.slug).toBe('home-1');
        expect(latestSnapshot.data?.content?.title).toBe('Home');
    });

    it('reuses document-started wiki bootstrap requests and paints the selected page before metadata', async () => {
        const wikiDataRequest = createDeferred<any>();
        const pageRequest = createDeferred<any>();
        window.__MODTALE_WIKI_BOOTSTRAP = {
            projectId: 'project-1',
            metadata: wikiDataRequest.promise,
            pages: {
                intro: {
                    promise: pageRequest.promise
                }
            }
        };

        await act(async () => {
            root.render(
                <Probe
                    projectId="project-1"
                    pageSlug="intro"
                    enabled={true}
                    onRender={(snapshot) => {
                        latestSnapshot = snapshot;
                    }}
                />
            );
        });
        await settle();

        expect(mockedProjectClient.getWikiData).not.toHaveBeenCalled();
        expect(mockedProjectClient.getWikiPage).not.toHaveBeenCalled();
        expect(latestSnapshot.loading).toBe(true);

        await act(async () => {
            pageRequest.resolve({
                title: 'Intro',
                content: 'content for intro'
            });
            await pageRequest.promise;
        });
        await settle();

        expect(latestSnapshot.loading).toBe(false);
        expect(latestSnapshot.data?.content?.title).toBe('Intro');
        expect(latestSnapshot.data?.mod?.pages).toEqual([]);

        await act(async () => {
            wikiDataRequest.resolve({
                index: { slug: 'intro' },
                pages: [
                    { id: '1', slug: 'intro', title: 'Intro' },
                    { id: '2', slug: 'guide', title: 'Guide' }
                ]
            });
            await wikiDataRequest.promise;
        });
        await settle();

        expect(mockedProjectClient.getWikiData).not.toHaveBeenCalled();
        expect(mockedProjectClient.getWikiPage).not.toHaveBeenCalled();
        expect(latestSnapshot.loading).toBe(false);
        expect(latestSnapshot.data?.content?.title).toBe('Intro');
        expect(latestSnapshot.data?.mod?.pages).toHaveLength(2);
    });

    it('keeps root page content visible if metadata fails after the fast-path page loads', async () => {
        const wikiDataRequest = createDeferred<any>();
        mockedProjectClient.getWikiData.mockReturnValue(wikiDataRequest.promise);
        mockedProjectClient.getWikiPage.mockResolvedValue({
            title: 'Home',
            content: 'content for home-1'
        });

        await act(async () => {
            root.render(
                <Probe
                    projectId="project-1"
                    pageSlug={undefined}
                    enabled={true}
                    onRender={(snapshot) => {
                        latestSnapshot = snapshot;
                    }}
                />
            );
        });
        await settle();

        expect(latestSnapshot.loading).toBe(false);
        expect(latestSnapshot.error).toBe(false);
        expect(latestSnapshot.data?.content?.title).toBe('Home');

        await act(async () => {
            wikiDataRequest.reject(new Error('metadata failed'));
            try {
                await wikiDataRequest.promise;
            } catch {}
        });
        await settle();

        expect(latestSnapshot.loading).toBe(false);
        expect(latestSnapshot.error).toBe(false);
        expect(latestSnapshot.data?.content?.title).toBe('Home');
    });

    it('reuses the project-page initial wiki warmup when the wiki view mounts', async () => {
        mockedProjectClient.getWikiData.mockResolvedValue({
            index: { slug: 'intro' },
            pages: [
                { id: '1', slug: 'intro', title: 'Intro' }
            ]
        });
        mockedProjectClient.getWikiPage.mockImplementation(async (_projectId, slug) => ({
            title: 'Intro',
            content: `content for ${slug}`
        }));

        prefetchInitialWikiPage('project-1');
        await settle();

        expect(mockedProjectClient.getWikiData).toHaveBeenCalledTimes(1);
        expect(mockedProjectClient.getWikiPage).toHaveBeenCalledTimes(1);
        expect(mockedProjectClient.getWikiPage).toHaveBeenCalledWith('project-1', 'intro');

        await act(async () => {
            root.render(
                <Probe
                    projectId="project-1"
                    pageSlug={undefined}
                    enabled={true}
                    onRender={(snapshot) => {
                        latestSnapshot = snapshot;
                    }}
                />
            );
        });
        await settle();

        expect(mockedProjectClient.getWikiData).toHaveBeenCalledTimes(1);
        expect(mockedProjectClient.getWikiPage).toHaveBeenCalledTimes(1);
        expect(latestSnapshot.loading).toBe(false);
        expect(latestSnapshot.data?.content?.title).toBe('Intro');
    });
});
