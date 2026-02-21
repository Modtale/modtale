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
    joinedModjamIds?: string[];
    connectedAccounts?: ConnectedAccount[];
    badges?: string[];
    notificationPreferences?: {
        projectUpdates: 'OFF' | 'ON';
        creatorUploads: 'OFF' | 'ON';
        newComments: 'OFF' | 'ON';
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

export interface Reply {
    user: string;
    userAvatarUrl?: string;
    content: string;
    date: string;
}

export interface Comment {
    id: string;
    user: string;
    userAvatarUrl?: string;
    content: string;
    date: string;
    updatedAt?: string;
    developerReply?: Reply;
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
    updatedAt: string;
    createdAt?: string;
    modIds?: string[];
    childProjectIds?: string[];
    modjamIds?: string[];
    sizeBytes?: number;
    comments: Comment[];
    versions: ProjectVersion[];
    galleryImages: string[];
    repositoryUrl?: string;
    contributors?: string[];
    pendingInvites?: string[];
    lastTrendingNotification?: string;
    allowModpacks?: boolean;
    allowComments?: boolean;
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
    totalDownloads: number;
}

export interface Report {
    id: string;
    reporterId: string;
    reporterUsername: string;
    targetId: string;
    targetType: 'PROJECT' | 'COMMENT' | 'USER';
    targetSummary: string;
    reason: string;
    description: string;
    status: 'OPEN' | 'RESOLVED' | 'DISMISSED';
    createdAt: string;
}

export interface ModjamCategory {
    id: string;
    name: string;
    description: string;
    maxScore: number;
}

export interface Modjam {
    id: string;
    slug: string;
    title: string;
    description: string;
    imageUrl?: string;
    bannerUrl?: string;
    hostId: string;
    hostName: string;
    startDate: string;
    endDate: string;
    votingEndDate: string;
    status: 'DRAFT' | 'UPCOMING' | 'ACTIVE' | 'VOTING' | 'COMPLETED';
    participantIds: string[];
    categories: ModjamCategory[];
    allowPublicVoting: boolean;
    allowConcurrentVoting: boolean;
    showResultsBeforeVotingEnds: boolean;
    createdAt: string;
    updatedAt?: string;
}

export interface ModjamVote {
    id: string;
    voterId: string;
    categoryId: string;
    score: number;
}

export interface ModjamSubmission {
    id: string;
    jamId: string;
    projectId: string;
    projectTitle?: string;
    projectImageUrl?: string;
    projectBannerUrl?: string;
    projectAuthor?: string;
    projectDescription?: string;
    submitterId: string;
    votes: ModjamVote[];
    categoryScores?: Record<string, number>;
    totalScore?: number;
    rank?: number;
    createdAt: string;
}