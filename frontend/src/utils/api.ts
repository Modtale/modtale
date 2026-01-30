import axios, { type InternalAxiosRequestConfig } from 'axios';

const RAW_URL =
    import.meta.env.PUBLIC_API_URL || 'https://api.modtale.net/api';

export const API_BASE_URL = RAW_URL.endsWith('/')
    ? RAW_URL.slice(0, -1)
    : RAW_URL;

export const BACKEND_URL = new URL(API_BASE_URL).origin;

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

export const api = axios.create({
    baseURL: API_BASE_URL,
    withCredentials: true,
    headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
    }
});

api.interceptors.request.use(
    (config: InternalAxiosRequestConfig) => {
        if (!config.headers) {
            config.headers = {} as any;
        }

        if (['post', 'put', 'delete', 'patch'].includes(config.method?.toLowerCase() || '')) {
            const token = getCookie('XSRF-TOKEN');
            if (token) {
                if (typeof config.headers.set === 'function') {
                    config.headers.set('X-XSRF-TOKEN', token);
                } else {
                    (config.headers as any)['X-XSRF-TOKEN'] = token;
                }
            }
        }

        return config;
    },
    error => Promise.reject(error)
);