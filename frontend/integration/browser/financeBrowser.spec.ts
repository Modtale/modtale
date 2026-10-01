import { test, expect, type BrowserContext, type Page } from '@playwright/test';

const backend = process.env.MODTALE_FINANCE_TEST_ORIGIN!;
const frontend = 'http://localhost:3000';
const checkout = `${backend}/api/v1/finance/projects/project/donations/checkout-url`;
const external: string[] = [];

async function isolatedRoutes(context: BrowserContext) {
    external.length = 0;
    await context.route('**/*', route => {
        const target = new URL(route.request().url());
        if (target.origin === backend || target.origin === frontend) return route.continue();
        if (target.href === 'https://checkout.stripe.com/c/pay/fixture' || target.href === 'https://billing.stripe.com/p/session/fixture') {
            // Real browser navigation, but no network traffic or cookies ever reach Stripe.
            return route.fulfill({ status: 200, contentType: 'text/html', body: '<title>Isolated provider destination</title><p>Provider navigation fixture. No payment.</p>' });
        }
        external.push(target.origin);
        return route.abort('blockedbyclient');
    });
}

async function signin(page: Page, username: 'owner' | 'other' = 'owner') {
    await page.getByRole('button', { name: `Sign in ${username}`, exact: true }).click();
    await expect(page.getByTestId('identity')).toHaveText(username);
}

async function status(page: Page, operation: 'monthly' | 'subscriptions' | 'invalid-amount' | 'other-portal' | 'other-settings' | 'wrong-password') {
    return page.evaluate(async op => {
        const { api, financeClient } = (window as any).financeFixture;
        try {
            if (op === 'monthly') await financeClient.createDonationCheckout('project', 500, true, false, 1234);
            if (op === 'subscriptions') await financeClient.getSupportSubscriptions();
            if (op === 'invalid-amount') await financeClient.createDonationCheckout('project', 500.5, false, true, 1234);
            if (op === 'other-portal') await financeClient.openSupportBillingPortal('sub_owner');
            if (op === 'other-settings') await financeClient.updateProjectMonetization('project', { donationPlatformCutBps: 2500 });
            if (op === 'wrong-password') await api.post('/auth/signin', { username: 'owner', password: 'incorrect' });
            return 200;
        } catch (error: any) { return error.response?.status ?? 0; }
    }, operation);
}

test.beforeEach(async ({ context, page }) => {
    await isolatedRoutes(context);
    await page.goto('/integration/browser/index.html');
    await expect(page.getByRole('button', { name: 'Open support', exact: true })).toBeEnabled();
});
test.afterEach(() => { expect(external).toEqual([]); });

test('guest tip uses real cookies, exact terms and one actual isolated popup', async ({ page }) => {
    const requests: string[] = [];
    page.on('request', request => { if (request.url() === checkout) requests.push(request.postData()!); });
    await page.getByRole('button', { name: 'Open support', exact: true }).click();
    const dialog = page.getByRole('dialog');
    await expect(dialog).toContainText('12.34% supports Modtale');
    await expect(dialog).toContainText('Your download is free');
    await expect(dialog.getByRole('button', { name: 'Monthly', exact: true })).toHaveCount(0);
    const opened = page.waitForEvent('popup');
    const response = page.waitForResponse(checkout);
    await dialog.getByRole('button', { name: 'Tip $5.00', exact: true }).evaluate(button => { (button as HTMLButtonElement).click(); (button as HTMLButtonElement).click(); });
    const popup = await opened;
    await expect(popup).toHaveURL('https://checkout.stripe.com/c/pay/fixture');
    await expect(popup.getByText('Provider navigation fixture. No payment.')).toBeVisible();
    expect(await popup.evaluate(() => window.opener)).toBeNull();
    expect((await response).status()).toBe(200);
    expect(requests).toHaveLength(1);
    expect(JSON.parse(requests[0])).toEqual({ amountCents: 500, recurring: false, guestCheckout: true, expectedPlatformCutBps: 1234 });
    const cookies = await page.context().cookies(backend);
    expect(cookies.some(cookie => cookie.name === 'XSRF-TOKEN')).toBe(true);
    await popup.close();
});

test('actual sign-in session permits monthly support only after an explicit choice', async ({ page }) => {
    expect(await status(page, 'monthly')).toBe(400);
    expect(await status(page, 'wrong-password')).toBe(401);
    expect(await status(page, 'subscriptions')).toBe(401);
    await signin(page);
    expect((await page.context().cookies(backend)).some(cookie => cookie.name === 'JSESSIONID' && cookie.httpOnly)).toBe(true);
    await page.getByRole('button', { name: 'Open support', exact: true }).click();
    const dialog = page.getByRole('dialog');
    await expect(dialog.getByRole('button', { name: 'One-time', exact: true })).toHaveAttribute('aria-pressed', 'true');
    await dialog.getByRole('button', { name: 'Monthly', exact: true }).click();
    await expect(dialog).toContainText('Renews monthly until cancelled');
    const opened = page.waitForEvent('popup');
    const response = page.waitForResponse(checkout);
    await dialog.getByRole('button', { name: 'Tip $5.00/mo', exact: true }).click();
    const popup = await opened;
    await expect(popup).toHaveURL('https://checkout.stripe.com/c/pay/fixture');
    expect((await response).request().postDataJSON()).toEqual({ amountCents: 500, recurring: true, guestCheckout: false, expectedPlatformCutBps: 1234 });
    await popup.close();
});

test('stale terms close the real blank tab and require an explicit retry; return URLs never prove payment', async ({ page }) => {
    await page.getByRole('button', { name: 'Open stale quote', exact: true }).click();
    const dialog = page.getByRole('dialog');
    await expect(dialog).toContainText('10% supports Modtale');
    const oldTab = page.waitForEvent('popup');
    const rejected = page.waitForResponse(checkout);
    await dialog.getByRole('button', { name: 'Tip $5.00', exact: true }).click();
    const old = await oldTab;
    expect((await rejected).status()).toBe(409);
    await expect.poll(() => old.isClosed()).toBe(true);
    await expect(page.getByTestId('outcome')).toHaveText('TERMS_CHANGED');
    await expect(dialog).toContainText('12.34% supports Modtale');
    await expect(dialog.getByRole('alert')).toContainText('Review the new share before retrying');
    const opened = page.waitForEvent('popup');
    const accepted = page.waitForResponse(checkout);
    await dialog.getByRole('button', { name: 'Tip $5.00', exact: true }).click();
    const popup = await opened;
    await expect(popup).toHaveURL('https://checkout.stripe.com/c/pay/fixture');
    const intent = await (await accepted).json();
    expect(intent).toMatchObject({ platformCents: 62, creatorCents: 438 });
    const returns = await page.evaluate(async id => {
        const { verifySupportReturn } = (window as any).financeFixture;
        return [(await verifySupportReturn(id, false, () => true)).title, (await verifySupportReturn(id, true, () => true)).title];
    }, intent.intentId);
    expect(returns).toEqual(['Support Pending', 'Checkout Closed']);
    await popup.close();
});

test('owned billing portal survives popup failure and blocks other-account access', async ({ page }) => {
    await signin(page);
    await page.getByRole('button', { name: 'Toggle monthly support', exact: true }).click();
    await expect(page.getByText('Isolated support fixture', { exact: true })).toBeVisible();
    await expect(page.getByText('$5.00 / month', { exact: false })).toBeVisible();
    await page.evaluate(() => { (window as any).restoreFinanceOpen = window.open; window.open = () => { throw new Error('Browser popup interrupted'); }; });
    const manage = page.getByRole('button', { name: 'Manage or cancel in a new tab', exact: true });
    await manage.click();
    await expect(page.getByRole('alert')).toContainText('Could not open billing management');
    await expect(manage).toBeEnabled();
    await page.evaluate(() => { window.open = (window as any).restoreFinanceOpen; });
    const opened = page.waitForEvent('popup');
    await manage.evaluate(button => { (button as HTMLButtonElement).click(); (button as HTMLButtonElement).click(); });
    const popup = await opened;
    await expect(popup).toHaveURL('https://billing.stripe.com/p/session/fixture');
    expect(await popup.evaluate(() => window.opener)).toBeNull();
    await popup.close();
    await signin(page, 'other');
    expect(await status(page, 'other-portal')).toBe(403);
    expect(await status(page, 'other-settings')).toBe(403);
    await page.getByRole('button', { name: 'Refresh', exact: true }).click();
    await expect(page.getByText('Isolated support fixture', { exact: true })).toHaveCount(0);
});

test('browser enforces missing CSRF, rejects fractional cents and loses authenticated access on logout', async ({ page }) => {
    await signin(page);
    const rejected = await page.evaluate(async origin => {
        const response = await fetch(`${origin}/api/v1/finance/projects/project/donations/checkout-url`, {
            method: 'POST', credentials: 'include', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ amountCents: 500, recurring: true, guestCheckout: false, expectedPlatformCutBps: 1234 })
        });
        return response.status;
    }, backend);
    expect(rejected).toBe(403);
    expect(await status(page, 'invalid-amount')).toBe(400);
    await page.getByRole('button', { name: 'Sign out', exact: true }).click();
    await expect(page.getByTestId('identity')).toHaveText('guest');
    expect(await status(page, 'subscriptions')).toBe(401);
    expect(await status(page, 'monthly')).toBe(400);
});
