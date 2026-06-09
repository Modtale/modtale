import { act } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { useProjectSearch } from '@/modules/discovery/hooks/useProjectSearch';
import { discoveryClient } from '@/modules/discovery/api/discoveryClient';
import { captureError } from '@/utils/errorTracking';

vi.mock('@/modules/discovery/api/discoveryClient', () => ({
    discoveryClient: {
        searchProjects: vi.fn(),
        getGameVersions: vi.fn()
    }
}));

vi.mock('@/utils/errorTracking', () => ({
    captureError: vi.fn()
}));

const mockedDiscoveryClient = vi.mocked(discoveryClient);
const mockedCaptureError = vi.mocked(captureError);

const settle = async (times = 8) => {
    for (let i = 0; i < times; i += 1) {
        await act(async () => {
            await Promise.resolve();
        });
    }
};

type HookSnapshot = ReturnType<typeof useProjectSearch> & { locationSearch: string };

const Probe = ({
    initialClassification,
    useSSRData,
    initialItems,
    initialTotalPages,
    initialTotalItems,
    onRender
}: {
    initialClassification: any;
    useSSRData: boolean;
    initialItems: any[];
    initialTotalPages: number;
    initialTotalItems: number;
    onRender: (snapshot: HookSnapshot) => void;
}) => {
    const snapshot = useProjectSearch(initialClassification, useSSRData, initialItems as any, initialTotalPages, initialTotalItems);
    const location = useLocation();
    onRender({ ...snapshot, locationSearch: location.search });

    return (
        <div
            id="probe"
            data-loading={String(snapshot.loading)}
            data-items={String(snapshot.items.length)}
            data-total-pages={String(snapshot.totalPages)}
            data-total-items={String(snapshot.totalItems)}
            data-search={location.search}
        />
    );
};

describe('useProjectSearch', () => {
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

    it('skips the first fetch when SSR data is present and fetches once params change later', async () => {
        mockedDiscoveryClient.searchProjects.mockResolvedValue({
            content: [{ id: 'project-2' }],
            totalPages: 2,
            totalElements: 13
        } as any);

        await act(async () => {
            root.render(
                <MemoryRouter initialEntries={['/mods']}>
                    <Probe
                        initialClassification="All"
                        useSSRData={true}
                        initialItems={[{ id: 'project-1' }]}
                        initialTotalPages={1}
                        initialTotalItems={1}
                        onRender={snapshot => {
                            latestSnapshot = snapshot;
                        }}
                    />
                </MemoryRouter>
            );
        });
        await settle();

        expect(mockedDiscoveryClient.searchProjects).not.toHaveBeenCalled();
        expect(latestSnapshot.items).toEqual([{ id: 'project-1' }]);
        expect(latestSnapshot.loading).toBe(false);

        await act(async () => {
            latestSnapshot.updateParams({ view: 'favorites' });
        });
        await settle();

        expect(mockedDiscoveryClient.searchProjects).toHaveBeenCalledTimes(1);
        expect(mockedDiscoveryClient.searchProjects).toHaveBeenCalledWith({
            page: 0,
            size: 12,
            classification: undefined,
            tags: '',
            search: '',
            sort: 'relevance',
            gameVersion: undefined,
            minDownloads: undefined,
            minFavorites: undefined,
            dateRange: 'all',
            category: 'Favorites'
        }, expect.any(AbortSignal));
        expect(latestSnapshot.locationSearch).toBe('?view=favorites');
        expect(latestSnapshot.items).toEqual([{ id: 'project-2' }]);
    });

    it('translates router query params into a discovery search request', async () => {
        mockedDiscoveryClient.searchProjects.mockResolvedValue({
            content: [{ id: 'project-9' }],
            totalPages: 4,
            totalElements: 48
        } as any);

        await act(async () => {
            root.render(
                <MemoryRouter initialEntries={['/mods?q=sky&view=favorites&version=1.20.1&minDl=10&minFav=3&date=30d&tags=magic,tech&sort=popular&page=2']}>
                    <Probe
                        initialClassification="All"
                        useSSRData={false}
                        initialItems={[]}
                        initialTotalPages={0}
                        initialTotalItems={0}
                        onRender={snapshot => {
                            latestSnapshot = snapshot;
                        }}
                    />
                </MemoryRouter>
            );
        });
        await settle();

        expect(mockedDiscoveryClient.searchProjects).toHaveBeenCalledWith({
            page: 2,
            size: 12,
            classification: undefined,
            tags: 'magic,tech',
            search: 'sky',
            sort: 'popular',
            gameVersion: '1.20.1',
            minDownloads: 10,
            minFavorites: 3,
            dateRange: '30d',
            category: 'Favorites'
        }, expect.any(AbortSignal));
        expect(latestSnapshot.items).toEqual([{ id: 'project-9' }]);
        expect(latestSnapshot.totalPages).toBe(4);
        expect(latestSnapshot.totalItems).toBe(48);
    });

    it('rewrites out-of-range pages back to the last available page', async () => {
        mockedDiscoveryClient.searchProjects.mockResolvedValue({
            content: [{ id: 'project-2' }],
            totalPages: 2,
            totalElements: 24
        } as any);

        await act(async () => {
            root.render(
                <MemoryRouter initialEntries={['/mods?page=5']}>
                    <Probe
                        initialClassification="All"
                        useSSRData={false}
                        initialItems={[]}
                        initialTotalPages={0}
                        initialTotalItems={0}
                        onRender={snapshot => {
                            latestSnapshot = snapshot;
                        }}
                    />
                </MemoryRouter>
            );
        });
        await settle(12);

        expect(mockedDiscoveryClient.searchProjects).toHaveBeenCalledTimes(2);
        expect(latestSnapshot.locationSearch).toBe('?page=1');
        expect(latestSnapshot.page).toBe(1);
        expect(latestSnapshot.items).toEqual([{ id: 'project-2' }]);
    });

    it('captures non-cancel errors and clears the current result set', async () => {
        const error = new Error('boom');
        mockedDiscoveryClient.searchProjects.mockRejectedValue(error);

        await act(async () => {
            root.render(
                <MemoryRouter initialEntries={['/mods?q=broken']}>
                    <Probe
                        initialClassification="All"
                        useSSRData={false}
                        initialItems={[{ id: 'stale-project' }]}
                        initialTotalPages={3}
                        initialTotalItems={22}
                        onRender={snapshot => {
                            latestSnapshot = snapshot;
                        }}
                    />
                </MemoryRouter>
            );
        });
        await settle();

        expect(mockedCaptureError).toHaveBeenCalledWith(error);
        expect(latestSnapshot.items).toEqual([]);
        expect(latestSnapshot.totalPages).toBe(0);
        expect(latestSnapshot.totalItems).toBe(0);
        expect(latestSnapshot.loading).toBe(false);
    });

    it('enters a pending state immediately when browse params change, before the fetch resolves', async () => {
        let resolveSearch: ((value: any) => void) | undefined;
        mockedDiscoveryClient.searchProjects.mockImplementation(() => new Promise((resolve) => {
            resolveSearch = resolve;
        }) as any);

        await act(async () => {
            root.render(
                <MemoryRouter initialEntries={['/mods']}>
                    <Probe
                        initialClassification="All"
                        useSSRData={true}
                        initialItems={[{ id: 'project-1' }]}
                        initialTotalPages={1}
                        initialTotalItems={1}
                        onRender={snapshot => {
                            latestSnapshot = snapshot;
                        }}
                    />
                </MemoryRouter>
            );
        });
        await settle();

        expect(latestSnapshot.isPending).toBe(false);
        expect(latestSnapshot.items).toEqual([{ id: 'project-1' }]);

        await act(async () => {
            latestSnapshot.updateParams({ view: 'trending' });
        });

        expect(latestSnapshot.isPending).toBe(true);
        expect(latestSnapshot.items).toEqual([]);
        expect(latestSnapshot.totalPages).toBe(0);
        expect(latestSnapshot.totalItems).toBe(0);
        expect(mockedDiscoveryClient.searchProjects).toHaveBeenCalledTimes(1);

        await act(async () => {
            resolveSearch?.({
                content: [{ id: 'project-2' }],
                totalPages: 3,
                totalElements: 25
            });
            await Promise.resolve();
        });
        await settle();

        expect(latestSnapshot.isPending).toBe(false);
        expect(latestSnapshot.items).toEqual([{ id: 'project-2' }]);
    });
});
