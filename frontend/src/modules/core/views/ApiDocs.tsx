import React, { useEffect, useMemo, useState } from 'react';
import {
    Activity,
    Shield,
    Server,
    ExternalLink,
    Lock,
    Unlock,
    Layers,
    FileText,
    Database,
    Globe,
    Download,
    User,
    Users,
    Bell,
    Key,
    Gauge,
    Zap,
    Search,
    Braces,
    ChevronRight,
} from 'lucide-react';
import { Link } from 'react-router-dom';
import { BACKEND_URL } from '@/utils/api';
import { SiteRoutes } from '@/utils/routes';

type EndpointParam = {
    name: string;
    in: string;
    required: boolean;
    type?: string;
    description?: string;
};

type EndpointRequestBody = {
    required: boolean;
    contentTypes: string[];
    schemaHints?: string[];
};

type ResponseExample = {
    contentType: string;
    name?: string;
    value: string;
};

type EndpointResponse = {
    code: string;
    description?: string;
    examples: ResponseExample[];
};

type RateLimitTier = {
    name: string;
    readPerMinute: number | string;
    writePerMinute: number | string;
    notes?: string;
};

type EndpointDoc = {
    method: string;
    path: string;
    summary: string;
    description?: string;
    public: boolean;
    params: EndpointParam[];
    requestBody?: EndpointRequestBody;
    responses: EndpointResponse[];
    rateLimitTiers: RateLimitTier[];
};

type OpenApiIndex = {
    title: string;
    version: string;
    server: string;
    totalEndpoints: number;
    rateLimitTiers: RateLimitTier[];
    schemas: SchemaDoc[];
    endpoints: EndpointDoc[];
};

type SchemaField = {
    name: string;
    required: boolean;
    type: string;
    description?: string;
};

type SchemaDoc = {
    name: string;
    type: string;
    description?: string;
    fields: SchemaField[];
    example: string;
};

type RawSchema = {
    type?: string;
    format?: string;
    description?: string;
    enum?: unknown[];
    default?: unknown;
    example?: unknown;
    items?: RawSchema;
    properties?: Record<string, RawSchema>;
    required?: string[];
    additionalProperties?: boolean | RawSchema;
    allOf?: RawSchema[];
    oneOf?: RawSchema[];
    anyOf?: RawSchema[];
    $ref?: string;
};

type RawOpenApi = {
    info?: {
        title?: string;
        version?: string;
    };
    servers?: Array<{ url?: string }>;
    components?: {
        schemas?: Record<string, RawSchema>;
    };
    paths?: Record<string, RawPathItem>;
};

type RawPathItem = Record<string, RawOperation>;

type RawOperation = {
    summary?: string;
    description?: string;
    parameters?: Array<{
        name?: string;
        in?: string;
        required?: boolean;
        description?: string;
        schema?: { type?: string };
    }>;
    requestBody?: {
        required?: boolean;
        content?: Record<string, { schema?: Record<string, unknown> }>;
    };
    responses?: Record<string, {
        description?: string;
        content?: Record<string, {
            schema?: RawSchema;
            example?: unknown;
            examples?: Record<string, { value?: unknown }>;
        }>;
    }>;
    security?: unknown[];
    ['x-modtale-access']?: string;
    ['x-modtale-rate-limit-tiers']?: RateLimitTier[];
};

const OPENAPI_JSON_URL = `${BACKEND_URL}/api/v1/docs/openapi`;
const OPENAPI_YAML_URL = `${BACKEND_URL}/api/v1/docs/openapi.yaml`;
const HTTP_METHODS = ['get', 'post', 'put', 'patch', 'delete', 'head'] as const;
const EXAMPLE_MAX_DEPTH = 6;
const ADMIN_RATE_LIMIT_TIER = 'Admin-Session';

const getMethodColor = (method: string): string => {
    if (method === 'GET') return 'bg-blue-600';
    if (method === 'POST') return 'bg-green-600';
    if (method === 'PUT') return 'bg-amber-500';
    if (method === 'DELETE') return 'bg-red-500';
    return 'bg-slate-500';
};

const classifySection = (path: string): string => {
    if (path.startsWith('/api/v1/auth/')) return 'Auth & Session';

    if (
        path === '/api/v1/tags' ||
        path.startsWith('/api/v1/meta/') ||
        path.startsWith('/api/v1/status') ||
        path.startsWith('/api/v1/wiki/') ||
        path.startsWith('/api/v1/og/') ||
        path === '/api/v1/analytics/platform/stats'
    ) {
        return 'Metadata & System';
    }

    if (
        path.startsWith('/api/v1/projects') ||
        path.startsWith('/api/v1/version/') ||
        path.startsWith('/api/v1/download/') ||
        path.startsWith('/api/v1/download-bundle/')
    ) {
        return 'Projects, Versions & Downloads';
    }

    if (
        path.startsWith('/api/v1/user/') ||
        path.startsWith('/api/v1/users/') ||
        path.startsWith('/api/v1/creators/')
    ) {
        return 'Users & Profiles';
    }

    if (path === '/api/v1/orgs' || path.startsWith('/api/v1/orgs/')) return 'Organizations & Connections';
    if (path === '/api/v1/notifications' || path.startsWith('/api/v1/notifications/') || path === '/api/v1/reports') return 'Notifications & Reports';

    return 'Other';
};

const sectionIcon = (section: string): React.ReactNode => {
    if (section === 'Metadata & System') return <Database className="w-6 h-6 text-slate-400" />;
    if (section === 'Projects, Versions & Downloads') return <Download className="w-6 h-6 text-slate-400" />;
    if (section === 'Users & Profiles') return <User className="w-6 h-6 text-slate-400" />;
    if (section === 'Organizations & Connections') return <Users className="w-6 h-6 text-slate-400" />;
    if (section === 'Notifications & Reports') return <Bell className="w-6 h-6 text-slate-400" />;
    if (section === 'Auth & Session') return <Key className="w-6 h-6 text-slate-400" />;
    return <Globe className="w-6 h-6 text-slate-400" />;
};

const toHint = (schema: Record<string, unknown> | undefined): string => {
    if (!schema) return 'schema';
    const ref = typeof schema.$ref === 'string' ? schema.$ref : null;
    if (ref) return `$ref ${ref}`;

    const schemaType = typeof schema.type === 'string' ? schema.type : null;
    if (!schemaType) return 'schema';

    const format = typeof schema.format === 'string' ? schema.format : null;
    return format ? `${schemaType} (${format})` : schemaType;
};

const schemaLabel = (schema: RawSchema | undefined, registry: Record<string, RawSchema>): string => {
    if (!schema) return 'schema';
    if (schema.$ref) {
        const key = schema.$ref.includes('/') ? schema.$ref.substring(schema.$ref.lastIndexOf('/') + 1) : schema.$ref;
        return key;
    }

    if (schema.enum && schema.enum.length > 0) {
        const baseType = schema.type || 'enum';
        return `${baseType} (${schema.enum.map((item) => String(item)).join(' | ')})`;
    }

    if (schema.allOf?.length) {
        return schema.allOf.map((part) => schemaLabel(part, registry)).join(' & ');
    }
    if (schema.oneOf?.length) {
        return schema.oneOf.map((part) => schemaLabel(part, registry)).join(' | ');
    }
    if (schema.anyOf?.length) {
        return schema.anyOf.map((part) => schemaLabel(part, registry)).join(' | ');
    }
    if (schema.type === 'array' || schema.items) {
        return `Array<${schemaLabel(schema.items, registry)}>`;
    }
    if (schema.additionalProperties) {
        if (typeof schema.additionalProperties === 'object') {
            return `Record<string, ${schemaLabel(schema.additionalProperties, registry)}>`;
        }
        return 'Record<string, unknown>';
    }

    const type = schema.type || 'object';
    return schema.format ? `${type} (${schema.format})` : type;
};

const stringifyExample = (value: unknown): string => {
    if (typeof value === 'string') {
        const trimmed = value.trim();
        if (trimmed.startsWith('{') || trimmed.startsWith('[') || trimmed.startsWith('"')) {
            return value;
        }
        return JSON.stringify(value, null, 2);
    }
    try {
        return JSON.stringify(value, null, 2);
    } catch {
        return String(value);
    }
};

const isJsonContentType = (contentType: string): boolean => {
    const normalized = contentType.toLowerCase();
    return normalized.includes('json') || normalized.endsWith('+json');
};

const isAdminOnlyPath = (path: string): boolean => {
    if (path.startsWith('/api/v1/admin/')) return true;
    if (path === '/api/v1/admin') return true;
    if (path === '/api/v1/analytics/platform/full') return true;
    return false;
};

const resolveRefSchema = (ref: string, registry: Record<string, RawSchema>): RawSchema | undefined => {
    const key = ref.includes('/') ? ref.substring(ref.lastIndexOf('/') + 1) : ref;
    return registry[key];
};

const primitiveExample = (schema: RawSchema | undefined, fieldName?: string): unknown => {
    const name = (fieldName || '').toLowerCase();
    const type = schema?.type || '';
    const format = schema?.format || '';

    if (format === 'uuid' || name === 'id' || name.endsWith('id') || name.endsWith('_id')) {
        return '123e4567-e89b-12d3-a456-426614174000';
    }
    if (format === 'date-time' || name.endsWith('at') || name.endsWith('_at') || name.includes('timestamp')) {
        return '2026-01-01T00:00:00Z';
    }
    if (format === 'date') {
        return '2026-01-01';
    }
    if (format === 'email' || name === 'email') {
        return 'creator@example.com';
    }
    if (format === 'uri' || format === 'url' || name.includes('url') || name.includes('uri') || name.includes('link')) {
        return 'https://example.modtale.net/resource';
    }
    if (name.includes('username') || name === 'user') {
        return 'modtale_creator';
    }
    if (name.includes('slug')) {
        return 'skyforge-utilities';
    }
    if (name.includes('title') || name.includes('name')) {
        return 'Skyforge Utilities';
    }
    if (name.includes('description') || name.includes('summary') || name.includes('bio')) {
        return 'Example response text for this field.';
    }
    if (name === 'status') {
        return 'success';
    }
    if (name.includes('message')) {
        return 'Operation completed successfully.';
    }
    if (name.includes('error')) {
        return 'Bad Request';
    }
    if (name.includes('token')) {
        return 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.example.signature';
    }

    if (type === 'boolean' || name.startsWith('is') || name.startsWith('has') || name.endsWith('enabled')) {
        return true;
    }
    if (type === 'integer' || type === 'number' || format === 'int32' || format === 'int64' || format === 'float' || format === 'double') {
        if (name.includes('count') || name.includes('total')) return 42;
        if (name.includes('page')) return 1;
        if (name.includes('size') || name.includes('limit')) return 20;
        return 1;
    }
    if (type === 'array') {
        return ['example'];
    }
    if (type === 'object') {
        return { key: 'value' };
    }

    return 'example';
};

const buildSchemaExample = (
    schema: RawSchema | undefined,
    registry: Record<string, RawSchema>,
    fieldName: string,
    depth: number,
    seenRefs: Set<string>,
): unknown => {
    if (!schema || depth > EXAMPLE_MAX_DEPTH) {
        return primitiveExample(schema, fieldName);
    }

    if (schema.example !== undefined) return schema.example;
    if (schema.default !== undefined) return schema.default;
    if (schema.enum && schema.enum.length > 0) return schema.enum[0];

    if (schema.$ref) {
        const refKey = schema.$ref.includes('/') ? schema.$ref.substring(schema.$ref.lastIndexOf('/') + 1) : schema.$ref;
        if (seenRefs.has(refKey)) {
            return { id: primitiveExample({ format: 'uuid' }, 'id') };
        }
        const target = resolveRefSchema(schema.$ref, registry);
        if (!target) {
            return primitiveExample(schema, fieldName);
        }
        const nextSeen = new Set(seenRefs);
        nextSeen.add(refKey);
        return buildSchemaExample(target, registry, fieldName, depth + 1, nextSeen);
    }

    if (schema.allOf && schema.allOf.length > 0) {
        const merged: Record<string, unknown> = {};
        schema.allOf.forEach((part) => {
            const partExample = buildSchemaExample(part, registry, fieldName, depth + 1, seenRefs);
            if (partExample && typeof partExample === 'object' && !Array.isArray(partExample)) {
                Object.assign(merged, partExample);
            }
        });
        if (Object.keys(merged).length > 0) return merged;
    }

    if (schema.oneOf && schema.oneOf.length > 0) {
        return buildSchemaExample(schema.oneOf[0], registry, fieldName, depth + 1, seenRefs);
    }

    if (schema.anyOf && schema.anyOf.length > 0) {
        return buildSchemaExample(schema.anyOf[0], registry, fieldName, depth + 1, seenRefs);
    }

    if (schema.type === 'array' || schema.items) {
        const item = buildSchemaExample(schema.items, registry, `${fieldName}_item`, depth + 1, seenRefs);
        return [item, item];
    }

    if (schema.type === 'object' || schema.properties || schema.additionalProperties) {
        const obj: Record<string, unknown> = {};
        const required = new Set(schema.required || []);
        const entries = Object.entries(schema.properties || {});

        entries
            .sort(([a], [b]) => {
                const aReq = required.has(a) ? 0 : 1;
                const bReq = required.has(b) ? 0 : 1;
                if (aReq !== bReq) return aReq - bReq;
                return a.localeCompare(b);
            })
            .forEach(([name, propSchema]) => {
                obj[name] = buildSchemaExample(propSchema, registry, name, depth + 1, seenRefs);
            });

        if (Object.keys(obj).length === 0 && typeof schema.additionalProperties === 'object') {
            obj.additionalProp1 = buildSchemaExample(schema.additionalProperties, registry, 'additionalProp1', depth + 1, seenRefs);
        }

        return obj;
    }

    return primitiveExample(schema, fieldName);
};

const sampleUserSummary = {
    id: '67f5f6a0d5de9b5f94b61234',
    username: 'modtale_creator',
    avatarUrl: 'https://cdn.modtale.net/avatars/modtale_creator.png',
    bannerUrl: 'https://cdn.modtale.net/banners/modtale_creator.png',
    bio: 'Creator of performance-focused Minecraft tools.',
    createdAt: '2025-01-16T13:44:02Z',
    tier: 'STANDARD',
    roles: ['USER'],
    accountType: 'USER',
    badges: ['EARLY_CREATOR'],
};

const sampleUser = {
    ...sampleUserSummary,
    email: 'creator@example.com',
    emailVerified: true,
    mfaEnabled: true,
    followingIds: ['67f5f6a0d5de9b5f94b62222'],
    followerIds: ['67f5f6a0d5de9b5f94b63333'],
    connectedAccounts: [
        {
            provider: 'github',
            providerId: '1287312',
            username: 'modtale-creator',
            profileUrl: 'https://github.com/modtale-creator',
            visible: true,
        },
    ],
    notificationPreferences: {
        projectUpdates: 'ON',
        creatorUploads: 'ON',
        newComments: 'ON',
        newFollowers: 'ON',
        dependencyUpdates: 'OFF',
    },
    organizationRoles: [],
    organizationMembers: [],
    pendingOrgInvites: [],
    likedModIds: ['67f62da0d5de9b5f94b69999'],
};

const sampleVersionSummary = {
    id: '6806d1e9db0dbb390f0f1001',
    versionNumber: '2.4.1',
    gameVersions: ['1.20.1', '1.20.4'],
    downloadCount: 9214,
    releaseDate: '2026-04-01T20:15:00Z',
    channel: 'RELEASE',
    reviewStatus: 'APPROVED',
};

const sampleProjectSummary = {
    id: '67f62da0d5de9b5f94b69999',
    slug: 'skyforge-utilities',
    title: 'Skyforge Utilities',
    description: 'Optimization and QoL utilities for multiplayer servers.',
    authorId: '67f5f6a0d5de9b5f94b61234',
    author: 'modtale_creator',
    imageUrl: 'https://cdn.modtale.net/projects/skyforge/icon.png',
    bannerUrl: 'https://cdn.modtale.net/projects/skyforge/banner.png',
    classification: 'PLUGIN',
    downloadCount: 154321,
    favoriteCount: 3821,
    updatedAt: '2026-05-24T16:42:10Z',
    childProjectIds: [],
};

const sampleProject = {
    ...sampleProjectSummary,
    categories: ['Performance', 'Utilities'],
    tags: ['performance', 'server', 'utility'],
    about: 'A toolkit for profiling tick performance and balancing gameplay.',
    trendScore: 91,
    relevanceScore: 0.98,
    popularScore: 0.94,
    repositoryUrl: 'https://github.com/modtale/skyforge-utilities',
    createdAt: '2025-09-11T10:00:00Z',
    license: 'MIT',
    links: {
        homepage: 'https://modtale.net/mod/skyforge-utilities',
        docs: 'https://docs.modtale.net/skyforge-utilities',
        issues: 'https://github.com/modtale/skyforge-utilities/issues',
    },
    types: ['SERVER'],
    allowModpacks: true,
    allowComments: true,
    hmWikiEnabled: true,
    hmWikiSlug: 'skyforge-utilities',
    status: 'PUBLISHED',
    canEdit: false,
    isOwner: false,
    projectRoles: [
        {
            id: 'role-maintainer',
            name: 'Maintainer',
            color: '#22c55e',
            permissions: ['PROJECT_EDIT_METADATA', 'VERSION_CREATE'],
        },
    ],
    teamMembers: [
        {
            userId: '67f5f6a0d5de9b5f94b61234',
            roleId: 'role-maintainer',
        },
    ],
    teamInvites: [],
    galleryImages: [
        'https://cdn.modtale.net/projects/skyforge/gallery/1.png',
    ],
    comments: [
        {
            id: 'comment-9001',
            userId: '67f5f6a0d5de9b5f94b63333',
            content: 'Incredible performance gains on our 300-player server.',
            date: '2026-05-05T14:21:00Z',
            updatedAt: '2026-05-05T14:21:00Z',
            upvoteCount: 22,
            downvoteCount: 1,
            userVote: null,
            developerReply: {
                userId: '67f5f6a0d5de9b5f94b61234',
                content: 'Thanks! We just shipped a memory patch in 2.4.1.',
                date: '2026-05-05T18:00:00Z',
                upvoteCount: 10,
                downvoteCount: 0,
                userVote: null,
            },
        },
    ],
    versions: [
        {
            ...sampleVersionSummary,
            fileUrl: 'projects/skyforge-utilities/2.4.1.jar',
            changelog: '- Improved chunk scheduler\n- Fixed memory leak in cache invalidation',
            dependencies: [
                {
                    modId: '67f70e06d5de9b5f94b6a111',
                    modTitle: 'Skyforge Core',
                    versionNumber: '3.1.0',
                    optional: false,
                },
            ],
        },
    ],
};

const sampleProjectShell = {
    id: sampleProject.id,
    slug: sampleProject.slug,
    title: sampleProject.title,
    about: sampleProject.about,
    description: sampleProject.description,
    authorId: sampleProject.authorId,
    author: sampleProject.author,
    imageUrl: sampleProject.imageUrl,
    bannerUrl: sampleProject.bannerUrl,
    classification: sampleProject.classification,
    tags: sampleProject.tags,
    downloadCount: sampleProject.downloadCount,
    favoriteCount: sampleProject.favoriteCount,
    repositoryUrl: sampleProject.repositoryUrl,
    updatedAt: sampleProject.updatedAt,
    createdAt: sampleProject.createdAt,
    license: sampleProject.license,
    links: sampleProject.links,
    allowModpacks: sampleProject.allowModpacks,
    allowComments: sampleProject.allowComments,
    hmWikiEnabled: sampleProject.hmWikiEnabled,
    hmWikiSlug: sampleProject.hmWikiSlug,
    status: sampleProject.status,
    canEdit: sampleProject.canEdit,
    isOwner: sampleProject.isOwner,
};

const sampleVersionWithoutChangelog = {
    ...sampleVersionSummary,
    fileUrl: 'projects/skyforge-utilities/2.4.1.jar',
    dependencies: [
        {
            modId: '67f70e06d5de9b5f94b6a111',
            modTitle: 'Skyforge Core',
            versionNumber: '3.1.0',
            optional: false,
        },
    ],
};

const samplePage = (content: unknown[]) => ({
    content,
    pageable: {
        pageNumber: 0,
        pageSize: 10,
        sort: { sorted: true, unsorted: false, empty: false },
        offset: 0,
        paged: true,
        unpaged: false,
    },
    totalPages: 1,
    totalElements: content.length,
    last: true,
    size: 10,
    number: 0,
    sort: { sorted: true, unsorted: false, empty: false },
    numberOfElements: content.length,
    first: true,
    empty: content.length === 0,
});

const endpointSpecificExample = (method: string, path: string, code: string): unknown | null => {
    const status = Number.parseInt(code, 10);
    const is2xx = !Number.isNaN(status) && status >= 200 && status < 300;
    const is4xx = !Number.isNaN(status) && status >= 400 && status < 500;

    if (path.startsWith('/api/v1/download/') || path.startsWith('/api/v1/download-bundle/')) return null;

    if (path === '/api/v1/auth/register') {
        if (code === '200') return { message: 'User registered successfully', username: 'new_creator' };
        if (code === '400') return { error: 'Username is already taken' };
    }
    if (path === '/api/v1/auth/verify') {
        if (code === '200') return { message: 'Email verified successfully' };
        if (code === '400') return { error: 'Verification token is invalid or expired' };
    }
    if (path === '/api/v1/auth/resend-verification' && code === '200') return { message: 'Verification email sent' };
    if (path === '/api/v1/auth/forgot-password' && code === '200') {
        return { message: 'If an account exists for that email, a password reset link has been sent.' };
    }
    if (path === '/api/v1/auth/reset-password') {
        if (code === '200') return { message: 'Password reset successfully. You can now login.' };
        if (code === '400') return { error: 'Reset token is invalid or expired' };
    }
    if (path === '/api/v1/auth/mfa/setup') {
        if (code === '200') {
            return {
                secret: 'JBSWY3DPEHPK3PXP',
                qrCode: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA...',
            };
        }
        if (code === '400') return { error: 'MFA is already enabled.' };
    }
    if (path === '/api/v1/auth/mfa/verify') {
        if (code === '200') return { message: 'MFA enabled successfully' };
        if (code === '400') return { error: 'Invalid verification code. 2FA not enabled.' };
    }
    if (path === '/api/v1/auth/signin') {
        if (code === '200') return { status: 'success' };
        if (code === '202') return { mfaRequired: true, preAuthToken: 'b8af319e-09a3-4f5a-9d87-9695dcab4711' };
        if (code === '401') return { error: 'Invalid credentials' };
    }
    if (path === '/api/v1/auth/mfa/validate-login') {
        if (code === '200') return { status: 'success' };
        if (code === '400') return { error: 'Invalid 2FA code' };
        if (code === '401') return { error: 'Session expired or invalid. Please login again.' };
    }

    if (path === '/api/v1/projects' && method === 'GET' && code === '200') return samplePage([sampleProjectSummary]);
    if (path === '/api/v1/projects/{id}' && method === 'GET' && code === '200') return sampleProjectShell;
    if (path === '/api/v1/projects/{id}/details' && method === 'GET' && code === '200') return sampleProject;
    if (path === '/api/v1/projects/{id}/versions' && method === 'GET' && code === '200') return { versions: [sampleVersionWithoutChangelog] };
    if (path === '/api/v1/projects/{id}/comments' && method === 'GET' && code === '200') return { comments: sampleProject.comments };
    if (path === '/api/v1/projects/{id}/gallery' && method === 'GET' && code === '200') return { galleryImages: sampleProject.galleryImages };
    if (path === '/api/v1/projects/{id}/team' && method === 'GET' && code === '200') {
        return {
            projectRoles: sampleProject.projectRoles,
            teamMembers: sampleProject.teamMembers,
            teamInvites: sampleProject.teamInvites,
        };
    }
    if (path === '/api/v1/projects/{id}/meta' && code === '200') {
        return {
            title: sampleProject.title,
            description: sampleProject.description,
            icon: sampleProject.imageUrl,
            author: sampleProject.author,
            classification: sampleProject.classification,
            downloads: sampleProject.downloadCount,
            repositoryUrl: sampleProject.repositoryUrl,
            slug: sampleProject.slug,
        };
    }
    if (path === '/api/v1/tags' && code === '200') return ['performance', 'utility', 'pvp', 'economy'];
    if (path === '/api/v1/meta/classifications' && code === '200') return ['PLUGIN', 'DATA', 'ART', 'SAVE', 'MODPACK'];
    if (path === '/api/v1/meta/game-versions' && code === '200') return ['1.20.1', '1.20.4', '1.21.1'];
    if (path === '/api/v1/meta/game-versions/catalog' && code === '200') {
        return {
            releaseVersions: ['1.21.1', '1.20.4', '1.20.1'],
            preReleaseVersions: ['1.21.2-rc1'],
            allVersions: ['1.21.2-rc1', '1.21.1', '1.20.4', '1.20.1'],
            orderedVersions: ['1.21.2-rc1', '1.21.1', '1.20.4', '1.20.1'],
        };
    }
    if (path === '/api/v1/projects' && method === 'POST' && code === '200') return sampleProject;
    if (path === '/api/v1/projects/{id}/versions/{version}/dependencies' && code === '200') {
        return {
            dependencies: [
                { id: 'b612a7db-3475-4c43-bb46-c951b330bcd2', projectId: '67f70e06d5de9b5f94b6a111', projectTitle: 'Skyforge Core', versionNumber: '3.1.0', dependencyType: 'REQUIRED', source: 'MODTALE' },
                { id: '3c6b637b-76cb-4efe-9d20-c7b5356e2676', projectId: '67f70e06d5de9b5f94b6a222', projectTitle: 'Skyforge Map Layer', versionNumber: '1.4.2', dependencyType: 'OPTIONAL', source: 'MODTALE' },
                { id: '7737a837-76d3-414a-a149-c078ff981c0b', projectId: 'curseforge:1450386', projectTitle: 'SimpleCompost', versionNumber: '1.0.0', dependencyType: 'REQUIRED', source: 'CURSEFORGE', externalId: '1450386', externalUrl: 'https://www.curseforge.com/hytale/mods/simplecompost/files/8227810', externalFileUrl: 'https://www.curseforge.com/api/v1/mods/1450386/files/8227810/download', externalFileName: 'SimpleCompost-1.0.0.jar', cachedFileUrl: 'external-dependencies/curseforge/1450386/8227810/SimpleCompost-1.0.0.jar' },
            ],
        };
    }
    if (path === '/api/v1/projects/{id}/versions/dependency-suggestions' && code === '200') {
        return {
            gameVersion: '1.20.1',
            modVersion: '2.4.1',
            suggestions: [
                {
                    manifestKey: 'skyforge_core',
                    requestedVersion: '[3.0.0,)',
                    projectId: '67f70e06d5de9b5f94b6a111',
                    projectTitle: 'Skyforge Core',
                    versionNumber: '3.1.0',
                    optional: false,
                    confidence: 97,
                },
            ],
        };
    }
    if (path === '/api/v1/projects/{id}/versions/{version}/download-url' && code === '200') {
        return {
            downloadUrl: '/download/eyJwcm9qZWN0SWQiOiI2N2Y2MmRhMGQ1ZGU5YjVmOTRiNjk5OTkiLCJ2ZXJzaW9uIjoiMi40LjEifQ',
            expiresIn: 300,
        };
    }
    if (path === '/api/v1/projects/{id}/versions/{version}/download-bundle-url' && code === '200') {
        return {
            downloadUrl: '/download-bundle/eyJwcm9qZWN0SWQiOiI2N2Y2MmRhMGQ1ZGU5YjVmOTRiNjk5OTkiLCJ2ZXJzaW9uIjoiMi40LjEifQ',
            expiresIn: 300,
        };
    }
    if (path === '/api/v1/version/{hash}' && code === '200') {
        return {
            id: sampleVersionSummary.id,
            versionNumber: sampleVersionSummary.versionNumber,
            gameVersions: sampleVersionSummary.gameVersions,
            fileUrl: 'projects/skyforge-utilities/2.4.1.jar',
            downloadCount: sampleVersionSummary.downloadCount,
            releaseDate: sampleVersionSummary.releaseDate,
            changelog: '- Improved chunk scheduler\n- Fixed memory leak in cache invalidation',
            dependencies: [
                { modId: '67f70e06d5de9b5f94b6a111', modTitle: 'Skyforge Core', versionNumber: '3.1.0', optional: false },
            ],
            channel: sampleVersionSummary.channel,
        };
    }

    if (path === '/api/v1/users/search' && code === '200') return [sampleUserSummary];
    if (path === '/api/v1/users/batch' && code === '200') return [sampleUserSummary];
    if (path === '/api/v1/user/me' && code === '200') return sampleUser;
    if (path === '/api/v1/user/profile/{userId}' && code === '200') {
        return {
            ...sampleUserSummary,
            connectedAccounts: sampleUser.connectedAccounts,
            followingIds: sampleUser.followingIds,
            followerIds: sampleUser.followerIds,
            organizationRoles: sampleUser.organizationRoles,
            organizationMembers: sampleUser.organizationMembers,
        };
    }
    if (path === '/api/v1/user/profile' && code === '200') return sampleUser;
    if ((path === '/api/v1/user/profile/avatar' || path === '/api/v1/user/profile/banner') && code === '200') {
        return 'https://cdn.modtale.net/users/modtale_creator/uploaded-image.png';
    }
    if (path === '/api/v1/users/{userId}/following' && code === '200') return [sampleUserSummary];
    if (path === '/api/v1/users/{userId}/followers' && code === '200') return [sampleUserSummary];
    if (path === '/api/v1/creators/search' && code === '200') return [sampleUserSummary];
    if (path === '/api/v1/creators/{userId}/projects' && code === '200') return samplePage([sampleProjectSummary]);
    if (path === '/api/v1/projects/user/contributed' && code === '200') {
        return samplePage([{ ...sampleProjectSummary, canEdit: true, isOwner: false, status: 'PUBLISHED', versions: [sampleVersionSummary] }]);
    }

    if (path === '/api/v1/orgs' && method === 'POST' && code === '200') {
        return {
            ...sampleUser,
            id: '67f8f1fdd5de9b5f94b6f123',
            username: 'skyforge-studios',
            accountType: 'ORGANIZATION',
            organizationRoles: [{ id: 'owner', name: 'Owner', color: '#f97316', permissions: ['*'], isOwner: true }],
            organizationMembers: [{ userId: sampleUserSummary.id, roleId: 'owner' }],
        };
    }
    if (path === '/api/v1/user/orgs' && code === '200') return [{ ...sampleUser, accountType: 'ORGANIZATION' }];
    if (path === '/api/v1/users/{userId}/organizations' && code === '200') return [{ ...sampleUserSummary, accountType: 'ORGANIZATION' }];
    if (path === '/api/v1/orgs/{orgId}/members' && code === '200') return [sampleUserSummary];
    if (path === '/api/v1/orgs/{orgId}/invites' && code === '200') {
        return [{ ...sampleUserSummary, username: 'invite_pending_user' }];
    }
    if (
        (
            path === '/api/v1/orgs/{orgId}/roles' ||
            path === '/api/v1/orgs/{orgId}/roles/{roleId}' ||
            path === '/api/v1/orgs/{orgId}'
        ) &&
        is2xx
    ) {
        return {
            ...sampleUser,
            id: '67f8f1fdd5de9b5f94b6f123',
            username: 'skyforge-studios',
            accountType: 'ORGANIZATION',
        };
    }
    if ((path === '/api/v1/orgs/{orgId}/avatar' || path === '/api/v1/orgs/{orgId}/banner') && code === '200') {
        return { url: 'https://cdn.modtale.net/orgs/skyforge-studios/uploaded-image.png' };
    }

    if (path === '/api/v1/notifications' && code === '200') {
        return [
            {
                id: 'notif-83c120',
                title: 'New follower',
                message: 'buildercraft started following you.',
                link: SiteRoutes.creator(sampleUserSummary.id, sampleUserSummary.username),
                iconUrl: 'https://cdn.modtale.net/avatars/buildercraft.png',
                read: false,
                type: 'NEW_FOLLOWER',
                metadata: {
                    actorId: '67f5f6a0d5de9b5f94b63333',
                },
                createdAt: '2026-05-28T15:34:12',
            },
        ];
    }

    if (path === '/api/v1/reports' && code === '200') return { id: 'report-1f3410df8a3' };

    if (path === '/api/v1/user/repos' && code === '200') {
        return [
            {
                name: 'modtale/skyforge-utilities',
                url: 'https://github.com/modtale/skyforge-utilities',
                description: 'Optimization and QoL utilities for multiplayer servers.',
                private: false,
            },
        ];
    }
    if ((path === '/api/v1/user/repos/github' || path === '/api/v1/user/repos/gitlab' || path === '/api/v1/orgs/{orgId}/repos/github') && code === '200') {
        return [
            {
                name: 'modtale/skyforge-core',
                url: 'https://github.com/modtale/skyforge-core',
                description: 'Core shared libraries for Skyforge projects.',
                private: false,
            },
        ];
    }

    if (path === '/api/v1/status' && code === '200') {
        return {
            overall: 'operational',
            services: [
                { id: 'api', name: 'API Gateway', status: 'operational', latency: 42 },
                { id: 'database', name: 'Database (Atlas)', status: 'operational', latency: 28 },
                { id: 'storage', name: 'Storage (R2)', status: 'operational', latency: 57 },
            ],
            timestamp: 1770000000000,
            history: [
                { time: 1769996400000, api: 39, db: 27, storage: 55 },
                { time: 1769998200000, api: 43, db: 29, storage: 58 },
            ],
        };
    }

    if (path === '/api/v1/analytics/platform/stats' && code === '200') {
        return {
            totalProjects: 2842,
            totalUsers: 49713,
            totalDownloads: 92841653,
        };
    }
    if (path === '/api/v1/user/analytics' && code === '200') {
        return {
            totalDownloads: 287441,
            totalViews: 942110,
            periodDownloads: 12495,
            previousPeriodDownloads: 10112,
            periodViews: 34980,
            previousPeriodViews: 31109,
            projectDownloads: {
                '67f62da0d5de9b5f94b69999': [
                    { date: '2026-05-01', value: 421, count: 421 },
                    { date: '2026-05-02', value: 398, count: 398 },
                ],
            },
            projectViews: {
                '67f62da0d5de9b5f94b69999': [
                    { date: '2026-05-01', value: 1310, count: 1310 },
                    { date: '2026-05-02', value: 1274, count: 1274 },
                ],
            },
            projectMeta: {
                '67f62da0d5de9b5f94b69999': {
                    id: '67f62da0d5de9b5f94b69999',
                    title: 'Skyforge Utilities',
                    totalDownloads: 154321,
                },
            },
        };
    }
    if (path === '/api/v1/projects/{id}/analytics' && code === '200') {
        return {
            projectId: '67f62da0d5de9b5f94b69999',
            projectTitle: 'Skyforge Utilities',
            totalDownloads: 154321,
            totalViews: 481221,
            totalApiDownloads: 44211,
            totalFrontendDownloads: 110110,
            views: [
                { date: '2026-05-01', value: 1302, count: 1302 },
                { date: '2026-05-02', value: 1287, count: 1287 },
            ],
            versionDownloads: {
                '2.4.1': [
                    { date: '2026-05-01', value: 305, count: 305 },
                    { date: '2026-05-02', value: 318, count: 318 },
                ],
            },
        };
    }

    if (path === '/api/v1/user/api-keys' && method === 'GET' && code === '200') {
        return [
            {
                id: 'apikey_67facb28',
                name: 'CI Deploy Key',
                prefix: 'modtale_sk_live_',
                tier: 'STANDARD',
                contextPermissions: {
                    GLOBAL: ['PROJECT_READ', 'VERSION_READ'],
                },
                lastUsed: '2026-05-27T20:10:11',
                createdAt: '2026-02-18T09:30:00',
            },
        ];
    }
    if (path === '/api/v1/user/api-keys' && method === 'POST') {
        if (code === '200') return { key: 'modtale_sk_live_3f3b1c8f7b5e4e9b91bcb836637f1d74' };
        if (code === '403') return { error: 'Email verification required.' };
    }

    if (path === '/api/v1/wiki/{id}' && code === '200') {
        return {
            project: {
                slug: 'skyforge-utilities',
                title: 'Skyforge Utilities',
            },
            page: {
                slug: 'home',
                title: 'Home',
                html: '<h1>Skyforge Utilities Wiki</h1><p>Getting started...</p>',
            },
        };
    }

    if (is4xx && code === '400') {
        return { error: 'Invalid request' };
    }

    return null;
};

const extractExamples = (
    method: string,
    path: string,
    code: string,
    response: NonNullable<RawOperation['responses']>[string],
    registry: Record<string, RawSchema>,
): ResponseExample[] => {
    const examples: ResponseExample[] = [];
    const content = response.content || {};

    Object.entries(content).forEach(([contentType, media]) => {
        if (media.example !== undefined) {
            examples.push({ contentType, value: stringifyExample(media.example) });
            return;
        }

        const namedExamples = media.examples || {};
        Object.entries(namedExamples).forEach(([name, ex]) => {
            if (ex && ex.value !== undefined) {
                examples.push({ contentType, name, value: stringifyExample(ex.value) });
            }
        });

        if (examples.length > 0 || !isJsonContentType(contentType)) {
            return;
        }

        const generated = buildSchemaExample(media.schema, registry, 'response', 0, new Set());
        if (generated !== undefined) {
            examples.push({
                contentType,
                value: stringifyExample(generated),
            });
        }
    });

    if (examples.length === 0) {
        const endpointExample = endpointSpecificExample(method, path, code);
        if (endpointExample !== null) {
            const jsonContentType = Object.keys(content).find(isJsonContentType) || 'application/json';
            examples.push({
                contentType: jsonContentType,
                value: stringifyExample(endpointExample),
            });
        }
    }

    return examples;
};

const parseOpenApi = (raw: RawOpenApi): OpenApiIndex => {
    const endpoints: EndpointDoc[] = [];
    const rateLimitTierMap = new Map<string, RateLimitTier>();
    const paths = raw.paths || {};
    const schemas = raw.components?.schemas || {};
    const schemaDocs: SchemaDoc[] = Object.entries(schemas)
        .map(([name, schema]) => {
            const resolved = schema.$ref ? resolveRefSchema(schema.$ref, schemas) || schema : schema;
            const required = new Set(resolved.required || []);
            const fields = Object.entries(resolved.properties || {})
                .map(([fieldName, fieldSchema]) => ({
                    name: fieldName,
                    required: required.has(fieldName),
                    type: schemaLabel(fieldSchema, schemas),
                    description: fieldSchema.description,
                }))
                .sort((a, b) => {
                    if (a.required !== b.required) return a.required ? -1 : 1;
                    return a.name.localeCompare(b.name);
                });

            return {
                name,
                type: schemaLabel(resolved, schemas),
                description: resolved.description,
                fields,
                example: stringifyExample(buildSchemaExample(resolved, schemas, name, 0, new Set())),
            };
        })
        .sort((a, b) => a.name.localeCompare(b.name));

    Object.entries(paths).forEach(([path, pathItem]) => {
        if (isAdminOnlyPath(path)) return;

        HTTP_METHODS.forEach((method) => {
            const operation = pathItem[method];
            if (!operation) return;

            const params = (operation.parameters || []).map((p) => ({
                name: p.name || 'unknown',
                in: p.in || 'query',
                required: !!p.required,
                description: p.description,
                type: p.schema?.type,
            }));

            const requestBody = operation.requestBody
                ? {
                    required: !!operation.requestBody.required,
                    contentTypes: Object.keys(operation.requestBody.content || {}),
                    schemaHints: Object.values(operation.requestBody.content || {}).map((c) => toHint(c.schema)),
                }
                : undefined;

            const responses: EndpointResponse[] = Object.entries(operation.responses || {})
                .map(([code, response]) => ({
                    code,
                    description: response.description,
                    examples: extractExamples(method.toUpperCase(), path, code, response, schemas),
                }))
                .sort((a, b) => a.code.localeCompare(b.code, undefined, { numeric: true }));

            const isPublic = operation['x-modtale-access'] === 'public' || (Array.isArray(operation.security) && operation.security.length === 0);
            const endpointRateLimitTiers = Array.isArray(operation['x-modtale-rate-limit-tiers'])
                ? operation['x-modtale-rate-limit-tiers'].filter((tier) => tier.name !== ADMIN_RATE_LIMIT_TIER)
                : [];

            endpointRateLimitTiers.forEach((tier) => {
                if (!rateLimitTierMap.has(tier.name)) {
                    rateLimitTierMap.set(tier.name, tier);
                }
            });

            endpoints.push({
                method: method.toUpperCase(),
                path,
                summary: operation.summary || `${method.toUpperCase()} ${path}`,
                description: operation.description,
                public: isPublic,
                params,
                requestBody,
                responses,
                rateLimitTiers: endpointRateLimitTiers,
            });
        });
    });

    const server = raw.servers?.[0]?.url || BACKEND_URL;

    return {
        title: raw.info?.title || 'Modtale API',
        version: raw.info?.version || 'v1',
        server,
        totalEndpoints: endpoints.length,
        rateLimitTiers: Array.from(rateLimitTierMap.values()).sort((a, b) => a.name.localeCompare(b.name)),
        schemas: schemaDocs,
        endpoints,
    };
};

const SchemaCard: React.FC<{ schema: SchemaDoc }> = ({ schema }) => {
    return (
        <div className="grid grid-cols-1 xl:grid-cols-[minmax(0,1.1fr)_minmax(0,0.9fr)] gap-4 p-5 rounded-2xl border border-slate-200 dark:border-white/10 bg-slate-50/80 dark:bg-slate-950/40 overflow-hidden">
            <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2 mb-2">
                    <h3 className="text-lg font-black text-slate-900 dark:text-white break-all">{schema.name}</h3>
                    <span className="px-2 py-1 rounded-full text-[11px] font-mono bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 text-slate-600 dark:text-slate-300">
                        {schema.type}
                    </span>
                </div>
                {schema.description && (
                    <p className="text-sm text-slate-600 dark:text-slate-400 mb-4 leading-relaxed">{schema.description}</p>
                )}

                {schema.fields.length > 0 ? (
                    <div className="space-y-2">
                        {schema.fields.map((field) => (
                            <div key={`${schema.name}:${field.name}`} className="rounded-xl border border-slate-200 dark:border-white/10 bg-white/80 dark:bg-slate-900/70 p-3">
                                <div className="flex flex-wrap items-center gap-2 text-sm">
                                    <span className="font-mono font-bold text-slate-900 dark:text-white break-all">{field.name}</span>
                                    <ChevronRight className="w-3 h-3 text-slate-400" />
                                    <span className="font-mono text-slate-600 dark:text-slate-300 break-all">{field.type}</span>
                                    {field.required && (
                                        <span className="px-1.5 py-0.5 rounded bg-amber-50 dark:bg-amber-500/10 text-[10px] uppercase font-bold tracking-wide text-amber-700 dark:text-amber-300 border border-amber-200 dark:border-amber-500/20">
                                            Required
                                        </span>
                                    )}
                                </div>
                                {field.description && (
                                    <p className="mt-1 text-xs text-slate-500 dark:text-slate-400 leading-relaxed">{field.description}</p>
                                )}
                            </div>
                        ))}
                    </div>
                ) : (
                    <p className="text-sm text-slate-500 dark:text-slate-400">No explicit properties are defined for this schema.</p>
                )}
            </div>

            <div className="min-w-0">
                <div className="text-[11px] uppercase tracking-[0.22em] font-bold text-slate-500 dark:text-slate-400 mb-2">
                    Example Payload
                </div>
                <pre className="whitespace-pre-wrap break-all text-[11px] bg-slate-950 text-slate-200 p-4 rounded-xl border border-slate-800 overflow-auto max-h-[28rem]">
                    {schema.example}
                </pre>
            </div>
        </div>
    );
};

const EndpointCard: React.FC<{ endpoint: EndpointDoc }> = ({ endpoint }) => {
    return (
        <div className="border-b border-slate-100 dark:border-white/5 pb-8 mb-8 last:border-0 last:pb-0 last:mb-0 w-full">
            <div className="flex flex-col md:flex-row md:items-center gap-3 font-mono text-sm mb-2">
                <div className="flex items-center gap-2">
                    <span className={`px-2 py-1 rounded text-xs font-bold text-white shadow-sm min-w-[60px] text-center ${getMethodColor(endpoint.method)}`}>
                        {endpoint.method}
                    </span>
                    {endpoint.public ? (
                        <span className="flex items-center gap-1 text-[10px] uppercase font-bold text-slate-500 bg-slate-100 dark:text-slate-400 dark:bg-white/5 px-1.5 py-0.5 rounded border border-slate-200 dark:border-white/10">
                            <Unlock className="w-3 h-3" /> Public
                        </span>
                    ) : (
                        <span className="flex items-center gap-1 text-[10px] uppercase font-bold text-amber-600 bg-amber-50 dark:text-amber-400 dark:bg-amber-500/10 px-1.5 py-0.5 rounded border border-amber-200 dark:border-amber-500/20">
                            <Lock className="w-3 h-3" /> Auth
                        </span>
                    )}
                </div>
                <span className="text-slate-700 dark:text-slate-300 font-bold select-all break-all text-base">{endpoint.path}</span>
            </div>

            <p className="text-sm text-slate-600 dark:text-slate-400 leading-relaxed max-w-4xl mb-4">
                {endpoint.summary || 'No summary provided.'}
            </p>

            {endpoint.description && (
                <div className="p-3 bg-blue-50 dark:bg-blue-900/20 border border-blue-100 dark:border-blue-800 rounded-lg text-xs text-blue-700 dark:text-blue-300 mb-4">
                    {endpoint.description}
                </div>
            )}

            <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
                <div className="space-y-4">
                    {endpoint.params.length > 0 && (
                        <div className="bg-slate-50 dark:bg-black/20 p-4 rounded-lg text-xs border border-slate-100 dark:border-white/5">
                            <h4 className="font-bold text-slate-500 uppercase mb-2 flex items-center gap-2">
                                <Layers className="w-3 h-3" /> Parameters
                            </h4>
                            <div className="max-h-52 overflow-auto space-y-2">
                                {endpoint.params.map((p) => (
                                    <div key={`${endpoint.path}:${endpoint.method}:${p.name}`} className="border-b border-slate-200 dark:border-white/10 last:border-0 pb-2 last:pb-0">
                                        <div className="font-mono text-slate-700 dark:text-slate-200 break-all">
                                            {p.name} <span className="text-slate-400">({p.in}{p.required ? ', required' : ''}{p.type ? `, ${p.type}` : ''})</span>
                                        </div>
                                        {p.description && <div className="text-slate-500 dark:text-slate-400">{p.description}</div>}
                                    </div>
                                ))}
                            </div>
                        </div>
                    )}

                    {endpoint.requestBody && (
                        <div className="bg-slate-50 dark:bg-black/20 p-4 rounded-lg text-xs border border-slate-100 dark:border-white/5">
                            <h4 className="font-bold text-slate-500 uppercase mb-2 flex items-center gap-2">
                                <FileText className="w-3 h-3" /> Request Body
                            </h4>
                            <div className="text-slate-600 dark:text-slate-300">
                                <div className="mb-2">
                                    Required: <span className="font-semibold">{endpoint.requestBody.required ? 'Yes' : 'No'}</span>
                                </div>
                                <div className="mb-2 break-all">Content Types: {endpoint.requestBody.contentTypes.join(', ') || 'None'}</div>
                                {endpoint.requestBody.schemaHints && endpoint.requestBody.schemaHints.length > 0 && (
                                    <div>
                                        Fields/Schema:
                                        <ul className="list-disc list-inside mt-1 space-y-0.5">
                                            {endpoint.requestBody.schemaHints.map((f) => (
                                                <li key={`${endpoint.path}:${endpoint.method}:${f}`} className="break-all">{f}</li>
                                            ))}
                                        </ul>
                                    </div>
                                )}
                            </div>
                        </div>
                    )}

                </div>

                <div className="bg-slate-900 p-4 rounded-lg text-xs font-mono text-slate-300 overflow-auto border border-slate-800 h-fit max-h-[480px]">
                    <h4 className="font-bold text-slate-500 uppercase mb-2">Responses</h4>
                    {endpoint.responses.length > 0 ? (
                        <div className="space-y-3">
                            {endpoint.responses.map((response) => (
                                <div key={`${endpoint.path}:${endpoint.method}:${response.code}`} className="border border-slate-800 rounded-lg p-2 bg-slate-950/40">
                                    <div className="text-slate-200 font-bold">{response.code}{response.description ? ` - ${response.description}` : ''}</div>
                                    {response.examples.length > 0 && (
                                        <div className="mt-2 space-y-2">
                                            {response.examples.map((example, idx) => (
                                                <div key={`${endpoint.path}:${endpoint.method}:${response.code}:${example.contentType}:${idx}`}>
                                                    <div className="text-[10px] uppercase text-slate-500 mb-1">
                                                        {example.contentType}{example.name ? ` - ${example.name}` : ''}
                                                    </div>
                                                    <pre className="whitespace-pre-wrap break-all text-[11px] bg-black/50 p-2 rounded border border-slate-800">{example.value}</pre>
                                                </div>
                                            ))}
                                        </div>
                                    )}
                                </div>
                            ))}
                        </div>
                    ) : (
                        <span>No response metadata.</span>
                    )}
                </div>
            </div>
        </div>
    );
};

export const ApiDocs: React.FC = () => {
    const [data, setData] = useState<OpenApiIndex | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [schemaQuery, setSchemaQuery] = useState('');

    useEffect(() => {
        let isMounted = true;

        fetch(OPENAPI_JSON_URL)
            .then((r) => {
                if (!r.ok) throw new Error(`Failed to load generated API spec (${r.status})`);
                return r.json();
            })
            .then((json: RawOpenApi) => {
                if (!isMounted) return;
                setData(parseOpenApi(json));
            })
            .catch((err: unknown) => {
                if (isMounted) setError(err instanceof Error ? err.message : 'Failed to load generated API docs.');
            });

        return () => {
            isMounted = false;
        };
    }, []);

    const grouped = useMemo(() => {
        if (!data) return new Map<string, EndpointDoc[]>();

        const map = new Map<string, EndpointDoc[]>();
        data.endpoints.forEach((ep) => {
            const section = classifySection(ep.path);
            if (!map.has(section)) map.set(section, []);
            map.get(section)!.push(ep);
        });

        map.forEach((arr) => {
            arr.sort((a, b) => {
                if (a.path === b.path) return a.method.localeCompare(b.method);
                return a.path.localeCompare(b.path);
            });
        });

        return map;
    }, [data]);

    const rateTierByName = useMemo(() => {
        const map = new Map<string, RateLimitTier>();
        (data?.rateLimitTiers || []).forEach((tier) => map.set(tier.name, tier));
        return map;
    }, [data]);

    const publicTier = rateTierByName.get('Public-IP');
    const standardTier = rateTierByName.get('Standard-API');
    const enterpriseTier = rateTierByName.get('Enterprise-API');
    const filteredSchemas = useMemo(() => {
        if (!data) return [];
        const query = schemaQuery.trim().toLowerCase();
        if (!query) return data.schemas;

        return data.schemas.filter((schema) => {
            if (schema.name.toLowerCase().includes(query)) return true;
            if ((schema.description || '').toLowerCase().includes(query)) return true;
            return schema.fields.some((field) =>
                field.name.toLowerCase().includes(query) ||
                field.type.toLowerCase().includes(query) ||
                (field.description || '').toLowerCase().includes(query));
        });
    }, [data, schemaQuery]);

    const fmt = (value: number | string | undefined): string => {
        if (typeof value === 'number') return value.toLocaleString();
        if (typeof value === 'string') return value;
        return 'N/A';
    };

    return (
        <div className="min-h-screen bg-slate-50 dark:bg-slate-950 pb-20">
            <div className="w-full max-w-[112rem] px-6 sm:px-12 md:px-16 lg:px-20 xl:px-28 mx-auto py-16 overflow-x-hidden">
                <div className="text-center mb-12 w-full">
                    <h1 className="text-4xl md:text-5xl font-black text-slate-900 dark:text-white mb-4 tracking-tight">
                        Modtale <span className="text-modtale-accent">API v1</span>
                    </h1>
                    <p className="text-lg text-slate-600 dark:text-slate-400 max-w-3xl mx-auto mb-6">
                        This page is generated from live backend OpenAPI v1 metadata. Swagger and this custom reference now share the same source.
                    </p>

                    <div className="inline-flex flex-wrap justify-center items-center gap-3 px-5 py-3 bg-slate-100 dark:bg-white/5 rounded-full text-sm font-mono text-slate-600 dark:text-slate-400 border border-slate-200 dark:border-white/10 mb-6 shadow-sm max-w-full">
                        <Server className="w-4 h-4 text-modtale-accent shrink-0" />
                        <span>Base URL:</span>
                        <span className="font-bold text-slate-900 dark:text-white select-all break-all">{data?.server ?? BACKEND_URL}</span>
                        {data && <span className="text-slate-400">• {data.totalEndpoints} operations</span>}
                    </div>

                    <div className="flex flex-col sm:flex-row justify-center items-center gap-4 w-full">
                        <Link to="/dashboard/developer" className="px-6 py-3 bg-slate-900 dark:bg-white text-white dark:text-slate-900 rounded-xl font-bold hover:opacity-90 transition-transform active:scale-95 shadow-lg flex items-center justify-center gap-2 w-full sm:w-auto">
                            <Shield className="w-4 h-4" /> Get API Key
                        </Link>
                        <Link to={SiteRoutes.swaggerDocs()} className="px-6 py-3 bg-white dark:bg-slate-900/90 border border-slate-200 dark:border-white/10 text-slate-700 dark:text-white rounded-xl font-bold hover:border-modtale-accent hover:text-modtale-accent transition-all active:scale-95 shadow-sm flex items-center justify-center gap-2 group w-full sm:w-auto">
                            <span>Open Swagger UI</span>
                            <ExternalLink className="w-3 h-3 opacity-50" />
                        </Link>
                        <a href={OPENAPI_YAML_URL} target="_blank" rel="noreferrer" className="px-6 py-3 bg-white dark:bg-slate-900/90 border border-slate-200 dark:border-white/10 text-slate-700 dark:text-white rounded-xl font-bold hover:border-modtale-accent hover:text-modtale-accent transition-all active:scale-95 shadow-sm flex items-center justify-center gap-2 group w-full sm:w-auto">
                            <span>View OpenAPI YAML</span>
                            <ExternalLink className="w-3 h-3 opacity-50" />
                        </a>
                    </div>
                </div>

                {error && (
                    <div className="mb-8 p-4 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg text-sm text-red-700 dark:text-red-300">
                        {error}
                    </div>
                )}

                {!data && !error && (
                    <div className="rounded-[2rem] border border-slate-200 dark:border-white/10 bg-white/90 dark:bg-slate-900/90 shadow-2xl overflow-hidden">
                        <div className="p-6 md:p-8">
                            <div className="flex items-center gap-3 mb-5">
                                <div className="p-3 rounded-2xl bg-slate-100 dark:bg-white/5 text-modtale-accent">
                                    <Braces className="w-6 h-6" />
                                </div>
                                <div>
                                    <h2 className="text-xl font-black text-slate-900 dark:text-white">Loading API reference</h2>
                                    <p className="text-sm text-slate-500 dark:text-slate-400">Pulling live OpenAPI metadata, examples, and schemas from the backend.</p>
                                </div>
                            </div>
                            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                                <div className="h-28 rounded-2xl bg-slate-100 dark:bg-white/5 animate-pulse" />
                                <div className="h-28 rounded-2xl bg-slate-100 dark:bg-white/5 animate-pulse" />
                                <div className="h-28 rounded-2xl bg-slate-100 dark:bg-white/5 animate-pulse" />
                            </div>
                        </div>
                    </div>
                )}

                {data && (
                    <div className="space-y-10 md:space-y-14 w-full overflow-hidden">
                        {data.rateLimitTiers.length > 0 && (
                            <section className="w-full">
                                <div className="bg-white/90 dark:bg-slate-900/90 backdrop-blur-2xl border border-slate-200 dark:border-white/10 rounded-3xl p-6 md:p-10 shadow-2xl w-full overflow-hidden">
                                    <div className="flex items-center gap-3 mb-6">
                                        <div className="p-3 bg-green-50 dark:bg-green-500/10 rounded-lg text-green-600 dark:text-green-400 shrink-0">
                                            <Key className="w-6 h-6" />
                                        </div>
                                        <div>
                                            <h2 className="text-xl font-black text-slate-900 dark:text-white">Authentication & Security</h2>
                                            <p className="text-sm text-slate-500 dark:text-slate-400">Secure your requests.</p>
                                        </div>
                                    </div>
                                    <p className="text-sm text-slate-600 dark:text-slate-400 mb-6 leading-relaxed">
                                        All authenticated API requests must be directed to <code>https://api.modtale.net</code>.
                                        Include your API key in the request header to identify your client and get higher limits.
                                    </p>

                                    <div className="bg-slate-900 rounded-lg p-4 font-mono text-sm text-slate-300 border border-white/10 overflow-x-auto mb-8 max-w-full">
                                        <span className="text-purple-400">X-MODTALE-KEY</span>: <span className="text-white">md_12345abcdef...</span>
                                    </div>

                                    <h3 className="text-sm font-bold text-slate-900 dark:text-white uppercase mb-4 tracking-wider flex items-center gap-2">
                                        <Gauge className="w-4 h-4 text-slate-400" /> Rate Limits (Token Bucket)
                                    </h3>

                                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 mb-2">
                                        <div className="p-6 rounded-xl bg-slate-50 dark:bg-slate-800/50 border border-slate-100 dark:border-white/5 relative overflow-hidden group hover:border-slate-300 dark:hover:border-white/20 transition-colors">
                                            <div className="absolute top-0 right-0 p-4 opacity-5 pointer-events-none text-slate-500"><Globe className="w-24 h-24" /></div>
                                            <div className="flex items-center gap-2 mb-3">
                                                <div className="p-2 bg-slate-100 dark:bg-white/10 rounded-lg text-slate-600 dark:text-slate-300">
                                                    <Unlock className="w-4 h-4" />
                                                </div>
                                                <span className="text-xs font-bold uppercase tracking-wider text-slate-600 dark:text-slate-400">Public Access</span>
                                            </div>
                                            <div className="text-3xl font-black text-slate-900 dark:text-white mb-1">{fmt(publicTier?.readPerMinute)} <span className="text-sm font-medium text-slate-500">req/min</span></div>
                                            <div className="text-xl font-bold text-slate-700 dark:text-slate-300">{fmt(publicTier?.writePerMinute)} <span className="text-sm font-medium text-slate-500">write/min</span></div>
                                            <p className="text-xs text-slate-500 dark:text-slate-400 mt-2">Perfect for browsing and testing. No key required.</p>
                                        </div>

                                        <div className="p-6 rounded-xl bg-blue-50 dark:bg-blue-900/10 border border-blue-100 dark:border-blue-500/20 relative overflow-hidden group hover:border-blue-200 dark:hover:border-blue-500/30 transition-colors">
                                            <div className="absolute top-0 right-0 p-4 opacity-5 pointer-events-none text-blue-500"><Shield className="w-24 h-24" /></div>
                                            <div className="flex items-center gap-2 mb-3">
                                                <div className="p-2 bg-blue-100 dark:bg-blue-500/20 rounded-lg text-blue-600 dark:text-blue-400">
                                                    <Zap className="w-4 h-4" />
                                                </div>
                                                <span className="text-xs font-bold uppercase tracking-wider text-blue-600 dark:text-blue-400">Standard Key</span>
                                            </div>
                                            <div className="text-3xl font-black text-slate-900 dark:text-white mb-1">{fmt(standardTier?.readPerMinute)} <span className="text-sm font-medium text-slate-500">req/min</span></div>
                                            <div className="text-xl font-bold text-slate-700 dark:text-slate-300">{fmt(standardTier?.writePerMinute)} <span className="text-sm font-medium text-slate-500">write/min</span></div>
                                            <p className="text-xs text-slate-500 dark:text-slate-400 mt-2">Ideal for most apps and scripts.</p>
                                        </div>

                                        <div className="p-6 rounded-xl bg-purple-50 dark:bg-purple-900/10 border border-purple-100 dark:border-purple-500/20 relative overflow-hidden group hover:border-purple-200 dark:hover:border-purple-500/30 transition-colors">
                                            <div className="absolute top-0 right-0 p-4 opacity-5 pointer-events-none text-purple-500"><Server className="w-24 h-24" /></div>
                                            <div className="flex items-center gap-2 mb-3">
                                                <div className="p-2 bg-purple-100 dark:bg-purple-500/20 rounded-lg text-purple-600 dark:text-purple-400">
                                                    <Activity className="w-4 h-4" />
                                                </div>
                                                <span className="text-xs font-bold uppercase tracking-wider text-purple-600 dark:text-purple-400">Enterprise Key</span>
                                            </div>
                                            <div className="text-3xl font-black text-purple-900 dark:text-white mb-1">{fmt(enterpriseTier?.readPerMinute)} <span className="text-sm font-medium text-purple-400">req/min</span></div>
                                            <div className="text-xl font-bold text-purple-700 dark:text-purple-300">{fmt(enterpriseTier?.writePerMinute)} <span className="text-sm font-medium text-purple-400">write/min</span></div>
                                            <p className="text-xs text-purple-700 dark:text-purple-300 mt-2">For high-volume integrations.</p>
                                        </div>
                                    </div>
                                </div>
                            </section>
                        )}

                        {Array.from(grouped.entries()).map(([section, endpoints]) => (
                            <section key={section} className="w-full">
                                <h2 className="text-2xl font-black text-slate-900 dark:text-white mb-5 flex items-center gap-2">
                                    {sectionIcon(section)} {section}
                                </h2>
                                <div className="bg-white/90 dark:bg-slate-900/90 backdrop-blur-2xl border border-slate-200 dark:border-white/10 rounded-3xl p-6 md:p-8 shadow-2xl w-full overflow-hidden">
                                    {endpoints.map((endpoint) => (
                                        <EndpointCard
                                            key={`${endpoint.method}:${endpoint.path}`}
                                            endpoint={endpoint}
                                        />
                                    ))}
                                </div>
                            </section>
                        ))}

                        {data.schemas.length > 0 && (
                            <section className="w-full">
                                <div className="flex flex-col md:flex-row md:items-end md:justify-between gap-4 mb-5">
                                    <div>
                                        <h2 className="text-2xl font-black text-slate-900 dark:text-white flex items-center gap-2">
                                            <Braces className="w-6 h-6 text-slate-400" /> Schemas
                                        </h2>
                                        <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
                                            Browse the shared request and response models generated from the same live OpenAPI source.
                                        </p>
                                    </div>
                                    <label className="relative block w-full md:w-80">
                                        <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
                                        <input
                                            type="search"
                                            value={schemaQuery}
                                            onChange={(event) => setSchemaQuery(event.target.value)}
                                            placeholder="Search schema names or fields"
                                            className="w-full pl-10 pr-4 py-3 rounded-xl border border-slate-200 dark:border-white/10 bg-white/90 dark:bg-slate-900/90 text-sm text-slate-900 dark:text-white placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-modtale-accent/40"
                                        />
                                    </label>
                                </div>

                                <div className="bg-white/90 dark:bg-slate-900/90 backdrop-blur-2xl border border-slate-200 dark:border-white/10 rounded-3xl p-6 md:p-8 shadow-2xl w-full overflow-hidden">
                                    {filteredSchemas.length > 0 ? (
                                        <div className="space-y-4">
                                            {filteredSchemas.map((schema) => (
                                                <SchemaCard key={schema.name} schema={schema} />
                                            ))}
                                        </div>
                                    ) : (
                                        <div className="rounded-2xl border border-dashed border-slate-300 dark:border-white/10 p-8 text-center text-sm text-slate-500 dark:text-slate-400">
                                            No schemas matched <span className="font-mono text-slate-700 dark:text-slate-200">{schemaQuery}</span>.
                                        </div>
                                    )}
                                </div>
                            </section>
                        )}
                    </div>
                )}
            </div>
        </div>
    );
};
