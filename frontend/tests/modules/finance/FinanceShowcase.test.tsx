import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import FinanceShowcase from '@/modules/finance/demo/FinanceShowcase';
import { api } from '@/utils/api';

let host: HTMLDivElement;
let root: Root;
beforeEach(() => {
    host = document.createElement('div'); document.body.append(host); root = createRoot(host);
});
afterEach(async () => { await act(async () => root.unmount()); host.remove(); vi.restoreAllMocks(); });

describe('synthetic finance showcase', () => {
    it('renders real finance and support components without a network request', async () => {
        const request = vi.spyOn(api, 'get');
        await act(async () => root.render(<FinanceShowcase />));
        expect(host.textContent).toContain('Finance demo');
        expect(host.textContent).toContain('Your monthly support');
        expect(host.textContent).toContain('Synthetic preview data only');
        expect([...host.querySelectorAll('button')].find(button => button.textContent?.includes('Request Payout'))?.disabled).toBe(true);
        await act(async () => [...host.querySelectorAll('button')].find(button => button.textContent === 'Open support dialog')!.click());
        expect(host.querySelector('[role="dialog"]')).not.toBeNull();
        expect(host.textContent).toContain('Payment processing fees');
        expect(request).not.toHaveBeenCalled();
        await expect(api.get('/never-send')).rejects.toThrow('Network access is disabled');
    });
    it('renders the transfer review fixture without provider or account calls', async () => {
        const get = vi.spyOn(api, 'get'); const post = vi.spyOn(api, 'post');
        await act(async () => root.render(<FinanceShowcase />));
        await act(async () => [...host.querySelectorAll('button')].find(button => button.textContent === 'Show transfer review')!.click());
        expect(host.textContent).toContain('Transfer reconciliation'); expect(host.textContent).toContain('Synthetic lost provider response');
        await act(async () => [...host.querySelectorAll('button')].find(button => button.textContent === 'Review recipient 1')!.click());
        expect(host.textContent).toContain('Existing Stripe transfer ID'); expect(get).not.toHaveBeenCalled(); expect(post).not.toHaveBeenCalled();
    });
    it('renders dispute decisions without any accounting or provider API request', async () => {
        const get = vi.spyOn(api, 'get'); const post = vi.spyOn(api, 'post');
        await act(async () => root.render(<FinanceShowcase />));
        await act(async () => [...host.querySelectorAll('button')].find(button => button.textContent === 'Show dispute review')!.click());
        await act(async () => [...host.querySelectorAll('button')].find(button => button.textContent?.includes('dp_demo'))!.click());
        expect(host.textContent).toContain('No discretionary fee is assigned automatically');
        expect([...host.querySelectorAll('button')].find(button => button.textContent === 'Record reviewed decision')?.disabled).toBe(true);
        expect(get).not.toHaveBeenCalled(); expect(post).not.toHaveBeenCalled();
    });
    it('shows settled synthetic amounts as currency rather than raw cents', async () => {
        await act(async () => root.render(<FinanceShowcase />));
        const state = host.querySelector('select')!;
        await act(async () => { state.value = 'settled'; state.dispatchEvent(new Event('change', { bubbles: true })); });
        expect(host.textContent).toContain('$65.00');
        expect(host.textContent).toContain('$30.00');
        expect([...host.querySelectorAll('button')].find(button => button.textContent?.includes('Request Payout'))?.disabled).toBe(false);
        expect(host.textContent).not.toContain('No data selected');
        await act(async () => [...host.querySelectorAll('button')].find(button => button.textContent === 'Toggle theme')!.click());
        expect(document.documentElement.classList.contains('dark')).toBe(true);
        await act(async () => [...host.querySelectorAll('button')].find(button => button.textContent?.includes('Request Payout'))!.click());
        expect(host.textContent).toContain('RESERVED');
        expect([...host.querySelectorAll('button')].find(button => button.textContent?.includes('Request Payout'))?.disabled).toBe(true);
    });
    it('shows provisional ad review and audit without any account or payment request', async () => {
        const read = vi.spyOn(api, 'get'); const write = vi.spyOn(api, 'post');
        await act(async () => root.render(<FinanceShowcase />));
        await act(async () => [...host.querySelectorAll('button')].find(button => button.textContent === 'Show ad review')!.click());
        await act(async () => [...host.querySelectorAll('button')].find(button => button.textContent?.includes('demo-network · demo-2026-08'))!.click());
        expect(host.textContent).toContain('Credit release locked');
        expect(host.textContent).toContain('$1,200.00');
        expect(host.textContent).toContain('$900.00');
        expect(host.textContent).toContain('historical owner identity needs review');
        const review = [...host.querySelectorAll('button')].find(button => button.textContent === 'Mark provisionally reviewed')!;
        expect(review.disabled).toBe(true);
        const reason = host.querySelector('textarea')!;
        await act(async () => {
            Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value')!.set!.call(reason, 'Synthetic review only; no verified funding.');
            reason.dispatchEvent(new Event('input', { bubbles: true }));
        });
        await act(async () => review.click());
        expect(host.textContent).toContain('Creator credit release remains locked');
        expect(host.textContent).toContain('REVIEWED PROVISIONAL');
        expect(read).not.toHaveBeenCalled(); expect(write).not.toHaveBeenCalled();
    });
});
