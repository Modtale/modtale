import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

describe('prefetchProject', () => {
    const get = vi.fn();

    beforeEach(() => {
        vi.resetModules();
        get.mockReset();
    });

    afterEach(() => {
        vi.doUnmock('@/utils/api');
        vi.useRealTimers();
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
        expect(get).toHaveBeenCalledWith('/projects/p1', { timeout: 10_000 });
    });

    it('lets the detail view consume a prefetched project once it resolves', async () => {
        get.mockResolvedValue({ data: { id: 'p1', title: 'Prefetched' } });
        const { consumePrefetchedProject, prefetchProject } = await loadModule();

        prefetchProject('p1');

        await expect(consumePrefetchedProject('p1')).resolves.toEqual({ id: 'p1', title: 'Prefetched' });
        await expect(consumePrefetchedProject('p1')).resolves.toBeNull();
    });

    it('expires cached data before it can replace a fresh detail response', async () => {
        vi.useFakeTimers();
        get.mockResolvedValue({ data: { id: 'p1' } });
        const { prefetchProject, consumePrefetchedProject } = await loadModule();
        prefetchProject('p1');
        await Promise.resolve();
        vi.advanceTimersByTime(60_001);
        await expect(consumePrefetchedProject('p1')).resolves.toBeNull();
        prefetchProject('p1');
        expect(get).toHaveBeenCalledTimes(2);
    });

    it('bounds both completed cache entries and simultaneous hover requests', async () => {
        get.mockImplementation(async (url: string) => ({ data: { id: url } }));
        const { prefetchProject, consumePrefetchedProject } = await loadModule();
        for (let index = 0; index < 60; index++) {
            prefetchProject(`p${index}`);
            await Promise.resolve();
        }
        await expect(consumePrefetchedProject('p0')).resolves.toBeNull();
        await expect(consumePrefetchedProject('p59')).resolves.toEqual({ id: '/projects/p59' });
        get.mockClear().mockImplementation(() => new Promise(() => {}));
        for (let index = 0; index < 60; index++) prefetchProject(`pending${index}`);
        expect(get).toHaveBeenCalledTimes(8);
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
