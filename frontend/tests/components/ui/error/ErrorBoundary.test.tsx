import React, { act } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { createRoot, type Root } from 'react-dom/client';
import { ErrorBoundary } from '@/components/ui/error/ErrorBoundary';
import { captureError } from '@/utils/errorTracking';

vi.mock('@/utils/errorTracking', () => ({
    captureError: vi.fn()
}));

const mockedCaptureError = vi.mocked(captureError);

const ThrowingChild = () => {
    throw new Error('framework render failure');
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

    it('renders the fallback UI and reports render-time framework errors', async () => {
        const container = document.createElement('div');
        document.body.appendChild(container);
        const root: Root = createRoot(container);
        const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});

        await act(async () => {
            root.render(
                <ErrorBoundary>
                    <ThrowingChild />
                </ErrorBoundary>
            );
        });

        await settle();

        expect(container.textContent).toContain('Application Error');
        expect(container.textContent).toContain('Error: framework render failure');
        expect(container.querySelector('button')?.textContent).toContain('Reload Application');
        expect(mockedCaptureError).toHaveBeenCalledTimes(1);
        expect(mockedCaptureError).toHaveBeenCalledWith(
            expect.any(Error),
            expect.objectContaining({ componentStack: expect.any(String) })
        );
        expect(consoleError).toHaveBeenCalledWith(
            'Uncaught error:',
            expect.any(Error),
            expect.objectContaining({ componentStack: expect.any(String) })
        );

        await act(async () => {
            root.unmount();
        });
        container.remove();
        consoleError.mockRestore();
    });
});
