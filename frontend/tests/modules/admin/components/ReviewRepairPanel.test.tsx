import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { beforeEach, afterEach, expect, it, vi } from 'vitest';
import { ReviewRepairPanel } from '@/modules/admin/components/ReviewRepairPanel';
import * as repair from '@/modules/admin/api/reviewRepair';
vi.mock('@/modules/admin/api/reviewRepair', async original => ({ ...await original<object>(), repairAvailable: vi.fn(), inspectRepair: vi.fn(), prepareRepair: vi.fn(), executeRepair: vi.fn(), repairReceipt: vi.fn() }));
let root: Root; let container: HTMLDivElement;
const target: repair.RepairTarget = { position: { projectIdType: 'STRING', projectId: 'p', versionIndex: 0 }, versionId: 'v' };
const preview: repair.RepairPreview = { ...target, sha256: 'a'.repeat(64), eligible: true, effect: 'ISOLATE_LOCAL_REVIEW' };
const prepared: repair.PreparedRepair = { id: '11111111-1111-1111-1111-111111111111', sha256: preview.sha256, createdAt: 1000, expiresAt: 2000 };
const callbacks = { onReady: vi.fn(), onLock: vi.fn(), onDismiss: vi.fn() };
async function render(selected: repair.RepairTarget | null = null, subject = 'one') { await act(async () => root.render(<ReviewRepairPanel subject={subject} selected={selected} {...callbacks} />)); }
async function click(text: string) { const button = [...container.querySelectorAll('button')].find(b => b.textContent === text)!; expect(button).toBeTruthy(); await act(async () => button.click()); }
beforeEach(async () => {
    vi.resetAllMocks(); sessionStorage.clear(); vi.mocked(repair.repairAvailable).mockResolvedValue(true); vi.mocked(repair.inspectRepair).mockResolvedValue(preview); vi.mocked(repair.prepareRepair).mockResolvedValue(prepared);
    container = document.createElement('div'); document.body.append(container); root = createRoot(container); await render();
});
afterEach(async () => { await act(async () => root.unmount()); container.remove(); vi.restoreAllMocks(); vi.unstubAllGlobals(); sessionStorage.clear(); });
it('requires preview, preparation and confirmation, saving the reference before submission', async () => {
    await render(target); expect(repair.prepareRepair).not.toHaveBeenCalled(); await click('Prepare isolation'); expect(repair.executeRepair).not.toHaveBeenCalled();
    vi.mocked(repair.executeRepair).mockImplementationOnce(async () => { expect(JSON.parse(sessionStorage.getItem('review-repair:one')!).prepared).toEqual(prepared); return { state: 'APPLIED', afterSha256: 'b'.repeat(64) }; });
    await click('Confirm isolation'); expect(repair.executeRepair).toHaveBeenCalledTimes(1); expect(container.textContent).toContain('Local review isolated'); expect(sessionStorage.getItem('review-repair:one')).toBeNull();
    expect(document.activeElement).toBe(container.querySelector('h3'));
});
it('unknown submissions permit only receipt lookup and retain the operation reference', async () => {
    await render(target); await click('Prepare isolation'); vi.mocked(repair.executeRepair).mockRejectedValueOnce(new Error('private server data')); await click('Confirm isolation');
    expect(container.textContent).toContain('outcome is unconfirmed'); expect(container.textContent).not.toContain('private server data'); expect(sessionStorage.getItem('review-repair:one')).not.toBeNull();
    vi.mocked(repair.repairReceipt).mockResolvedValueOnce({ state: 'APPLIED', afterSha256: 'b'.repeat(64) }); await click('Check operation receipt'); expect(repair.executeRepair).toHaveBeenCalledTimes(1);
});
it('restores a pending operation without executing it again', async () => {
    sessionStorage.setItem('review-repair:two', JSON.stringify({ target, prepared })); await render(null, 'two');
    expect(container.textContent).toContain(prepared.id); expect(container.textContent).toContain('outcome is unconfirmed'); expect(repair.executeRepair).not.toHaveBeenCalled(); expect(repair.repairReceipt).not.toHaveBeenCalled();
});
it('does not submit when saving the operation reference fails', async () => {
    await render(target); await click('Prepare isolation'); vi.stubGlobal('sessionStorage', { getItem: () => null, setItem: () => { throw new Error(); } }); await click('Confirm isolation');
    expect(repair.executeRepair).not.toHaveBeenCalled(); expect(container.textContent).toContain('Isolation has not started');
});
it.each(['account', 'unmount'])('discards inspection results after %s', async mode => {
    let resolve!: (value: repair.RepairPreview) => void; vi.mocked(repair.inspectRepair).mockImplementationOnce(() => new Promise(r => { resolve = r; })); await render(target);
    const signal = vi.mocked(repair.inspectRepair).mock.calls[0][1];
    if (mode === 'account') await render(null, 'two'); else await act(async () => root.render(<div>Access removed</div>));
    expect(signal.aborted).toBe(true); await act(async () => resolve(preview)); expect(container.textContent).not.toContain(preview.sha256);
});
it('retains a submitted reference on unmount even when the response arrives later', async () => {
    await render(target); await click('Prepare isolation'); let resolve!: (value: repair.RepairResult) => void;
    vi.mocked(repair.executeRepair).mockImplementationOnce(() => new Promise(r => { resolve = r; })); await click('Confirm isolation');
    await act(async () => root.render(<div>Closed</div>)); await act(async () => resolve({ state: 'APPLIED', afterSha256: 'b'.repeat(64) }));
    expect(sessionStorage.getItem('review-repair:one')).not.toBeNull(); expect(container.textContent).toBe('Closed');
});
it('keeps ineligible records out of preparation', async () => {
    vi.mocked(repair.inspectRepair).mockResolvedValueOnce({ ...preview, eligible: false }); await render(target);
    expect(container.textContent).toContain('not eligible'); expect(repair.prepareRepair).not.toHaveBeenCalled(); expect(container.textContent).not.toContain('Prepare isolation');
});
