import { api } from '@/utils/api';
import type { Project } from '@/types';

export interface SearchParams {
    page: number;
    size: number;
    classification?: string;
    tags?: string;
    search?: string;
    sort: string;
    gameVersion?: string;
    minDownloads?: number;
    minFavorites?: number;
    dateRange?: string;
    category?: string;
}

export const discoveryClient = {
    searchProjects: async (params: SearchParams, signal?: AbortSignal) => {
        const res = await api.get('/projects', { params, signal });
        return res.data;
    },
    getGameVersions: async () => {
        const res = await api.get<string[]>('/meta/game-versions');
        return Array.isArray(res.data) ? res.data : [];
    }
};