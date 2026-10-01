import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { financeClient } from '@/modules/finance/api/financeClient';
import { SupportSubscriptions } from '@/modules/finance/components/SupportSubscriptions';

vi.mock('@/modules/finance/api/financeClient', () => ({
    financeClient: { getSupportSubscriptions: vi.fn(), openSupportBillingPortal: vi.fn() }
}));

const subscription = (id = 'sub_owner', projectTitle = 'Creator project') => ({
    id, projectId: 'project', projectTitle, projectUrl: '/project/creator', amountCents: 500,
    currency: 'usd', status: 'active', cancelAtPeriodEnd: false, testMode: false
});
const billingUrl = 'https://billing.stripe.com/p/session/fixture';
const popup = () => ({ opener: {}, location: { replace: vi.fn() }, close: vi.fn() });
const deferred = <T,>() => {
    let resolve!: (value: T) => void;
    let reject!: (error: unknown) => void;
    const promise = new Promise<T>((done, fail) => { resolve = done; reject = fail; });
    return { promise, resolve, reject };
};
let host: HTMLDivElement;
let root: Root | null;
const manageButtons = () => [...host.querySelectorAll('button')].filter(button => button.textContent?.startsWith('Manage or cancel'));
const refreshButton = () => [...host.querySelectorAll('button')].find(button => button.textContent === 'Refresh')!;
const mount = async () => { await act(async () => root!.render(<SupportSubscriptions />)); };
const click = async (button: HTMLButtonElement) => { await act(async () => button.click()); };
const unmount = async () => { await act(async () => root!.unmount()); root = null; };

beforeEach(() => {
    vi.mocked(financeClient.getSupportSubscriptions).mockReset().mockResolvedValue([subscription()]);
    vi.mocked(financeClient.openSupportBillingPortal).mockReset().mockResolvedValue({ url: billingUrl });
    host = document.createElement('div'); document.body.append(host); root = createRoot(host);
});
afterEach(async () => {
    if (root) await unmount();
    host.remove();
});

describe('monthly support billing portal recovery', () => {
    it('opens the verified portal without an opener and leaves a successful tab open on unmount', async () => {
        const tab = popup();
        const open = vi.spyOn(window, 'open').mockReturnValue(tab as unknown as Window);
        await mount();
        expect(host.textContent).toContain('$5.00 / month');
        await click(manageButtons()[0]);
        expect(open).toHaveBeenCalledExactlyOnceWith('about:blank', '_blank');
        expect(financeClient.openSupportBillingPortal).toHaveBeenCalledExactlyOnceWith('sub_owner');
        expect(tab.opener).toBeNull();
        expect(tab.location.replace).toHaveBeenCalledExactlyOnceWith(billingUrl);
        expect(manageButtons()[0].disabled).toBe(false);
        expect(refreshButton().disabled).toBe(false);
        expect(host.querySelector('[role="alert"]')).toBeNull();
        await unmount();
        expect(tab.close).not.toHaveBeenCalled();
    });

    it.each(['blocked', 'open throws', 'opener throws'] as const)('recovers from %s without creating a portal session and permits retry', async failure => {
        const failedTab = popup();
        if (failure === 'opener throws') Object.defineProperty(failedTab, 'opener', { set: () => { throw new Error('Cannot detach opener'); } });
        const retryTab = popup();
        const open = vi.spyOn(window, 'open').mockImplementationOnce(() => {
            if (failure === 'open throws') throw new Error('Popup denied');
            return failure === 'blocked' ? null : failedTab as unknown as Window;
        }).mockReturnValue(retryTab as unknown as Window);
        await mount();
        await click(manageButtons()[0]);
        expect(host.querySelector('[role="alert"]')).not.toBeNull();
        expect(financeClient.openSupportBillingPortal).not.toHaveBeenCalled();
        expect(failedTab.location.replace).not.toHaveBeenCalled();
        expect(failedTab.close).toHaveBeenCalledTimes(failure === 'opener throws' ? 1 : 0);
        expect(manageButtons()[0].disabled).toBe(false);
        expect(refreshButton().disabled).toBe(false);
        await click(manageButtons()[0]);
        expect(open).toHaveBeenCalledTimes(2);
        expect(financeClient.openSupportBillingPortal).toHaveBeenCalledExactlyOnceWith('sub_owner');
        expect(retryTab.location.replace).toHaveBeenCalledExactlyOnceWith(billingUrl);
        expect(host.querySelector('[role="alert"]')).toBeNull();
    });

    it('rejects repeated clicks across subscriptions while the first request is pending', async () => {
        const response = deferred<{ url: string }>();
        vi.mocked(financeClient.getSupportSubscriptions).mockResolvedValue([subscription(), subscription('sub_other', 'Second project')]);
        vi.mocked(financeClient.openSupportBillingPortal).mockReturnValue(response.promise);
        const tab = popup();
        const open = vi.spyOn(window, 'open').mockReturnValue(tab as unknown as Window);
        await mount();
        const [first, second] = manageButtons();
        await act(async () => { first.click(); first.click(); second.click(); });
        expect(open).toHaveBeenCalledOnce();
        expect(financeClient.openSupportBillingPortal).toHaveBeenCalledExactlyOnceWith('sub_owner');
        expect(first.disabled).toBe(true); expect(second.disabled).toBe(true); expect(refreshButton().disabled).toBe(true);
        expect(host.textContent).toContain('Opening…');
        await act(async () => response.resolve({ url: billingUrl }));
        expect(tab.location.replace).toHaveBeenCalledOnce();
        expect(manageButtons().every(button => !button.disabled)).toBe(true);
        expect(refreshButton().disabled).toBe(false);
    });

    it.each([
        'https://evil.example/p/session/fixture',
        'https://billing.stripe.com.evil.example/p/session/fixture',
        'https://evil.billing.stripe.com/p/session/fixture',
        'https://billing.stripe.com./p/session/fixture',
        'https://billing.stripe.com@evil.example/p/session/fixture',
        'https://user:password@billing.stripe.com/p/session/fixture',
        'https://user@billing.stripe.com/p/session/fixture',
        'https://:password@billing.stripe.com/p/session/fixture',
        'https://billing.stripe.com:8443/p/session/fixture',
        'https://billing.stripe.com:0/p/session/fixture',
        'http://billing.stripe.com/p/session/fixture',
        'javascript:alert(1)',
        '/p/session/fixture',
        'not a URL'
    ])('closes the blank tab and permits retry for an invalid destination: %s', async url => {
        vi.mocked(financeClient.openSupportBillingPortal).mockResolvedValueOnce({ url });
        const failedTab = popup(); const retryTab = popup();
        vi.spyOn(window, 'open').mockReturnValueOnce(failedTab as unknown as Window).mockReturnValue(retryTab as unknown as Window);
        await mount();
        await click(manageButtons()[0]);
        expect(failedTab.location.replace).not.toHaveBeenCalled();
        expect(failedTab.close).toHaveBeenCalledOnce();
        expect(host.querySelector('[role="alert"]')).not.toBeNull();
        expect(manageButtons()[0].disabled).toBe(false);
        await click(manageButtons()[0]);
        expect(retryTab.location.replace).toHaveBeenCalledExactlyOnceWith(billingUrl);
        expect(host.querySelector('[role="alert"]')).toBeNull();
    });

    it('accepts the standard HTTPS port after URL normalization', async () => {
        const tab = popup(); vi.spyOn(window, 'open').mockReturnValue(tab as unknown as Window);
        vi.mocked(financeClient.openSupportBillingPortal).mockResolvedValue({ url: 'https://billing.stripe.com:443/p/session/fixture' });
        await mount(); await click(manageButtons()[0]);
        expect(tab.location.replace).toHaveBeenCalledExactlyOnceWith(billingUrl);
    });

    it.each(['request fails', 'navigation throws', 'close throws'] as const)('unlocks management after %s and permits retry', async failure => {
        const failedTab = popup(); const retryTab = popup();
        if (failure === 'navigation throws') failedTab.location.replace.mockImplementation(() => { throw new Error('Navigation denied'); });
        else vi.mocked(financeClient.openSupportBillingPortal).mockRejectedValueOnce({ response: { data: { message: 'Portal unavailable' } } });
        if (failure === 'close throws') failedTab.close.mockImplementation(() => { throw new Error('Tab already gone'); });
        vi.spyOn(window, 'open').mockReturnValueOnce(failedTab as unknown as Window).mockReturnValue(retryTab as unknown as Window);
        await mount(); await click(manageButtons()[0]);
        expect(failedTab.close).toHaveBeenCalledOnce();
        expect(host.querySelector('[role="alert"]')).not.toBeNull();
        expect(manageButtons()[0].disabled).toBe(false);
        await click(manageButtons()[0]);
        expect(retryTab.location.replace).toHaveBeenCalledExactlyOnceWith(billingUrl);
        expect(host.querySelector('[role="alert"]')).toBeNull();
    });

    it.each(['resolves', 'rejects', 'cleanup close throws'] as const)('closes an abandoned blank tab and ignores a request that %s after unmount', async completion => {
        const response = deferred<{ url: string }>();
        vi.mocked(financeClient.openSupportBillingPortal).mockReturnValue(response.promise);
        const tab = popup();
        if (completion === 'cleanup close throws') tab.close.mockImplementation(() => { throw new Error('Tab already gone'); });
        vi.spyOn(window, 'open').mockReturnValue(tab as unknown as Window);
        await mount(); await click(manageButtons()[0]);
        expect(tab.location.replace).not.toHaveBeenCalled();
        await unmount();
        expect(tab.close).toHaveBeenCalledOnce();
        await act(async () => {
            if (completion === 'rejects') response.reject(new Error('Expired request'));
            else response.resolve({ url: billingUrl });
        });
        expect(tab.location.replace).not.toHaveBeenCalled();
        expect(tab.close).toHaveBeenCalledOnce();
        expect(host.textContent).toBe('');
    });
});

describe('monthly support list refresh', () => {
    it('clears prior financial rows while refreshing and keeps them hidden after a failure, then retries', async () => {
        const refresh = deferred<ReturnType<typeof subscription>[]>();
        vi.mocked(financeClient.getSupportSubscriptions).mockResolvedValueOnce([subscription()]).mockReturnValueOnce(refresh.promise)
            .mockResolvedValueOnce([subscription('sub_new', 'New account project')]);
        await mount();
        expect(host.textContent).toContain('Creator project');
        await click(refreshButton());
        expect(host.querySelector('[role="status"]')?.textContent).toContain('Loading');
        expect(host.textContent).not.toContain('Creator project');
        expect(host.textContent).not.toContain('$5.00');
        expect(manageButtons()).toHaveLength(0);
        await act(async () => refresh.reject({ response: { status: 401, data: { message: 'Please sign in again' } } }));
        expect(host.querySelector('[role="alert"]')?.textContent).toContain('Please sign in again');
        expect(host.textContent).not.toContain('Creator project');
        expect(host.textContent).not.toContain('You don’t have any monthly support plans');
        expect(manageButtons()).toHaveLength(0);
        expect(refreshButton().disabled).toBe(false);
        await click(refreshButton());
        expect(financeClient.getSupportSubscriptions).toHaveBeenCalledTimes(3);
        expect(host.textContent).toContain('New account project');
        expect(host.textContent).not.toContain('Creator project');
        expect(host.querySelector('[role="alert"]')).toBeNull();
    });

    it('shows an empty state only after a successful empty response', async () => {
        vi.mocked(financeClient.getSupportSubscriptions).mockRejectedValueOnce(new Error('List unavailable')).mockResolvedValueOnce([]);
        await mount();
        expect(host.querySelector('[role="alert"]')).not.toBeNull();
        expect(host.textContent).not.toContain('You don’t have any monthly support plans');
        await click(refreshButton());
        expect(host.textContent).toContain('You don’t have any monthly support plans');
        expect(host.querySelector('[role="alert"]')).toBeNull();
    });

    it.each(['resolves', 'rejects'] as const)('ignores a list refresh that %s after unmount', async completion => {
        const response = deferred<ReturnType<typeof subscription>[]>();
        vi.mocked(financeClient.getSupportSubscriptions).mockReturnValue(response.promise);
        await mount(); await unmount();
        await act(async () => {
            if (completion === 'rejects') response.reject(new Error('Expired list request'));
            else response.resolve([subscription()]);
        });
        expect(host.textContent).toBe('');
    });
});
