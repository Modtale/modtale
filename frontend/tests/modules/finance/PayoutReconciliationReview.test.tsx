import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { PayoutReconciliationReview } from '@/modules/finance/components/PayoutReconciliationReview';
import { financeClient } from '@/modules/finance/api/financeClient';

let host: HTMLDivElement; let root: Root;
const row = { id: 'test:creator:usd:request', creatorId: 'creator', providerAccountId: 'acct_fixture', testMode: true, currency: 'usd', amountCents: 1000,
    status: 'REQUIRES_REVIEW', transferGroup: 'group_fixture', createdAt: '2026-09-30T09:00:00Z', recipients: [{ userId: 'creator', accountId: 'acct_recipient', amountCents: 1000, authorizedAt: '2026-09-30T09:00:01Z' }] };
beforeEach(() => { host = document.createElement('div'); document.body.append(host); root = createRoot(host); });
afterEach(async () => { await act(async () => root.unmount()); host.remove(); vi.restoreAllMocks(); });
const button = (text: string) => [...host.querySelectorAll('button')].find(value => value.textContent === text)!;
async function render(rows = [row]) {
    vi.spyOn(financeClient, 'getPayoutReconciliationQueue').mockResolvedValue(rows);
    await act(async () => root.render(<PayoutReconciliationReview />));
}
async function fill() {
    await act(async () => button('Review recipient 1').click());
    const input = host.querySelector('input[type="text"], input:not([type])')! as HTMLInputElement;
    const reason = host.querySelector('textarea')!;
    await act(async () => {
        Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!.call(input, 'tr_verified'); input.dispatchEvent(new Event('input', { bubbles: true }));
        Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value')!.set!.call(reason, 'Matched original request and transfer group'); reason.dispatchEvent(new Event('input', { bubbles: true }));
        (host.querySelector('input[type="checkbox"]') as HTMLInputElement).click();
    });
}
describe('existing-transfer review', () => {
    it('loads the queue without starting a transfer or credit release', async () => {
        const confirm = vi.spyOn(financeClient, 'confirmExistingPayoutTransfer'); await render();
        expect(host.textContent).toContain('$10.00'); expect(host.textContent).toContain('This review never sends another transfer');
        expect(confirm).not.toHaveBeenCalled(); expect(host.textContent).toContain('acct_fixture');
    });
    it('requires evidence and an explicit review before submitting, then refreshes', async () => {
        const confirm = vi.spyOn(financeClient, 'confirmExistingPayoutTransfer').mockResolvedValue({ ok: true }); await render();
        await act(async () => button('Review recipient 1').click()); expect(button('Verify and record existing transfer').disabled).toBe(true);
        await fill(); await act(async () => button('Verify and record existing transfer').click());
        expect(confirm).toHaveBeenCalledExactlyOnceWith({ requestId: row.id, recipientIndex: 0, transferId: 'tr_verified', reason: 'Matched original request and transfer group' });
        expect(host.textContent).toContain('not the recipient’s bank'); expect(financeClient.getPayoutReconciliationQueue).toHaveBeenCalledTimes(2);
    });
    it('keeps rejected evidence visible for review and never retries automatically', async () => {
        const confirm = vi.spyOn(financeClient, 'confirmExistingPayoutTransfer').mockRejectedValue(new Error('mismatch')); await render(); await fill();
        await act(async () => button('Verify and record existing transfer').click());
        expect(host.querySelector('[role="alert"]')?.textContent).toContain('Transfer could not be verified'); expect(confirm).toHaveBeenCalledTimes(1);
        expect(host.querySelector('textarea')?.value).toContain('Matched original');
    });
    it('blocks repeated clicks while an outcome is pending', async () => {
        let resolve!: (result: unknown) => void;
        const confirm = vi.spyOn(financeClient, 'confirmExistingPayoutTransfer').mockImplementation(() => new Promise(done => { resolve = done; }));
        await render(); await fill(); const submit = button('Verify and record existing transfer');
        await act(async () => { submit.click(); submit.click(); }); expect(confirm).toHaveBeenCalledTimes(1);
        expect(button('Verifying…').disabled).toBe(true); await act(async () => resolve({ ok: true }));
    });
    it('does not let a missing original authorization adopt unrelated money', async () => {
        await render([{ ...row, recipients: [{ ...row.recipients[0], authorizedAt: undefined as unknown as string }] }]);
        expect(button('Review recipient 1').disabled).toBe(true); expect(host.textContent).toContain('No saved outbound authorization');
    });
    it('cancels a review without posting evidence', async () => {
        const confirm = vi.spyOn(financeClient, 'confirmExistingPayoutTransfer'); await render(); await fill();
        await act(async () => button('Cancel review').click()); expect(host.querySelector('textarea')).toBeNull(); expect(confirm).not.toHaveBeenCalled();
    });
});
