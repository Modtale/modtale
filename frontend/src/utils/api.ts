import axios, { type InternalAxiosRequestConfig } from 'axios';

const RAW_URL =
    import.meta.env.PUBLIC_API_URL?.trim() || 'http://localhost:8080/api/v1';

export const API_BASE_URL = RAW_URL.endsWith('/')
    ? RAW_URL.slice(0, -1)
    : RAW_URL;

export const BACKEND_URL = new URL(API_BASE_URL).origin;
const WRITE_METHODS = new Set(['post', 'put', 'delete', 'patch']);
const CSRF_COOKIE_NAME = 'XSRF-TOKEN';

type RetriableAxiosConfig = InternalAxiosRequestConfig & {
    _csrfRetryAttempted?: boolean;
    _csrfRefreshAttempted?: boolean;
};

const GENERIC_CLIENT_ERROR_PREFIXES = [
    'request failed',
    'network error',
    'timeout of',
    'canceled'
];

export const getCookie = (name: string): string | null => {
    if (typeof document === 'undefined') return null;
    if (!document.cookie) return null;

    const cookies = document.cookie.split(';');
    for (const c of cookies) {
        const [key, val] = c.trim().split('=');
        if (key === name) {
            return decodeURIComponent(val);
        }
    }
    return null;
};

let csrfRefreshPromise: Promise<void> | null = null;

const shouldAttachCsrfToken = (method?: string) => WRITE_METHODS.has(method?.toLowerCase() || '');

const setCsrfHeader = (config: InternalAxiosRequestConfig, token: string) => {
    if (typeof config.headers?.set === 'function') {
        config.headers.set('X-XSRF-TOKEN', token);
    } else {
        (config.headers as any)['X-XSRF-TOKEN'] = token;
    }
};

const refreshCsrfToken = async () => {
    if (typeof window === 'undefined') return;

    if (!csrfRefreshPromise) {
        csrfRefreshPromise = api.get(`/status?t=${Date.now()}`, {
            headers: { 'Cache-Control': 'no-cache' }
        }).then(() => undefined).finally(() => {
            csrfRefreshPromise = null;
        });
    }

    await csrfRefreshPromise;
};

export const api = axios.create({
    baseURL: API_BASE_URL,
    withCredentials: true,
    headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json'
    }
});

api.interceptors.request.use(
    async (config: InternalAxiosRequestConfig) => {
        if (!config.headers) {
            config.headers = {} as any;
        }

        const retryableConfig = config as RetriableAxiosConfig;
        if (shouldAttachCsrfToken(config.method)) {
            let token = getCookie(CSRF_COOKIE_NAME);
            if (!token && !retryableConfig._csrfRefreshAttempted) {
                retryableConfig._csrfRefreshAttempted = true;
                await refreshCsrfToken();
                token = getCookie(CSRF_COOKIE_NAME);
            }

            if (token) {
                setCsrfHeader(config, token);
            }
        }

        return config;
    },
    error => Promise.reject(error)
);

api.interceptors.response.use(
    response => response,
    async (error) => {
        const config = error.config as RetriableAxiosConfig | undefined;
        if (!config || !shouldAttachCsrfToken(config.method) || config._csrfRetryAttempted || error.response?.status !== 403) {
            return Promise.reject(error);
        }

        config._csrfRetryAttempted = true;

        try {
            await refreshCsrfToken();
            const token = getCookie(CSRF_COOKIE_NAME);
            if (token) {
                if (!config.headers) {
                    config.headers = {} as any;
                }
                setCsrfHeader(config, token);
            }
            return await api.request(config);
        } catch {
            return Promise.reject(error);
        }
    }
);

export const extractApiErrorMessage = (error: unknown, fallback: string): string => {
    const trimTrailingPunctuation = (value: string) => value.trim().replace(/[.:;\s]+$/, '');
    const ensureSentence = (value: string) => {
        const trimmed = value.trim();
        if (!trimmed) return '';
        return /[.!?]$/.test(trimmed) ? trimmed : `${trimmed}.`;
    };

    const normalizeText = (value: unknown): string | null => {
        if (typeof value !== 'string') {
            return null;
        }

        const trimmed = value.trim();
        return trimmed ? trimmed : null;
    };

    const extractPayloadMessage = (payload: unknown): string | null => {
        const directText = normalizeText(payload);
        if (directText) {
            return directText;
        }

        if (Array.isArray(payload)) {
            const messages = payload
                .map(item => extractPayloadMessage(item))
                .filter((item): item is string => Boolean(item));
            return messages.length > 0 ? messages.join(' ') : null;
        }

        if (!payload || typeof payload !== 'object') {
            return null;
        }

        const payloadRecord = payload as Record<string, unknown>;
        const keysToCheck = ['error', 'message', 'detail', 'details', 'reason'] as const;

        for (const key of keysToCheck) {
            const text = extractPayloadMessage(payloadRecord[key]);
            if (text) {
                return text;
            }
        }

        const errors = extractPayloadMessage(payloadRecord.errors);
        return errors || null;
    };

    const isGenericClientMessage = (message: string) => {
        const lower = message.toLowerCase();
        return GENERIC_CLIENT_ERROR_PREFIXES.some(prefix => lower === prefix || lower.startsWith(prefix));
    };

    const combineMessages = (baseFallback: string, detail?: string | null) => {
        const normalizedFallback = trimTrailingPunctuation(baseFallback);
        const normalizedDetail = detail?.trim() || '';

        if (!normalizedDetail) {
            return baseFallback.trim() || ensureSentence(normalizedFallback || fallback);
        }

        if (!normalizedFallback) {
            return normalizedDetail;
        }

        const lowerFallback = normalizedFallback.toLowerCase();
        const lowerDetail = normalizedDetail.toLowerCase();

        if (
            lowerDetail === lowerFallback ||
            lowerDetail.startsWith(`${lowerFallback}:`) ||
            lowerDetail.startsWith(`${lowerFallback}.`)
        ) {
            return normalizedDetail;
        }

        return `${ensureSentence(normalizedFallback)} ${normalizedDetail}`;
    };

    const buildStatusFallback = () => {
        const status = (error as any)?.response?.status as number | undefined;
        const code = (error as any)?.code as string | undefined;
        const errorMessage = error instanceof Error ? error.message.toLowerCase() : '';

        if (code === 'ECONNABORTED') {
            return 'The request took too long to complete. Please try again in a moment.';
        }

        if (errorMessage.includes('failed to fetch') || errorMessage === 'network error') {
            return 'We could not reach the server. Check your internet connection and try again.';
        }

        if (errorMessage.includes('canceled')) {
            return 'The request was interrupted before it finished. Please try again.';
        }

        switch (status) {
            case 400:
            case 422:
                return 'The server rejected part of this request. Review the details and try again.';
            case 401:
                return 'You need to sign in again before trying this action. Your session may have expired.';
            case 403:
                return 'Your account is signed in, but it does not have permission to do this.';
            case 404:
                return 'The item you were trying to access could not be found.';
            case 409:
                return 'This action conflicted with the current state of the data. Refresh and try again.';
            case 413:
                return 'The upload was too large for the server to accept.';
            case 415:
                return 'The server does not support the format that was submitted.';
            case 429:
                return 'Too many requests were sent too quickly. Please wait a moment and try again.';
            default:
                if (typeof status === 'number' && status >= 500) {
                    return 'The server ran into an unexpected problem while handling this request. Please try again shortly.';
                }
                return null;
        }
    };

    if (typeof error === 'string' && error.trim()) {
        return error.trim();
    }

    const payloadMessage = extractPayloadMessage((error as any)?.response?.data);
    if (payloadMessage) {
        return combineMessages(fallback, payloadMessage);
    }

    if (error instanceof Error && error.message.trim() && !isGenericClientMessage(error.message)) {
        return combineMessages(fallback, error.message);
    }

    return combineMessages(fallback, buildStatusFallback());
};
