import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api, extractApiErrorMessage, getCookie } from '@/utils/api';

const clearCookie = (name: string) => {
    document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`;
};

describe('api utils', () => {
    beforeEach(() => {
        clearCookie('session');
        clearCookie('XSRF-TOKEN');
    });

    afterEach(() => {
        clearCookie('session');
        clearCookie('XSRF-TOKEN');
    });

    it('reads cookie values and decodes them', () => {
        document.cookie = 'session=abc123; path=/';
        document.cookie = 'XSRF-TOKEN=csrf%20token; path=/';

        expect(getCookie('session')).toBe('abc123');
        expect(getCookie('XSRF-TOKEN')).toBe('csrf token');
        expect(getCookie('missing')).toBeNull();
    });

    it('adds the xsrf token header for mutating requests', async () => {
        document.cookie = 'XSRF-TOKEN=csrf-token; path=/';
        const handler = (api.interceptors.request as any).handlers[0].fulfilled;

        const config = await handler({
            headers: {},
            method: 'post'
        });

        expect(config.headers['X-XSRF-TOKEN']).toBe('csrf-token');
    });

    it('uses the header set function when axios headers expose it', async () => {
        document.cookie = 'XSRF-TOKEN=csrf-token; path=/';
        const handler = (api.interceptors.request as any).handlers[0].fulfilled;
        const set = vi.fn();

        await handler({
            headers: { set },
            method: 'patch'
        });

        expect(set).toHaveBeenCalledWith('X-XSRF-TOKEN', 'csrf-token');
    });

    it('leaves non-mutating requests unchanged', async () => {
        document.cookie = 'XSRF-TOKEN=csrf-token; path=/';
        const handler = (api.interceptors.request as any).handlers[0].fulfilled;

        const config = await handler({
            headers: {},
            method: 'get'
        });

        expect(config.headers['X-XSRF-TOKEN']).toBeUndefined();
    });

    it('extracts the most useful api error message available', () => {
        expect(extractApiErrorMessage('Plain string', 'Fallback')).toBe('Plain string');

        const responseStringError = new Error('Request failed') as Error & {
            response?: { data?: unknown };
        };
        responseStringError.response = { data: 'Readable backend error' };
        expect(extractApiErrorMessage(responseStringError, 'Fallback')).toBe('Readable backend error');

        const responseObjectError = new Error('Request failed') as Error & {
            response?: { data?: unknown };
        };
        responseObjectError.response = { data: { error: 'Validation failed' } };
        expect(extractApiErrorMessage(responseObjectError, 'Fallback')).toBe('Validation failed');

        const responseMessageError = new Error('Request failed') as Error & {
            response?: { data?: unknown };
        };
        responseMessageError.response = { data: { message: 'Token expired' } };
        expect(extractApiErrorMessage(responseMessageError, 'Fallback')).toBe('Token expired');

        expect(extractApiErrorMessage(new Error('Network offline'), 'Fallback')).toBe('Network offline');
        expect(extractApiErrorMessage({ bad: 'shape' }, 'Fallback')).toBe('Fallback');
    });
});
