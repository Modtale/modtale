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
        await act(async () => [...host.querySelectorAll('button')].find(button => button.textContent === 'Open support dialog')!.click());
        expect(host.querySelector('[role="dialog"]')).not.toBeNull();
        expect(host.textContent).toContain('Payment processing fees');
        expect(request).not.toHaveBeenCalled();
        await expect(api.get('/never-send')).rejects.toThrow('Network access is disabled');
    });
    it('shows settled synthetic amounts as currency rather than raw cents', async () => {
        await act(async () => root.render(<FinanceShowcase />));
        const state = host.querySelector('select')!;
        await act(async () => { state.value = 'settled'; state.dispatchEvent(new Event('change', { bubbles: true })); });
        expect(host.textContent).toContain('$65.00');
        expect(host.textContent).toContain('$30.00');
        expect(host.textContent).not.toContain('No data selected');
    });
});
