import { financeClient } from './financeClient';
import { hasSupportTerms, type DonationConfig } from './financeTypes';

type Outcome = { status: 'OPENED' | 'SIMULATED' | 'UNAVAILABLE' | 'STALE' } | { status: 'TERMS_CHANGED'; configuration: DonationConfig };

/** Popup creation remains synchronous with the click; every asynchronous result is tied to its originating project. */
export async function openSupportCheckout(input: {
    projectId: string; amountCents: number; recurring: boolean; guestCheckout: boolean; expectedPlatformCutBps: number;
    isCurrent: () => boolean;
    openWindow?: () => Window | null;
}): Promise<Outcome> {
    if (!input.isCurrent()) return { status: 'STALE' };
    if (!hasSupportTerms({ donationPlatformCutBps: input.expectedPlatformCutBps })) return { status: 'UNAVAILABLE' };
    let popup: Window | null = null;
    const closePopup = () => { try { popup?.close(); } catch { /* The user may already have closed this window. */ } };
    try {
        popup = (input.openWindow || (() => window.open('about:blank', '_blank')))();
        if (!popup) return { status: 'UNAVAILABLE' };
        popup.opener = null;
    } catch { closePopup(); return { status: 'UNAVAILABLE' }; }
    try {
        const checkout = await financeClient.createDonationCheckout(input.projectId, input.amountCents, input.recurring, input.guestCheckout, input.expectedPlatformCutBps);
        if (!input.isCurrent()) { closePopup(); return { status: 'STALE' }; }
        if (checkout?.simulated || checkout?.mockStripeEnabled) { closePopup(); return { status: 'SIMULATED' }; }
        const target = new URL(checkout?.checkoutUrl);
        if (target.protocol !== 'https:' || target.hostname !== 'checkout.stripe.com' || target.port || target.username || target.password) throw new Error('Invalid checkout destination');
        popup.location.replace(target.href);
        return { status: 'OPENED' };
    } catch (error: any) {
        closePopup();
        if (!input.isCurrent()) return { status: 'STALE' };
        if (error?.response?.status === 409 && error?.response?.data?.code === 'SUPPORT_TERMS_CHANGED') {
            try {
                const configuration = await financeClient.getDonationConfig(input.projectId);
                if (!input.isCurrent()) return { status: 'STALE' };
                if (configuration?.projectId === input.projectId && configuration.donationsEnabled && configuration.checkoutEnabled && hasSupportTerms(configuration)) {
                    return { status: 'TERMS_CHANGED', configuration };
                }
            } catch { /* A missing or unavailable quote must never fall back to guessed fees. */ }
        }
        return { status: 'UNAVAILABLE' };
    }
}

export async function verifySupportReturn(intentId: string, cancelled: boolean, isCurrent: () => boolean): Promise<{ type: 'success' | 'info' | 'warning'; title: string; message: string } | null> {
    try {
        const result = await financeClient.confirmDonationIntent(intentId);
        if (!isCurrent()) return null;
        if (result?.ok) return { type: 'success', title: 'Support Confirmed', message: 'Thanks for supporting this creator.' };
        if (cancelled) return { type: 'info', title: 'Checkout Closed', message: 'Your download is free. No payment was confirmed here.' };
        if (result?.status === 'FAILED' || result?.status === 'EXPIRED') return { type: 'info', title: 'Support Not Completed', message: 'No payment was confirmed. Your download is still available.' };
        return { type: 'info', title: 'Support Pending', message: 'Payment is still awaiting confirmation. Your download is free.' };
    } catch {
        return isCurrent() ? { type: 'warning', title: 'Could Not Confirm Support', message: 'Payment status could not be checked. Please try again later.' } : null;
    }
}
