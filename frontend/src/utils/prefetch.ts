import { api } from './api';
import type { Project } from '@/types';

const prefetchedProjects = new Map<string, Project | null>();
const prefetchedProjectPromises = new Map<string, Promise<Project | null>>();

export const prefetchProject = (id: string) => {
    if (!id || prefetchedProjects.has(id) || prefetchedProjectPromises.has(id)) return;

    const request = api.get<Project>(`/projects/${id}`)
        .then((res) => {
            const project = res.data ?? null;
            prefetchedProjects.set(id, project);
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
        const project = prefetchedProjects.get(id) ?? null;
        prefetchedProjects.delete(id);
        return project;
    }

    const pending = prefetchedProjectPromises.get(id);
    if (!pending) return null;

    const project = await pending;
    prefetchedProjects.delete(id);
    prefetchedProjectPromises.delete(id);
    return project;
};
