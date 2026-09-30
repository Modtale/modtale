import React, { useEffect, useId, useRef, useState } from 'react';
import { HeartHandshake, ShieldCheck, X } from 'lucide-react';
import { useScrollLock } from '@/hooks/useScrollLock';
import { parseSupportAmount } from '@/modules/finance/api/financeTypes';

interface DonationPromptModalProps {
    show: boolean;
    currency?: string;
    suggestedAmountCents: number;
    recurringDefault: boolean;
    allowRecurring?: boolean;
    platformCutPercent?: number;
    testMode?: boolean;
    onClose: () => void;
    onSkip: () => void;
    onDonate: (amountCents: number, recurring: boolean, guestCheckout: boolean) => void;
    isProcessing?: boolean;
}

export const DonationPromptModal: React.FC<DonationPromptModalProps> = ({
    show, currency = 'USD', suggestedAmountCents, platformCutPercent = 10,
    allowRecurring = false, testMode = false, onClose, onSkip, onDonate, isProcessing = false
}) => {
    useScrollLock(show);
    const titleId = useId();
    const descriptionId = useId();
    const amountId = useId();
    const errorId = useId();
    const dialogRef = useRef<HTMLDivElement>(null);
    const closeRef = useRef(onClose);
    const processingRef = useRef(isProcessing);
    const submittedRef = useRef(false);
    closeRef.current = onClose;
    processingRef.current = isProcessing;
    const [amount, setAmount] = useState('5.00');
    const [recurring, setRecurring] = useState(false);

    useEffect(() => {
        if (!show) return;
        setAmount((Math.max(100, Math.min(100000, suggestedAmountCents)) / 100).toFixed(2));
        submittedRef.current = false;
        setRecurring(false);
        const previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null;
        dialogRef.current?.focus();
        const onKeyDown = (event: KeyboardEvent) => {
            if (event.key === 'Escape' && !processingRef.current) {
                event.preventDefault();
                closeRef.current();
            }
            if (event.key !== 'Tab') return;
            const controls = dialogRef.current?.querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled), a[href]');
            if (!controls?.length) { event.preventDefault(); return; }
            const first = controls[0];
            const last = controls[controls.length - 1];
            if (event.shiftKey && (document.activeElement === first || document.activeElement === dialogRef.current)) {
                event.preventDefault(); last.focus();
            } else if (!event.shiftKey && (document.activeElement === last || document.activeElement === dialogRef.current)) {
                event.preventDefault(); first.focus();
            }
        };
        document.addEventListener('keydown', onKeyDown);
        return () => {
            document.removeEventListener('keydown', onKeyDown);
            if (previousFocus?.isConnected) previousFocus.focus();
        };
    }, [show, suggestedAmountCents]);

    if (!show) return null;
    const cents = parseSupportAmount(amount);
    const valid = cents !== null;
    const formatted = valid ? new Intl.NumberFormat(undefined, { style: 'currency', currency }).format(cents / 100) : '';
    const submit = () => {
        if (cents === null || isProcessing || submittedRef.current) return;
        submittedRef.current = true;
        onDonate(cents, recurring && allowRecurring, !(recurring && allowRecurring));
    };
    const dismiss = () => { if (!isProcessing) onClose(); };

    return (
        <div className="fixed inset-0 z-[110] bg-black/55 backdrop-blur-sm flex items-center justify-center p-4" onClick={dismiss}>
            <div ref={dialogRef} role="dialog" aria-modal="true" aria-labelledby={titleId} aria-describedby={descriptionId} aria-busy={isProcessing} tabIndex={-1}
                className="w-full max-w-lg max-h-[calc(100dvh-2rem)] overflow-y-auto rounded-2xl border border-slate-200 dark:border-white/10 bg-white dark:bg-slate-900 shadow-2xl outline-none" onClick={e => e.stopPropagation()}>
                <div className="px-6 py-5 border-b border-slate-200 dark:border-white/10 flex items-start justify-between gap-3">
                    <div>
                        <div className="mb-2 inline-flex rounded-lg bg-modtale-accent/10 p-2 text-modtale-accent"><HeartHandshake className="h-5 w-5" /></div>
                        <h2 id={titleId} className="text-xl font-black text-slate-900 dark:text-white">Support this creator</h2>
                        <p id={descriptionId} className="mt-1 text-sm text-slate-600 dark:text-slate-300">Your download is free. A one-time tip helps the creator keep building.</p>
                    </div>
                    <button type="button" onClick={dismiss} disabled={isProcessing} aria-label="Close support dialog" className="p-2 rounded-full text-slate-500 hover:bg-slate-100 dark:hover:bg-white/10 disabled:opacity-50"><X className="h-5 w-5" /></button>
                </div>
                <div className="px-6 py-5 space-y-4">
                    {testMode && <p role="status" className="rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-900 dark:bg-amber-900/20 dark:text-amber-200">Preview checkout. No real money or creator earnings.</p>}
                    {allowRecurring && <div className="flex rounded-xl border border-slate-200 p-1 dark:border-white/10" role="group" aria-label="Support frequency">
                        <button type="button" disabled={isProcessing} aria-pressed={!recurring} onClick={() => setRecurring(false)} className={`flex-1 rounded-lg px-4 py-2 text-sm font-bold ${!recurring ? 'bg-modtale-accent text-white' : 'text-slate-600 dark:text-slate-300'}`}>One-time</button>
                        <button type="button" disabled={isProcessing} aria-pressed={recurring} onClick={() => setRecurring(true)} className={`flex-1 rounded-lg px-4 py-2 text-sm font-bold ${recurring ? 'bg-modtale-accent text-white' : 'text-slate-600 dark:text-slate-300'}`}>Monthly</button>
                    </div>}
                    <div>
                        <label htmlFor={amountId} className="block text-sm font-bold text-slate-700 dark:text-slate-200">Tip amount ({currency.toUpperCase()})</label>
                        <div className="mt-2 flex gap-2">{[300, 500, 1000].map(preset => <button key={preset} type="button" disabled={isProcessing} aria-pressed={cents === preset} onClick={() => setAmount((preset / 100).toFixed(2))} className={`flex-1 rounded-lg border px-3 py-2 font-bold ${cents === preset ? 'border-modtale-accent bg-modtale-accent/10 text-modtale-accent' : 'border-slate-200 dark:border-white/20 text-slate-600 dark:text-slate-300'}`}>{new Intl.NumberFormat(undefined, { style: 'currency', currency, maximumFractionDigits: 0 }).format(preset / 100)}</button>)}</div>
                        <input id={amountId} type="text" inputMode="decimal" value={amount} disabled={isProcessing} onChange={event => setAmount(event.target.value)} aria-invalid={!valid} aria-describedby={!valid ? errorId : undefined}
                            className="mt-3 w-full rounded-lg border border-slate-300 dark:border-white/20 bg-white dark:bg-slate-950 px-3 py-2.5 text-slate-900 dark:text-white font-bold" />
                        {!valid && <p id={errorId} className="mt-2 text-sm text-red-600 dark:text-red-400">Enter 1.00–1,000.00 with no more than two decimal places.</p>}
                    </div>
                    <p className="text-xs leading-relaxed text-slate-500 dark:text-slate-400">{platformCutPercent}% of the tip supports Modtale. Payment processing fees are deducted from the creator’s remaining share. This supports their content and is not a tax-deductible charitable donation.</p>
                    <p className="flex items-center gap-2 text-xs text-slate-500 dark:text-slate-400"><ShieldCheck className="h-4 w-4 shrink-0" />{recurring ? 'Renews monthly until cancelled. Manage or cancel in Finance → Your monthly support.' : 'One-time payment. No subscription.'} No reminder emails.</p>
                </div>
                <div className="px-6 py-4 border-t border-slate-200 dark:border-white/10 bg-slate-50 dark:bg-slate-800/50 flex flex-col-reverse sm:flex-row gap-2 sm:justify-end">
                    <button type="button" onClick={onSkip} disabled={isProcessing} className="px-4 py-2.5 rounded-xl border border-slate-300 dark:border-white/20 text-slate-700 dark:text-slate-200 font-bold hover:bg-slate-100 dark:hover:bg-white/10 disabled:opacity-50">Download without tipping</button>
                    <button type="button" onClick={submit} disabled={isProcessing || !valid} className="px-4 py-2.5 rounded-xl bg-modtale-accent text-white font-bold hover:bg-modtale-accentHover disabled:opacity-50">{isProcessing ? 'Opening checkout…' : valid ? `Tip ${formatted}${recurring ? '/month' : ''} & download` : 'Tip & download'}</button>
                </div>
            </div>
        </div>
    );
};
