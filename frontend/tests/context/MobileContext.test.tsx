import React, { act } from 'react';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { createRoot, type Root } from 'react-dom/client';
import { MobileProvider, useMobile } from '@/context/MobileContext';

const Probe = () => {
    const { isMobile } = useMobile();
    return <div id="probe">{isMobile ? 'mobile' : 'desktop'}</div>;
};

const setWindowWidth = (width: number) => {
    Object.defineProperty(window, 'innerWidth', {
        configurable: true,
        writable: true,
        value: width
    });
};

describe('MobileContext', () => {
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

    it('detects mobile widths on mount', async () => {
        setWindowWidth(640);

        await act(async () => {
            root.render(
                <MobileProvider>
                    <Probe />
                </MobileProvider>
            );
        });

        expect(container.querySelector('#probe')?.textContent).toBe('mobile');
    });

    it('updates when the viewport crosses the mobile breakpoint', async () => {
        setWindowWidth(1024);

        await act(async () => {
            root.render(
                <MobileProvider>
                    <Probe />
                </MobileProvider>
            );
        });
        expect(container.querySelector('#probe')?.textContent).toBe('desktop');

        setWindowWidth(500);
        await act(async () => {
            window.dispatchEvent(new Event('resize'));
        });
        expect(container.querySelector('#probe')?.textContent).toBe('mobile');
    });
});
