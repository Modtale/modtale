import { api } from '@/utils/api';
import type { DiagnosticItem } from './reviewDiagnostics';
export type RepairTarget = { position: DiagnosticItem['position']; versionId: string };
export type RepairPreview = RepairTarget & { sha256: string; eligible: boolean; effect: 'ISOLATE_LOCAL_REVIEW' };
export type PreparedRepair = { id: string; sha256: string; createdAt: number; expiresAt: number };
export type RepairResult = { state: 'APPLIED' | 'NOT_APPLIED' | 'INELIGIBLE' | 'UNKNOWN'; afterSha256: string | null };
export type PendingRepair = { target: RepairTarget; prepared: PreparedRepair };
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const digest = /^[0-9a-f]{64}$/;
const invalid = () => new Error('Invalid repair response.');
export function validateTarget(value: unknown): RepairTarget {
    const t = value as RepairTarget; const p = t?.position;
    if (!p || !['STRING', 'OBJECT_ID'].includes(p.projectIdType) || typeof p.projectId !== 'string' || !p.projectId || p.projectId.length > 128
        || (p.projectIdType === 'OBJECT_ID' && !/^[0-9a-f]{24}$/.test(p.projectId)) || !Number.isInteger(p.versionIndex) || p.versionIndex < 0 || p.versionIndex > 16777216
        || typeof t.versionId !== 'string' || !t.versionId.trim() || t.versionId.length > 128 || /[\x00-\x1f\x7f-\x9f]/.test(t.versionId)) throw invalid();
    return { position: { projectIdType: p.projectIdType, projectId: p.projectId, versionIndex: p.versionIndex }, versionId: t.versionId };
}
export function validatePrepared(value: unknown): PreparedRepair {
    const p = value as PreparedRepair;
    if (!p || typeof p.id !== 'string' || !uuid.test(p.id) || typeof p.sha256 !== 'string' || !digest.test(p.sha256)
        || !Number.isSafeInteger(p.createdAt) || p.createdAt <= 0 || !Number.isSafeInteger(p.expiresAt) || p.expiresAt <= p.createdAt || p.expiresAt - p.createdAt > 900000) throw invalid();
    return { id: p.id, sha256: p.sha256, createdAt: p.createdAt, expiresAt: p.expiresAt };
}
export function validateResult(value: unknown): RepairResult {
    const r = value as RepairResult;
    if (!r || !['APPLIED', 'NOT_APPLIED', 'INELIGIBLE', 'UNKNOWN'].includes(r.state)
        || (r.state === 'APPLIED' ? typeof r.afterSha256 !== 'string' || !digest.test(r.afterSha256) : r.afterSha256 !== null)) throw invalid();
    return { state: r.state, afterSha256: r.afterSha256 };
}
export function restorePending(raw: string): PendingRepair {
    if (raw.length > 4096) throw invalid();
    const parsed = JSON.parse(raw);
    return { target: validateTarget(parsed?.target), prepared: validatePrepared(parsed?.prepared) };
}
export async function repairAvailable(signal: AbortSignal): Promise<boolean> {
    try {
        const response = await api.get('/admin/verification/repairs/capabilities', { signal });
        if (response.data?.action !== 'ISOLATE_LOCAL_REVIEW') throw invalid();
        return true;
    } catch (error) {
        if ((error as { response?: { status?: number } })?.response?.status === 404) return false;
        throw error;
    }
}
export async function inspectRepair(target: RepairTarget, signal: AbortSignal): Promise<RepairPreview> {
    const expected = validateTarget(target);
    const { data } = await api.post('/admin/verification/repairs/inspect', expected, { signal });
    const returned = validateTarget(data);
    if (JSON.stringify(returned) !== JSON.stringify(expected) || typeof data.sha256 !== 'string' || !digest.test(data.sha256)
        || typeof data.eligible !== 'boolean' || data.effect !== 'ISOLATE_LOCAL_REVIEW') throw invalid();
    return { ...returned, sha256: data.sha256, eligible: data.eligible, effect: data.effect };
}
export async function prepareRepair(preview: RepairPreview, id: string, signal: AbortSignal): Promise<PreparedRepair> {
    if (!uuid.test(id) || !preview.eligible || !digest.test(preview.sha256)) throw invalid();
    const { data } = await api.post('/admin/verification/repairs/prepare', { ...validateTarget(preview), id, expectedSha256: preview.sha256 }, { signal });
    const prepared = validatePrepared(data);
    if (prepared.id !== id || prepared.sha256 !== preview.sha256) throw invalid();
    return prepared;
}
export async function executeRepair(prepared: PreparedRepair, signal: AbortSignal): Promise<RepairResult> {
    const config = { signal, skipCsrfRetry: true };
    return validateResult((await api.post('/admin/verification/repairs/execute', validatePrepared(prepared), config)).data);
}
export async function repairReceipt(prepared: PreparedRepair, target: RepairTarget, signal: AbortSignal): Promise<RepairResult> {
    const expected = validateTarget(target);
    const { data } = await api.post('/admin/verification/repairs/receipt', validatePrepared(prepared), { signal });
    if (JSON.stringify(validateTarget(data)) !== JSON.stringify(expected) || data.beforeSha256 !== prepared.sha256) throw invalid();
    return validateResult(data);
}

export type RepairOperation = { id: string; recordedState: 'RESERVED' | 'EXECUTING' | 'UNKNOWN' | 'APPLIED' | 'NOT_APPLIED' };
export type RepairOperationPage = { items: RepairOperation[]; nextCursor: string | null; order: 'OPERATION_ID' };
export type RecoveredRepair = PendingRepair & { result: RepairResult };
export async function repairOperations(cursor: string | null, signal: AbortSignal): Promise<RepairOperationPage> {
    if (cursor !== null && !/^r1\.[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(cursor)) throw invalid();
    const { data } = await api.get('/admin/verification/repairs/operations', { params: { ...(cursor === null ? {} : { cursor }), limit: 25 }, signal });
    if (!data || data.order !== 'OPERATION_ID' || !Array.isArray(data.items) || data.items.length > 25) throw invalid();
    let previous = cursor?.slice(3) ?? '';
    const items: RepairOperation[] = data.items.map((item: RepairOperation) => {
        if (!item || typeof item.id !== 'string' || !uuid.test(item.id) || item.id <= previous || !['RESERVED', 'EXECUTING', 'UNKNOWN', 'APPLIED', 'NOT_APPLIED'].includes(item.recordedState)) throw invalid();
        previous = item.id;
        return { id: item.id, recordedState: item.recordedState };
    });
    if (data.nextCursor !== null && (items.length !== 25 || data.nextCursor !== `r1.${previous}`)) throw invalid();
    return { items, nextCursor: data.nextCursor, order: 'OPERATION_ID' };
}
export async function recoverRepair(id: string, signal: AbortSignal): Promise<RecoveredRepair> {
    if (!uuid.test(id)) throw invalid();
    const { data } = await api.get(`/admin/verification/repairs/operations/${id}`, { signal });
    const prepared = validatePrepared(data?.prepared); const target = validateTarget(data?.receipt); const result = validateResult(data?.receipt);
    if (prepared.id !== id || data.receipt.beforeSha256 !== prepared.sha256 || result.state === 'INELIGIBLE') throw invalid();
    return { prepared, target, result };
}

export async function closeExpiredRepair(pending: PendingRepair, signal: AbortSignal): Promise<RepairResult> {
    const prepared = validatePrepared(pending.prepared); const target = validateTarget(pending.target);
    const config = { signal, skipCsrfRetry: true };
    const { data } = await api.post('/admin/verification/repairs/close-expired', prepared, config);
    if (JSON.stringify(validateTarget(data)) !== JSON.stringify(target) || data.beforeSha256 !== prepared.sha256) throw invalid();
    const result = validateResult(data); if (result.state === 'INELIGIBLE') throw invalid();
    return result;
}
