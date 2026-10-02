import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { ProviderCostReview } from '@/modules/finance/components/ProviderCostReview';
import { financeClient } from '@/modules/finance/api/financeClient';
let host: HTMLDivElement; let root: Root;
beforeEach(() => { host = document.createElement('div'); document.body.append(host); root = createRoot(host); });
afterEach(async () => { await act(async () => root.unmount()); host.remove(); vi.restoreAllMocks(); });
const submit = () => [...host.querySelectorAll('button')].find(button => button.textContent === 'Verify and import fee evidence')!;
async function render(rows: unknown[] = []) { vi.spyOn(financeClient, 'getProviderCosts').mockResolvedValue(rows); await act(async () => root.render(<ProviderCostReview />)); }
async function fill(mode = 'test') {
    await act(async () => {
        for (const [index, value] of ['txn_fixture', 'acct_fixture'].entries()) {
            const input = host.querySelectorAll('input')[index]; Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!.call(input, value); input.dispatchEvent(new Event('input', { bubbles: true }));
        }
        const select = host.querySelector('select')!; select.value = mode; select.dispatchEvent(new Event('change', { bubbles: true }));
        const reason = host.querySelector('textarea')!; Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value')!.set!.call(reason, 'Review canonical service fee'); reason.dispatchEvent(new Event('input', { bubbles: true }));
    });
}
it('requires an explicit environment and exposes no amount or credential entry', async () => {
    const request = vi.spyOn(financeClient, 'importStripeCost'); await render(); await fill('');
    expect(submit().disabled).toBe(true); expect(request).not.toHaveBeenCalled(); expect(host.querySelectorAll('input')).toHaveLength(2);
    expect(host.textContent).toContain('Aggregate costs remain unallocated');
});
it('submits exact provider scope and reason without creator allocation', async () => {
    const request = vi.spyOn(financeClient, 'importStripeCost').mockResolvedValue({}); await render(); await fill();
    await act(async () => submit().click());
    expect(request).toHaveBeenCalledExactlyOnceWith({ balanceTransactionId: 'txn_fixture', expectedAccountId: 'acct_fixture', expectedTestMode: true, reason: 'Review canonical service fee' });
    expect(host.textContent).toContain('without allocating costs or changing creator balances'); expect(submit().disabled).toBe(true);
});
it('prevents duplicate requests and preserves the draft after a rejected provider scope', async () => {
    let reject!: (error: Error) => void;
    const request = vi.spyOn(financeClient, 'importStripeCost').mockImplementation(() => new Promise((_ok, fail) => { reject = fail; }));
    await render(); await fill(); const button = submit(); await act(async () => { button.click(); button.click(); }); expect(request).toHaveBeenCalledTimes(1);
    await act(async () => reject(new Error('mismatch'))); expect(host.querySelector('[role="alert"]')).not.toBeNull(); expect(host.querySelector('input')!.value).toBe('txn_fixture');
});
it('preserves non-USD provider minor units without guessing currency scaling', async () => {
    await render([{ id: 'cost', providerAccountId: 'acct_fixture', testMode: true, balanceTransactionId: 'txn_fixture', currency: 'jpy', costMinorUnits: -200, allocationStatus: 'UNALLOCATED', recordedBy: 'reviewer', reason: 'Provider credit', recordedAt: '2026-09-30T00:00:00Z' }]);
    expect(host.textContent).toContain('-200 JPY provider minor units'); expect(host.textContent).not.toContain('2.00'); expect(host.textContent).toContain('Credits appear as negative costs');
});
