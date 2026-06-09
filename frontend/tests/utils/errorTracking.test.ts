import { describe, expect, it, vi, afterEach } from 'vitest';

const sentryMocks = vi.hoisted(() => ({
    init: vi.fn(),
    captureException: vi.fn(),
    browserTracingIntegration: vi.fn(() => ({ name: 'browserTracing' })),
    replayIntegration: vi.fn(() => ({ name: 'replay' }))
}));

vi.mock('@sentry/react', () => sentryMocks);

import { captureError, initErrorTracking } from '@/utils/errorTracking';

describe('error tracking utils', () => {
    afterEach(() => {
        vi.clearAllMocks();
    });

    it('initializes sentry with tracing, replay, and the current mode', async () => {
        await initErrorTracking();

        expect(sentryMocks.browserTracingIntegration).toHaveBeenCalledTimes(1);
        expect(sentryMocks.replayIntegration).toHaveBeenCalledTimes(1);
        expect(sentryMocks.init).toHaveBeenCalledWith({
            dsn: 'https://c26ba856731fbe1677a3057b019b7d13@o4510421984346112.ingest.us.sentry.io/4510421986770944',
            integrations: [
                { name: 'browserTracing' },
                { name: 'replay' }
            ],
            tracesSampleRate: 1.0,
            replaysSessionSampleRate: 0.1,
            replaysOnErrorSampleRate: 1.0,
            environment: import.meta.env.MODE
        });
    });

    it('logs and captures errors with optional context', async () => {
        const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});
        const error = new Error('boom');

        await captureError(error, { route: '/dashboard' });

        expect(consoleError).toHaveBeenCalledWith('Caught Error:', error);
        expect(sentryMocks.captureException).toHaveBeenCalledWith(error, {
            extra: { route: '/dashboard' }
        });
    });
});
