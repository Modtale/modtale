import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api, BACKEND_URL, getCookie } from '@/utils/api';
import { financeClient } from '@/modules/finance/api/financeClient';
import { openSupportCheckout, verifySupportReturn } from '@/modules/finance/api/supportCheckout';
import { DonationPromptModal } from '@/modules/project/components/dialogs/DonationPromptModal';
import { SupportSubscriptions } from '@/modules/finance/components/SupportSubscriptions';

// Real XMLHttpRequest, browser cookie jar, CORS and the production Axios interceptors.
// Only popup navigation is stubbed: no request can leave the disposable loopback server.
api.defaults.adapter = 'xhr';
api.defaults.timeout = 5000;
api.interceptors.request.use(config => {
    const target = new URL(config.url || '', `${config.baseURL}/`);
    if (target.origin !== process.env.MODTALE_FINANCE_TEST_ORIGIN) throw new Error('External test request rejected');
    return config;
});

let host: HTMLDivElement;
let root: Root;
const login = (username = 'owner') => api.post('/auth/signin', { username, password: 'isolated-fixture-password' });
const button = (text: string) => [...host.querySelectorAll('button')].find(node => node.textContent?.trim() === text)!;
const popup = () => ({ opener: {}, close: vi.fn(), location: { replace: vi.fn() } });

beforeEach(async () => {
    await api.post('/auth/logout');
    host = document.createElement('div'); document.body.append(host); root = createRoot(host);
});
afterEach(async () => { await act(async () => root.unmount()); host.remove(); });

describe('authenticated frontend ↔ servlet finance integration', { concurrent: false }, () => {
    it('renders exact server terms and sends a guest one-time tip once through real CSRF', async () => {
        const configuration = await financeClient.getDonationConfig('project');
        expect(configuration).toMatchObject({ donationPlatformCutBps: 1234, donationPlatformCutPercent: 12.34, currency: 'usd', checkoutEnabled: true });
        expect(getCookie('XSRF-TOKEN')).toBeNull(); // API-host cookie cannot be read from the frontend host.
        const tab = popup();
        let pending: ReturnType<typeof openSupportCheckout> | undefined;
        const donate = vi.fn((amountCents, recurring, guestCheckout) => {
            pending = openSupportCheckout({ projectId: configuration.projectId, amountCents, recurring, guestCheckout,
                expectedPlatformCutBps: configuration.donationPlatformCutBps, isCurrent: () => true, openWindow: () => tab as unknown as Window });
        });
        await act(async () => root.render(<DonationPromptModal show suggestedAmountCents={configuration.suggestedDonationCents}
            recurringDefault={false} platformCutBps={configuration.donationPlatformCutBps} testMode={configuration.testMode}
            onClose={() => {}} onSkip={() => {}} onDonate={donate} />));
        expect(host.textContent).toContain('12.34% supports Modtale');
        expect(host.textContent).toContain('Your download is free');
        await act(async () => { button('Tip $5.00').click(); button('Tip $5.00').click(); await pending; });
        expect(donate).toHaveBeenCalledExactlyOnceWith(500, false, true);
        expect(await pending).toEqual({ status: 'OPENED' });
        expect(tab.opener).toBeNull(); expect(tab.location.replace).toHaveBeenCalledExactlyOnceWith('https://checkout.stripe.com/c/pay/fixture');
    });

    it('signs in through the real session endpoint and permits an explicit monthly choice', async () => {
        await expect(financeClient.createDonationCheckout('project', 500, true, false, 1234)).rejects.toMatchObject({ response: { status: 400 } });
        await expect(api.post('/auth/signin', { username: 'owner', password: 'incorrect' })).rejects.toMatchObject({ response: { status: 401 } });
        await expect(financeClient.getSupportSubscriptions()).rejects.toMatchObject({ response: { status: 401 } });
        expect((await login()).data.status).toBe('success');
        const configuration = await financeClient.getDonationConfig('project');
        const tab = popup(); let pending: ReturnType<typeof openSupportCheckout> | undefined;
        await act(async () => root.render(<DonationPromptModal show suggestedAmountCents={500} recurringDefault={false}
            platformCutBps={configuration.donationPlatformCutBps} allowRecurring={configuration.recurringEnabled}
            onClose={() => {}} onSkip={() => {}} onDonate={(amountCents, recurring, guestCheckout) => {
                pending = openSupportCheckout({ projectId: 'project', amountCents, recurring, guestCheckout,
                    expectedPlatformCutBps: configuration.donationPlatformCutBps, isCurrent: () => true, openWindow: () => tab as unknown as Window });
            }} />));
        await act(async () => button('Monthly').click());
        expect(host.textContent).toContain('Renews monthly until cancelled');
        await act(async () => { button('Tip $5.00/mo').click(); await pending; });
        expect(await pending).toEqual({ status: 'OPENED' });
        expect(tab.location.replace).toHaveBeenCalledOnce();
    });

    it('refreshes a stale quote from HTTP 409 without paying until an explicit retry', async () => {
        const oldTab = popup();
        const result = await openSupportCheckout({ projectId: 'project', amountCents: 500, recurring: false, guestCheckout: true,
            expectedPlatformCutBps: 1000, isCurrent: () => true, openWindow: () => oldTab as unknown as Window });
        expect(result.status).toBe('TERMS_CHANGED');
        expect(oldTab.close).toHaveBeenCalledOnce(); expect(oldTab.location.replace).not.toHaveBeenCalled();
        if (result.status !== 'TERMS_CHANGED') throw new Error('Expected fresh server terms');
        expect(result.configuration.donationPlatformCutBps).toBe(1234);
        const checkout = await financeClient.createDonationCheckout('project', 500, false, true, result.configuration.donationPlatformCutBps!);
        expect(checkout).toMatchObject({ creatorCents: 438, platformCents: 62, simulated: false });
        expect((await verifySupportReturn(checkout.intentId, false, () => true))?.title).toBe('Support Pending');
        expect((await verifySupportReturn(checkout.intentId, true, () => true))?.title).toBe('Checkout Closed');
    });

    it('renders the signed-in support list and opens only its owned billing portal', async () => {
        await login();
        await act(async () => { root.render(<SupportSubscriptions />); });
        await vi.waitFor(async () => {
            await act(async () => { await new Promise(resolve => setTimeout(resolve, 20)); });
            expect(host.textContent).toContain('Isolated support fixture');
        });
        expect(host.textContent).toContain('$5.00 / month');
        const tab = popup(); vi.spyOn(window, 'open').mockReturnValue(tab as unknown as Window);
        await act(async () => {
            const manage = [...host.querySelectorAll('button')].find(node => node.textContent?.startsWith('Manage or cancel'))!;
            manage.click(); manage.click();
            await vi.waitFor(() => expect(tab.location.replace).toHaveBeenCalledOnce());
        });
        expect(tab.location.replace).toHaveBeenCalledWith('https://billing.stripe.com/p/session/fixture');
        await api.post('/auth/logout');
        await login('other');
        expect(await financeClient.getSupportSubscriptions()).toEqual([]);
        await expect(financeClient.openSupportBillingPortal('sub_owner')).rejects.toMatchObject({ response: { status: 403 } });
        await expect(financeClient.updateProjectMonetization('project', { donationPlatformCutBps: 2500 })).rejects.toMatchObject({ response: { status: 403 } });
    });

    it('rejects missing CSRF, preserves integer validation, and removes monthly access on logout', async () => {
        await login();
        const status = await new Promise<number>((resolve, reject) => {
            const request = new XMLHttpRequest(); request.open('POST', `${BACKEND_URL}/api/v1/finance/projects/project/donations/checkout-url`);
            request.withCredentials = true; request.setRequestHeader('Content-Type', 'application/json');
            request.onload = () => resolve(request.status); request.onerror = () => reject(new Error('Loopback XHR failed'));
            request.send(JSON.stringify({ amountCents: 500, recurring: true, guestCheckout: false, expectedPlatformCutBps: 1234 }));
        });
        expect(status).toBe(403);
        await expect(financeClient.createDonationCheckout('project', 500.5, false, false, 1234)).rejects.toMatchObject({ response: { status: 400 } });
        await api.post('/auth/logout');
        await expect(financeClient.getSupportSubscriptions()).rejects.toMatchObject({ response: { status: 401 } });
        await expect(financeClient.createDonationCheckout('project', 500, true, false, 1234)).rejects.toMatchObject({ response: { status: 400 } });
    });
});
