import React, { useEffect, useState } from 'react';
import { Check, HeartHandshake, X } from 'lucide-react';
import { useScrollLock } from '@/hooks/useScrollLock';

interface DonationPromptModalProps {
    show: boolean;
    currency?: string;
    suggestedAmountCents: number;
    recurringDefault: boolean;
    onClose: () => void;
    onSkip: () => void;
    onDonate: (amountCents: number, recurring: boolean) => void;
    isProcessing?: boolean;
}

export const DonationPromptModal: React.FC<DonationPromptModalProps> = ({
    show,
    currency = 'USD',
    suggestedAmountCents,
    recurringDefault,
    onClose,
    onSkip,
    onDonate,
    isProcessing = false
}) => {
    useScrollLock(show);
    const [amount, setAmount] = useState((suggestedAmountCents / 100).toFixed(2));
    const [recurring, setRecurring] = useState(recurringDefault);

    useEffect(() => {
        if (!show) return;
        setAmount((Math.max(100, suggestedAmountCents) / 100).toFixed(2));
        setRecurring(recurringDefault);
    }, [show, suggestedAmountCents, recurringDefault]);

    if (!show) return null;

    const normalizedCents = Math.max(100, Math.round(Number(amount || 0) * 100));

    return (
        <div className="fixed inset-0 z-[110] bg-black/55 backdrop-blur-sm flex items-center justify-center p-4" onClick={onClose}>
            <div className="w-full max-w-lg rounded-2xl border border-slate-200 dark:border-white/10 bg-white dark:bg-slate-900 shadow-2xl overflow-hidden" onClick={(e) => e.stopPropagation()}>
                <div className="px-6 py-5 border-b border-slate-200 dark:border-white/10 bg-slate-50 dark:bg-slate-800/50 flex items-start justify-between gap-3">
                    <div>
                        <h3 className="text-xl font-black text-slate-900 dark:text-white flex items-center gap-2"><HeartHandshake className="w-5 h-5 text-modtale-accent" /> Support this creator</h3>
                    </div>
                    <button onClick={onClose} className="p-2 rounded-full text-slate-500 hover:bg-slate-100 dark:hover:bg-white/10">
                        <X className="w-5 h-5" />
                    </button>
                </div>

                <div className="px-6 py-5 space-y-4">
                    <p className="text-sm text-slate-600 dark:text-slate-300">
                        This creator accepts optional donations. You can continue without donating.
                    </p>

                    <div className="rounded-xl border border-slate-200 dark:border-white/10 p-4 bg-slate-50 dark:bg-white/5">
                        <label className="block text-xs font-black uppercase tracking-wider text-slate-500 dark:text-slate-400 mb-2">Suggested Amount ({currency.toUpperCase()})</label>
                        <input
                            type="number"
                            min="1"
                            step="0.01"
                            value={amount}
                            onChange={(e) => setAmount(e.target.value)}
                            className="w-full rounded-lg border border-slate-300 dark:border-white/20 bg-white dark:bg-slate-900 px-3 py-2.5 text-slate-900 dark:text-white font-bold"
                        />
                        <button
                            type="button"
                            onClick={() => setRecurring((prev) => !prev)}
                            className="mt-3 inline-flex items-center gap-2 text-sm text-slate-700 dark:text-slate-300 font-medium"
                            aria-pressed={recurring}
                        >
                            <span className={`w-5 h-5 rounded border flex items-center justify-center transition-colors ${recurring ? 'bg-modtale-accent border-modtale-accent text-white' : 'bg-white dark:bg-slate-900 border-slate-300 dark:border-white/20 text-transparent'}`}>
                                <Check className="w-3.5 h-3.5" />
                            </span>
                            Make this a recurring monthly donation
                        </button>
                    </div>
                </div>

                <div className="px-6 py-4 border-t border-slate-200 dark:border-white/10 bg-slate-50 dark:bg-slate-800/50 flex flex-col sm:flex-row gap-2 sm:justify-end">
                    <button
                        onClick={onSkip}
                        disabled={isProcessing}
                        className="px-4 py-2.5 rounded-xl border border-slate-300 dark:border-white/20 text-slate-700 dark:text-slate-200 font-bold hover:bg-slate-100 dark:hover:bg-white/10"
                    >
                        Continue Without Donating
                    </button>
                    <button
                        onClick={() => onDonate(normalizedCents, recurring)}
                        disabled={isProcessing}
                        className="px-4 py-2.5 rounded-xl bg-modtale-accent text-white font-bold hover:bg-modtale-accentHover disabled:opacity-60"
                    >
                        {isProcessing ? 'Processing...' : 'Donate and Continue'}
                    </button>
                </div>
            </div>
        </div>
    );
};
