import React, { useEffect, useRef, useState } from 'react';
import { ArrowRight, Calendar, X } from 'lucide-react';
import { api, extractApiErrorMessage } from '@/utils/api';
import type { Modjam } from '@/types';
import { ModalPortal } from '@/components/ui/ModalPortal';
import { Spinner } from '@/components/ui/Spinner';
import { useDialogFocus } from '@/hooks/useDialogFocus';
import { useScrollLock } from '@/hooks/useScrollLock';
import { jamSlugFromTitle, normalizeJamSlug, validateJamSlug } from '@/modules/jam/utils/slug';

export const JamCreateModal: React.FC<{ onClose: () => void; onCreated: (jam: Modjam) => void }> = ({ onClose, onCreated }) => {
    const [title, setTitle] = useState('');
    const [slug, setSlug] = useState('');
    const [slugEdited, setSlugEdited] = useState(false);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const dialogRef = useRef<HTMLDivElement>(null);
    const pendingRef = useRef(false);
    const mountedRef = useRef(true);
    const slugError = slug ? validateJamSlug(slug) : null;
    useScrollLock(true);
    useDialogFocus(true, dialogRef);
    useEffect(() => { mountedRef.current = true; return () => { mountedRef.current = false; }; }, []);
    useEffect(() => {
        const dismiss = (event: KeyboardEvent) => { if (event.key === 'Escape' && !pendingRef.current) onClose(); };
        document.addEventListener('keydown', dismiss);
        return () => document.removeEventListener('keydown', dismiss);
    }, [onClose]);

    const createDraft = async (event: React.FormEvent) => {
        event.preventDefault();
        if (pendingRef.current || title.trim().length < 5 || validateJamSlug(slug)) return;
        pendingRef.current = true;
        setSaving(true);
        setError(null);
        try {
            const response = await api.post('/modjams', { title: title.trim(), slug, description: '', categories: [] });
            if (mountedRef.current) onCreated(response.data);
        } catch (failure) {
            if (mountedRef.current) setError(extractApiErrorMessage(failure, 'We could not create your draft. Please try again.'));
        } finally {
            pendingRef.current = false;
            if (mountedRef.current) setSaving(false);
        }
    };

    return <ModalPortal>
        <div className="fixed inset-0 z-[10000] flex items-center justify-center bg-black/60 backdrop-blur-sm p-4" onMouseDown={event => { if (event.target === event.currentTarget && !pendingRef.current) onClose(); }}>
            <div ref={dialogRef} role="dialog" aria-modal="true" aria-labelledby="jam-create-title" aria-describedby="jam-create-description" className="relative w-full max-w-lg max-h-[calc(100dvh-2rem)] overflow-y-auto rounded-3xl border border-slate-200 dark:border-white/10 bg-white dark:bg-slate-900 p-6 sm:p-8 shadow-2xl">
                <button type="button" onClick={onClose} disabled={saving} aria-label="Close jam creation" className="absolute right-4 top-4 rounded-lg p-2 text-slate-500 hover:bg-slate-100 dark:hover:bg-white/5 disabled:opacity-40"><X className="size-5" /></button>
                <div className="mb-6 pr-6">
                    <Calendar className="size-8 text-modtale-accent mb-4" aria-hidden="true" />
                    <h2 id="jam-create-title" className="text-3xl font-black text-slate-900 dark:text-white tracking-tight">Let's name your jam.</h2>
                    <p id="jam-create-description" className="mt-2 text-sm font-medium text-slate-500 dark:text-slate-400">Start with a private draft. Add the schedule, rules and judging criteria before publishing.</p>
                </div>
                <form onSubmit={createDraft} className="space-y-5" aria-busy={saving}>
                    <div>
                        <label htmlFor="jam-title" className="block mb-2 text-xs font-bold text-slate-600 dark:text-slate-300">Event title</label>
                        <input id="jam-title" autoFocus value={title} disabled={saving} onChange={event => { const next = event.target.value; setTitle(next); if (!slugEdited) setSlug(jamSlugFromTitle(next)); setError(null); }} placeholder="Summer Hackathon 2026" className="w-full rounded-2xl border border-slate-300 dark:border-white/20 bg-slate-50 dark:bg-black/30 px-4 py-4 text-xl font-black text-slate-900 dark:text-white outline-none focus:ring-2 focus:ring-modtale-accent" />
                        <p className="mt-1 text-xs text-slate-500">At least 5 characters. You can change it later.</p>
                    </div>
                    <div>
                        <label htmlFor="jam-slug" className="block mb-2 text-xs font-bold text-slate-600 dark:text-slate-300">Jam URL</label>
                        <div className="flex flex-col sm:flex-row overflow-hidden rounded-xl border border-slate-300 dark:border-white/20 focus-within:ring-2 focus-within:ring-modtale-accent">
                            <span className="bg-slate-100 dark:bg-white/5 px-4 py-2 sm:py-3 text-sm text-slate-500">modtale.net/jam/</span>
                            <input id="jam-slug" value={slug} disabled={saving} aria-invalid={!!slugError} aria-describedby="jam-slug-help" onChange={event => { setSlug(normalizeJamSlug(event.target.value)); setSlugEdited(true); setError(null); }} className="min-w-0 flex-1 bg-slate-50 dark:bg-black/30 px-4 py-3 text-sm font-mono text-slate-900 dark:text-white outline-none" placeholder="summer-hackathon" />
                        </div>
                        <p id="jam-slug-help" className={`mt-2 text-xs ${slugError ? 'text-red-600 dark:text-red-400' : 'text-slate-500'}`}>{slugError || 'Use a short URL, or paste an existing jam URL to edit its last part.'}</p>
                    </div>
                    {error && <p role="alert" className="rounded-xl bg-red-500/10 px-4 py-3 text-sm font-medium text-red-600 dark:text-red-400">{error}</p>}
                    <button type="submit" disabled={saving || title.trim().length < 5 || !!validateJamSlug(slug)} className="flex h-14 w-full items-center justify-center gap-3 rounded-2xl bg-modtale-accent text-white font-black hover:bg-modtale-accentHover disabled:opacity-50 disabled:cursor-not-allowed transition-colors">
                        {saving ? <><Spinner className="size-5" fullScreen={false} /> Creating draft…</> : <>Start Building <ArrowRight className="size-5" aria-hidden="true" /></>}
                    </button>
                </form>
            </div>
        </div>
    </ModalPortal>;
};
