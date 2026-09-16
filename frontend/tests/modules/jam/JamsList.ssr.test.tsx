import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { SSRProvider } from '@/context/SSRContext';
import { JamsList } from '@/modules/jam/views/JamsList';
import { api } from '@/utils/api';
import type { Modjam } from '@/types';

vi.mock('@/components/ui/OptimizedImage', () => ({
    OptimizedImage: ({ alt }: { alt: string }) => <img alt={alt} />
}));

vi.mock('@/utils/api', async (importOriginal) => {
    const original = await importOriginal<typeof import('@/utils/api')>();
    return { ...original, api: { ...original.api, get: vi.fn() } };
});

const jam: Modjam = {
    id: 'jam-ssr',
    slug: 'server-rendered-jam',
    title: 'Server Rendered Jam',
    description: 'Already available during the initial render.',
    hostId: 'host-1',
    hostName: 'Builder',
    startDate: '2026-08-01T00:00:00Z',
    endDate: '2026-08-31T00:00:00Z',
    votingEndDate: '2026-09-03T00:00:00Z',
    status: 'ACTIVE',
    participantIds: [],
    categories: [],
    allowPublicVoting: true,
    allowConcurrentVoting: false,
    showResultsBeforeVotingEnds: false,
    createdAt: '2026-07-01T00:00:00Z'
};

describe('JamsList SSR bootstrap', () => {
    let container: HTMLDivElement;
    let root: Root;

    beforeEach(() => {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        vi.mocked(api.get).mockReset();
    });

    afterEach(async () => {
        await act(async () => root.unmount());
        container.remove();
    });

    it('renders server-provided jams without repeating the list request', async () => {
        await act(async () => {
            root.render(
                <MemoryRouter initialEntries={['/jams']}>
                    <SSRProvider initialPath="/jams" data={{ jamsDataReady: true, jamsData: [jam] }}>
                        <JamsList currentUser={null} />
                    </SSRProvider>
                </MemoryRouter>
            );
        });

        expect(container.textContent).toContain('Server Rendered Jam');
        expect(container.textContent).not.toContain('No jams found');
        expect(api.get).not.toHaveBeenCalled();
    });
});
