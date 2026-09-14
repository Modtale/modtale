import { expect, it, vi, beforeEach } from 'vitest';
import { api } from '@/utils/api';
import { executeRepair, inspectRepair, prepareRepair, repairAvailable, restorePending, validatePrepared, validateResult, validateTarget } from '@/modules/admin/api/reviewRepair';
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
