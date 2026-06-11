import React, { act } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { ProjectAnalyticsSection } from '@/modules/home/components/FeaturePreviews';

vi.mock('@/components/ui/charts/LineChart', () => ({
    LineChart: () => <div data-testid="line-chart" />
}));

describe('FeaturePreviews ProjectAnalyticsSection', () => {
    let container: HTMLDivElement;
    let root: Root;

    beforeEach(() => {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
    });

    afterEach(async () => {
        await act(async () => {
            root.unmount();
        });
        container.remove();
    });

    it('renders the conversion rate card by default or when showConversionRate is true', async () => {
        await act(async () => {
            root.render(
                <MemoryRouter>
                    <ProjectAnalyticsSection />
                </MemoryRouter>
            );
        });

        expect(container.textContent).toContain('Conversion Rate');
        expect(container.textContent).toContain('Downloads');
        expect(container.textContent).toContain('Views');
        
        const gridContainer = container.querySelector('.grid');
        expect(gridContainer?.className).toContain('md:grid-cols-3');
    });

    it('does not render the conversion rate card when showConversionRate is false', async () => {
        await act(async () => {
            root.render(
                <MemoryRouter>
                    <ProjectAnalyticsSection showConversionRate={false} />
                </MemoryRouter>
            );
        });

        expect(container.textContent).not.toContain('Conversion Rate');
        expect(container.textContent).toContain('Downloads');
        expect(container.textContent).toContain('Views');

        const gridContainer = container.querySelector('.grid');
        expect(gridContainer?.className).not.toContain('md:grid-cols-3');
    });
});
