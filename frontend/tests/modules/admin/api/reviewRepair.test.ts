import { expect, it, vi, beforeEach } from 'vitest';
import { api } from '@/utils/api';
import { executeRepair, repairReceipt, inspectRepair, prepareRepair, repairAvailable, restorePending, validatePrepared, validateResult, validateTarget } from '@/modules/admin/api/reviewRepair';
vi.mock('@/utils/api', () => ({ api: { get: vi.fn(), post: vi.fn() } }));
const target = { position: { projectIdType: 'STRING' as const, projectId: 'p', versionIndex: 0 }, versionId: 'v' };
const preview = { ...target, sha256: 'a'.repeat(64), eligible: true, effect: 'ISOLATE_LOCAL_REVIEW' as const };
const prepared = { id: '11111111-1111-1111-1111-111111111111', sha256: preview.sha256, createdAt: 1000, expiresAt: 2000 };
beforeEach(() => vi.clearAllMocks());
it('checks server availability and treats only 404 as disabled', async () => {
    vi.mocked(api.get).mockRejectedValueOnce({ response: { status: 404 } }); expect(await repairAvailable(new AbortController().signal)).toBe(false);
    vi.mocked(api.get).mockRejectedValueOnce({ response: { status: 403 } }); await expect(repairAvailable(new AbortController().signal)).rejects.toBeTruthy();
    vi.mocked(api.get).mockResolvedValueOnce({ data: { action: 'OTHER' } }); await expect(repairAvailable(new AbortController().signal)).rejects.toThrow();
});
it('binds inspection and preparation responses to the exact request', async () => {
    const signal = new AbortController().signal;
    vi.mocked(api.post).mockResolvedValueOnce({ data: preview }); expect(await inspectRepair(target, signal)).toEqual(preview);
    vi.mocked(api.post).mockResolvedValueOnce({ data: { ...preview, position: { ...target.position, projectIdType: 'OBJECT_ID', projectId: 'a'.repeat(24) } } });
    await expect(inspectRepair(target, signal)).rejects.toThrow();
    vi.mocked(api.post).mockResolvedValueOnce({ data: { ...prepared, sha256: 'b'.repeat(64) } }); await expect(prepareRepair(preview, prepared.id, signal)).rejects.toThrow();
});
it.each([null, { ...prepared, id: 'bad' }, { ...prepared, sha256: 'A'.repeat(64) }, { ...prepared, expiresAt: 1000 }, { ...prepared, expiresAt: 901001 }, { ...prepared, createdAt: 1.5 }])('rejects malformed prepared identity', value => expect(() => validatePrepared(value)).toThrow());
it.each([null, { state: 'APPROVED', afterSha256: null }, { state: 'APPLIED', afterSha256: null }, { state: 'UNKNOWN', afterSha256: 'a'.repeat(64) }])('rejects ambiguous outcome payloads', value => expect(() => validateResult(value)).toThrow());
it('validates saved references and rejects unbounded or ambiguous positions', () => {
    expect(restorePending(JSON.stringify({ target, prepared }))).toEqual({ target, prepared });
    expect(() => restorePending(' '.repeat(4097))).toThrow(); expect(() => validateTarget({ ...target, versionId: null })).toThrow();
    expect(() => validateTarget({ ...target, position: { ...target.position, versionIndex: -1 } })).toThrow();
});

it('disables shared-client replay for isolation submission', async () => {
    vi.mocked(api.post).mockResolvedValueOnce({ data: { state: 'UNKNOWN', afterSha256: null } });
    const signal = new AbortController().signal; await executeRepair(prepared, signal);
    expect(api.post).toHaveBeenLastCalledWith('/admin/verification/repairs/execute', prepared, { signal, skipCsrfRetry: true });
});

it.each(['project', 'type', 'position', 'version', 'digest'])('rejects a receipt bound to another %s', async field => {
    const expected = field === 'type' ? { ...target, position: { ...target.position, projectId: 'a'.repeat(24) } } : target;
    const value = { ...expected, position: { ...expected.position }, beforeSha256: prepared.sha256, state: 'APPLIED', afterSha256: 'b'.repeat(64) };
    if (field === 'project') value.position.projectId = 'other';
    if (field === 'type') { value.position.projectIdType = 'OBJECT_ID' as 'STRING'; value.position.projectId = 'a'.repeat(24); }
    if (field === 'position') value.position.versionIndex = 1;
    if (field === 'version') value.versionId = 'other';
    if (field === 'digest') value.beforeSha256 = 'c'.repeat(64);
    vi.mocked(api.post).mockResolvedValueOnce({ data: value }); await expect(repairReceipt(prepared, expected, new AbortController().signal)).rejects.toThrow();
});
it('accepts an authenticated receipt for the saved original identity', async () => {
    vi.mocked(api.post).mockResolvedValueOnce({ data: { ...target, beforeSha256: prepared.sha256, state: 'APPLIED', afterSha256: 'b'.repeat(64) } });
    expect(await repairReceipt(prepared, target, new AbortController().signal)).toEqual({ state: 'APPLIED', afterSha256: 'b'.repeat(64) });
});

it('validates bounded ordered history and sends actor-free pagination', async () => {
    const { repairOperations } = await import('@/modules/admin/api/reviewRepair');
    const signal = new AbortController().signal;
    const data = { items: [{ id: prepared.id, recordedState: 'UNKNOWN' }], nextCursor: null, order: 'OPERATION_ID' };
    vi.mocked(api.get).mockResolvedValueOnce({ data }); expect(await repairOperations(null, signal)).toEqual(data);
    expect(api.get).toHaveBeenLastCalledWith('/admin/verification/repairs/operations', { params: { limit: 25 }, signal });
});
it.each(['oversize', 'duplicate', 'backwards', 'state', 'cursor', 'order'])('rejects invalid history %s', async kind => {
    const { repairOperations } = await import('@/modules/admin/api/reviewRepair');
    const item = { id: prepared.id, recordedState: 'UNKNOWN' };
    const data = { items: [item], nextCursor: null as string | null, order: 'OPERATION_ID' };
    if (kind === 'oversize') data.items = Array(26).fill(item);
    if (kind === 'duplicate') data.items = [item, item];
    if (kind === 'state') item.recordedState = 'APPROVED';
    if (kind === 'cursor') data.nextCursor = `r1.${prepared.id}`;
    if (kind === 'order') data.order = 'DATE';
    vi.mocked(api.get).mockResolvedValueOnce({ data });
    await expect(repairOperations(kind === 'backwards' ? `r1.${prepared.id}` : null, new AbortController().signal)).rejects.toThrow();
});
it('recovers original intent by ID without browser state or mutation', async () => {
    const { recoverRepair } = await import('@/modules/admin/api/reviewRepair');
    const result = { state: 'APPLIED' as const, afterSha256: 'b'.repeat(64) };
    vi.mocked(api.get).mockResolvedValueOnce({ data: { prepared, receipt: { ...target, beforeSha256: prepared.sha256, ...result } } });
    expect(await recoverRepair(prepared.id, new AbortController().signal)).toEqual({ prepared, target, result });
    expect(api.post).not.toHaveBeenCalled();
});
it.each(['id', 'digest', 'outcome'])('rejects inconsistent recovered %s', async kind => {
    const { recoverRepair } = await import('@/modules/admin/api/reviewRepair');
    const data = { prepared: { ...prepared }, receipt: { ...target, beforeSha256: prepared.sha256, state: 'UNKNOWN', afterSha256: null } };
    if (kind === 'id') data.prepared.id = '22222222-2222-2222-2222-222222222222';
    if (kind === 'digest') data.receipt.beforeSha256 = 'c'.repeat(64);
    if (kind === 'outcome') data.receipt.state = 'INELIGIBLE';
    vi.mocked(api.get).mockResolvedValueOnce({ data }); await expect(recoverRepair(prepared.id, new AbortController().signal)).rejects.toThrow();
});

it('closes only the explicit original intent and disables mutation replay', async () => {
    const { closeExpiredRepair } = await import('@/modules/admin/api/reviewRepair'); const signal = new AbortController().signal;
    vi.mocked(api.post).mockResolvedValueOnce({ data: { ...target, beforeSha256: prepared.sha256, state: 'NOT_APPLIED', afterSha256: null } });
    expect(await closeExpiredRepair({ target, prepared }, signal)).toEqual({ state: 'NOT_APPLIED', afterSha256: null });
    expect(api.post).toHaveBeenLastCalledWith('/admin/verification/repairs/close-expired', prepared, { signal, skipCsrfRetry: true });
});
it.each(['identity', 'digest', 'outcome'])('rejects an inconsistent closure %s', async kind => {
    const { closeExpiredRepair } = await import('@/modules/admin/api/reviewRepair');
    const value = { ...target, beforeSha256: prepared.sha256, state: 'NOT_APPLIED', afterSha256: null };
    if (kind === 'identity') value.versionId = 'different';
    if (kind === 'digest') value.beforeSha256 = 'b'.repeat(64);
    if (kind === 'outcome') value.state = 'INELIGIBLE';
    vi.mocked(api.post).mockResolvedValueOnce({ data: value }); await expect(closeExpiredRepair({ target, prepared }, new AbortController().signal)).rejects.toThrow();
});
