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

    it('lets the detail view consume a prefetched project once it resolves', async () => {
        get.mockResolvedValue({ data: { id: 'p1', title: 'Prefetched' } });
        const { consumePrefetchedProject, prefetchProject } = await loadModule();

        prefetchProject('p1');

        await expect(consumePrefetchedProject('p1')).resolves.toEqual({ id: 'p1', title: 'Prefetched' });
        await expect(consumePrefetchedProject('p1')).resolves.toBeNull();
    });

    it('evicts failed prefetches so they can be retried', async () => {
        get.mockRejectedValueOnce(new Error('boom')).mockResolvedValueOnce({});
        const { prefetchProject, consumePrefetchedProject } = await loadModule();

        prefetchProject('p1');
        await consumePrefetchedProject('p1');
        prefetchProject('p1');

        expect(get).toHaveBeenCalledTimes(2);
    });
});
