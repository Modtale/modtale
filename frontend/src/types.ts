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
    emailVerified?: boolean;
    createdAt?: string;
    likedModIds: string[];
    followingIds?: string[];
    followerIds?: string[];
    connectedAccounts?: ConnectedAccount[];
    badges?: string[];
    notificationPreferences?: {
        projectUpdates: 'OFF' | 'ON';
        creatorUploads: 'OFF' | 'ON';
        newReviews: 'OFF' | 'ON';
        newFollowers: 'OFF' | 'ON';
        dependencyUpdates: 'OFF' | 'ON';
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

export interface ScanIssue {
    severity: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
    type: string;
    description: string;
    filePath: string;
    lineStart: number;
    lineEnd: number;
    resolved?: boolean;
}

export interface ScanResult {
    status: 'CLEAN' | 'SUSPICIOUS' | 'INFECTED' | 'FAILED';
    riskScore: number;
    issues: ScanIssue[];
    scanTimestamp: number;
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
    scanResult?: ScanResult;
    reviewStatus?: 'PENDING' | 'APPROVED' | 'REJECTED';
    rejectionReason?: string;
}

export interface Review {
    id: string;
    user: string;
    userAvatarUrl?: string;
    comment: string;
    rating: number;
    date: string;
    version: string;
    updatedAt?: string;
    developerReply?: string;
    developerReplyDate?: string;
}

export interface Mod {
    id: string;
    slug?: string;
    title: string;
    about?: string;
    description: string;
    authorId: string;
    author: string;
    imageUrl: string;
    bannerUrl?: string;
    license?: string;
    links?: Record<string, string>;
    classification: 'PLUGIN' | 'DATA' | 'ART' | 'SAVE' | 'MODPACK';
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
    allowReviews?: boolean;
    status?: 'DRAFT' | 'PENDING' | 'PUBLISHED' | 'UNLISTED' | 'DELETED' | 'ARCHIVED';
    expiresAt?: string;
    canEdit?: boolean;
    isOwner?: boolean;
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

export interface Report {
    id: string;
    reporterId: string;
    reporterUsername: string;
    projectId: string;
    projectTitle: string;
    reason: string;
    description: string;
    status: 'OPEN' | 'RESOLVED' | 'DISMISSED';
    createdAt: string;
}