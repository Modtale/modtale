import React, { act } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createRoot, type Root } from 'react-dom/client';

import App from '@/App';

const projectRouteStats = vi.hoisted(() => ({
    mounts: 0,
    unmounts: 0
}));

vi.mock('@/modules/project/views/ProjectDetails', async () => {
    const React = await import('react');
    const { useLocation, useNavigate } = await import('react-router-dom');

    return {
        ProjectDetails: () => {
            const location = useLocation();
            const navigate = useNavigate();

            React.useEffect(() => {
                projectRouteStats.mounts += 1;
                return () => {
                    projectRouteStats.unmounts += 1;
                };
            }, []);

            return React.createElement(
                'section',
                { 'data-testid': 'project-detail', 'data-path': location.pathname },
                React.createElement(
                    'button',
                    {
                        type: 'button',
                        'data-testid': 'close-project-modal',
                        onClick: () => navigate('/mod/skyforge')
                    },
                    'Close'
                )
            );
        }
    };
});

describe('App project routes', () => {
    let container: HTMLDivElement;
    let root: Root;

    beforeEach(() => {
        projectRouteStats.mounts = 0;
        projectRouteStats.unmounts = 0;
        vi.spyOn(window, 'scrollTo').mockImplementation(() => {});
        window.history.replaceState(null, '', '/');

        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
    });

    afterEach(async () => {
        await act(async () => {
            root.unmount();
        });
        container.remove();
        window.history.replaceState(null, '', '/');
    });

    it('keeps the project detail instance mounted when closing a modal subroute', async () => {
        window.history.replaceState(null, '', '/mod/skyforge/download');

        await act(async () => {
            root.render(<App initialPath="/mod/skyforge/download" ssrData={null} />);
        });

        expect(projectRouteStats.mounts).toBe(1);
        expect(projectRouteStats.unmounts).toBe(0);
        expect(container.querySelector('[data-testid="project-detail"]')?.getAttribute('data-path'))
            .toBe('/mod/skyforge/download');

        const closeButton = container.querySelector('[data-testid="close-project-modal"]') as HTMLButtonElement;

        await act(async () => {
            closeButton.click();
        });

        expect(window.location.pathname).toBe('/mod/skyforge');
        expect(container.querySelector('[data-testid="project-detail"]')?.getAttribute('data-path'))
            .toBe('/mod/skyforge');
        expect(projectRouteStats.mounts).toBe(1);
        expect(projectRouteStats.unmounts).toBe(0);
    });
});
