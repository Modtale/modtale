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

    it('preloads every wiki page when enabled and serves later navigation from the cache', async () => {
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
        mockedProjectClient.getWikiPage.mockImplementation(async (_projectId, slug) => ({
            title: slug === 'intro' ? 'Intro' : 'Install',
            content: `content for ${slug}`
        }));

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
        expect(latestSnapshot.loading).toBe(false);
        expect(latestSnapshot.data?.content?.title).toBe('Intro');

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

        expect(latestSnapshot.data?.content?.title).toBe('Install');
        expect(mockedProjectClient.getWikiPage).toHaveBeenCalledTimes(2);
    });
});
