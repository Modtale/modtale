import { beforeEach, describe, expect, it, vi } from 'vitest';
import { openSupportCheckout, verifySupportReturn } from '@/modules/finance/api/supportCheckout';
import { financeClient } from '@/modules/finance/api/financeClient';
import { createDemoDonationConfig } from '@/modules/finance/demo/financeDemoConfiguration';

const deferred = <T>() => { let resolve!: (value: T) => void; const promise = new Promise<T>(done => { resolve = done; }); return { promise, resolve }; };
let popup: Window;
let replace: ReturnType<typeof vi.fn>;
let close: ReturnType<typeof vi.fn>;
const input = () => ({ projectId: 'project', amountCents: 500, recurring: false, guestCheckout: true, expectedPlatformCutBps: 1000, isCurrent: () => true, openWindow: () => popup });
beforeEach(() => { replace = vi.fn(); close = vi.fn(); popup = { opener: {}, location: { replace }, close } as unknown as Window; });

describe('optional checkout sequencing', () => {
    it('sends exact displayed terms and opens only the verified Stripe destination', async () => {
        const create = vi.spyOn(financeClient, 'createDonationCheckout').mockResolvedValue({ checkoutUrl: 'https://checkout.stripe.com/c/pay/test' });
        expect(await openSupportCheckout(input())).toEqual({ status: 'OPENED' });
        expect(create).toHaveBeenCalledWith('project', 500, false, true, 1000);
        expect(popup.opener).toBeNull(); expect(replace).toHaveBeenCalledWith('https://checkout.stripe.com/c/pay/test');
    });
    it('does not create a session when the popup is blocked or throws', async () => {
        const create = vi.spyOn(financeClient, 'createDonationCheckout');
        expect(await openSupportCheckout({ ...input(), openWindow: () => null })).toEqual({ status: 'UNAVAILABLE' });
        expect(await openSupportCheckout({ ...input(), openWindow: () => { throw new Error('blocked'); } })).toEqual({ status: 'UNAVAILABLE' });
        expect(create).not.toHaveBeenCalled();
    });
    it('ignores an old project response and closes its empty popup', async () => {
        const response = deferred<any>(); let current = true;
        vi.spyOn(financeClient, 'createDonationCheckout').mockReturnValue(response.promise);
        const outcome = openSupportCheckout({ ...input(), isCurrent: () => current });
        current = false; response.resolve({ checkoutUrl: 'https://checkout.stripe.com/c/pay/old' });
        expect(await outcome).toEqual({ status: 'STALE' }); expect(close).toHaveBeenCalled(); expect(replace).not.toHaveBeenCalled();
    });
    it.each(['https://evil.example/pay', 'javascript:alert(1)', 'http://checkout.stripe.com/pay', 'https://name:password@checkout.stripe.com/pay', 'https://checkout.stripe.com:8443/pay'])('rejects an untrusted checkout destination: %s', async checkoutUrl => {
        vi.spyOn(financeClient, 'createDonationCheckout').mockResolvedValue({ checkoutUrl });
        expect(await openSupportCheckout(input())).toEqual({ status: 'UNAVAILABLE' }); expect(replace).not.toHaveBeenCalled(); expect(close).toHaveBeenCalled();
    });
    it('returns refreshed changed terms without automatically retrying payment', async () => {
        const create = vi.spyOn(financeClient, 'createDonationCheckout').mockRejectedValue({ response: { status: 409, data: { code: 'SUPPORT_TERMS_CHANGED' } } });
        const configuration = { ...createDemoDonationConfig(), projectId: 'project', donationPlatformCutBps: 1234, donationPlatformCutPercent: 12.34 };
        vi.spyOn(financeClient, 'getDonationConfig').mockResolvedValue(configuration);
        expect(await openSupportCheckout(input())).toEqual({ status: 'TERMS_CHANGED', configuration });
        expect(create).toHaveBeenCalledTimes(1); expect(replace).not.toHaveBeenCalled(); expect(close).toHaveBeenCalled();
    });
    it('fails closed on missing terms rather than guessing a ten-percent share', async () => {
        const create = vi.spyOn(financeClient, 'createDonationCheckout');
        expect(await openSupportCheckout({ ...input(), expectedPlatformCutBps: Number.NaN })).toEqual({ status: 'UNAVAILABLE' });
        expect(create).not.toHaveBeenCalled();
    });
});

describe('payment return confirmation', () => {
    it('does not turn a success URL or cancellation URL into proof of payment', async () => {
        vi.spyOn(financeClient, 'confirmDonationIntent').mockResolvedValue({ ok: false, status: 'PENDING' });
        expect((await verifySupportReturn('intent', false, () => true))?.title).toBe('Support Pending');
        expect((await verifySupportReturn('intent', true, () => true))?.title).toBe('Checkout Closed');
    });
    it('honors server-confirmed payment even when another tab returned through cancel', async () => {
        vi.spyOn(financeClient, 'confirmDonationIntent').mockResolvedValue({ ok: true, status: 'COMPLETED' });
        expect((await verifySupportReturn('intent', true, () => true))?.title).toBe('Support Confirmed');
    });
    it('does not display or navigate using an abandoned route response', async () => {
        const response = deferred<any>(); let current = true;
        vi.spyOn(financeClient, 'confirmDonationIntent').mockReturnValue(response.promise);
        const outcome = verifySupportReturn('intent', false, () => current);
        current = false; response.resolve({ ok: true }); expect(await outcome).toBeNull();
    });
});
