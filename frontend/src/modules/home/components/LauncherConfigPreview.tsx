import React, { useEffect, useId, useRef, useState } from 'react';
import { Search, SlidersHorizontal } from 'lucide-react';

const settings = [
    { key: 'notifications', name: 'Notifications', category: 'Display & sounds' },
    { key: 'sounds', name: 'Sound effects', category: 'Display & sounds' },
    { key: 'cooldown', name: 'Cooldown (seconds)', category: 'General' },
] as const;
export type ModConfig = { notifications: boolean; sounds: boolean; cooldown: string };
export const defaultModConfig: ModConfig = { notifications: true, sounds: true, cooldown: '5' };

export function LauncherConfigPreview({ project, saved, onSave, onClose }: {
    project: string;
    saved: ModConfig;
    onSave: (values: ModConfig) => void;
    onClose: () => void;
}) {
    const [draft, setDraft] = useState(saved);
    const [category, setCategory] = useState('All settings');
    const [search, setSearch] = useState('');
    const [status, setStatus] = useState('');
    const dialog = useRef<HTMLDivElement>(null);
    const titleId = useId();
    const dirty = settings.some(({ key }) => saved[key] !== draft[key]);
    const invalid = !draft.cooldown.trim() || !Number.isFinite(Number(draft.cooldown)) || Number(draft.cooldown) < 0;
    useEffect(() => {
        const trigger = document.activeElement as HTMLElement | null;
        dialog.current?.focus();
        return () => trigger?.focus();
    }, []);
    const close = () => {
        if (dirty || invalid) setStatus('Save or reset your changes before closing.');
        else onClose();
    };
    const save = () => {
        if (!dirty || invalid) return;
        onSave({ ...draft });
        setStatus('Changes saved. Ready for your next game.');
    };
    const visible = settings.filter(setting => search.trim()
        ? `${setting.name} ${setting.category}`.toLowerCase().includes(search.trim().toLowerCase())
        : category === 'All settings' || category === setting.category);
    return <div className="llp-config-overlay">
        <div className="llp-config" ref={dialog} role="dialog" aria-modal="true" aria-labelledby={titleId} tabIndex={-1} onKeyDown={event => {
            if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); close(); }
            if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') { event.preventDefault(); save(); }
            if (event.key === 'Tab') {
                const controls = Array.from(dialog.current!.querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled)'));
                const first = controls[0];
                const last = controls[controls.length - 1];
                if (event.shiftKey && (document.activeElement === first || document.activeElement === dialog.current)) { event.preventDefault(); last?.focus(); }
                else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
            }
        }}>
            <div className="llp-panel-heading"><SlidersHorizontal size={16} /><h3 id={titleId}>{project}</h3><button type="button" className="llp-button" onClick={close}>Done</button></div>
            <div className="llp-settings-body">
                <nav className="llp-categories" aria-label="Settings categories">
                    {['All settings', 'General', 'Display & sounds'].map(name => <button type="button" key={name} aria-pressed={category === name} onClick={() => { setCategory(name); setSearch(''); }}>
                        {name}<span>{settings.filter(setting => name === 'All settings' || setting.category === name).length}</span>
                    </button>)}
                </nav>
                <div className="llp-settings-content">
                    <label className="llp-search"><Search size={14} /><input aria-label={`Search ${project} settings`} placeholder="Search settings…" value={search} onChange={event => setSearch(event.target.value)} /></label>
                    <h4>{search.trim() ? 'Search results' : category}</h4>
                    <div className="llp-settings-card">
                        {visible.map(setting => <label className="llp-setting" key={setting.key}>
                            <strong>{setting.name}</strong>
                            {setting.key === 'cooldown'
                                ? <input type="number" min="0" step="any" value={draft.cooldown} aria-invalid={invalid} onChange={event => { setDraft({ ...draft, cooldown: event.target.value }); setStatus(''); }} />
                                : <input type="checkbox" role="switch" className="llp-toggle" checked={draft[setting.key]} onChange={event => { setDraft({ ...draft, [setting.key]: event.target.checked }); setStatus(''); }} />}
                        </label>)}
                    </div>
                    {!visible.length && <p className="llp-empty">No matching settings. Try another search.</p>}
                </div>
            </div>
            <div className="llp-settings-footer">
                <p role="status" className={invalid ? 'llp-error' : ''}>{invalid ? 'Enter a number of zero or more.' : status || (dirty ? 'Unsaved changes' : 'Sample mod settings · Preview only')}</p>
                <div><button type="button" className="llp-button" disabled={!dirty} onClick={() => { setDraft({ ...saved }); setStatus('Changes reset.'); }}>Reset changes</button>
                    <button type="button" className="llp-button llp-primary" disabled={!dirty || invalid} onClick={save}>Save changes</button></div>
            </div>
        </div>
    </div>;
}
