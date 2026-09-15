import { api } from '@/utils/api';

export type CancellationPreview = { isolationId: string; projectIdType: 'STRING' | 'OBJECT_ID'; projectId: string; versionIndex: number; versionId: string; requestId: string; jobId: string; beforeSha256: string; targetSha256: string; artifactSha256: string };
export type CancellationIntent = { isolationId: string; targetSha256: string; createdAt: number; expiresAt: number };
export type RemoteStatus = { jobId: string; state: string; artifactRetained: boolean; createdAt: number; expiresAt: number; workState: string | null };
export type CancellationObservation = { kind: 'REMOTE_STATUS' | 'NOT_FOUND' | 'CONTEXT_CONFLICT' | 'UNAVAILABLE' | 'UNKNOWN'; status: RemoteStatus | null; httpStatus: number | null };
export type CancellationReceipt = { prepared: CancellationIntent; state: 'PREPARED' | 'EXECUTING' | 'DISPATCHING' | 'OBSERVED' | 'UNKNOWN'; observation: CancellationObservation | null; observedAt: number | null };
export type CancellationCheck = { id: string; original: CancellationIntent };
export type CheckReceipt = { id: string; original: CancellationIntent; state: 'RESERVED' | 'READING' | 'OBSERVED' | 'UNKNOWN'; observation: CancellationObservation | null; receivedAt: number | null };
export type CancellationExecution = { state: CancellationReceipt['state'] | 'BUSY' | 'SHUTDOWN'; receipt: CancellationReceipt | null };
export type SavedCancellation = { preview: CancellationPreview; original: CancellationIntent; checkId: string | null };
const base = '/admin/verification/cancellations';
const noReplay = (signal: AbortSignal) => ({ signal, skipCsrfRetry: true });
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const digest = /^[0-9a-f]{64}$/;
const invalid = () => new Error('Invalid cancellation response.');
function object(value: unknown, keys: string[]): Record<string, unknown> {
    if (!value || typeof value !== 'object' || Array.isArray(value) || Object.keys(value).length !== keys.length || keys.some(k => !Object.hasOwn(value, k))) throw invalid();
    return value as Record<string, unknown>;
}
function text(value: unknown, pattern: RegExp): string { if (typeof value !== 'string' || !pattern.test(value)) throw invalid(); return value; }
function positive(value: unknown): number { if (!Number.isSafeInteger(value) || (value as number) <= 0) throw invalid(); return value as number; }
function same(a: CancellationIntent, b: CancellationIntent) { if (a.isolationId !== b.isolationId || a.targetSha256 !== b.targetSha256 || a.createdAt !== b.createdAt || a.expiresAt !== b.expiresAt) throw invalid(); }
export function validateCancellationPreview(value: unknown, isolationId: string): CancellationPreview {
    const p = object(value, ['isolationId', 'projectIdType', 'projectId', 'versionIndex', 'versionId', 'requestId', 'jobId', 'beforeSha256', 'targetSha256', 'artifactSha256']);
    if (text(p.isolationId, uuid) !== text(isolationId, uuid) || !['STRING', 'OBJECT_ID'].includes(p.projectIdType as string)
        || typeof p.projectId !== 'string' || !p.projectId || [...p.projectId].length > 128 || /[\uD800-\uDFFF]/u.test(p.projectId)
        || (p.projectIdType === 'OBJECT_ID' && !/^[0-9a-f]{24}$/.test(p.projectId))
        || !Number.isSafeInteger(p.versionIndex) || (p.versionIndex as number) < 0 || (p.versionIndex as number) > 16777216
        || typeof p.versionId !== 'string' || !p.versionId.trim() || p.versionId.length > 128 || /[\x00-\x1f\x7f-\x9f]/.test(p.versionId)) throw invalid();
    text(p.requestId, uuid); text(p.jobId, uuid); text(p.beforeSha256, digest); text(p.targetSha256, digest); text(p.artifactSha256, digest);
    return { ...p } as CancellationPreview;
}
export function validateCancellationIntent(value: unknown, preview: CancellationPreview): CancellationIntent {
    const p = object(value, ['isolationId', 'targetSha256', 'createdAt', 'expiresAt']);
    if (text(p.isolationId, uuid) !== preview.isolationId || text(p.targetSha256, digest) !== preview.targetSha256) throw invalid();
    const createdAt = positive(p.createdAt), expiresAt = positive(p.expiresAt);
    if (expiresAt <= createdAt || expiresAt - createdAt > 120000) throw invalid();
    return { isolationId: p.isolationId as string, targetSha256: p.targetSha256 as string, createdAt, expiresAt };
}
function observation(value: unknown, jobId: string): CancellationObservation {
    const o = object(value, ['kind', 'status', 'httpStatus']);
    if (!['REMOTE_STATUS', 'NOT_FOUND', 'CONTEXT_CONFLICT', 'UNAVAILABLE', 'UNKNOWN'].includes(o.kind as string)) throw invalid();
    if (o.kind === 'REMOTE_STATUS') {
        const s = object(o.status, ['jobId', 'state', 'artifactRetained', 'createdAt', 'expiresAt', 'workState']);
        if (o.httpStatus !== null || text(s.jobId, uuid) !== jobId || !['QUEUED', 'RUNNING', 'COMPLETED', 'CANCELLED', 'EXPIRED', 'HELD', 'UPLOADING', 'AWAITING_UPLOAD'].includes(s.state as string)
            || typeof s.artifactRetained !== 'boolean' || positive(s.expiresAt) <= positive(s.createdAt)
            || (s.workState !== null && (typeof s.workState !== 'string' || s.workState.length > 128))) throw invalid();
        return { kind: 'REMOTE_STATUS', status: { ...s } as RemoteStatus, httpStatus: null };
    }
    if (o.status !== null || (o.httpStatus !== null && (!Number.isInteger(o.httpStatus) || (o.httpStatus as number) < 100 || (o.httpStatus as number) > 599))
        || (o.kind === 'NOT_FOUND' && o.httpStatus !== 404) || (o.kind === 'CONTEXT_CONFLICT' && o.httpStatus !== 409)
        || (o.kind === 'UNAVAILABLE' && ![401, 403, 429].includes(o.httpStatus as number))) throw invalid();
    return { ...o } as CancellationObservation;
}
function outcome(state: unknown, value: unknown, time: unknown, preview: CancellationPreview, pending: string[]) {
    if (pending.includes(state as string)) { if (value !== null || time !== null) throw invalid(); return null; }
    if (state !== 'OBSERVED' && state !== 'UNKNOWN') throw invalid();
    positive(time); const result = observation(value, preview.jobId);
    if ((state === 'UNKNOWN') !== (result.kind === 'UNKNOWN')) throw invalid(); return result;
}
export function validateCancellationReceipt(value: unknown, preview: CancellationPreview, expected?: CancellationIntent): CancellationReceipt {
    const r = object(value, ['prepared', 'state', 'observation', 'observedAt']);
    const prepared = validateCancellationIntent(r.prepared, preview); if (expected) same(prepared, expected);
    const observed = outcome(r.state, r.observation, r.observedAt, preview, ['PREPARED', 'EXECUTING', 'DISPATCHING']);
    if (r.observedAt !== null && (r.observedAt as number) < prepared.createdAt) throw invalid();
    return { prepared, state: r.state as CancellationReceipt['state'], observation: observed, observedAt: r.observedAt as number | null };
}
export function validateCheckReceipt(value: unknown, preview: CancellationPreview, expected: CancellationCheck): CheckReceipt {
    const r = object(value, ['id', 'original', 'state', 'observation', 'receivedAt']);
    if (text(r.id, uuid) !== text(expected.id, uuid)) throw invalid();
    const original = validateCancellationIntent(r.original, preview); same(original, expected.original);
    const observed = outcome(r.state, r.observation, r.receivedAt, preview, ['RESERVED', 'READING']);
    return { id: r.id as string, original, state: r.state as CheckReceipt['state'], observation: observed, receivedAt: r.receivedAt as number | null };
}
export function restoreCancellation(raw: string): SavedCancellation {
    if (raw.length > 8192) throw invalid(); const s = object(JSON.parse(raw), ['preview', 'original', 'checkId']);
    const id = (s.preview as CancellationPreview)?.isolationId;
    const preview = validateCancellationPreview(s.preview, id), original = validateCancellationIntent(s.original, preview);
    if (s.checkId !== null) text(s.checkId, uuid);
    return { preview, original, checkId: s.checkId as string | null };
}
export function saveCancellation(storage: Pick<Storage, 'setItem' | 'getItem'>, subject: string, value: SavedCancellation): void {
    if (!subject.trim()) throw invalid(); const raw = JSON.stringify(restoreCancellation(JSON.stringify(value)));
    const key = cancellationStorageKey(subject); storage.setItem(key, raw); if (storage.getItem(key) !== raw) throw new Error('Cancellation reference could not be saved.');
}
export function cancellationStorageKey(subject: string) { return `review-cancellation:${encodeURIComponent(subject)}`; }
export async function cancellationAvailable(signal: AbortSignal): Promise<boolean> {
    try { const { data } = await api.get(`${base}/capabilities`, { signal }); if (object(data, ['action']).action !== 'CANCEL_ORIGINAL_REVIEW') throw invalid(); return true; }
    catch (e) { if ((e as { response?: { status?: number } })?.response?.status === 404) return false; throw e; }
}
export async function previewCancellation(id: string, signal: AbortSignal) { text(id, uuid); return validateCancellationPreview((await api.get(`${base}/targets/${id}`, { signal })).data, id); }
export async function prepareCancellation(preview: CancellationPreview, signal: AbortSignal) {
    const p = validateCancellationPreview(preview, preview.isolationId);
    return validateCancellationIntent((await api.post(`${base}/prepare`, { isolationId: p.isolationId }, noReplay(signal))).data, p);
}
export async function recoverCancellation(preview: CancellationPreview, signal: AbortSignal) {
    const p = validateCancellationPreview(preview, preview.isolationId);
    return validateCancellationReceipt((await api.get(`${base}/operations/${p.isolationId}`, { signal })).data, p);
}
export async function cancellationReceipt(preview: CancellationPreview, original: CancellationIntent, signal: AbortSignal) {
    const p = validateCancellationPreview(preview, preview.isolationId), intent = validateCancellationIntent(original, p);
    return validateCancellationReceipt((await api.post(`${base}/receipt`, intent, { signal })).data, p, intent);
}
export async function executeCancellation(preview: CancellationPreview, original: CancellationIntent, signal: AbortSignal): Promise<CancellationExecution> {
    const p = validateCancellationPreview(preview, preview.isolationId), intent = validateCancellationIntent(original, p);
    const r = object((await api.post(`${base}/execute`, intent, noReplay(signal))).data, ['state', 'receipt']);
    if (r.receipt === null) { if (!['BUSY', 'SHUTDOWN', 'UNKNOWN'].includes(r.state as string)) throw invalid(); return { state: r.state as CancellationExecution['state'], receipt: null }; }
    const receipt = validateCancellationReceipt(r.receipt, p, intent); if (receipt.state !== r.state) throw invalid();
    return { state: receipt.state, receipt };
}
async function checkRequest(path: string, preview: CancellationPreview, check: CancellationCheck, signal: AbortSignal) {
    const p = validateCancellationPreview(preview, preview.isolationId), request = { id: text(check.id, uuid), original: validateCancellationIntent(check.original, p) };
    return validateCheckReceipt((await api.post(`${base}/${path}`, request, noReplay(signal))).data, p, request);
}
export const checkCancellation = (preview: CancellationPreview, check: CancellationCheck, signal: AbortSignal) => checkRequest('checks', preview, check, signal);
export const cancellationCheckReceipt = (preview: CancellationPreview, check: CancellationCheck, signal: AbortSignal) => checkRequest('checks/receipt', preview, check, signal);
