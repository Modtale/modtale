export interface ConnectedAccount {
    provider: string;
    providerId: string;
    username: string;
    profileUrl: string;
    visible: boolean;
}

export interface OrganizationMember {
    userId: string;
    role: 'ADMIN' | 'MEMBER';
}

export interface User {
    id: string;
    username: string;
    displayName?: string;
    avatarUrl: string;
    bannerUrl?: string;
    bio?: string;
    email?: string;
    createdAt?: string;
    likedModIds: string[];
    likedModpackIds: string[];
    followingIds?: string[];
    followerIds?: string[];
    connectedAccounts?: ConnectedAccount[];
    badges?: string[];
    notificationPreferences?: {
        projectUpdates: 'OFF' | 'ON' | 'EMAIL';
        creatorUploads: 'OFF' | 'ON' | 'EMAIL';
        newReviews: 'OFF' | 'ON' | 'EMAIL';
        newFollowers: 'OFF' | 'ON' | 'EMAIL';
        dependencyUpdates: 'OFF' | 'ON' | 'EMAIL';
    };
    roles?: string[];
    tier?: string;
    accountType?: 'USER' | 'ORGANIZATION';
    organizationMembers?: OrganizationMember[];
}

export interface ModDependency {
    modId: string;
    modTitle: string;
    versionNumber: string;
    isOptional?: boolean;
}

export interface ProjectVersion {
    id: string;
    versionNumber: string;
    gameVersion: string;
    gameVersions?: string[];
    fileUrl: string;
    downloadCount: number;
    releaseDate: string;
    changelog?: string;
    dependencies?: ModDependency[];
    channel?: 'RELEASE' | 'BETA' | 'ALPHA';
}

export interface Review {
    id: string;
    user: string;
    userAvatarUrl?: string;
    comment: string;
    rating: number;
    date: string;
    version: string;
}

export interface Mod {
    id: string;
    slug?: string;
    title: string;
    about?: string;
    description: string;
    author: string;
    imageUrl: string;
    bannerUrl?: string;
    license?: string;
    links?: Record<string, string>;
    classification: 'PLUGIN' | 'DATA' | 'ART' | 'SAVE' | 'MODPACK';
    categories: string[];
    category?: string;
    tags?: string[];
    downloadCount: number;
    favoriteCount: number;
    rating: number;
    updatedAt: string;
    createdAt?: string;
    modIds?: string[];
    childProjectIds?: string[];
    sizeBytes?: number;
    reviews: Review[];
    versions: ProjectVersion[];
    galleryImages: string[];
    repositoryUrl?: string;
    contributors?: string[];
    pendingInvites?: string[];
    lastTrendingNotification?: string;
    allowModpacks?: boolean;
    status?: 'DRAFT' | 'PENDING' | 'PUBLISHED' | 'UNLISTED' | 'DELETED' | 'ARCHIVED';
    expiresAt?: string;
}

export type Modpack = Mod;
export type World = Mod;

export interface AnalyticsDataPoint {
    date: string;
    value: number;
    count: number;
}

export interface ProjectMeta {
    id: string;
    title: string;
    currentRating: number;
    totalDownloads: number;
}

export interface CreatorAnalytics {
    totalDownloads: number;
    totalViews: number;
    periodDownloads: number;
    previousPeriodDownloads: number;
    periodViews: number;
    previousPeriodViews: number;
    projectDownloads: Record<string, AnalyticsDataPoint[]>;
    projectViews: Record<string, AnalyticsDataPoint[]>;
    projectMeta: Record<string, ProjectMeta>;
}

export interface ProjectAnalyticsDetail {
    projectId: string;
    projectTitle: string;
    totalDownloads: number;
    totalViews: number;
    versionDownloads: Record<string, AnalyticsDataPoint[]>;
    views: AnalyticsDataPoint[];
    ratingHistory: AnalyticsDataPoint[];
}