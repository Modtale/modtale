import * as Sentry from '@sentry/react';

export const initErrorTracking = () => {
    Sentry.init({
        dsn: "https://c26ba856731fbe1677a3057b019b7d13@o4510421984346112.ingest.us.sentry.io/4510421986770944", // TODO: Replace with your actual Sentry DSN
        integrations: [
            Sentry.browserTracingIntegration(),
            Sentry.replayIntegration(),
        ],

        tracesSampleRate: 1.0,

        replaysSessionSampleRate: 0.1,
        replaysOnErrorSampleRate: 1.0,

        environment: import.meta.env?.MODE || 'development',
    });
};

export const captureError = (error: unknown, context?: Record<string, any>) => {
    console.error("Caught Error:", error);
    Sentry.captureException(error, {
        extra: context
    });
};