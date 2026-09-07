/** Resolved API fixtures for comparing real initial loading states in the visual harness.
 * Hold requests pending for loading captures; use these responses for loaded captures.
 */
export const reviewPoints = Array.from({ length: 65 }, (_, index) => ({
    date: new Date(Date.UTC(2026, 0, index + 1)).toISOString().slice(0, 10), count: 30 + (index * 17) % 80,
}));
export const platformAnalyticsReviewData = {
    totalDownloads: 1234, previousTotalDownloads: 1000, totalViews: 5678, previousTotalViews: 5000,
    totalNewUsers: 123, previousTotalNewUsers: 100, totalNewProjects: 123, previousTotalNewProjects: 100,
    downloadsChart: reviewPoints, apiDownloadsChart: reviewPoints, viewsChart: reviewPoints,
    newProjectsChart: reviewPoints, newUsersChart: reviewPoints, newOrgsChart: reviewPoints,
};
export const verificationReviewData = Array.from({ length: 3 }, (_, index) => ({
    id: `project-${index}`, title: ['Better Adventures', 'Builder’s Toolkit', 'Cozy Worlds'][index], author: 'Adventure Studio',
    description: 'Explore new biomes, discover hidden treasures, and make every journey through Orbis feel like a new adventure.',
    imageUrl: '/assets/favicon.svg', classification: 'PLUGIN' as const, status: 'PENDING' as const, updatedAt: '2026-01-01',
}));
export const reportReviewData = Array.from({ length: 3 }, (_, index) => ({
    id: `report-${index}`, reporterId: 'reporter', reporterUsername: 'AdventureFan',
    targetId: '00000000-0000-0000-0000-000000000000', targetType: 'PROJECT' as const,
    targetSummary: verificationReviewData[index].title, reason: 'INAPPROPRIATE_CONTENT',
    description: 'Report description with details of the content requiring review.', status: 'OPEN' as const,
    createdAt: '2026-01-01T12:00:00Z', resolvedBy: 'Administrator', resolutionNote: 'Review outcome and response to the reporter.',
}));
export const auditLogsReviewData = {
    content: Array.from({ length: 5 }, (_, index) => ({
        id: `00000000-0000-0000-0000-00000000000${index}`, adminUsername: 'Administrator', action: 'PUBLISH_PROJECT',
        targetId: '00000000-0000-0000-0000-000000000000', targetType: 'PROJECT',
        details: 'Administrative action and review details.', timestamp: '2026-01-01T12:00:00Z',
    })), totalPages: 1, totalElements: 5,
};
export const creatorAnalyticsReviewData = {
    periodDownloads: 1234, previousPeriodDownloads: 1000, totalDownloads: 12345,
    periodViews: 5678, previousPeriodViews: 5000, totalViews: 56789,
    projectMeta: Object.fromEntries(verificationReviewData.map(project => [project.id, { ...project, totalDownloads: 12345 }])),
    projectDownloads: Object.fromEntries(verificationReviewData.map(project => [project.id, reviewPoints])),
    projectViews: Object.fromEntries(verificationReviewData.map(project => [project.id, reviewPoints])),
};
export const projectAnalyticsReviewData = {
    totalDownloads: 1234, totalViews: 5678, views: reviewPoints,
    versionDownloads: Object.fromEntries(['v1', 'v2', 'v3'].map(id => [id, reviewPoints])),
};
export const projectDetailsReviewData = {
    ...verificationReviewData[0], downloadCount: 12345,
    versions: ['v1', 'v2', 'v3'].map((id, index) => ({ id, versionNumber: `1.${index}.0`, gameVersions: ['2026.01.01'], releaseDate: '2026-01-01', downloadCount: 1234 })),
};
export const statusReviewData = {
    overall: 'operational', timestamp: Date.UTC(2026, 0, 1, 12),
    services: [{ id: 'api', name: 'API Gateway', status: 'operational', latency: 123 }, { id: 'database', name: 'Database', status: 'operational', latency: 123 }, { id: 'storage', name: 'Storage', status: 'operational', latency: 123 }],
    history: Array.from({ length: 24 }, (_, index) => ({ time: Date.UTC(2026, 0, 1, index), api: 30 + index * 3, db: 20 + index * 2, storage: 50 + index * 4 })),
    activeIncidents: [], scheduledMaintenances: [], incidentHistory: [],
};

export const apiDocsReviewData = {
    info: { title: 'Modtale API', version: '1.0' }, servers: [{ url: 'https://api.modtale.net' }],
    paths: { '/api/v1/projects': { get: {
        summary: 'Browse projects', security: [], responses: {},
        'x-modtale-rate-limit-tiers': ['Public-IP', 'Standard-API', 'Enterprise-API'].map(name => ({ name, readPerMinute: 123, writePerMinute: 123 })),
    } } }, components: { schemas: { Response: { type: 'object', properties: {} } } },
};
export const statusIncidentsReviewData = ['INVESTIGATING', 'SCHEDULED', 'RESOLVED'].map((state, index) => ({
    id: `incident-${index}`, kind: state === 'SCHEDULED' ? 'MAINTENANCE' : 'INCIDENT', state, impact: 'DEGRADED',
    title: 'Service status update', affectedServices: ['api'], createdAt: '2026-01-01T12:00:00Z', updatedAt: '2026-01-01T12:00:00Z',
    updates: [{ id: `update-${index}`, state, impact: 'DEGRADED', message: 'Latest service impact and incident update.', createdAt: '2026-01-01T12:00:00Z' }],
}));
