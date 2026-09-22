import { api } from './api';
import type { Project } from '@/types';

const MAX_PREFETCHED_PROJECTS = 50;
const MAX_PENDING_PREFETCHES = 8;
const PREFETCH_TTL_MS = 60_000;
const prefetchedProjects = new Map<string, { project: Project | null; expiresAt: number }>();
const prefetchedProjectPromises = new Map<string, Promise<Project | null>>();

export const prefetchProject = (id: string) => {
    const now = Date.now();
    for (const [key, entry] of prefetchedProjects) {
        if (entry.expiresAt <= now) prefetchedProjects.delete(key);
    }
    if (!id || prefetchedProjects.has(id) || prefetchedProjectPromises.has(id)
        || prefetchedProjectPromises.size >= MAX_PENDING_PREFETCHES) return;

    const request = api.get<Project>(`/projects/${encodeURIComponent(id)}`, { timeout: 10_000 })
        .then((res) => {
            const project = res.data ?? null;
            prefetchedProjects.set(id, { project, expiresAt: Date.now() + PREFETCH_TTL_MS });
            if (prefetchedProjects.size > MAX_PREFETCHED_PROJECTS) {
                const oldest = prefetchedProjects.keys().next().value;
                if (oldest !== undefined) prefetchedProjects.delete(oldest);
            }
            prefetchedProjectPromises.delete(id);
            return project;
        })
        .catch(() => {
            prefetchedProjects.delete(id);
            prefetchedProjectPromises.delete(id);
            return null;
        });

    prefetchedProjectPromises.set(id, request);
};

export const consumePrefetchedProject = async (id: string) => {
    if (!id) return null;

    if (prefetchedProjects.has(id)) {
        const entry = prefetchedProjects.get(id)!;
        prefetchedProjects.delete(id);
        return entry.expiresAt > Date.now() ? entry.project : null;
    }

    const pending = prefetchedProjectPromises.get(id);
    if (!pending) return null;

    const project = await pending;
    prefetchedProjects.delete(id);
    prefetchedProjectPromises.delete(id);
    return project;
};