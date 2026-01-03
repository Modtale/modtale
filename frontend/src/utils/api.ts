import axios from 'axios';

const RAW_URL =
    import.meta.env.PUBLIC_API_URL || 'https://api.modtale.net/api';

export const API_BASE_URL = RAW_URL.endsWith('/')
    ? RAW_URL.slice(0, -1)
    : RAW_URL;

export const BACKEND_URL = API_BASE_URL.replace(/\/api(\/v1)?$/, '');

export const getCookie = (name: string): string | null => {
    if (typeof document === 'undefined') return null;
    if (!document.cookie) return null;

    const xsrfCookies = document.cookie
        .split(';')
        .map(c => c.trim())
        .filter(c => c.startsWith(name + '='));

    if (xsrfCookies.length === 0) return null;
    return decodeURIComponent(xsrfCookies[0].split('=')[1]);
};

export const api = axios.create({
    baseURL: API_BASE_URL,
    withCredentials: true,
    headers: { Accept: 'application/json' }
});

api.interceptors.request.use(
    (config: any) => {
        config.withCredentials = true;

        if (['post', 'put', 'delete', 'patch'].includes(config.method?.toLowerCase() || '')) {
            const token = getCookie('XSRF-TOKEN');
            if (token && config.headers) {
                config.headers.set('X-XSRF-TOKEN', token);
            }
        }

        return config;
    },
    error => Promise.reject(error)
);
