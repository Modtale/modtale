import { act } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from 'vitest';
import { createRoot, type Root } from 'react-dom/client';
import { PlatformAnalytics } from '@/modules/admin/components/PlatformAnalytics';
import { adminClient } from '@/modules/admin/api/adminClient';

vi.mock('@/modules/admin/api/adminClient', () => ({
    adminClient: {
        getPlatformAnalytics: vi.fn()
    }
}));

vi.mock('@/components/ui/charts/LineChart', () => ({
    LineChart: ({ datasets }: any) => (
        <div>
            {datasets.map((dataset: any) => (
                <span key={dataset.id} data-series={dataset.id} data-values={JSON.stringify(dataset.data)}>{dataset.label}</span>
            ))}
        </div>
    )
}));

const points = [
    { date: '2026-06-24', count: 1 },
    { date: '2026-06-25', count: -2 },
    { date: '2026-06-26', count: 3 }
];

const platformAnalytics = {
    totalDownloads: 800,
    previousTotalDownloads: 600,
    totalViews: 1200,
    previousTotalViews: 1000,
    apiDownloads: 200,
    previousApiDownloads: 150,
    frontendDownloads: 600,
    previousFrontendDownloads: 450,
    totalNewProjects: -2,
    previousTotalNewProjects: 4,
    totalNewUsers: 5,
    previousTotalNewUsers: -2,
    totalNewOrgs: 1,
    previousTotalNewOrgs: 0,
    downloadsChart: points,
    apiDownloadsChart: points,
    launcherDownloadsChart: Array.from({ length: 15 }, (_, index) => ({ date: `2026-06-${String(index + 1).padStart(2, '0')}`, count: index + 10 })),
    viewsChart: points,
    newProjectsChart: points,
    newUsersChart: points,
    newOrgsChart: points
};

const flush = async () => {
    await act(async () => {
        await new Promise(resolve => setTimeout(resolve, 0));
    });
};

describe('PlatformAnalytics', () => {
    let container: HTMLDivElement;
    let root: Root;
    const getPlatformAnalytics = adminClient.getPlatformAnalytics as unknown as Mock;

    beforeEach(() => {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        getPlatformAnalytics.mockResolvedValue(platformAnalytics);
    });

    afterEach(async () => {
        await act(async () => {
            root.unmount();
        });
        container.remove();
        getPlatformAnalytics.mockReset();
    });

    it('shows net new users and projects instead of API traffic share', async () => {
        await act(async () => {
            root.render(<PlatformAnalytics />);
        });

        for (let attempt = 0; attempt < 20 && !container.textContent?.includes('Platform Analytics'); attempt += 1) {
            await flush();
        }

        expect(getPlatformAnalytics).toHaveBeenCalledWith('30d');
        expect(container.textContent).toContain('Net New Users');
        expect(container.textContent).toContain('Net New Projects');
        expect(container.textContent).toContain('Net New Organizations');
        expect(container.textContent).toContain('-2');
        expect(container.textContent).toContain('350.0%');
        expect(container.textContent).not.toContain('API Traffic');
        expect(container.textContent).toContain('Launcher Downloads');
        expect(JSON.parse(container.querySelector('[data-series=launcherDownloads]')!.getAttribute('data-values')!))
            .toEqual([{ date: '2026-06-15', value: 24 }]);
        expect(container.textContent).toContain('API Downloads');
        expect(container.textContent).toContain('Platform Downloads');
    });
});
