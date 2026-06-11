import { beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from '@/utils/api';
import { discoveryClient } from '@/modules/discovery/api/discoveryClient';

vi.mock('@/utils/api', () => ({
    api: {
        get: vi.fn(),
        post: vi.fn(),
        put: vi.fn(),
        delete: vi.fn()
    }
}));

const mockedApi = vi.mocked(api);

describe('discoveryClient', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('forwards search params and abort signals to the shared api client', async () => {
        const signal = new AbortController().signal;
        const params = { page: 2, size: 24, search: 'sky', sort: 'relevance', category: 'trending' };
        mockedApi.get.mockResolvedValue({ data: { content: [] } } as any);

        await discoveryClient.searchProjects(params, signal);

        expect(mockedApi.get).toHaveBeenCalledWith('/projects', { params, signal });
    });

    it('returns only array game version payloads', async () => {
        mockedApi.get.mockResolvedValueOnce({ data: ['1.0.0'] } as any);
        await expect(discoveryClient.getGameVersions()).resolves.toEqual(['1.0.0']);

        mockedApi.get.mockResolvedValueOnce({ data: { versions: ['1.1.0'] } } as any);
        await expect(discoveryClient.getGameVersions()).resolves.toEqual([]);
    });
});
