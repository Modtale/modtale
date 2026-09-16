import React, { useEffect, useId, useRef, useState } from 'react';
import { ArrowLeft, RotateCcw, Search, SlidersHorizontal } from 'lucide-react';
import { theme } from '@/styles/theme';
import { modpackDialog } from './modpackDialogStyles';
import { Input } from './FormShared';
import { type SettingsDocument, settingError } from '../utils/configSettings';

export function ModConfigEditor({ headingId, title, filename, document, onSave, onCancel }: {
    headingId: string; title: string; filename: string; document: SettingsDocument; onSave: (text: string) => void; onCancel: () => void;
}) {
    const [values, setValues] = useState<Record<string, string>>({});
    const [category, setCategory] = useState('All settings');
    const [search, setSearch] = useState('');
    const [error, setError] = useState<string | null>(null);
    const [discard, setDiscard] = useState(false);
    const id = useId();
    const editorRef = useRef<HTMLDivElement>(null);
    useEffect(() => { editorRef.current?.focus(); }, []);
    const dirty = document.settings.some(setting => (values[setting.id] ?? setting.value) !== setting.value);
    const invalid = document.settings.some(setting => settingError(setting, values[setting.id] ?? setting.value));
    const categories = ['All settings', ...Array.from(new Set(document.settings.map(setting => setting.category))).sort()];
    const visible = document.settings.filter(setting => (category === 'All settings' || setting.category === category) && `${setting.label} ${setting.context} ${setting.category}`.toLowerCase().includes(search.toLowerCase().trim()));
    const close = () => dirty ? setDiscard(true) : onCancel();
    return <div ref={editorRef} tabIndex={-1} className="flex flex-col min-h-0 flex-1 outline-none" onKeyDown={event => { if (event.key === 'Escape') { event.stopPropagation(); close(); } }}>
        <div className={modpackDialog.header}>
            <div className="min-w-0"><h3 id={headingId} className={modpackDialog.title}><SlidersHorizontal className={`w-5 h-5 ${theme.colors.accent}`} />{title}</h3><p className={`text-xs mt-1 ${theme.colors.textMuted}`}>{filename}</p></div>
            <button type="button" onClick={close} className={`${theme.components.buttonGhost} inline-flex items-center justify-center gap-2`}><ArrowLeft className="w-4 h-4" /> Back</button>
        </div>
        <div className="flex min-h-0 flex-1 flex-col sm:flex-row">
            <nav aria-label="Setting categories" className={`sm:w-52 shrink-0 p-4 border-b sm:border-b-0 sm:border-r ${theme.colors.border} overflow-auto max-h-36 sm:max-h-none`}>
                {categories.map(item => <button key={item} type="button" onClick={() => setCategory(item)} aria-pressed={category === item} className={`w-full flex items-center justify-between gap-2 text-left rounded-xl px-3 py-2.5 text-sm mb-1 ${category === item ? 'bg-modtale-accent text-white font-semibold' : `${theme.colors.textSecondary} ${theme.colors.bgSurfaceHover}`}`}><span>{item}</span><span className="text-xs opacity-70">{item === 'All settings' ? document.settings.length : document.settings.filter(setting => setting.category === item).length}</span></button>)}
            </nav>
            <div className="flex-1 min-w-0 min-h-0 flex flex-col p-5 gap-4">
                <div className="relative shrink-0"><Search className={`absolute left-3.5 top-3.5 w-4 h-4 ${theme.colors.textMuted}`} /><Input aria-label="Search settings" placeholder="Search settings…" value={search} onChange={event => setSearch(event.target.value)} className="!pl-10" /></div>
                <h4 className={`font-bold ${theme.colors.textPrimary}`}>{search ? 'Search results' : category}</h4>
                <div className="min-h-0 overflow-auto space-y-2 pr-1">
                    {visible.map(setting => { const value = values[setting.id] ?? setting.value; const problem = settingError(setting, value); const inputId = `${id}-${setting.id}`; return <div key={setting.id} className={`${modpackDialog.row} flex items-center justify-between gap-5`}>
                        <div className="min-w-0"><label htmlFor={inputId} className={`text-sm font-semibold ${theme.colors.textPrimary}`}>{setting.label}</label>{setting.context && <p className={`text-xs mt-1 ${theme.colors.textMuted}`}>{setting.context}</p>}{problem && <p id={`${inputId}-error`} className={`text-xs mt-1 ${theme.colors.dangerText}`}>{problem}</p>}</div>
                        {setting.type === 'boolean' ? <button id={inputId} type="button" role="switch" aria-label={setting.label} aria-checked={value === 'true'} onClick={() => setValues(previous => ({ ...previous, [setting.id]: value === 'true' ? 'false' : 'true' }))} className={`relative w-10 h-6 shrink-0 rounded-full transition-colors focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-modtale-accent ${value === 'true' ? 'bg-modtale-accent' : 'bg-slate-400 dark:bg-slate-600'}`}><span className={`absolute top-1 left-1 h-4 w-4 bg-white rounded-full transition-transform ${value === 'true' ? 'translate-x-4' : ''}`} /></button> : <Input id={inputId} value={value} inputMode={setting.type === 'number' ? 'decimal' : undefined} aria-invalid={!!problem} aria-describedby={problem ? `${inputId}-error` : undefined} onChange={event => setValues(previous => ({ ...previous, [setting.id]: event.target.value }))} className={`shrink-0 !py-2 ${setting.type === 'number' ? '!w-28' : '!w-40 sm:!w-52'}`} />}
                    </div>; })}
                    {!visible.length && <p className={`py-8 text-center text-sm ${theme.colors.textMuted}`}>No matching settings.</p>}
                </div>
            </div>
        </div>
        <div className={`${modpackDialog.footer} flex-wrap`}>
            <span role="status" className={`text-sm flex-1 ${error ? theme.colors.dangerText : theme.colors.textMuted}`}>{error || (discard ? 'Discard your unsaved changes?' : dirty ? 'Unsaved changes' : '')}</span>
            {discard ? <><button type="button" onClick={() => setDiscard(false)} className={`${theme.components.buttonGhost} inline-flex items-center justify-center gap-2`}>Keep editing</button><button type="button" onClick={onCancel} className={theme.components.buttonSecondary}>Discard changes</button></> : <><button type="button" disabled={!dirty} onClick={() => { setValues({}); setError(null); }} className={`${theme.components.buttonGhost} inline-flex items-center justify-center gap-2`}><RotateCcw className="w-4 h-4" />Reset changes</button><button type="button" disabled={!dirty || invalid} onClick={() => { try { onSave(document.save(values)); } catch (e) { setError((e as Error).message); } }} className={theme.components.buttonPrimary}>Save changes</button></>}
        </div>
    </div>;
}
