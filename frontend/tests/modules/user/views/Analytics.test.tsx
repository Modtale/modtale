import { act } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from 'vitest';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { Analytics } from '@/modules/user/views/Analytics';
import { api } from '@/utils/api';

vi.mock('@/utils/api', () => ({
    api: {
        get: vi.fn()
    }
}));

const makePoints = () => Array.from({ length: 20 }, (_, index) => ({
    date: `2026-06-${String(index + 1).padStart(2, '0')}`,
    count: index + 1
}));

const flush = async () => {
    await act(async () => {
        await new Promise(resolve => setTimeout(resolve, 0));
    });
};

describe('Analytics', () => {
    let container: HTMLDivElement;
    let root: Root;
    const getMock = api.get as unknown as Mock;

    beforeEach(() => {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        getMock.mockImplementation(async (url: string) => {
            if (url === '/user/me') {
                return { data: { id: 'user-1', username: 'creator' } };
            }
            if (url === '/user/orgs') {
                return { data: [] };
            }
            if (url.startsWith('/creators/')) {
                return { data: { totalElements: 1 } };
            }
            if (url.startsWith('/user/analytics')) {
                return {
                    data: {
                        totalDownloads: 210,
                        totalViews: 420,
                        periodDownloads: 120,
                        previousPeriodDownloads: 80,
                        periodViews: 240,
                        previousPeriodViews: 200,
                        projectMeta: {
                            'project-1': {
                                title: 'Sky Tools',
                                totalDownloads: 210,
                                updatedAt: '2026-06-16T12:00:00Z'
                            }
                        },
                        projectDownloads: { 'project-1': makePoints() },
                        projectViews: { 'project-1': makePoints() }
                    }
                };
            }
            throw new Error(`Unexpected API call: ${url}`);
        });
    });

    afterEach(async () => {
        await act(async () => {
            root.unmount();
        });
        container.remove();
        getMock.mockReset();
    });

    it('keeps downloads and views legend toggles independent even when dataset ids match', async () => {
        await act(async () => {
            root.render(
                <MemoryRouter>
                    <Analytics />
                </MemoryRouter>
            );
        });

        for (let attempt = 0; attempt < 20 && !container.textContent?.includes('Total Views'); attempt += 1) {
            await flush();
        }

        const findLegendButton = (label: string) => Array.from(container.querySelectorAll('button'))
            .find(button => button.textContent?.trim() === label) as HTMLButtonElement | undefined;

        await act(async () => {
            findLegendButton('Overall')?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
        });

        const downloadsOverall = findLegendButton('Overall');
        const totalViews = findLegendButton('Total Views');

        expect(downloadsOverall?.className).toContain('bg-transparent');
        expect(totalViews?.className).toContain('bg-white');
        expect(totalViews?.className).not.toContain('bg-transparent');
    });
});
