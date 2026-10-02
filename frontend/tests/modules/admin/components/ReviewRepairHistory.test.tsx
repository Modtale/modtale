import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { beforeEach, afterEach, expect, it, vi } from 'vitest';
import { ReviewRepairHistory } from '@/modules/admin/components/ReviewRepairHistory';
import * as repair from '@/modules/admin/api/reviewRepair';
vi.mock('@/modules/admin/api/reviewRepair', () => ({ repairOperations: vi.fn(), recoverRepair: vi.fn(), closeExpiredRepair: vi.fn() }));
let root: Root; let container: HTMLDivElement;
const id = '11111111-1111-1111-1111-111111111111';
const page: repair.RepairOperationPage = { items: [{ id, recordedState: 'APPLIED' }], nextCursor: 'r1.' + id, order: 'OPERATION_ID' };
const recovered: repair.RecoveredRepair = { prepared: { id, sha256: 'a'.repeat(64), createdAt: 1, expiresAt: 2 }, target: { position: { projectIdType: 'OBJECT_ID', projectId: 'a'.repeat(24), versionIndex: 3 }, versionId: 'original' }, result: { state: 'UNKNOWN', afterSha256: null } };
async function click(text: string) { const button = [...container.querySelectorAll('button')].find(b => b.textContent === text)!; expect(button).toBeTruthy(); await act(async () => button.click()); }
beforeEach(async () => {
    vi.resetAllMocks(); vi.mocked(repair.repairOperations).mockResolvedValue(page); vi.mocked(repair.recoverRepair).mockResolvedValue(recovered);
    container = document.createElement('div'); document.body.append(container); root = createRoot(container); await act(async () => root.render(<ReviewRepairHistory />));
});
afterEach(async () => { await act(async () => root.unmount()); container.remove(); });
it('loads only on demand and does not treat recorded state as a verified outcome', async () => {
    expect(repair.repairOperations).not.toHaveBeenCalled(); await click('Load operation history'); expect(repair.recoverRepair).not.toHaveBeenCalled();
    expect(container.textContent).toContain('Recorded state: APPLIED'); expect(container.textContent).not.toContain('confirms local isolation');
    await click('Check receipt ' + id); expect(container.textContent).toContain('outcome remains unconfirmed'); expect(container.textContent).toContain('original'); expect(container.textContent).toContain('OBJECT_ID');
    expect(container.textContent).not.toContain('Confirm isolation'); expect(document.activeElement).toBe(container.querySelector('h3'));
});
it('replaces each page and refreshes from the beginning', async () => {
    await click('Load operation history'); vi.mocked(repair.repairOperations).mockResolvedValueOnce({ items: [], nextCursor: null, order: 'OPERATION_ID' }); await click('Next operation page');
    expect(repair.repairOperations).toHaveBeenLastCalledWith(page.nextCursor, expect.any(AbortSignal)); expect(container.textContent).not.toContain(id);
    await click('Refresh operation history'); expect(repair.repairOperations).toHaveBeenLastCalledWith(null, expect.any(AbortSignal));
});
it('clears stale receipts before lookup and reports failure without leaking server text', async () => {
    await click('Load operation history'); await click('Check receipt ' + id);
    vi.mocked(repair.recoverRepair).mockRejectedValueOnce(new Error('secret')); await click('Check receipt ' + id);
    expect(container.textContent).not.toContain('Original record SHA'); expect(container.textContent).not.toContain('secret'); expect(container.textContent).toContain('could not be verified');
});
it.each(['page', 'receipt'])('aborts and discards late %s results after access is removed', async kind => {
    let resolve!: (value: never) => void;
    if (kind === 'page') vi.mocked(repair.repairOperations).mockImplementationOnce(() => new Promise(r => { resolve = r; }));
    await click('Load operation history');
    if (kind === 'receipt') { vi.mocked(repair.recoverRepair).mockImplementationOnce(() => new Promise(r => { resolve = r; })); await click('Check receipt ' + id); }
    const signal = kind === 'page' ? vi.mocked(repair.repairOperations).mock.calls[0][1] : vi.mocked(repair.recoverRepair).mock.calls[0][1];
    await act(async () => root.render(<div>Closed</div>)); expect(signal.aborted).toBe(true); await act(async () => resolve((kind === 'page' ? page : recovered) as never)); expect(container.textContent).toBe('Closed');
});
it('blocks duplicate in-flight requests synchronously', async () => {
    vi.mocked(repair.repairOperations).mockImplementationOnce(() => new Promise(() => {}));
    const button = container.querySelector('button')!; await act(async () => { button.click(); button.click(); }); expect(repair.repairOperations).toHaveBeenCalledTimes(1);
});

it('requires explicit confirmation before closing the recovered attempt', async () => {
    await click('Load operation history'); await click('Check receipt ' + id); expect(repair.closeExpiredRepair).not.toHaveBeenCalled();
    await click('Close expired attempt'); expect(repair.closeExpiredRepair).not.toHaveBeenCalled(); await click('Keep attempt unchanged');
    expect(container.textContent).not.toContain('Confirm closing expired attempt'); await click('Close expired attempt');
    vi.mocked(repair.closeExpiredRepair).mockResolvedValueOnce({ state: 'NOT_APPLIED', afterSha256: null }); await click('Confirm closing expired attempt');
    expect(repair.closeExpiredRepair).toHaveBeenCalledExactlyOnceWith(recovered, expect.any(AbortSignal)); expect(container.textContent).toContain('confirms isolation was not applied');
    expect(container.textContent).not.toContain('Close expired attempt'); expect(container.textContent).toContain('original');
});
it('does not assume closure succeeded after a lost response or automatically retry it', async () => {
    await click('Load operation history'); await click('Check receipt ' + id); await click('Close expired attempt');
    vi.mocked(repair.closeExpiredRepair).mockRejectedValueOnce(new Error('private')); await click('Confirm closing expired attempt');
    expect(container.textContent).toContain('outcome remains unconfirmed'); expect(container.textContent).not.toContain('private'); expect(container.textContent).not.toContain('Close expired attempt');
    await click('Check receipt ' + id); expect(repair.closeExpiredRepair).toHaveBeenCalledTimes(1);
});
it('preserves a winning applied receipt returned by closure', async () => {
    await click('Load operation history'); await click('Check receipt ' + id); await click('Close expired attempt');
    vi.mocked(repair.closeExpiredRepair).mockResolvedValueOnce({ state: 'APPLIED', afterSha256: 'b'.repeat(64) }); await click('Confirm closing expired attempt');
    expect(container.textContent).toContain('confirms local isolation was applied'); expect(container.textContent).not.toContain('Close expired attempt');
});
