import { act } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createRoot, type Root } from 'react-dom/client';
import { LineChart } from '@/components/ui/charts/LineChart';

describe('LineChart', () => {
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

    it('reports the next hidden state when toggling hidden legend items', async () => {
        const onToggle = vi.fn();

        await act(async () => {
            root.render(
                <LineChart
                    datasets={[
                        {
                            id: 'momentum',
                            label: 'Momentum',
                            color: '#10b981',
                            hidden: true,
                            data: [{ date: '2026-06-01', value: 12 }]
                        }
                    ]}
                    onToggle={onToggle}
                />
            );
        });

        const button = container.querySelector('button');
        expect(button).not.toBeNull();

        await act(async () => {
            button?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
        });

        expect(onToggle).toHaveBeenCalledWith('momentum', false);
    });
    it('distinguishes genuinely empty earnings from deliberately hidden series', async () => {
        const series = { id: 'earnings', label: 'Earnings', color: '#10b981', data: [] };
        await act(async () => root.render(<LineChart datasets={[series]} emptyMessage="No earnings yet." />));
        expect(container.textContent).toContain('No earnings yet.');
        expect(container.textContent).not.toContain('Toggle items');
        await act(async () => root.render(<LineChart datasets={[{ ...series, hidden: true }]} onToggle={vi.fn()} emptyMessage="No earnings yet." />));
        expect(container.textContent).toContain('No data selected. Toggle items above.');
    });
});
