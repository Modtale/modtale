import type { Permission } from '@/modules/permissions/permissions';

export enum VersionRelationKind {
    DEPENDENCY = 'DEPENDENCY',
    INCOMPATIBILITY = 'INCOMPATIBILITY'
}

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
    permissions: Permission[];
    isOwner?: boolean;
}

export interface OrganizationMember {
    userId: string;
    roleId: string;
}

export interface OrgPayoutShare {
    userId: string;
    percent: number;
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
    orgPayoutMode?: 'DIRECT_TO_ORG_STRIPE' | 'DISTRIBUTE_TO_MEMBERS';
    orgPayoutShares?: OrgPayoutShare[];
}

export interface ProjectDependency {
    projectId: string;
    projectTitle: string;
    versionNumber: string;
    icon?: string;
    title?: string;
    classification?: string;
    slug?: string;
    isOptional?: boolean;
    isEmbedded?: boolean;
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
    modVersion?: string;
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
    severity: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | string;
    type: string;
    category?: string;
    description: string;
    filePath: string;
    lineStart: number;
    lineEnd: number;
    scoreImpact?: number;
    confidence?: number;
    reviewPriority?: 'P0' | 'P1' | 'P2' | string;
    evidenceLevel?: 'SIGNATURE' | 'CORRELATED' | 'BEHAVIORAL' | 'HEURISTIC' | string;
    reviewCadence?: 'ALWAYS' | 'WHEN_CHANGED' | string;
    noiseSuppressed?: boolean;
    tactics?: string[];
    resolved?: boolean;
    fingerprint?: string;
    knownIssue?: boolean;
    escalated?: boolean;
    baselineVersion?: string;
    baselineScoreImpact?: number;
    baselineSeverity?: string;
}

export interface ScanSummary {
    totalIssues?: number;
    criticalIssues?: number;
    highIssues?: number;
    mediumIssues?: number;
    lowIssues?: number;
    lowConfidenceIssues?: number;
    filesScanned?: number;
    classFilesScanned?: number;
    archivesScanned?: number;
    recoverableErrors?: number;
    uniquePackageRoots?: number;
    dominantPackageRootClasses?: number;
    dominantPackageRootSharePercent?: number;
    maxArchiveDepthReached?: number;
    oversizedEntriesSkipped?: number;
    nestedArchiveReadFailures?: number;
    correlatedThreatClusters?: number;
    alwaysReviewIssues?: number;
    suppressedNoiseIssues?: number;
}

export interface ScanReviewTarget {
    filePath: string;
    priority: 'P0' | 'P1' | 'P2' | string;
    reason: string;
    issueCount: number;
    cumulativeImpact: number;
    alwaysReview?: boolean;
    tactics?: string[];
    relatedChecks?: string[];
}

export interface ScanResult {
    status: 'SCANNING' | 'CLEAN' | 'SUSPICIOUS' | 'INFECTED' | 'FAILED' | 'FLAGGED' | string;
    verdict?: 'AUTO_APPROVE' | 'REVIEW' | 'BLOCK' | string;
    riskLevel?: 'LOW' | 'ELEVATED' | 'HIGH' | 'CRITICAL' | string;
    scanState?: string;
    riskScore: number;
    confidenceScore?: number;
    scanAttempt?: number;
    holdUntilTimestamp?: number;
    reviewerNotes?: string[];
    summary?: ScanSummary;
    reviewTargets?: ScanReviewTarget[];
    knownIssueCount?: number;
    newIssueCount?: number;
    escalatedIssueCount?: number;
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
    incompatibleProjectIds?: string[];
    channel?: 'RELEASE' | 'BETA' | 'ALPHA';
    scanResult?: ScanResult;
    reviewStatus?: 'PENDING' | 'SCHEDULED' | 'APPROVED' | 'REJECTED';
    rejectionReason?: string;
}

export interface ProjectVersionChangelog {
    id: string;
    versionNumber: string;
    changelog?: string | null;
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
    permissions: Permission[];
}

export interface ProjectMember {
    userId: string;
    roleId: string;
    username?: string;
    avatarUrl?: string;
}

export interface GalleryImage {
    url?: string;
    imageUrl?: string;
    caption?: string;
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
    customLicenseOpenSource?: boolean;
    links?: Record<string, string>;
    classification: 'PLUGIN' | 'DATA' | 'ART' | 'SAVE' | 'MODPACK';
    tags?: string[];
    downloadCount: number;
    favoriteCount: number;
    updatedAt: string;
    createdAt?: string;
    projectIds?: string[];
    childProjectIds?: string[];
    sizeBytes?: number;
    comments?: Comment[];
    versions?: ProjectVersion[];
    galleryImages?: Array<string | GalleryImage>;
    galleryImageCaptions?: Record<string, string>;
    repositoryUrl?: string;

    projectRoles?: ProjectRole[];
    teamMembers?: ProjectMember[];
    teamInvites?: ProjectMember[];

    lastTrendingNotification?: string;
    allowModpacks?: boolean;
    allowComments?: boolean;
    adsEnabled?: boolean;
    donationsEnabled?: boolean;
    suggestedDonationCents?: number;
    donationRecurringDefault?: boolean;
    donationPlatformCutBps?: number;
    hmWikiEnabled?: boolean;
    hmWikiSlug?: string;
    galleryCarouselEnabled?: boolean;
    status?: 'DRAFT' | 'PRIVATE' | 'PENDING' | 'PUBLISHED' | 'UNLISTED' | 'DELETED' | 'ARCHIVED';
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
