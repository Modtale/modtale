import { api } from './api';

const prefetchCache = new Set<string>();

export const prefetchProject = (id: string) => {
    if (!id || prefetchCache.has(id)) return;

    prefetchCache.add(id);

    api.get(`/projects/${id}`).catch(() => {
        prefetchCache.delete(id);
    });
};