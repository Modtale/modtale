import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { StripeReadiness, type StripeReadinessReport } from '@/modules/finance/components/StripeReadiness';
import { financeClient } from '@/modules/finance/api/financeClient';
let host: HTMLDivElement; let root: Root;
const report: StripeReadinessReport = { mode: 'TEST', apiVersion: '2026-08-26.dahlia', livePaymentsEnabled: false, providerConfigurationVerified: false,
    checks: [{ code: 'platform_account', passed: false, action: 'Set STRIPE_PLATFORM_ACCOUNT_ID.' }], remainingChecks: ['Deliver an authentic signed webhook.'] };
beforeEach(() => { host = document.createElement('div'); document.body.append(host); root = createRoot(host); });
afterEach(async () => { await act(async () => root.unmount()); host.remove(); vi.restoreAllMocks(); });
async function render(value = report) { vi.spyOn(financeClient, 'getStripeReadiness').mockResolvedValue(value); await act(async () => root.render(<StripeReadiness />)); }
it('loads local configuration without automatically calling provider verification', async () => {
    const verify = vi.spyOn(financeClient, 'verifyStripeReadiness'); await render();
    expect(verify).not.toHaveBeenCalled(); expect(host.textContent).toContain('Live activation: Off');
    expect(host.textContent).toContain('Never paste keys'); expect(host.querySelector('input')).toBeNull();
});
it('keeps launch checks explicit after successful provider configuration', async () => {
    const verify = vi.spyOn(financeClient, 'verifyStripeReadiness').mockResolvedValue({ ...report, providerConfigurationVerified: true }); await render();
    await act(async () => host.querySelector('button')!.click()); expect(verify).toHaveBeenCalledTimes(1);
    expect(host.textContent).toContain('launch approvals still need verification'); expect(host.textContent).toContain('Deliver an authentic signed webhook');
});
it('prevents double verification and invalidates a prior successful result on failure', async () => {
    let reject!: (error: Error) => void;
    const verify = vi.spyOn(financeClient, 'verifyStripeReadiness').mockImplementation(() => new Promise((_ok, fail) => { reject = fail; }));
    await render({ ...report, providerConfigurationVerified: true }); const button = host.querySelector('button')!;
    await act(async () => { button.click(); button.click(); }); expect(verify).toHaveBeenCalledTimes(1); expect(button.disabled).toBe(true);
    await act(async () => reject(new Error('fixture unavailable')));
    expect(host.querySelector('[role="alert"]')).not.toBeNull(); expect(host.textContent).not.toContain('Provider configuration checks passed');
});
it('does not offer verification before configuration is available', async () => {
    vi.spyOn(financeClient, 'getStripeReadiness').mockRejectedValue(new Error('fixture unavailable'));
    await act(async () => root.render(<StripeReadiness />)); expect(host.querySelector('button')!.disabled).toBe(true);
});
