import { api } from '@/utils/api';
import type { AdminVerificationQueueItem } from '@/types';
export type QueueFilter = 'ALL' | 'SECURITY' | 'OPERATIONS';
export interface QueuePage { filter?: QueueFilter; items: AdminVerificationQueueItem[]; nextCursor: string | null; unavailableItems: number; order: 'PROJECT_VERSION'; }
const cursorValid = (value: unknown): value is string => typeof value === 'string' && value.length <= 800 && /^(1|2\.(SECURITY|OPERATIONS))\.[os]\.(0|[1-9][0-9]{0,7})\.[A-Za-z0-9_-]{1,684}$/.test(value);
const cursorMatchesFilter = (cursor: string, filter: QueueFilter) => filter === 'ALL' ? cursor.startsWith('1.') : cursor.startsWith(`2.${filter}.`);
export function validateQueuePage(value: unknown, filter: QueueFilter = 'ALL'): QueuePage {
    const page = value as QueuePage;
    if (!page || (page.filter ?? 'ALL') !== filter || !Array.isArray(page.items) || page.items.length > 25 || page.order !== 'PROJECT_VERSION'
        || (page.nextCursor !== null && (!cursorValid(page.nextCursor) || !cursorMatchesFilter(page.nextCursor, filter))) || !Number.isInteger(page.unavailableItems)
        || page.unavailableItems < 0 || page.unavailableItems + page.items.length > 25) throw new Error('Invalid moderation queue response.');
    const keys = new Set<string>();
    for (const row of page.items) {
        if (!row || typeof row.id !== 'string' || !row.id || (row.pendingVersion != null
            && (typeof row.pendingVersion.id !== 'string' || !row.pendingVersion.id || row.pendingVersion.reviewStatus !== 'PENDING'))) throw new Error('Invalid moderation queue entry.');
        for (const [field, max] of [['title',256],['description',1024],['author',128],['imageUrl',2048],['classification',32],['status',32],['updatedAt',64]] as const) {
            const text = row[field]; if (text != null && (typeof text !== 'string' || [...text].length > max)) throw new Error('Invalid moderation queue display data.');
        }
        if (row.pendingVersion) {
            const version = row.pendingVersion;
            if (version.versionNumber != null && (typeof version.versionNumber !== 'string' || [...version.versionNumber].length > 128)
                || version.changelog != null && (typeof version.changelog !== 'string' || [...version.changelog].length > 1024)) throw new Error('Invalid moderation queue version.');
            if (version.scan != null) {
                for (const field of ['status','verdict','scanState'] as const) if (version.scan[field] != null && typeof version.scan[field] !== 'string') throw new Error('Invalid moderation queue status.');
                for (const field of ['riskScore','knownIssueCount','newIssueCount','escalatedIssueCount'] as const) if (!Number.isInteger(version.scan[field])) throw new Error('Invalid moderation queue findings.');
            }
        }
        const key = JSON.stringify([row.id, row.pendingVersion?.id]);
        if (keys.has(key)) throw new Error('Duplicate moderation queue entry.');
        keys.add(key);
    }
    return page;
}
export async function getModerationQueuePage(cursor: string | null, signal: AbortSignal, filter: QueueFilter = 'ALL'): Promise<QueuePage> {
    if (cursor !== null && (!cursorValid(cursor) || !cursorMatchesFilter(cursor, filter))) throw new Error('Invalid moderation queue cursor.');
    const response = await api.get('/admin/verification/queue/page', { params: { cursor: cursor ?? undefined, limit: 25, filter }, signal });
    const page = validateQueuePage(response.data, filter);
    if (cursor !== null && page.nextCursor === cursor) throw new Error('The moderation queue did not advance. Refresh the queue.');
    return page;
}
