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
    if (typeof error === 'string' && error.trim()) {
        return error;
    }

    if (error instanceof Error && error.message.trim()) {
        const data = (error as any).response?.data;

        if (typeof data === 'string' && data.trim()) {
            return data;
        }

        if (data && typeof data === 'object') {
            const errorMessage = 'error' in data && typeof data.error === 'string' ? data.error : null;
            if (errorMessage?.trim()) {
                return errorMessage;
            }

            const message = 'message' in data && typeof data.message === 'string' ? data.message : null;
            if (message?.trim()) {
                return message;
            }
        }

        return error.message;
    }

    return fallback;
};
