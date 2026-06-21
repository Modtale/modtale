import React, { act } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { createRoot, type Root } from 'react-dom/client';
import { ErrorBoundary } from '@/components/ui/error/ErrorBoundary';
import { captureError } from '@/utils/errorTracking';

vi.mock('@/utils/errorTracking', () => ({
    captureError: vi.fn()
}));

const mockedCaptureError = vi.mocked(captureError);

const ThrowingReferenceErrorChild = () => {
    throw new ReferenceError('framework render failure');
};

const settle = async (times = 4) => {
    for (let i = 0; i < times; i += 1) {
        await act(async () => {
            await Promise.resolve();
        });
    }
};

describe('ErrorBoundary', () => {
    afterEach(() => {
        vi.clearAllMocks();
    });

    it('renders the fallback UI and reports render-time ReferenceErrors', async () => {
        const container = document.createElement('div');
        document.body.appendChild(container);
        const root: Root = createRoot(container);
        const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});

        await act(async () => {
            root.render(
                <ErrorBoundary>
                    <ThrowingReferenceErrorChild />
                </ErrorBoundary>
            );
        });

        await settle();

        expect(container.textContent).toContain('Application Error');
        expect(container.textContent).toContain('ReferenceError: framework render failure');
        expect(container.querySelector('button')?.textContent).toContain('Reload Application');
        expect(mockedCaptureError).toHaveBeenCalledTimes(1);
        expect(mockedCaptureError.mock.calls[0]?.[0]).toBeInstanceOf(ReferenceError);
        expect(mockedCaptureError.mock.calls[0]?.[1]).toEqual(
            expect.objectContaining({ componentStack: expect.any(String) })
        );
        expect(consoleError).toHaveBeenCalledWith(
            'Uncaught error:',
            expect.any(ReferenceError),
            expect.objectContaining({ componentStack: expect.any(String) })
        );

        await act(async () => {
            root.unmount();
        });
        container.remove();
        consoleError.mockRestore();
    });
});
