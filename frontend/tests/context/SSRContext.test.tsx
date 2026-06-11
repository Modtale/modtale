import React, { act } from 'react';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { SSRProvider, useSSRData } from '@/context/SSRContext';

const Probe = () => {
    const { initialData, isInitialRoute } = useSSRData();

    return (
        <div id="probe" data-initial-route={String(isInitialRoute)}>
            {initialData ? initialData.title : 'none'}
        </div>
    );
};

describe('SSRContext', () => {
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

    it('returns initial data for the original route even when trailing slashes differ', async () => {
        await act(async () => {
            root.render(
                <MemoryRouter initialEntries={['/projects/']}>
                    <SSRProvider data={{ title: 'Sky Tools' }} initialPath="/projects">
                        <Probe />
                    </SSRProvider>
                </MemoryRouter>
            );
        });

        const probe = container.querySelector('#probe') as HTMLDivElement;
        expect(probe.dataset.initialRoute).toBe('true');
        expect(probe.textContent).toBe('Sky Tools');
    });

    it('suppresses initial data after navigating to a different route', async () => {
        await act(async () => {
            root.render(
                <MemoryRouter initialEntries={['/browse']}>
                    <SSRProvider data={{ title: 'Sky Tools' }} initialPath="/projects">
                        <Probe />
                    </SSRProvider>
                </MemoryRouter>
            );
        });

        const probe = container.querySelector('#probe') as HTMLDivElement;
        expect(probe.dataset.initialRoute).toBe('false');
        expect(probe.textContent).toBe('none');
    });
});
