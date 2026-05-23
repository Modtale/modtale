export interface ConnectedAccount {
    provider: string;
    providerId: string;
    username: string;
    profileUrl: string;
    visible: boolean;
}

export interface OrganizationRole {
    id: string;
    name: string;
    color: string;
    permissions: string[];
    isOwner?: boolean;
}

export interface OrganizationMember {
    userId: string;
    roleId: string;
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
    likedProjectIds: string[];
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
    organizationRoles?: OrganizationRole[];
    pendingOrgInvites?: OrganizationMember[];
}

export interface ProjectDependency {
    projectId: string;
    projectTitle: string;
    versionNumber: string;
    isOptional?: boolean;
}

export interface ManifestDependencySuggestion {
    manifestKey: string;
    requestedVersion: string;
    projectId: string;
    projectTitle: string;
    versionNumber: string;
    optional: boolean;
    confidence: number;
    dependencyEntry: string;
}

export interface ManifestInspectionResult {
    gameVersion?: string;
    suggestions: ManifestDependencySuggestion[];
}

export interface GameVersionCatalog {
    releaseVersions: string[];
    preReleaseVersions: string[];
    allVersions: string[];
    orderedVersions?: string[];
    versions?: {
        version: string;
        preRelease: boolean;
        sourceUrl: string;
    }[];
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
    dependencies?: ProjectDependency[];
    channel?: 'RELEASE' | 'BETA' | 'ALPHA';
    scanResult?: ScanResult;
    reviewStatus?: 'PENDING' | 'APPROVED' | 'REJECTED';
    rejectionReason?: string;
}

export interface Reply {
    userId?: string;
    user: string;
    userAvatarUrl?: string;
    content: string;
    date: string;
    upvoteCount?: number;
    downvoteCount?: number;
    userVote?: 'up' | 'down' | null;
}

export interface Comment {
    id: string;
    userId?: string;
    user: string;
    content: string;
    date: string;
    updatedAt?: string;
    upvoteCount?: number;
    downvoteCount?: number;
    userVote?: 'up' | 'down' | null;
    developerReply?: Reply;
}

export interface ProjectRole {
    id: string;
    name: string;
    color: string;
    permissions: string[];
}

export interface ProjectMember {
    userId: string;
    roleId: string;
    username?: string;
    avatarUrl?: string;
}

export interface Project {
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
    projectIds?: string[];
    childProjectIds?: string[];
    modjamIds?: string[];
    sizeBytes?: number;
    comments: Comment[];
    versions: ProjectVersion[];
    galleryImages: string[];
    repositoryUrl?: string;

    projectRoles?: ProjectRole[];
    teamMembers?: ProjectMember[];
    teamInvites?: ProjectMember[];

    lastTrendingNotification?: string;
    allowModpacks?: boolean;
    allowComments?: boolean;
    hmWikiEnabled?: boolean;
    hmWikiSlug?: string;
    status?: 'DRAFT' | 'PENDING' | 'PUBLISHED' | 'UNLISTED' | 'DELETED' | 'ARCHIVED';
    expiresAt?: string;
    canEdit?: boolean;
    isOwner?: boolean;
}

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
    resolvedBy?: string;
    resolutionNote?: string;
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
    status: 'DRAFT' | 'UPCOMING' | 'ACTIVE' | 'VOTING' | 'AWAITING_WINNERS' | 'COMPLETED';
    participantIds: string[];
    judgeIds?: string[];
    pendingJudgeInvites?: string[];
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
    isJudge?: boolean;
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
    judgeCategoryScores?: Record<string, number>;
    totalJudgeScore?: number;
    totalPublicScore?: number;
    rank?: number;
    isWinner?: boolean;
    awardTitle?: string;
    createdAt: string;
    votesCast?: number;
    commentsGiven?: number;
}
