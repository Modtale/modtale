import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

describe('prefetchProject', () => {
    const get = vi.fn();

    beforeEach(() => {
        vi.resetModules();
        get.mockReset();
    });

    afterEach(() => {
        vi.doUnmock('@/utils/api');
    });

    const loadModule = async () => {
        vi.doMock('@/utils/api', () => ({
            api: {
                get
            }
        }));

        return import('@/utils/prefetch');
    };

    it('ignores empty project ids', async () => {
        const { prefetchProject } = await loadModule();

        prefetchProject('');

        expect(get).not.toHaveBeenCalled();
    });

    it('fetches a project only once while the id is cached', async () => {
        get.mockResolvedValue({});
        const { prefetchProject } = await loadModule();

        prefetchProject('p1');
        prefetchProject('p1');

        expect(get).toHaveBeenCalledTimes(1);
        expect(get).toHaveBeenCalledWith('/projects/p1');
    });

    it('evicts failed prefetches so they can be retried', async () => {
        get.mockRejectedValueOnce(new Error('boom')).mockResolvedValueOnce({});
        const { prefetchProject } = await loadModule();

        prefetchProject('p1');
        await get.mock.results[0]?.value.catch(() => undefined);
        prefetchProject('p1');

        expect(get).toHaveBeenCalledTimes(2);
    });
});
