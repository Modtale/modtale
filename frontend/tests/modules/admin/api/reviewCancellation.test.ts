import { beforeEach, expect, it, vi } from 'vitest';
import { api } from '@/utils/api';
import { cancellationAvailable, previewCancellation, prepareCancellation, recoverCancellation, cancellationReceipt, executeCancellation, checkCancellation, cancellationCheckReceipt, validateCancellationPreview, validateCancellationIntent, validateCancellationReceipt, validateCheckReceipt, restoreCancellation, saveCancellation, cancellationStorageKey } from '@/modules/admin/api/reviewCancellation';
vi.mock('@/utils/api', () => ({ api: { get: vi.fn(), post: vi.fn() } }));
const id = '11111111-1111-1111-1111-111111111111', job = '22222222-2222-2222-2222-222222222222', checkId = '33333333-3333-3333-3333-333333333333';
const preview = { isolationId: id, projectIdType: 'STRING' as const, projectId: 'p', versionIndex: 0, versionId: 'v', requestId: id, jobId: job, beforeSha256: 'b'.repeat(64), targetSha256: 'a'.repeat(64), artifactSha256: 'c'.repeat(64) };
const original = { isolationId: id, targetSha256: preview.targetSha256, createdAt: 1000, expiresAt: 2000 };
const pending = { prepared: original, state: 'DISPATCHING', observation: null, observedAt: null };
const status = { jobId: job, state: 'CANCELLED', artifactRetained: true, createdAt: 500, expiresAt: 3000, workState: null };
const observation = { kind: 'REMOTE_STATUS', status, httpStatus: null };
const receipt = { prepared: original, state: 'OBSERVED', observation, observedAt: 5000 };
const check = { id: checkId, original };
const later = { ...check, state: 'OBSERVED', observation, receivedAt: 6000 };
const saved = { preview, original, checkId };
const signal = new AbortController().signal;
beforeEach(() => vi.resetAllMocks());
it.each([null, { ...preview, isolationId: job }, { ...preview, projectIdType: 'NUMBER' }, { ...preview, projectIdType: 'OBJECT_ID' }, { ...preview, versionIndex: -1 }, { ...preview, versionId: '\n' }, { ...preview, jobId: 'bad' }, { ...preview, targetSha256: 'A'.repeat(64) }, { ...preview, extra: true }])('rejects malformed or mismatched previews', value => expect(() => validateCancellationPreview(value, id)).toThrow());
it('preserves BSON type and accepts valid Unicode root identity', () => {
    expect(validateCancellationPreview({ ...preview, projectIdType: 'OBJECT_ID', projectId: 'a'.repeat(24) }, id).projectIdType).toBe('OBJECT_ID');
    expect(validateCancellationPreview({ ...preview, projectId: '🌍' }, id).projectId).toBe('🌍');
    expect(() => validateCancellationPreview({ ...preview, projectId: '\ud800' }, id)).toThrow();
});
it.each([null, { ...original, targetSha256: 'd'.repeat(64) }, { ...original, isolationId: job }, { ...original, createdAt: 0 }, { ...original, expiresAt: 1000 }, { ...original, expiresAt: 121001 }, { ...original, expiresAt: 1000.5 }, { ...original, token: 'private' }])('rejects altered or invalid intent', value => expect(() => validateCancellationIntent(value, preview)).toThrow());
it('accepts expired intent for local recovery without changing its window', () => expect(validateCancellationIntent(original, preview)).toEqual(original));
it.each([
    { ...receipt, prepared: { ...original, expiresAt: 2001 } }, { ...receipt, observation: { ...observation, status: { ...status, jobId: id } } },
    { ...receipt, state: 'UNKNOWN' }, { ...receipt, observation: null }, { ...receipt, observedAt: null }, { ...receipt, observedAt: 999 },
    { ...pending, observation }, { ...pending, observedAt: 5000 }, { ...receipt, extra: true },
    { ...receipt, observation: { kind: 'NOT_FOUND', status: null, httpStatus: 200 } }, { ...receipt, observation: { ...observation, status: { ...status, state: 'CLEAN' } } },
])('rejects contradictory or retargeted cancellation receipts', value => expect(() => validateCancellationReceipt(value, preview, original)).toThrow());
it.each([['NOT_FOUND', 404], ['CONTEXT_CONFLICT', 409], ['UNAVAILABLE', 401], ['UNAVAILABLE', 403], ['UNAVAILABLE', 429], ['UNKNOWN', 503], ['UNKNOWN', null]])('preserves distinct observation %s %s', (kind, httpStatus) => {
    const value = { ...receipt, state: kind === 'UNKNOWN' ? 'UNKNOWN' : 'OBSERVED', observation: { kind, status: null, httpStatus } };
    expect(validateCancellationReceipt(value, preview, original)).toEqual(value);
});
it('validates separate later receipts without replacing original evidence', () => {
    const before = JSON.stringify(receipt); expect(validateCheckReceipt(later, preview, check)).toEqual(later); expect(JSON.stringify(receipt)).toBe(before);
    expect(() => validateCheckReceipt({ ...later, id }, preview, check)).toThrow();
    expect(() => validateCheckReceipt({ ...later, original: { ...original, createdAt: 999 } }, preview, check)).toThrow();
    expect(() => validateCheckReceipt({ ...later, state: 'READING' }, preview, check)).toThrow();
    expect(validateCheckReceipt({ ...check, state: 'READING', observation: null, receivedAt: null }, preview, check).state).toBe('READING');
});
it('requires canonical bounded saved references and verifies storage write-back', () => {
    expect(restoreCancellation(JSON.stringify(saved))).toEqual(saved);
    for (const raw of ['{}', '{', 'x'.repeat(8193), JSON.stringify({ ...saved, checkId: 'bad' }), JSON.stringify({ ...saved, original: { ...original, targetSha256: 'd'.repeat(64) } })]) expect(() => restoreCancellation(raw)).toThrow();
    const storage = { setItem: vi.fn(), getItem: vi.fn().mockReturnValue(JSON.stringify(saved)) };
    saveCancellation(storage, 'actor', saved); expect(storage.setItem).toHaveBeenCalledWith(cancellationStorageKey('actor'), JSON.stringify(saved));
    storage.getItem.mockReturnValue(null); expect(() => saveCancellation(storage, 'actor', saved)).toThrow();
    storage.setItem.mockImplementation(() => { throw new Error('quota'); }); expect(() => saveCancellation(storage, 'actor', saved)).toThrow();
    expect(cancellationStorageKey('a/b')).not.toBe(cancellationStorageKey('a%2Fb')); expect(api.post).not.toHaveBeenCalled();
});
it('treats only capability 404 as disabled', async () => {
    vi.mocked(api.get).mockRejectedValueOnce({ response: { status: 404 } }); expect(await cancellationAvailable(signal)).toBe(false);
    vi.mocked(api.get).mockRejectedValueOnce({ response: { status: 403 } }); await expect(cancellationAvailable(signal)).rejects.toBeTruthy();
    vi.mocked(api.get).mockResolvedValueOnce({ data: { action: 'CANCEL_ORIGINAL_REVIEW' } }); expect(await cancellationAvailable(signal)).toBe(true);
});
it('binds preview and preparation and disables application replay on preparation', async () => {
    vi.mocked(api.get).mockResolvedValueOnce({ data: preview }); expect(await previewCancellation(id, signal)).toEqual(preview);
    vi.mocked(api.post).mockResolvedValueOnce({ data: original }); expect(await prepareCancellation(preview, signal)).toEqual(original);
    expect(api.post).toHaveBeenCalledWith('/admin/verification/cancellations/prepare', { isolationId: id }, { signal, skipCsrfRetry: true });
    vi.mocked(api.post).mockResolvedValueOnce({ data: { ...original, targetSha256: 'd'.repeat(64) } }); await expect(prepareCancellation(preview, signal)).rejects.toThrow();
});
it('keeps recovery and receipt lookup local and never prepares or executes', async () => {
    vi.mocked(api.get).mockResolvedValueOnce({ data: receipt }); expect(await recoverCancellation(preview, signal)).toEqual(receipt);
    vi.mocked(api.post).mockResolvedValueOnce({ data: pending }); expect(await cancellationReceipt(preview, original, signal)).toEqual(pending);
    expect(api.get).toHaveBeenCalledWith(`/admin/verification/cancellations/operations/${id}`, { signal });
    expect(api.post).toHaveBeenCalledTimes(1); expect(api.post).toHaveBeenCalledWith('/admin/verification/cancellations/receipt', original, { signal });
});
it('validates execution state and never follows uncertain transport with another action', async () => {
    vi.mocked(api.post).mockResolvedValueOnce({ data: { state: 'OBSERVED', receipt } }); expect(await executeCancellation(preview, original, signal)).toEqual({ state: 'OBSERVED', receipt });
    expect(api.post).toHaveBeenCalledWith('/admin/verification/cancellations/execute', original, { signal, skipCsrfRetry: true });
    vi.mocked(api.post).mockResolvedValueOnce({ data: { state: 'UNKNOWN', receipt } }); await expect(executeCancellation(preview, original, signal)).rejects.toThrow();
    vi.mocked(api.post).mockRejectedValueOnce(new Error('connection lost')); await expect(executeCancellation(preview, original, signal)).rejects.toThrow(); expect(api.post).toHaveBeenCalledTimes(3); expect(api.get).not.toHaveBeenCalled();
});
it('separates new checks from receipt lookup using the same saved check ID', async () => {
    vi.mocked(api.post).mockResolvedValue({ data: later }); expect(await checkCancellation(preview, check, signal)).toEqual(later); expect(await cancellationCheckReceipt(preview, check, signal)).toEqual(later);
    expect(api.post).toHaveBeenNthCalledWith(1, '/admin/verification/cancellations/checks', check, { signal, skipCsrfRetry: true });
    expect(api.post).toHaveBeenNthCalledWith(2, '/admin/verification/cancellations/checks/receipt', check, { signal, skipCsrfRetry: true });
});
