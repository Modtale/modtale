import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { JamLayout } from '@/modules/jam/components/JamLayout';
import { JamCard } from '@/modules/jam/views/JamsList';
import type { Modjam } from '@/types';

vi.mock('@/components/ui/OptimizedImage', () => ({
    OptimizedImage: ({ alt, baseWidth, className = '' }: { alt: string; baseWidth: number; className?: string }) => (
        <img alt={alt} className={className} data-base-width={baseWidth} />
    )
}));

const jam: Modjam = {
    id: 'jam-1',
    slug: 'summer-build',
    title: 'Summer Build',
    description: 'Build something great.',
    imageUrl: '/uploads/jam-icon.png',
    bannerUrl: '/uploads/jam-banner.png',
    hostId: 'host-1',
    hostName: 'Builder',
    startDate: '2026-08-01T00:00:00Z',
    endDate: '2026-08-31T00:00:00Z',
    votingEndDate: '2026-09-03T00:00:00Z',
    status: 'ACTIVE',
    participantIds: ['user-1'],
    categories: [],
    allowPublicVoting: true,
    allowConcurrentVoting: false,
    showResultsBeforeVotingEnds: false,
    createdAt: '2026-07-01T00:00:00Z'
};

describe('jam presentation', () => {
    let container: HTMLDivElement;
    let root: Root;

    beforeEach(() => {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
    });

    afterEach(async () => {
        await act(async () => root.unmount());
        container.remove();
    });

    it('requests right-sized responsive images for jam cards', async () => {
        await act(async () => {
            root.render(<MemoryRouter><JamCard jam={jam} /></MemoryRouter>);
        });

        expect(container.querySelector('img[alt="Summer Build banner"]')?.getAttribute('data-base-width')).toBe('640');
        expect(container.querySelector('img[alt="Summer Build"]')?.getAttribute('data-base-width')).toBe('64');
    });

    it('uses a stable banner gradient and responsive content spacing', async () => {
        await act(async () => {
            root.render(
                <MemoryRouter>
                    <JamLayout
                        bannerUrl="/uploads/jam-banner.png"
                        titleContent={<h1>Summer Build</h1>}
                        mainContent={<p>Jam content</p>}
                    />
                </MemoryRouter>
            );
        });

        expect(container.querySelector('.bg-gradient-to-t')).not.toBeNull();
        expect(container.querySelector('.px-4.pb-8.pt-4')).not.toBeNull();
        expect(container.innerHTML).not.toContain('--fade-base');
    });
});
