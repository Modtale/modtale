import { api, API_BASE_URL } from '@/utils/api';

export interface WorldModListItem {
    id: string;
    modId?: string;
    projectId?: string;
    slug?: string;
    title: string;
    authorId?: string;
    author?: string;
    description?: string;
    versionNumber?: string;
    classification?: string;
    source?: string;
    externalId?: string;
    externalUrl?: string;
    icon?: string;
    bannerUrl?: string;
    downloadCount?: number;
    favoriteCount?: number;
    updatedAt?: string;
    downloadable: boolean;
    unavailableReason?: string;
}

export interface WorldModList {
    id: string;
    title: string;
    worldName?: string;
    gameVersion?: string;
    ownerUsername?: string;
    createdAt: string;
    lastViewedAt: string;
    expiresAt: string;
    viewCount: number;
    downloadCount: number;
    modCount: number;
    downloadableCount: number;
    shareUrl: string;
    downloadUrl: string;
    launcherInstallUrl: string;
    mods: WorldModListItem[];
}

export const worldListDownloadUrl = (id: string) => `${API_BASE_URL}/lists/${encodeURIComponent(id)}/download`;

export const worldListClient = {
    async get(id: string) {
        const response = await api.get<WorldModList>(`/lists/${encodeURIComponent(id)}`);
        return response.data;
    }
};
