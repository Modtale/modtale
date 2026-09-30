import React, { useEffect, useId, useRef, useState } from 'react';
import { HeartHandshake, X } from 'lucide-react';
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
                className="w-full max-w-md max-h-[calc(100dvh-2rem)] overflow-y-auto rounded-2xl border border-slate-200 dark:border-white/10 bg-white dark:bg-slate-900 shadow-2xl outline-none" onClick={e => e.stopPropagation()}>
                <div className="flex items-start justify-between gap-3 px-5 pt-5">
                    <div>
                        <h2 id={titleId} className="flex items-center gap-2 text-lg font-black text-slate-900 dark:text-white"><HeartHandshake className="h-5 w-5 text-modtale-accent" />Support this creator</h2>
                        <p id={descriptionId} className="mt-1 text-sm text-slate-500 dark:text-slate-400">Optional. Your download is free.</p>
                    </div>
                    <button type="button" onClick={dismiss} disabled={isProcessing} aria-label="Close support dialog" className="-mr-1 -mt-1 flex h-9 w-9 shrink-0 items-center justify-center rounded-lg text-slate-500 hover:bg-slate-100 dark:hover:bg-white/10 disabled:opacity-50"><X className="h-4 w-4" /></button>
                </div>
                <div className="space-y-4 px-5 py-5">
                    {testMode && <p role="status" className="text-xs font-semibold text-amber-700 dark:text-amber-300">Test mode · No real charge</p>}
                    {allowRecurring && <div className="flex gap-1 rounded-lg bg-slate-100 p-1 dark:bg-slate-950/70" role="group" aria-label="Support frequency">
                        <button type="button" disabled={isProcessing} aria-pressed={!recurring} onClick={() => setRecurring(false)} className={`flex-1 rounded-md px-3 py-2 text-sm font-bold ${!recurring ? 'bg-white text-slate-900 shadow-sm dark:bg-slate-700 dark:text-white' : 'text-slate-500 dark:text-slate-400'}`}>One-time</button>
                        <button type="button" disabled={isProcessing} aria-pressed={recurring} onClick={() => setRecurring(true)} className={`flex-1 rounded-md px-3 py-2 text-sm font-bold ${recurring ? 'bg-white text-slate-900 shadow-sm dark:bg-slate-700 dark:text-white' : 'text-slate-500 dark:text-slate-400'}`}>Monthly</button>
                    </div>}
                    <div>
                        <label htmlFor={amountId} className="block text-xs font-bold text-slate-500 dark:text-slate-400">Amount · {currency.toUpperCase()}</label>
                        <div className="mt-2 flex gap-2">{[300, 500, 1000].map(preset => <button key={preset} type="button" disabled={isProcessing} aria-pressed={cents === preset} onClick={() => setAmount((preset / 100).toFixed(2))} className={`h-11 min-w-0 flex-1 rounded-lg border px-2 text-sm font-bold ${cents === preset ? 'border-modtale-accent bg-modtale-accent/10 text-modtale-accent' : 'border-slate-200 text-slate-600 hover:bg-slate-50 dark:border-white/15 dark:text-slate-300 dark:hover:bg-white/5'}`}>{new Intl.NumberFormat(undefined, { style: 'currency', currency, maximumFractionDigits: 0 }).format(preset / 100)}</button>)}
                            <input id={amountId} aria-label={`Custom amount (${currency.toUpperCase()})`} type="text" inputMode="decimal" value={amount} disabled={isProcessing} onChange={event => setAmount(event.target.value)} aria-invalid={!valid} aria-describedby={!valid ? errorId : undefined}
                                className="h-11 w-24 rounded-lg border border-slate-300 bg-white px-3 text-center text-sm font-bold text-slate-900 focus:border-modtale-accent focus:outline-none focus:ring-2 focus:ring-modtale-accent/20 dark:border-white/20 dark:bg-slate-950 dark:text-white" />
                        </div>
                        {!valid && <p id={errorId} className="mt-2 text-xs text-red-600 dark:text-red-400">Enter 1.00–1,000.00, up to two decimal places.</p>}
                    </div>
                    <div className="space-y-1.5 text-xs leading-relaxed text-slate-500 dark:text-slate-400">
                        <p>{platformCutPercent}% supports Modtale. Payment processing fees come from the creator’s remaining share.</p>
                        <p>{recurring ? 'Renews monthly until cancelled. Manage in Finance.' : 'One-time payment.'} Not a charitable donation.</p>
                    </div>
                </div>
                <div className="grid grid-cols-2 gap-2 border-t border-slate-200 px-5 py-4 dark:border-white/10">
                    <button type="button" onClick={onSkip} disabled={isProcessing} className="flex h-11 items-center justify-center rounded-lg border border-slate-200 bg-slate-50 px-2 text-sm font-bold text-slate-700 hover:bg-slate-100 disabled:opacity-50 dark:border-white/15 dark:bg-white/5 dark:text-slate-200 dark:hover:bg-white/10">Download free</button>
                    <button type="button" onClick={submit} disabled={isProcessing || !valid} className="flex h-11 items-center justify-center whitespace-nowrap rounded-lg bg-modtale-accent px-2 text-sm font-bold text-white hover:bg-modtale-accentHover disabled:opacity-50">{isProcessing ? 'Opening…' : valid ? `Tip ${formatted}${recurring ? '/mo' : ''}` : 'Choose amount'}</button>
                </div>
            </div>
        </div>
    );
};
