import crypto from 'node:crypto';
import fs from 'node:fs/promises';
import path from 'node:path';
import process from 'node:process';
import { ObjectId } from 'mongodb';
import { connectMongo } from './mongo-connection.mjs';

const repoRoot = path.resolve(path.dirname(new URL(import.meta.url).pathname), '..', '..');
const outputDir = process.env.MOCK_DB_OUTPUT_DIR
  ? path.resolve(process.env.MOCK_DB_OUTPUT_DIR)
  : path.join(repoRoot, 'mock-db', 'generated', 'collections');

const sourceUri = process.env.MOCK_SOURCE_MONGODB_URI;
const sourceDbName = process.env.MOCK_SOURCE_DATABASE_NAME || 'modtale';
const projectLimit = Number.parseInt(process.env.MOCK_PROJECT_LIMIT || '80', 10);

const passwordHash = '$2a$10$YQPnaULIFCpHYqXreH4IdeK0tSn2gCrMgOSE6bOcKCIR16cG9/Ujy';
const classifications = ['PLUGIN', 'DATA', 'ART', 'SAVE', 'MODPACK'];

if (!sourceUri) {
  console.error('MOCK_SOURCE_MONGODB_URI is required.');
  process.exit(1);
}

const hash = (value, length = 12) =>
  crypto.createHash('sha256').update(String(value ?? '')).digest('hex').slice(0, length);

const slugify = (value, fallback = 'project') => {
  const slug = String(value || fallback)
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 42);
  return slug || fallback;
};

const number = (value, fallback = 0) => {
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : fallback;
};

const list = (value, limit = 8) =>
  Array.isArray(value)
    ? value.filter((entry) => typeof entry === 'string' && entry.trim()).slice(0, limit)
    : [];

const boundedString = (value, fallback = '', limit = 1000) => {
  if (typeof value !== 'string') return fallback;
  const trimmed = value.trim();
  return trimmed ? trimmed.slice(0, limit) : fallback;
};

const placeholder = (label, color = '334155', size = '512x512') =>
  `https://placehold.co/${size}/${color}/f8fafc?text=${encodeURIComponent(label).replace(/%20/g, '+')}`;

const cleanDate = (value, fallback = '2026-01-01') => {
  if (!value) return fallback;
  if (value instanceof Date) return value.toISOString().slice(0, 10);
  const raw = String(value);
  const match = raw.match(/^\d{4}-\d{2}-\d{2}/);
  return match ? match[0] : fallback;
};

const publicProjectProjection = {
  _id: 1,
  slug: 1,
  title: 1,
  about: 1,
  description: 1,
  author: 1,
  imageUrl: 1,
  bannerUrl: 1,
  classification: 1,
  categories: 1,
  tags: 1,
  downloadCount: 1,
  favoriteCount: 1,
  downloads7d: 1,
  downloads30d: 1,
  downloads90d: 1,
  trendScore: 1,
  relevanceScore: 1,
  popularScore: 1,
  trendingRank: 1,
  popularRank: 1,
  relevanceRank: 1,
  repositoryUrl: 1,
  updatedAt: 1,
  createdAt: 1,
  license: 1,
  customLicenseOpenSource: 1,
  links: 1,
  types: 1,
  childProjectIds: 1,
  modIds: 1,
  allowModpacks: 1,
  allowComments: 1,
  hmWikiEnabled: 1,
  hmWikiSlug: 1,
  galleryCarouselEnabled: 1,
  status: 1,
  authorId: 1,
  galleryImages: 1,
  galleryImageCaptions: 1,
  comments: 1,
  'versions._id': 1,
  'versions.versionNumber': 1,
  'versions.gameVersions': 1,
  'versions.fileUrl': 1,
  'versions.hash': 1,
  'versions.downloadCount': 1,
  'versions.releaseDate': 1,
  'versions.changelog': 1,
  'versions.dependencies': 1,
  'versions.incompatibleProjectIds': 1,
  'versions.channel': 1
};

const publicProjectFilter = {
  status: { $in: ['PUBLISHED', 'ARCHIVED'] },
  deletedAt: null
};

const projectIdClauses = (projectId) => {
  const id = String(projectId || '');
  const clauses = [{ _id: id }];
  if (ObjectId.isValid(id)) {
    clauses.unshift({ _id: new ObjectId(id) });
  }
  return clauses;
};

async function findPublicProjectById(db, projectId) {
  const clauses = projectIdClauses(projectId);
  return db.collection('projects').findOne(
    {
      ...publicProjectFilter,
      $or: clauses
    },
    { projection: publicProjectProjection }
  );
}

function extractDependencyProjectIds(project) {
  const dependencyIds = new Set();
  const versions = Array.isArray(project?.versions) ? project.versions : [];

  for (const version of versions) {
    const dependencies = Array.isArray(version?.dependencies) ? version.dependencies : [];
    for (const dependency of dependencies) {
      if (!dependency || typeof dependency !== 'object') continue;
      if (dependency.source && String(dependency.source).toUpperCase() !== 'MODTALE') continue;

      const dependencyId = dependency.projectId ?? dependency.modId;
      if (dependencyId != null && String(dependencyId).trim()) {
        dependencyIds.add(String(dependencyId));
      }
    }
  }

  return dependencyIds;
}

async function includeDependencyProjects(db, selected, seen) {
  let added = 0;
  const queue = [...selected];

  while (queue.length > 0) {
    const project = queue.shift();
    for (const dependencyId of extractDependencyProjectIds(project)) {
      if (seen.has(dependencyId)) continue;

      const dependencyProject = await findPublicProjectById(db, dependencyId);
      if (!dependencyProject) continue;

      const id = String(dependencyProject._id);
      if (seen.has(id)) continue;

      selected.push(dependencyProject);
      queue.push(dependencyProject);
      seen.add(id);
      added += 1;
    }
  }

  return added;
}

function baseUsers(authorProfiles) {
  const users = [
    {
      _id: '692620f7c2f3266e23ac0ded',
      username: 'super_admin',
      email: 'super_admin@example.test',
      emailVerified: true,
      password: passwordHash,
      avatarUrl: placeholder('SA', '111827'),
      bannerUrl: placeholder('Mock Super Admin', '1f2937', '1200x300'),
      bio: 'Synthetic super admin account for preview review.',
      createdAt: '2026-01-05',
      tier: 'ENTERPRISE',
      roles: ['USER', 'ADMIN'],
      accountType: 'USER',
      likedModIds: [],
      followingIds: [],
      followerIds: [],
      connectedAccounts: [],
      badges: ['preview-admin']
    },
    {
      _id: '692620f7c2f3266e23ac0dee',
      username: 'admin',
      email: 'admin@example.test',
      emailVerified: true,
      password: passwordHash,
      avatarUrl: placeholder('AD', '334155'),
      bannerUrl: placeholder('Mock Admin', '334155', '1200x300'),
      bio: 'Synthetic admin account for moderation flow testing.',
      createdAt: '2026-01-08',
      tier: 'ENTERPRISE',
      roles: ['USER', 'ADMIN'],
      accountType: 'USER',
      likedModIds: [],
      followingIds: [],
      followerIds: [],
      connectedAccounts: [],
      badges: ['preview-admin']
    },
    {
      _id: 'mock-user-1',
      username: 'user',
      email: 'user@example.test',
      emailVerified: true,
      password: passwordHash,
      avatarUrl: placeholder('U', '0f766e'),
      bannerUrl: placeholder('Mock User', '0f766e', '1200x300'),
      bio: 'Synthetic standard user for sign-in and profile testing.',
      createdAt: '2026-01-12',
      tier: 'USER',
      roles: ['USER'],
      accountType: 'USER',
      likedModIds: [],
      followingIds: [],
      followerIds: [],
      connectedAccounts: [],
      badges: ['early-preview']
    }
  ];

  [...authorProfiles.values()].forEach((profile, index) => {
    const id = profile.id;
    const ordinal = String(index + 1).padStart(3, '0');
    users.push({
      _id: id,
      username: profile.username || `creator_${ordinal}`,
      email: `creator_${ordinal}@example.test`,
      emailVerified: true,
      password: passwordHash,
      avatarUrl: placeholder(`C${ordinal}`, index % 2 === 0 ? '7c3aed' : 'be123c'),
      bannerUrl: placeholder(`Creator ${ordinal}`, index % 2 === 0 ? '4c1d95' : '881337', '1200x300'),
      bio: 'Synthetic creator generated from public project shape.',
      createdAt: '2026-01-16',
      tier: index === 0 ? 'ENTERPRISE' : 'USER',
      roles: ['USER'],
      accountType: 'USER',
      likedModIds: [],
      followingIds: [],
      followerIds: [],
      connectedAccounts: [],
      badges: ['creator']
    });
  });

  return users;
}

function sanitizeVersion(version, projectId, selectedProjectIds) {
  const sourceVersionId = version?._id || version?.id || `${projectId}-${version?.versionNumber || '1.0.0'}`;
  const versionId = String(sourceVersionId);
  const dependencies = Array.isArray(version?.dependencies)
    ? version.dependencies
        .map((dependency) => {
          const sourceDependencyId = dependency?.modId || dependency?.projectId;
          if (!sourceDependencyId || !selectedProjectIds.has(String(sourceDependencyId))) {
            return null;
          }
          return {
            modId: String(sourceDependencyId),
            modTitle: String(dependency.modTitle || dependency.projectTitle || 'Dependency'),
            versionNumber: String(dependency.versionNumber || '1.0.0'),
            isOptional: Boolean(dependency.isOptional ?? dependency.optional),
            isEmbedded: false
          };
        })
        .filter(Boolean)
    : [];

  return {
    _id: versionId,
    versionNumber: String(version?.versionNumber || '1.0.0').slice(0, 32),
    gameVersions: list(version?.gameVersions, 6),
    fileUrl: typeof version?.fileUrl === 'string' ? version.fileUrl : `https://example.test/mock-downloads/${versionId}.zip`,
    hash: typeof version?.hash === 'string' ? version.hash : `sha256:mock-${hash(versionId, 24)}`,
    downloadCount: number(version?.downloadCount),
    releaseDate: cleanDate(version?.releaseDate),
    changelog: typeof version?.changelog === 'string'
      ? version.changelog.slice(0, 12000)
      : 'Synthetic changelog generated for the public mock database.',
    dependencies,
    incompatibleProjectIds: list(version?.incompatibleProjectIds, 12).filter((id) => selectedProjectIds.has(String(id))),
    channel: ['RELEASE', 'BETA', 'ALPHA'].includes(version?.channel) ? version.channel : 'RELEASE',
    reviewStatus: 'APPROVED'
  };
}

function syntheticVoteIds(rawVotes, prefix, limit = 50) {
  const voteCount = Array.isArray(rawVotes)
    ? rawVotes.length
    : rawVotes instanceof Set
      ? rawVotes.size
      : 0;
  return Array.from({ length: Math.min(voteCount, limit) }, (_, index) => `mock-${prefix}-voter-${index + 1}`);
}

function sanitizeCommentReply(reply) {
  if (!reply || typeof reply !== 'object') return undefined;
  const content = boundedString(reply.content, '', 5000);
  if (!content) return undefined;
  const rawUserId = reply.userId == null ? undefined : String(reply.userId);

  return {
    userId: boundedString(rawUserId, 'mock-user-1', 120),
    content,
    date: boundedString(reply.date, cleanDate(reply.date), 80),
    upvotes: syntheticVoteIds(reply.upvotes, 'reply-upvote'),
    downvotes: syntheticVoteIds(reply.downvotes, 'reply-downvote')
  };
}

function sanitizeComments(comments) {
  if (!Array.isArray(comments)) return [];

  return comments
    .slice(0, 20)
    .map((comment, index) => {
      const content = boundedString(comment?.content, '', 5000);
      if (!content) return null;
      const rawCommentId = comment.id ?? comment._id;
      const rawUserId = comment.userId == null ? undefined : String(comment.userId);

      return {
        id: boundedString(rawCommentId == null ? undefined : String(rawCommentId), `mock-comment-${index + 1}`, 120),
        userId: boundedString(rawUserId, 'mock-user-1', 120),
        content,
        date: boundedString(comment.date, cleanDate(comment.date), 80),
        updatedAt: boundedString(comment.updatedAt, '', 80) || undefined,
        upvotes: syntheticVoteIds(comment.upvotes, 'comment-upvote'),
        downvotes: syntheticVoteIds(comment.downvotes, 'comment-downvote'),
        developerReply: sanitizeCommentReply(comment.developerReply)
      };
    })
    .filter(Boolean);
}

function sanitizeProject(project, index, selectedProjectIds, authorIdMap) {
  const sourceId = String(project._id);
  const projectSlug = slugify(project.slug || project.title || `project-${index + 1}`);
  const id = sourceId;
  const sourceAuthorId = String(project.authorId || `mock-author-${index + 1}`);
  const title = typeof project.title === 'string' && project.title.trim()
    ? project.title.trim().slice(0, 120)
    : `Project ${String(index + 1).padStart(2, '0')}`;
  const classification = classifications.includes(project.classification) ? project.classification : 'PLUGIN';
  const color = ['2563eb', '16a34a', 'be123c', '0891b2', 'f59e0b'][index % 5];
  const versions = Array.isArray(project.versions)
    ? project.versions.slice(0, 4).map((version) => sanitizeVersion(version, id, selectedProjectIds))
    : [];

  return {
    _id: id,
    slug: projectSlug,
    title,
    about: typeof project.about === 'string' ? project.about.slice(0, 2000) : '',
    description: typeof project.description === 'string' ? project.description.slice(0, 20000) : '',
    authorId: authorIdMap.get(sourceAuthorId) || sourceAuthorId,
    author: typeof project.author === 'string' && project.author.trim() ? project.author.trim().slice(0, 80) : 'creator',
    imageUrl: typeof project.imageUrl === 'string' ? project.imageUrl : placeholder(title, color),
    bannerUrl: typeof project.bannerUrl === 'string' ? project.bannerUrl : placeholder(title, color, '1600x420'),
    classification,
    categories: list(project.categories, 4),
    tags: list(project.tags, 8),
    downloadCount: number(project.downloadCount),
    favoriteCount: number(project.favoriteCount),
    downloads7d: number(project.downloads7d),
    downloads30d: number(project.downloads30d),
    downloads90d: number(project.downloads90d),
    trendScore: number(project.trendScore),
    relevanceScore: number(project.relevanceScore),
    popularScore: number(project.popularScore),
    trendingRank: number(project.trendingRank, index + 1),
    popularRank: number(project.popularRank, index + 1),
    relevanceRank: number(project.relevanceRank, index + 1),
    rankingDirty: false,
    repositoryUrl: typeof project.repositoryUrl === 'string' ? project.repositoryUrl : '',
    updatedAt: cleanDate(project.updatedAt),
    createdAt: cleanDate(project.createdAt),
    license: typeof project.license === 'string' ? project.license.slice(0, 32) : 'MIT',
    customLicenseOpenSource: Boolean(project.customLicenseOpenSource),
    links: project.links && typeof project.links === 'object' && !Array.isArray(project.links) ? project.links : {},
    types: list(project.types, 4),
    childProjectIds: list(project.childProjectIds, 4)
      .filter((childId) => selectedProjectIds.has(String(childId))),
    modIds: list(project.modIds, 4).map((modId) => slugify(modId)),
    allowModpacks: Boolean(project.allowModpacks ?? true),
    allowComments: Boolean(project.allowComments ?? true),
    hmWikiEnabled: Boolean(project.hmWikiEnabled && project.hmWikiSlug),
    hmWikiSlug: project.hmWikiEnabled && project.hmWikiSlug ? slugify(project.hmWikiSlug) : undefined,
    galleryCarouselEnabled: Boolean(project.galleryCarouselEnabled),
    status: project.status === 'ARCHIVED' ? 'ARCHIVED' : 'PUBLISHED',
    approvedBy: '692620f7c2f3266e23ac0dee',
    projectRoles: [],
    teamMembers: [],
    teamInvites: [],
    galleryImages: list(project.galleryImages, 12),
    galleryImageCaptions: project.galleryImageCaptions && typeof project.galleryImageCaptions === 'object'
      ? project.galleryImageCaptions
      : {},
    comments: sanitizeComments(project.comments),
    versions
  };
}

function syntheticPrivateProjects() {
  return [
    {
      _id: 'mock-plugin-review-me',
      slug: 'review-me',
      title: 'Review Me',
      about: 'Pending synthetic project for moderation and scanner review screens.',
      description: 'This is not sourced from production. It exists to populate admin review screens safely.',
      authorId: 'mock-user-1',
      author: 'user',
      imageUrl: placeholder('Review', '7c2d12'),
      bannerUrl: placeholder('Review Me', '7c2d12', '1600x420'),
      classification: 'PLUGIN',
      categories: ['API', 'Library'],
      tags: ['API', 'Library', 'Mechanics'],
      downloadCount: 0,
      favoriteCount: 0,
      downloads7d: 0,
      downloads30d: 0,
      downloads90d: 0,
      trendScore: 0,
      relevanceScore: 0,
      popularScore: 0,
      trendingRank: 999,
      popularRank: 999,
      relevanceRank: 999,
      rankingDirty: true,
      repositoryUrl: 'https://example.test/modtale-mock/review-me',
      updatedAt: '2026-06-18',
      createdAt: '2026-06-18',
      license: 'MIT',
      links: {},
      types: ['server-plugin'],
      childProjectIds: [],
      modIds: ['review-me'],
      allowModpacks: true,
      allowComments: true,
      hmWikiEnabled: false,
      galleryCarouselEnabled: false,
      status: 'PENDING',
      projectRoles: [],
      teamMembers: [],
      teamInvites: [],
      galleryImages: [],
      galleryImageCaptions: {},
      comments: [],
      versions: [
        {
          _id: 'review-me-0-1-0',
          versionNumber: '0.1.0',
          gameVersions: ['2026.05.15'],
          fileUrl: 'https://example.test/mock-downloads/review-me-0.1.0.jar',
          hash: 'sha256:mock-review-me-0-1-0',
          downloadCount: 0,
          releaseDate: '2026-06-18',
          changelog: 'Synthetic pending release.',
          dependencies: [],
          incompatibleProjectIds: [],
          channel: 'ALPHA',
          reviewStatus: 'PENDING',
          scanResult: {
            status: 'SUSPICIOUS',
            verdict: 'MANUAL_REVIEW',
            riskLevel: 'MEDIUM',
            scanState: 'COMPLETE',
            riskScore: 42,
            confidenceScore: 71,
            scanAttempt: 1,
            scanTimestamp: 1781740800000,
            knownIssueCount: 0,
            newIssueCount: 1,
            escalatedIssueCount: 0,
            reviewerNotes: ['Synthetic scanner note for PR preview testing.'],
            issues: []
          }
        }
      ]
    },
    {
      _id: 'mock-draft-sandbox',
      slug: 'draft-sandbox',
      title: 'Draft Sandbox',
      about: 'Draft synthetic project owned by the standard mock user.',
      description: 'Used for dashboard, editor, and draft visibility checks.',
      authorId: 'mock-user-1',
      author: 'user',
      imageUrl: placeholder('Draft', '0f766e'),
      bannerUrl: placeholder('Draft Sandbox', '0f766e', '1600x420'),
      classification: 'DATA',
      categories: ['Functions'],
      tags: ['Functions', 'Puzzle'],
      downloadCount: 0,
      favoriteCount: 0,
      downloads7d: 0,
      downloads30d: 0,
      downloads90d: 0,
      trendScore: 0,
      relevanceScore: 0,
      popularScore: 0,
      trendingRank: 1000,
      popularRank: 1000,
      relevanceRank: 1000,
      rankingDirty: true,
      repositoryUrl: 'https://example.test/modtale-mock/draft-sandbox',
      updatedAt: '2026-06-16',
      createdAt: '2026-06-16',
      license: 'ARR',
      links: {},
      types: ['data-asset'],
      childProjectIds: [],
      modIds: ['draft-sandbox'],
      allowModpacks: true,
      allowComments: true,
      hmWikiEnabled: false,
      galleryCarouselEnabled: false,
      status: 'DRAFT',
      projectRoles: [],
      teamMembers: [],
      teamInvites: [],
      galleryImages: [],
      galleryImageCaptions: {},
      comments: [],
      versions: []
    },
    {
      _id: 'mock-private-lab',
      slug: 'private-lab',
      title: 'Private Lab',
      about: 'Private synthetic project for owner-only routes.',
      description: 'Used for private project visibility and dashboard filtering.',
      authorId: 'mock-user-1',
      author: 'user',
      imageUrl: placeholder('Private', '6d28d9'),
      bannerUrl: placeholder('Private Lab', '5b21b6', '1600x420'),
      classification: 'ART',
      categories: ['Particles'],
      tags: ['Particles', 'Animations'],
      downloadCount: 0,
      favoriteCount: 0,
      downloads7d: 0,
      downloads30d: 0,
      downloads90d: 0,
      trendScore: 0,
      relevanceScore: 0,
      popularScore: 0,
      trendingRank: 1001,
      popularRank: 1001,
      relevanceRank: 1001,
      rankingDirty: true,
      repositoryUrl: 'https://example.test/modtale-mock/private-lab',
      updatedAt: '2026-06-15',
      createdAt: '2026-06-15',
      license: 'ARR',
      links: {},
      types: ['art-asset'],
      childProjectIds: [],
      modIds: ['private-lab'],
      allowModpacks: false,
      allowComments: false,
      hmWikiEnabled: false,
      galleryCarouselEnabled: false,
      status: 'PRIVATE',
      projectRoles: [],
      teamMembers: [],
      teamInvites: [],
      galleryImages: [],
      galleryImageCaptions: {},
      comments: [],
      versions: []
    },
    {
      _id: 'mock-plugin-admin-kit',
      slug: 'admin-kit',
      title: 'Admin Kit',
      about: 'Unlisted synthetic project for permission and visibility testing.',
      description: 'This is not sourced from production. It exists to populate unlisted project flows safely.',
      authorId: 'mock-user-1',
      author: 'user',
      imageUrl: placeholder('Admin', '475569'),
      bannerUrl: placeholder('Admin Kit', '334155', '1600x420'),
      classification: 'PLUGIN',
      categories: ['Admin Tools', 'Protection'],
      tags: ['Admin Tools', 'Protection', 'Chat'],
      downloadCount: 1260,
      favoriteCount: 92,
      downloads7d: 38,
      downloads30d: 180,
      downloads90d: 910,
      trendScore: 35,
      relevanceScore: 64.1,
      popularScore: 41.2,
      trendingRank: 30,
      popularRank: 42,
      relevanceRank: 28,
      rankingDirty: false,
      repositoryUrl: 'https://example.test/modtale-mock/admin-kit',
      updatedAt: '2026-05-18',
      createdAt: '2026-04-12',
      license: 'GPL-3.0',
      links: {},
      types: ['server-plugin'],
      childProjectIds: [],
      modIds: ['admin-kit'],
      allowModpacks: false,
      allowComments: false,
      hmWikiEnabled: false,
      galleryCarouselEnabled: false,
      status: 'UNLISTED',
      approvedBy: '692620f7c2f3266e23ac0dee',
      projectRoles: [],
      teamMembers: [],
      teamInvites: [],
      galleryImages: [],
      galleryImageCaptions: {},
      comments: [],
      versions: [
        {
          _id: 'admin-kit-0-9-0',
          versionNumber: '0.9.0',
          gameVersions: ['2026.03.11'],
          fileUrl: 'https://example.test/mock-downloads/admin-kit-0.9.0.jar',
          hash: 'sha256:mock-admin-kit-0-9-0',
          downloadCount: 1260,
          releaseDate: '2026-05-18',
          changelog: 'Synthetic unlisted release.',
          dependencies: [],
          incompatibleProjectIds: [],
          channel: 'BETA',
          reviewStatus: 'APPROVED'
        }
      ]
    },
    {
      _id: 'mock-archive-tutorial-town',
      slug: 'tutorial-town-legacy',
      title: 'Tutorial Town Legacy',
      about: 'Archived synthetic save used to verify archived public project behavior.',
      description: 'This record is intentionally archived and still public for route and badge checks.',
      authorId: 'mock-user-1',
      author: 'user',
      imageUrl: placeholder('Archive', '64748b'),
      bannerUrl: placeholder('Tutorial Town Legacy', '475569', '1600x420'),
      classification: 'SAVE',
      categories: ['Spawn'],
      tags: ['Spawn', 'Medieval'],
      downloadCount: 2310,
      favoriteCount: 188,
      downloads7d: 0,
      downloads30d: 12,
      downloads90d: 85,
      trendScore: 8,
      relevanceScore: 45,
      popularScore: 35.4,
      trendingRank: 75,
      popularRank: 63,
      relevanceRank: 58,
      rankingDirty: false,
      repositoryUrl: 'https://example.test/modtale-mock/tutorial-town-legacy',
      updatedAt: '2026-04-22',
      createdAt: '2026-01-28',
      license: 'CC0-1.0',
      links: {},
      types: ['world-save'],
      childProjectIds: [],
      modIds: ['tutorial-town-legacy'],
      allowModpacks: true,
      allowComments: false,
      hmWikiEnabled: false,
      galleryCarouselEnabled: false,
      status: 'ARCHIVED',
      approvedBy: '692620f7c2f3266e23ac0dee',
      projectRoles: [],
      teamMembers: [],
      teamInvites: [],
      galleryImages: [],
      galleryImageCaptions: {},
      comments: [],
      versions: [
        {
          _id: 'tutorial-town-legacy-1-0-0',
          versionNumber: '1.0.0',
          gameVersions: ['2026.03.11'],
          fileUrl: 'https://example.test/mock-downloads/tutorial-town-legacy-1.0.0.zip',
          hash: 'sha256:mock-tutorial-town-legacy-1-0-0',
          downloadCount: 2310,
          releaseDate: '2026-04-22',
          changelog: 'Archived synthetic save release.',
          dependencies: [],
          incompatibleProjectIds: [],
          channel: 'RELEASE',
          reviewStatus: 'APPROVED'
        }
      ]
    },
    {
      _id: 'mock-modpack-frontier',
      slug: 'frontier-pack',
      title: 'Frontier Pack',
      about: 'Synthetic modpack used to guarantee modpack classification coverage.',
      description: 'This is not sourced from production. It exists to populate modpack flows safely.',
      authorId: 'mock-user-1',
      author: 'user',
      imageUrl: placeholder('Pack', 'f59e0b'),
      bannerUrl: placeholder('Frontier Pack', 'b45309', '1600x420'),
      classification: 'MODPACK',
      categories: ['Adventure', 'Vanilla+'],
      tags: ['Adventure', 'Vanilla+', 'Exploration'],
      downloadCount: 4380,
      favoriteCount: 381,
      downloads7d: 380,
      downloads30d: 1305,
      downloads90d: 3890,
      trendScore: 84,
      relevanceScore: 90.8,
      popularScore: 80.2,
      trendingRank: 3,
      popularRank: 7,
      relevanceRank: 3,
      rankingDirty: false,
      repositoryUrl: 'https://example.test/modtale-mock/frontier-pack',
      updatedAt: '2026-06-14',
      createdAt: '2026-04-01',
      license: 'MPL-2.0',
      links: {},
      types: ['modpack'],
      childProjectIds: [],
      modIds: ['frontier-pack'],
      allowModpacks: false,
      allowComments: true,
      hmWikiEnabled: false,
      galleryCarouselEnabled: true,
      status: 'PUBLISHED',
      approvedBy: '692620f7c2f3266e23ac0dee',
      projectRoles: [],
      teamMembers: [],
      teamInvites: [],
      galleryImages: [placeholder('Frontier Pack', 'f59e0b', '1280x720')],
      galleryImageCaptions: {},
      comments: [],
      versions: [
        {
          _id: 'frontier-pack-1-0-0',
          versionNumber: '1.0.0',
          gameVersions: ['2026.03.11'],
          fileUrl: 'https://example.test/mock-downloads/frontier-pack-1.0.0.zip',
          hash: 'sha256:mock-frontier-pack-1-0-0',
          downloadCount: 4380,
          releaseDate: '2026-06-14',
          changelog: 'Synthetic modpack release.',
          dependencies: [],
          incompatibleProjectIds: [],
          channel: 'RELEASE',
          reviewStatus: 'APPROVED'
        }
      ]
    }
  ];
}

function buildProjectStats(projects) {
  return projects
    .filter((project) => project.status === 'PUBLISHED' || project.status === 'ARCHIVED')
    .slice(0, 20)
    .map((project, index) => {
      const totalDownloads = number(project.downloads30d, Math.floor(number(project.downloadCount) / 3));
      const totalViews = Math.max(totalDownloads * 3, totalDownloads + 100);
      const versionId = project.versions?.[0]?._id || `${project._id}-version`;
      return {
        _id: `stats-${project._id}-2026-06`,
        projectId: project._id,
        authorId: project.authorId,
        year: 2026,
        month: 6,
        totalViews,
        totalDownloads,
        apiDownloads: Math.floor(totalDownloads * 0.14),
        frontendDownloads: Math.ceil(totalDownloads * 0.86),
        days: {
          '01': { v: Math.floor(totalViews * 0.12), d: Math.floor(totalDownloads * 0.11) },
          '08': { v: Math.floor(totalViews * 0.16), d: Math.floor(totalDownloads * 0.15) },
          '15': { v: Math.floor(totalViews * 0.22), d: Math.floor(totalDownloads * 0.24) },
          '16': { v: Math.floor(totalViews * 0.24), d: Math.floor(totalDownloads * 0.25) },
          '17': { v: Math.floor(totalViews * 0.26), d: Math.floor(totalDownloads * 0.25) }
        },
        versionDownloads: {
          [versionId]: {
            [project.versions?.[0]?.gameVersions?.[0] || '2026.03.11']: totalDownloads
          }
        },
        syntheticOrdinal: index + 1
      };
    });
}

function buildPlatformStats(projects, users) {
  const totalDownloads = projects.reduce((sum, project) => sum + number(project.downloads30d), 0);
  const totalViews = Math.max(totalDownloads * 4, 1000);
  return [
    {
      _id: 'platform-2026-06',
      year: 2026,
      month: 6,
      totalViews,
      totalDownloads,
      apiDownloads: Math.floor(totalDownloads * 0.13),
      frontendDownloads: Math.ceil(totalDownloads * 0.87),
      newProjects: Math.min(projects.length, 12),
      newUsers: Math.min(users.length, 12),
      newOrgs: 1,
      days: {
        '01': { v: Math.floor(totalViews * 0.12), d: Math.floor(totalDownloads * 0.11), a: 4, f: 20, n: 0, u: 1, o: 0 },
        '08': { v: Math.floor(totalViews * 0.17), d: Math.floor(totalDownloads * 0.16), a: 6, f: 24, n: 1, u: 1, o: 0 },
        '15': { v: Math.floor(totalViews * 0.22), d: Math.floor(totalDownloads * 0.23), a: 8, f: 32, n: 1, u: 2, o: 1 },
        '16': { v: Math.floor(totalViews * 0.24), d: Math.floor(totalDownloads * 0.25), a: 9, f: 35, n: 0, u: 1, o: 0 },
        '17': { v: Math.floor(totalViews * 0.25), d: Math.floor(totalDownloads * 0.25), a: 10, f: 38, n: 1, u: 0, o: 0 }
      }
    }
  ];
}

const date = (iso) => ({ $date: iso });

function buildAdminLogs() {
  return [
    {
      _id: 'mock-admin-log-1',
      adminUsername: 'admin',
      action: 'PROJECT_APPROVED',
      targetId: 'mock-plugin-review-me',
      targetType: 'PROJECT',
      details: 'Synthetic audit event generated for the public mock database.',
      timestamp: date('2026-06-18T14:30:00.000Z')
    },
    {
      _id: 'mock-admin-log-2',
      adminUsername: 'super_admin',
      action: 'REPORT_RESOLVED',
      targetId: 'mock-report-2',
      targetType: 'REPORT',
      details: 'Synthetic report resolution generated for admin UI testing.',
      timestamp: date('2026-06-18T15:10:00.000Z')
    }
  ];
}

function firstGeneratedCreator(users) {
  const baseUserIds = new Set(['692620f7c2f3266e23ac0ded', '692620f7c2f3266e23ac0dee', 'mock-user-1']);
  return users.find((user) => !baseUserIds.has(String(user._id)))?._id || 'mock-user-1';
}

function buildReports(users) {
  const creatorId = firstGeneratedCreator(users);
  const creatorUsername = users.find((user) => user._id === creatorId)?.username || 'creator_001';
  return [
    {
      _id: 'mock-report-1',
      reporterId: 'mock-user-1',
      reporterUsername: 'user',
      targetId: 'mock-plugin-review-me',
      targetType: 'PROJECT',
      targetSummary: 'Review Me',
      reason: 'Synthetic queue item',
      description: 'Mock report used only to populate admin review screens.',
      status: 'OPEN',
      createdAt: date('2026-06-18T13:45:00.000Z')
    },
    {
      _id: 'mock-report-2',
      reporterId: creatorId,
      reporterUsername: creatorUsername,
      targetId: 'mock-comment-waystones-1',
      targetType: 'COMMENT',
      targetSummary: 'Synthetic project comment',
      reason: 'Synthetic resolved report',
      description: 'Resolved mock report for pagination and filters.',
      status: 'RESOLVED',
      createdAt: date('2026-06-17T16:20:00.000Z'),
      resolvedBy: '692620f7c2f3266e23ac0dee',
      resolutionNote: 'Synthetic resolution note.'
    }
  ];
}

function buildNotifications(projects, users) {
  const firstProject = projects.find((project) => project.status === 'PUBLISHED') || projects[0];
  const creatorId = firstGeneratedCreator(users);
  return [
    {
      _id: 'mock-notification-1',
      userId: 'mock-user-1',
      title: 'Preview project updated',
      message: `${firstProject?.title || 'A mock project'} published a synthetic preview update.`,
      link: `/projects/${firstProject?.slug || 'mock-project'}`,
      iconUrl: placeholder('N', '2563eb', '128x128'),
      isRead: false,
      type: 'PROJECT_UPDATE',
      metadata: { projectId: firstProject?._id || 'mock-project' },
      createdAt: date('2026-06-18T12:00:00.000Z')
    },
    {
      _id: 'mock-notification-2',
      userId: creatorId,
      title: 'New mock comment',
      message: 'A synthetic user commented on a preview project.',
      link: `/projects/${firstProject?.slug || 'mock-project'}?tab=comments`,
      iconUrl: placeholder('C', '0f766e', '128x128'),
      isRead: true,
      type: 'COMMENT',
      metadata: { projectId: firstProject?._id || 'mock-project' },
      createdAt: date('2026-06-18T12:30:00.000Z')
    }
  ];
}

function buildApiKeys() {
  return [
    {
      _id: 'mock-api-key-1',
      userId: 'mock-user-1',
      name: 'Preview API Key',
      keyHash: '$2a$10$7EqJtq98hPqEX7fNZaFWoOHiDwGZfq23E4M//LDdYX2.J9FLcweT2',
      prefix: 'md_mock-pr',
      tier: 'USER',
      contextPermissions: {
        global: ['PROJECT_READ', 'VERSION_READ']
      },
      createdAt: date('2026-06-16T10:00:00.000Z')
    }
  ];
}

function buildBannedEmails() {
  return [
    {
      _id: 'mock-banned-email-1',
      email: 'blocked-user@example.test',
      reason: 'Synthetic banned email for admin UI testing.',
      bannedBy: '692620f7c2f3266e23ac0dee',
      bannedAt: date('2026-06-15T10:00:00.000Z')
    }
  ];
}

function buildStatusIncidents() {
  return [
    {
      _id: 'mock-status-incident-1',
      kind: 'INCIDENT',
      state: 'RESOLVED',
      impact: 'DEGRADED',
      title: 'Synthetic preview incident',
      affectedServices: ['Backend API'],
      startedAt: date('2026-06-12T14:00:00.000Z'),
      resolvedAt: date('2026-06-12T14:35:00.000Z'),
      createdAt: date('2026-06-12T14:00:00.000Z'),
      updatedAt: date('2026-06-12T14:35:00.000Z'),
      createdBy: '692620f7c2f3266e23ac0dee',
      createdByUsername: 'admin',
      updates: [
        {
          id: 'mock-status-update-1',
          state: 'RESOLVED',
          impact: 'OPERATIONAL',
          message: 'Synthetic incident resolved.',
          createdAt: date('2026-06-12T14:35:00.000Z'),
          createdBy: '692620f7c2f3266e23ac0dee',
          createdByUsername: 'admin'
        }
      ]
    },
    {
      _id: 'mock-maintenance-1',
      kind: 'MAINTENANCE',
      state: 'SCHEDULED',
      impact: 'DEGRADED',
      title: 'Synthetic scheduled maintenance',
      affectedServices: ['Frontend'],
      scheduledStart: date('2026-06-25T04:00:00.000Z'),
      scheduledEnd: date('2026-06-25T05:00:00.000Z'),
      createdAt: date('2026-06-18T09:00:00.000Z'),
      updatedAt: date('2026-06-18T09:00:00.000Z'),
      createdBy: '692620f7c2f3266e23ac0dee',
      createdByUsername: 'admin',
      updates: []
    }
  ];
}

function buildStatusHistory() {
  return [
    {
      _id: 'mock-status-history-1',
      timestamp: date('2026-06-18T12:00:00.000Z'),
      apiLatency: 42,
      dbLatency: 18,
      storageLatency: 95,
      overallStatus: 'OPERATIONAL',
      apiStatus: 'OPERATIONAL',
      dbStatus: 'OPERATIONAL',
      storageStatus: 'OPERATIONAL'
    },
    {
      _id: 'mock-status-history-2',
      timestamp: date('2026-06-18T12:05:00.000Z'),
      apiLatency: 61,
      dbLatency: 24,
      storageLatency: 140,
      overallStatus: 'DEGRADED',
      apiStatus: 'OPERATIONAL',
      dbStatus: 'OPERATIONAL',
      storageStatus: 'DEGRADED'
    }
  ];
}

async function writeJson(name, docs) {
  await fs.mkdir(outputDir, { recursive: true });
  await fs.writeFile(path.join(outputDir, `${name}.json`), `${JSON.stringify(docs, null, 2)}\n`);
}

async function main() {
  const client = await connectMongo(sourceUri, {
    appName: 'modtale-mock-db-refresh',
    label: 'source'
  });

  try {
    const db = client.db(sourceDbName);
    const sourceProjects = await db.collection('projects')
      .find(
        publicProjectFilter,
        { projection: publicProjectProjection }
      )
      .sort({ downloadCount: -1, updatedAt: -1 })
      .limit(projectLimit * 3)
      .toArray();

    const selected = [];
    const seen = new Set();

    for (const classification of classifications) {
      const match = sourceProjects.find((project) => project.classification === classification);
      if (match && !seen.has(String(match._id))) {
        selected.push(match);
        seen.add(String(match._id));
      }
    }

    for (const project of sourceProjects) {
      if (selected.length >= projectLimit) break;
      const id = String(project._id);
      if (!seen.has(id)) {
        selected.push(project);
        seen.add(id);
      }
    }

    const dependencyCount = await includeDependencyProjects(db, selected, seen);
    if (dependencyCount > 0) {
      console.log(`Included ${dependencyCount} dependency project(s) in the mock project fixture.`);
    }

    const selectedProjectIds = new Set(selected.map((project) => String(project._id)));
    const reservedUserIds = new Set(['692620f7c2f3266e23ac0ded', '692620f7c2f3266e23ac0dee', 'mock-user-1']);
    const authorIdMap = new Map();
    const authorProfiles = new Map();
    selected.forEach((project, index) => {
      const sourceAuthorId = String(project.authorId || `mock-author-${index + 1}`);
      const authorId = reservedUserIds.has(sourceAuthorId)
        ? `mock-author-${hash(sourceAuthorId, 10)}`
        : sourceAuthorId;
      authorIdMap.set(sourceAuthorId, authorId);
      if (authorProfiles.has(authorId)) return;
      authorProfiles.set(authorId, {
        id: authorId,
        username: boundedString(project.author, `creator_${String(index + 1).padStart(3, '0')}`, 80)
      });
    });

    const users = baseUsers(authorProfiles);
    const projects = [
      ...selected.map((project, index) => sanitizeProject(project, index, selectedProjectIds, authorIdMap)),
      ...syntheticPrivateProjects()
    ];
    const projectStats = buildProjectStats(projects);
    const platformStats = buildPlatformStats(projects, users);
    const adminLogs = buildAdminLogs();
    const reports = buildReports(users);
    const notifications = buildNotifications(projects, users);
    const apiKeys = buildApiKeys();
    const bannedEmails = buildBannedEmails();
    const statusIncidents = buildStatusIncidents();
    const statusHistory = buildStatusHistory();

    await writeJson('users', users);
    await writeJson('projects', projects);
    await writeJson('project_monthly_stats', projectStats);
    await writeJson('platform_monthly_stats', platformStats);
    await writeJson('admin_logs', adminLogs);
    await writeJson('reports', reports);
    await writeJson('notifications', notifications);
    await writeJson('api_keys', apiKeys);
    await writeJson('banned_emails', bannedEmails);
    await writeJson('status_incidents', statusIncidents);
    await writeJson('status_history', statusHistory);

    console.log(`Generated ${projects.length} mock projects and ${users.length} mock users in ${outputDir}`);
  } finally {
    await client.close();
  }
}

main().catch((error) => {
  console.error(error?.message || error);
  process.exit(1);
});
