import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { beforeEach, afterEach, expect, it, vi } from 'vitest';
import { ReviewCancellationPanel } from '@/modules/admin/components/ReviewCancellationPanel';
import * as client from '@/modules/admin/api/reviewCancellation';
vi.mock('@/modules/admin/api/reviewCancellation', async importOriginal => ({ ...await importOriginal<typeof client>(), cancellationAvailable: vi.fn(), previewCancellation: vi.fn(), prepareCancellation: vi.fn(), executeCancellation: vi.fn(), recoverCancellation: vi.fn(), checkCancellation: vi.fn(), cancellationCheckReceipt: vi.fn() }));
const id = '11111111-1111-1111-1111-111111111111';
const preview: client.CancellationPreview = { isolationId: id, projectIdType: 'STRING', projectId: 'p', versionIndex: 0, versionId: 'original', requestId: id, jobId: '22222222-2222-2222-2222-222222222222', beforeSha256: 'b'.repeat(64), targetSha256: 'a'.repeat(64), artifactSha256: 'c'.repeat(64) };
const original = { isolationId: id, targetSha256: preview.targetSha256, createdAt: 1000, expiresAt: 2000 };
const receipt: client.CancellationReceipt = { prepared: original, state: 'UNKNOWN', observation: { kind: 'UNKNOWN', status: null, httpStatus: 503 }, observedAt: 3000 };
const owner = JSON.stringify(['actor', id]), key = client.cancellationStorageKey(owner);
let root: Root, container: HTMLDivElement;
async function render(subject = 'actor') { await act(async () => root.render(<ReviewCancellationPanel subject={subject} isolationId={id} />)); }
async function click(text: string) { const button = [...container.querySelectorAll('button')].find(b => b.textContent === text); expect(button).toBeTruthy(); await act(async () => button!.click()); }
async function prepared() { await click('Inspect original job'); await click('Prepare original job cancellation'); }
beforeEach(async () => {
    vi.restoreAllMocks(); vi.clearAllMocks(); sessionStorage.clear();
    vi.mocked(client.cancellationAvailable).mockResolvedValue(true); vi.mocked(client.previewCancellation).mockResolvedValue(preview);
    vi.mocked(client.prepareCancellation).mockResolvedValue(original); vi.mocked(client.executeCancellation).mockResolvedValue({ state: 'UNKNOWN', receipt }); vi.mocked(client.recoverCancellation).mockResolvedValue(receipt);
    container = document.createElement('div'); document.body.append(container); root = createRoot(container); await render();
});
afterEach(async () => { await act(async () => root.unmount()); container.remove(); vi.restoreAllMocks(); vi.unstubAllGlobals(); });
it('requires preview preparation and explicit confirmation, saving before execute', async () => {
    expect(client.previewCancellation).not.toHaveBeenCalled(); expect(client.executeCancellation).not.toHaveBeenCalled(); await prepared();
    expect(container.textContent).toContain(preview.jobId); expect(client.executeCancellation).not.toHaveBeenCalled();
    expect(client.restoreCancellation(sessionStorage.getItem(key)!).original).toEqual(original);
    vi.mocked(client.executeCancellation).mockImplementationOnce(async () => { expect(client.restoreCancellation(sessionStorage.getItem(key)!).original).toEqual(original); return { state: 'UNKNOWN', receipt }; });
    await click('Confirm original job cancellation'); expect(client.executeCancellation).toHaveBeenCalledTimes(1); expect(container.textContent).toContain('Original cancellation outcome'); expect(container.textContent).not.toContain('Confirm original job cancellation'); expect(document.activeElement).toBe(container.querySelector('h4'));
});
it('keeping the job unchanged does not dispatch', async () => { await prepared(); await click('Keep job unchanged'); expect(client.executeCancellation).not.toHaveBeenCalled(); });
it('storage failure prevents confirmation and dispatch', async () => {
    vi.stubGlobal('sessionStorage', { getItem: () => null, setItem: () => { throw new Error('quota'); } }); await prepared();
    expect(container.textContent).not.toContain('Confirm original job cancellation'); expect(client.executeCancellation).not.toHaveBeenCalled(); expect(container.textContent).toContain('could not be verified');
});
it('reopening restores a reference but never automatically executes or observes', async () => {
    await prepared(); await act(async () => root.render(<div />)); await render();
    expect(container.textContent).toContain('saved reference'); expect(container.textContent).not.toContain('Confirm original job cancellation'); expect(client.executeCancellation).not.toHaveBeenCalled(); expect(client.checkCancellation).not.toHaveBeenCalled();
    await click('Recover original cancellation receipt'); expect(client.recoverCancellation).toHaveBeenCalledTimes(1); expect(container.textContent).toContain('Original cancellation outcome');
});
it('lost execution response enables only explicit recovery, not automatic replay', async () => {
    await prepared(); vi.mocked(client.executeCancellation).mockRejectedValueOnce(new Error('private')); await click('Confirm original job cancellation');
    expect(container.textContent).not.toContain('private'); expect(container.textContent).not.toContain('Confirm original job cancellation'); expect(client.recoverCancellation).not.toHaveBeenCalled();
    await click('Recover original cancellation receipt'); expect(client.executeCancellation).toHaveBeenCalledTimes(1);
});
it('recovered PREPARED requires a new explicit review then confirmation', async () => {
    vi.mocked(client.recoverCancellation).mockResolvedValueOnce({ ...receipt, state: 'PREPARED', observation: null, observedAt: null });
    await click('Recover original cancellation receipt'); expect(client.executeCancellation).not.toHaveBeenCalled();
    await click('Review cancellation confirmation'); expect(client.executeCancellation).not.toHaveBeenCalled(); await click('Confirm original job cancellation'); expect(client.executeCancellation).toHaveBeenCalledTimes(1);
});
it('new observation saves its ID and later receipt lookup never repeats the check', async () => {
    await click('Recover original cancellation receipt');
    vi.mocked(client.checkCancellation).mockImplementationOnce(async (_p, check) => {
        expect(client.restoreCancellation(sessionStorage.getItem(key)!).checkId).toBe(check.id); throw new Error('lost');
    });
    await click('Request a new status observation'); const saved = client.restoreCancellation(sessionStorage.getItem(key)!);
    vi.mocked(client.cancellationCheckReceipt).mockResolvedValueOnce({ id: saved.checkId!, original, state: 'OBSERVED', observation: { kind: 'REMOTE_STATUS', status: { jobId: preview.jobId, state: 'CANCELLED', artifactRetained: true, createdAt: 1, expiresAt: 5000, workState: null }, httpStatus: null }, receivedAt: 4000 });
    await click('Recover observation receipt'); expect(client.checkCancellation).toHaveBeenCalledTimes(1);
    expect(container.textContent).toContain('Original cancellation outcome'); expect(container.textContent).toContain('HTTP 503'); expect(container.textContent).toContain('Remote job state: CANCELLED');
    await click('Finish this observation lookup'); expect(client.restoreCancellation(sessionStorage.getItem(key)!).checkId).toBeNull(); expect(container.textContent).toContain('Remote job state: CANCELLED');
});
it('unresolved observation blocks another new check', async () => {
    await click('Recover original cancellation receipt'); vi.mocked(client.checkCancellation).mockImplementationOnce(async (_p, check) => ({ ...check, state: 'READING', observation: null, receivedAt: null }));
    await click('Request a new status observation'); expect(container.textContent).not.toContain('Request a new status observation'); expect(container.textContent).not.toContain('Finish this observation lookup');
});
it('corrupt storage permits original read recovery but no remote action', async () => {
    await act(async () => root.render(<div />)); sessionStorage.setItem(key, '{'); await render();
    expect(container.textContent).not.toContain('Inspect original job'); await click('Recover original cancellation receipt');
    expect(container.textContent).not.toContain('Request a new status observation'); expect(client.executeCancellation).not.toHaveBeenCalled(); expect(client.checkCancellation).not.toHaveBeenCalled();
});
it('account changes abort requests and discard late results', async () => {
    let resolve!: (value: client.CancellationPreview) => void;
    vi.mocked(client.previewCancellation).mockImplementationOnce(() => new Promise(r => { resolve = r; }));
    await click('Inspect original job'); const signal = vi.mocked(client.previewCancellation).mock.calls[0][1];
    await render('other'); expect(signal.aborted).toBe(true); await act(async () => resolve(preview)); expect(container.textContent).not.toContain(preview.jobId); expect(sessionStorage.getItem(client.cancellationStorageKey(JSON.stringify(['other', id])))).toBeNull();
});
it('double clicks cannot start concurrent preparation', async () => {
    await click('Inspect original job'); vi.mocked(client.prepareCancellation).mockImplementationOnce(() => new Promise(() => {}));
    const b = [...container.querySelectorAll('button')].find(b => b.textContent === 'Prepare original job cancellation')!;
    await act(async () => { b.click(); b.click(); }); expect(client.prepareCancellation).toHaveBeenCalledTimes(1);
});
it('disabled capability hides the panel and makes no target requests', async () => {
    await act(async () => root.render(<div />)); vi.mocked(client.cancellationAvailable).mockResolvedValueOnce(false); await render(); expect(container.textContent).toBe(''); expect(client.previewCancellation).not.toHaveBeenCalled();
});
