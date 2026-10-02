import { api } from '@/utils/api';
import { validDiagnosticCursor } from './reviewDiagnostics';
export const originLabels = {
    MISSING: 'Original service not recorded',
    INVALID: 'Original service record malformed',
    RECORDED: 'Service identity recorded',
    UNREADABLE_BINDING: 'Review reference unreadable',
} as const;
export interface OriginItem {
    position: { projectIdType: 'STRING' | 'OBJECT_ID'; projectId: string; versionIndex: number };
    versionId: string | null; ambiguousVersion: boolean; requestId: string | null; jobId: string | null;
    originState: keyof typeof originLabels;
}
export interface OriginPage { items: OriginItem[]; nextCursor: string | null; examinedSlots: number; scope: 'RETAINED_REVIEW_ORIGINS'; }
export function validOriginCursor(value: unknown): value is string {
    return typeof value === 'string' && value.startsWith('o1.') && validDiagnosticCursor(`d1.${value.slice(3)}`);
}
const uuid = (value: unknown) => value === null || typeof value === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(value);
export function validateOriginPage(value: unknown): OriginPage {
    const page = value as OriginPage;
    if (!page || page.scope !== 'RETAINED_REVIEW_ORIGINS' || !Array.isArray(page.items) || page.items.length > 25
        || !Number.isInteger(page.examinedSlots) || page.examinedSlots < page.items.length || page.examinedSlots > 25
        || (page.nextCursor !== null && !validOriginCursor(page.nextCursor))) throw new Error('Invalid origin inventory response.');
    const seen = new Set<string>();
    for (const item of page.items) {
        const p = item?.position;
        if (!p || !['STRING', 'OBJECT_ID'].includes(p.projectIdType) || typeof p.projectId !== 'string' || !p.projectId || [...p.projectId].length > 128
            || (p.projectIdType === 'OBJECT_ID' && !/^[0-9a-f]{24}$/.test(p.projectId)) || !Number.isInteger(p.versionIndex) || p.versionIndex < 0 || p.versionIndex > 16777216
            || (item.versionId !== null && (typeof item.versionId !== 'string' || !item.versionId.trim() || item.versionId.length > 128 || /[\x00-\x1f\x7f-\x9f]/.test(item.versionId)))
            || typeof item.ambiguousVersion !== 'boolean' || (item.versionId === null && !item.ambiguousVersion) || !uuid(item.requestId) || !uuid(item.jobId)
            || typeof item.originState !== 'string' || !Object.hasOwn(originLabels, item.originState)) throw new Error('Invalid origin inventory record.');
        const key = JSON.stringify([p.projectIdType, p.projectId, p.versionIndex]);
        if (seen.has(key)) throw new Error('Duplicate origin inventory position.');
        seen.add(key);
    }
    return page;
}
export async function getOriginPage(cursor: string | null, signal: AbortSignal): Promise<OriginPage> {
    if (cursor !== null && !validOriginCursor(cursor)) throw new Error('Invalid origin inventory cursor.');
    const response = await api.get('/admin/verification/origins/page', { params: { cursor: cursor ?? undefined, limit: 25 }, signal });
    const page = validateOriginPage(response.data);
    if (cursor !== null && cursor === page.nextCursor) throw new Error('Origin inventory did not advance. Refresh from start.');
    return page;
}
