import React, { useEffect, useState } from 'react';
import { ArrowRight, Check, Download, RefreshCw } from 'lucide-react';
import type { Project } from '@/types';

export function LauncherUpdatesPreview({ mods, loading }: { mods: Project[]; loading: boolean }) {
    const [installed, setInstalled] = useState<string[]>([]);
    const [updating, setUpdating] = useState<string | null>(null);
    const [progress, setProgress] = useState(0);
    const [checking, setChecking] = useState(false);
    const [checked, setChecked] = useState(false);
    useEffect(() => {
        if (!updating) return;
        const timer = window.setInterval(() => setProgress(value => Math.min(100, value + 20)), 220);
        return () => window.clearInterval(timer);
    }, [updating]);
    useEffect(() => {
        if (progress !== 100 || !updating) return;
        setInstalled(current => [...current, updating]);
        setUpdating(null);
    }, [progress, updating]);
    useEffect(() => {
        if (!checking) return;
        const timer = window.setTimeout(() => { setChecking(false); setChecked(true); }, 800);
        return () => window.clearTimeout(timer);
    }, [checking]);
    const candidates = mods.slice(0, 2);
    const pending = candidates.filter(mod => !installed.includes(mod.id));
    return <div className="llp-updates">
        <div className="llp-panel-heading"><Download size={16} /><h3>Updates</h3><button type="button" className="llp-button" disabled={checking || !!updating || loading || !mods.length} onClick={() => { setChecking(true); setChecked(false); }}><RefreshCw size={12} className={checking ? 'llp-spin' : ''} />{checking ? 'Checking…' : 'Check for updates'}</button></div>
        <p className="llp-update-status" role="status">{loading && !mods.length ? 'Loading projects…' : !mods.length ? 'Projects are unavailable right now.' : checking ? 'Comparing your library with the catalog…' : pending.length ? `${pending.length} ${pending.length === 1 ? 'update' : 'updates'} available${checked ? ' · Just checked' : ''}` : 'Your library is up to date.'}</p>
        <div className="llp-update-list">{candidates.map((mod, index) => {
            const done = installed.includes(mod.id);
            const busy = updating === mod.id;
            return <div className="llp-update-card" key={mod.id}>
                <div className="llp-update-row"><div className="llp-mod-copy"><strong>{mod.title}</strong><span>Demo version · {done ? `1.${index + 1}.0` : <>1.{index}.0 <ArrowRight size={10} /> <b>1.{index + 1}.0</b></>}</span></div>
                    <button type="button" className={`llp-button ${done ? 'llp-complete' : 'llp-primary'}`} disabled={done || !!updating || checking} onClick={() => { setProgress(0); setUpdating(mod.id); }}>{done ? <Check size={12} /> : <Download size={12} />}{done ? 'Updated' : busy ? 'Updating…' : 'Update'}</button></div>
                {busy && <div className="llp-progress" role="progressbar" aria-label={`Updating ${mod.title}`} aria-valuemin={0} aria-valuemax={100} aria-valuenow={progress}><span style={{ width: `${progress}%` }} /></div>}
            </div>;
        })}</div>
        {mods.length > 0 && !pending.length && <div className="llp-updated"><Check size={24} /><strong>No updates queued</strong><span>Ready for your next adventure.</span></div>}
    </div>;
}
