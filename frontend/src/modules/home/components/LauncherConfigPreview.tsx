import React, { useState } from 'react';
import { Search, SlidersHorizontal } from 'lucide-react';

const settings = [
    { key: 'pvp', name: 'PvP', category: 'General', initial: false },
    { key: 'fall', name: 'Fall damage', category: 'General', initial: true },
    { key: 'pause', name: 'Pause day / night cycle', category: 'General', initial: false },
    { key: 'death', name: 'Inventory penalty on death', category: 'Death', initial: '', hint: 'Partial drop applies to resources marked to drop on death.' },
    { key: 'loss', name: 'Resource loss on death (%)', category: 'Death', initial: '', hint: 'Leave blank to use the gameplay default.' },
];
type Values = Record<string, string | boolean>;
const defaults: Values = Object.fromEntries(settings.map(setting => [setting.key, setting.initial]));

export function LauncherConfigPreview({ world }: { world: string }) {
    const [saved, setSaved] = useState<Values>(defaults);
    const [draft, setDraft] = useState<Values>(defaults);
    const [category, setCategory] = useState('All settings');
    const [search, setSearch] = useState('');
    const [status, setStatus] = useState('');
    const dirty = settings.some(({ key }) => saved[key] !== draft[key]);
    const loss = String(draft.loss);
    const invalid = loss !== '' && (!Number.isFinite(Number(loss)) || Number(loss) < 0 || Number(loss) > 100);
    const visible = settings.filter(setting => search.trim()
        ? `${setting.name} ${setting.category}`.toLowerCase().includes(search.trim().toLowerCase())
        : category === 'All settings' || category === setting.category);
    return <div className="llp-config">
        <div className="llp-panel-heading"><SlidersHorizontal size={16} /><h3>{world} — World settings</h3></div>
        <div className="llp-settings-body">
            <nav className="llp-categories" aria-label="Settings categories">
                {['All settings', 'General', 'Death'].map(name => <button type="button" key={name} aria-pressed={category === name} onClick={() => { setCategory(name); setSearch(''); }}>
                    {name}<span>{settings.filter(setting => name === 'All settings' || setting.category === name).length}</span>
                </button>)}
            </nav>
            <div className="llp-settings-content">
                <label className="llp-search"><Search size={14} /><input aria-label={`Search ${world} settings`} placeholder="Search settings…" value={search} onChange={event => setSearch(event.target.value)} /></label>
                <h4>{search.trim() ? 'Search results' : category}</h4>
                <div className="llp-settings-card">
                    {visible.map(setting => <label className="llp-setting" key={setting.key}>
                        <span><strong>{setting.name}</strong>{setting.hint && <small>{setting.hint}</small>}</span>
                        {typeof setting.initial === 'boolean'
                            ? <input type="checkbox" role="switch" className="llp-toggle" checked={Boolean(draft[setting.key])} onChange={event => { setDraft({ ...draft, [setting.key]: event.target.checked }); setStatus(''); }} />
                            : setting.key === 'death'
                                ? <select value={String(draft.death)} onChange={event => { setDraft({ ...draft, death: event.target.value }); setStatus(''); }}><option value="">Gameplay default</option><option value="None">Keep inventory</option><option value="Configured">Partial drop</option><option value="All">Drop everything</option></select>
                                : <input type="number" min="0" max="100" step="any" placeholder="Default" value={loss} aria-invalid={invalid} onChange={event => { setDraft({ ...draft, loss: event.target.value }); setStatus(''); }} />}
                    </label>)}
                </div>
                {!visible.length && <p className="llp-empty">No matching settings. Try another search.</p>}
            </div>
        </div>
        <div className="llp-settings-footer">
            <p role="status" className={invalid ? 'llp-error' : ''}>{invalid ? 'Enter a value from 0 to 100.' : status || (dirty ? 'Unsaved changes' : 'Try editing your world settings.')}</p>
            <div><button type="button" className="llp-button" disabled={!dirty} onClick={() => { setDraft({ ...saved }); setStatus('Changes reset.'); }}>Reset changes</button>
                <button type="button" className="llp-button llp-primary" disabled={!dirty || invalid} onClick={() => { setSaved({ ...draft }); setStatus('Changes saved. Ready for your next game.'); }}>Save changes</button></div>
        </div>
    </div>;
}
