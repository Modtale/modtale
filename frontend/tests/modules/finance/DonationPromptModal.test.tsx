import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { DonationPromptModal } from '@/modules/project/components/dialogs/DonationPromptModal';

vi.mock('@/hooks/useScrollLock', () => ({ useScrollLock: vi.fn() }));
let host: HTMLDivElement;
let root: Root;
const onDonate = vi.fn(); const onClose = vi.fn(); const onSkip = vi.fn();
const render = async (props = {}) => {
    await act(async () => root.render(<DonationPromptModal show suggestedAmountCents={500} recurringDefault allowRecurring onDonate={onDonate} onClose={onClose} onSkip={onSkip} {...props} />));
};
beforeEach(() => { host = document.createElement('div'); document.body.append(host); root = createRoot(host); });
afterEach(async () => { await act(async () => root.unmount()); host.remove(); });

describe('optional creator support dialog', () => {
    it('labels the dialog, discloses fees and makes repeated clicks safe', async () => {
        await render();
        expect(host.querySelector('[role="dialog"]')?.getAttribute('aria-modal')).toBe('true');
        expect(host.textContent).toContain('Payment processing fees');
        expect(host.textContent).toContain('not a tax-deductible charitable donation');
        const button = [...host.querySelectorAll('button')].find(b => b.textContent?.includes('& download'))!;
        await act(async () => { button.click(); button.click(); });
        expect(onDonate).toHaveBeenCalledTimes(1);
        expect(onDonate).toHaveBeenCalledWith(500, false, true);
    });
    it('allows skipping and Escape without a payment', async () => {
        await render();
        await act(async () => [...host.querySelectorAll('button')].find(b => b.textContent === 'Download without tipping')!.click());
        expect(onSkip).toHaveBeenCalledOnce(); expect(onDonate).not.toHaveBeenCalled();
        await act(async () => document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true })));
        expect(onClose).toHaveBeenCalledOnce();
    });
    it('does not dismiss or pay again during checkout creation', async () => {
        await render({ isProcessing: true });
        await act(async () => document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true })));
        expect(onClose).not.toHaveBeenCalled();
        expect([...host.querySelectorAll('button')].every(b => b.disabled)).toBe(true);
    });
    it('focuses the modal and restores the initiating control after close', async () => {
        const opener = document.createElement('button'); document.body.append(opener); opener.focus();
        await render(); expect(document.activeElement).toBe(host.querySelector('[role="dialog"]'));
        await render({ show: false }); expect(document.activeElement).toBe(opener); opener.remove();
    });
    it('requires an explicit monthly choice and explains renewal and cancellation', async () => {
        await render();
        await act(async () => [...host.querySelectorAll('button')].find(button => button.textContent === 'Monthly')!.click());
        expect(host.textContent).toContain('Renews monthly until cancelled');
        expect(host.textContent).toContain('Optional support helps the creator');
        expect(host.textContent).not.toContain('A one-time tip');
        await act(async () => [...host.querySelectorAll('button')].find(button => button.textContent?.includes('/month & download'))!.click());
        expect(onDonate).toHaveBeenCalledWith(500, true, false);
    });

});
