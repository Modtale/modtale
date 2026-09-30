import type { AdStage } from '@/modules/finance/components/AdSettlementReview';

export function createAdSettlementFixture(): AdStage {
    return {
        id: 'demo-stage', provider: 'demo-network', providerAccount: 'demo-publisher', reportId: 'demo-2026-08', depositId: 'demo-deposit-08', currency: 'usd', revision: 1, status: 'AWAITING_REVIEW',
        snapshots: [{ revision: 1, from: '2026-08-01', through: '2026-08-31', reportedCollectedCents: 120000, reportSha256: 'a'.repeat(64), activityDigest: 'b'.repeat(64), creatorShareBps: 7500,
            provisionalCreatorPoolCents: 90000, provisionalPlatformCents: 30000, unallocatedCreatorCents: 0, activityRule: 'provisional-pageviews-plus-launcher-downloads-v1', capturedAt: '2026-09-29T15:00:00Z',
            projects: [
                { projectId: 'demo-frontier', creatorId: 'demo-creator-a', title: 'Frontier Adventures', pageviews: 4200, launcherDownloads: 1800, frontendDownloads: 750, apiDownloads: 1000, provisionalPoints: 6000, provisionalCreatorCents: 60000, eligibilityNote: 'Provisional: fraud filtering and historical opt-in still require validation.' },
                { projectId: 'demo-builders', creatorId: 'demo-creator-b', title: 'Builder’s Companion', pageviews: 2400, launcherDownloads: 600, frontendDownloads: 400, apiDownloads: 650, provisionalPoints: 3000, provisionalCreatorCents: 30000, eligibilityNote: 'Provisional: fraud filtering and historical opt-in still require validation.' },
                { projectId: 'demo-legacy', creatorId: 'demo-creator-c', title: 'Legacy Tools', pageviews: 300, launcherDownloads: 0, frontendDownloads: 150, apiDownloads: 200, provisionalPoints: 0, provisionalCreatorCents: 0, eligibilityNote: 'Excluded: historical owner identity needs review.' }
            ] }],
        audit: [{ operationId: 'demo-create', action: 'STAGED', revision: 1, actorId: 'demo-finance-reviewer', reason: 'Synthetic evidence only. No report or deposit has been verified.', createdAt: '2026-09-29T15:00:00Z' }]
    };
}
