import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { DisputeReconciliationReview } from '@/modules/finance/components/DisputeReconciliationReview';
import { financeClient } from '@/modules/finance/api/financeClient';

let host: HTMLDivElement; let root: Root;
const row = { id: 'case_fixture', disputeId: 'dp_fixture', chargeId: 'ch_fixture', creatorId: 'creator', currency: 'usd', testMode: true,
    providerStatus: 'lost', disputedCents: 1000, actualFeeCents: 1500, principalMovementCents: -1000, reviewStatus: 'POLICY_REVIEW_REQUIRED',
    evidenceReady: true, evidenceDigest: 'a'.repeat(64), balanceTransactions: [], updatedAt: '2026-09-30T09:00:00Z' };
beforeEach(() => { host = document.createElement('div'); document.body.append(host); root = createRoot(host); });
afterEach(async () => { await act(async () => root.unmount()); host.remove(); vi.restoreAllMocks(); });
const button = (label: string) => [...host.querySelectorAll('button')].find(button => button.textContent === label)!;
async function render(value = row, history = Promise.resolve([])) {
    void history.catch(() => {});
    vi.spyOn(financeClient, 'getDisputeReconciliationCases').mockResolvedValue([value]);
    vi.spyOn(financeClient, 'getDisputeDecisions').mockImplementation(() => history);
    await act(async () => root.render(<DisputeReconciliationReview />));
    await act(async () => [...host.querySelectorAll('button')].find(button => button.textContent?.includes('dp_fixture'))!.click());
}
async function fill(fee: string) {
    await act(async () => {
        const input = host.querySelector('input:not([type])')!;
        Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!.call(input, fee); input.dispatchEvent(new Event('input', { bubbles: true }));
        const reason = host.querySelector('textarea')!;
        Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value')!.set!.call(reason, 'Reviewed the approved policy and actual costs'); reason.dispatchEvent(new Event('input', { bubbles: true }));
        if (!(host.querySelector('input[type="checkbox"]') as HTMLInputElement).checked) (host.querySelector('input[type="checkbox"]') as HTMLInputElement).click();
    });
}
describe('explicit dispute review', () => {
    it('does not default fees to either party and requires policy review', async () => {
        const submit = vi.spyOn(financeClient, 'resolveDispute'); await render();
        expect((host.querySelector('input:not([type])') as HTMLInputElement).value).toBe('');
        expect(button('Record reviewed decision').disabled).toBe(true); expect(submit).not.toHaveBeenCalled();
        expect(host.textContent).toContain('No discretionary fee is assigned automatically');
    });
    it.each(['-1', '0.5', '1501', '1e2', ''])('rejects invalid or excessive fee %s', async fee => {
        const submit = vi.spyOn(financeClient, 'resolveDispute'); await render(); await fill(fee);
        expect(button('Record reviewed decision').disabled).toBe(true); expect(submit).not.toHaveBeenCalled();
    });
    it('sends explicit zero and the exact displayed digest once', async () => {
        const submit = vi.spyOn(financeClient, 'resolveDispute').mockResolvedValue({ id: 'resolution' }); await render(); await fill('0');
        await act(async () => button('Record reviewed decision').click());
        expect(submit).toHaveBeenCalledExactlyOnceWith({ caseId: row.id, expectedEvidenceDigest: row.evidenceDigest, creatorFeeCents: 0, reason: 'Reviewed the approved policy and actual costs' });
        expect(host.textContent).toContain('unrelated holds remain'); expect(financeClient.getDisputeReconciliationCases).toHaveBeenCalledTimes(2);
    });
    it('blocks duplicate clicks and does not retry rejected stale evidence automatically', async () => {
        let reject!: (reason: Error) => void;
        const submit = vi.spyOn(financeClient, 'resolveDispute').mockImplementation(() => new Promise((_resolve, fail) => { reject = fail; }));
        await render(); await fill('100'); const trigger = button('Record reviewed decision');
        await act(async () => { trigger.click(); trigger.click(); }); expect(submit).toHaveBeenCalledTimes(1);
        await act(async () => reject(new Error('stale evidence')));
        expect(host.querySelector('[role="alert"]')?.textContent).toContain('Refresh');
        expect(button('Record reviewed decision').disabled).toBe(true); expect((host.querySelector('input[type="checkbox"]') as HTMLInputElement).checked).toBe(false);
    });
    it('refreshes provider evidence and clears the old fee decision before another review', async () => {
        const refresh = vi.spyOn(financeClient, 'refreshDisputeCase').mockResolvedValue({ ...row, actualFeeCents: 2000, evidenceDigest: 'b'.repeat(64) });
        await render(); await fill('100'); await act(async () => button('Refresh provider evidence').click());
        expect(refresh).toHaveBeenCalledExactlyOnceWith(row.id); expect((host.querySelector('input:not([type])') as HTMLInputElement).value).toBe('');
        expect(button('Record reviewed decision').disabled).toBe(true); expect(financeClient.getDisputeDecisions).toHaveBeenCalledTimes(2);
    });
    it('keeps pending provider evidence locked', async () => {
        await render({ ...row, evidenceReady: false, reviewStatus: 'EVIDENCE_PENDING' });
        expect(host.querySelector('textarea')).toBeNull(); expect(host.textContent).toContain('hold cannot be cleared');
    });
    it('does not permit a decision while prior audit history is unavailable', async () => {
        await render(row, Promise.reject(new Error('history unavailable')));
        expect(host.querySelector('textarea')).toBeNull(); expect(host.textContent).toContain('Prior decisions must finish loading');
    });
    it('cancels and reopens with blank fees and unchecked consent', async () => {
        const submit = vi.spyOn(financeClient, 'resolveDispute'); await render(); await fill('100');
        await act(async () => button('Cancel dispute review').click());
        await act(async () => [...host.querySelectorAll('button')].find(button => button.textContent?.includes('dp_fixture'))!.click());
        expect((host.querySelector('input:not([type])') as HTMLInputElement).value).toBe(''); expect((host.querySelector('input[type="checkbox"]') as HTMLInputElement).checked).toBe(false);
        expect(submit).not.toHaveBeenCalled();
    });
});
