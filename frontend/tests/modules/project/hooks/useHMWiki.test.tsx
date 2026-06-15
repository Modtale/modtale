import { act } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createRoot, type Root } from 'react-dom/client';
import { useHMWiki } from '@/modules/project/hooks/useHMWiki';
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
        vi.clearAllMocks();
    });

    afterEach(async () => {
        await act(async () => {
            root.unmount();
        });
        container.remove();
    });

    it('loads the active page first while prefetching the rest in the background', async () => {
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
        expect(mockedProjectClient.getWikiPage).toHaveBeenCalledTimes(2);
        expect(mockedProjectClient.getWikiPage).toHaveBeenCalledWith('project-1', 'intro');
        expect(mockedProjectClient.getWikiPage).toHaveBeenCalledWith('project-1', 'guides/install');
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
        mockedProjectClient.getWikiData.mockResolvedValue({
            index: { slug: 'intro' },
            pages: [
                { id: '1', slug: 'intro', title: 'Intro' }
            ]
        });
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

        expect(mockedProjectClient.getWikiPage).toHaveBeenCalledWith('project-1', 'hidden/page');
        expect(latestSnapshot.loading).toBe(false);
        expect(latestSnapshot.data?.content?.title).toBe('Hidden page');
    });
});
