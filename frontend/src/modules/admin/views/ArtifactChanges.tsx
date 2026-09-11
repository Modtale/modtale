import React, { useEffect, useMemo, useRef, useState } from 'react';
import { adminClient } from '../api/adminClient';
import { extractApiErrorMessage } from '@/utils/api';

export interface ArtifactChangeSummary {
    baselineVersion: string | null;
    contextComparable: boolean;
    contextChanged: boolean;
    added: number;
    modified: number;
    removed: number;
    unchanged: number;
    files: { path: string; change: 'ADDED' | 'MODIFIED' | 'REMOVED' | 'UNCHANGED' }[];
}

export function ArtifactChanges({ projectId, version, onInspect }: {
    projectId: string;
    version: string;
    onInspect: (version: string, path: string) => void;
}) {
    const [result, setResult] = useState<ArtifactChangeSummary | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [showUnchanged, setShowUnchanged] = useState(false);
    const [search, setSearch] = useState('');
    const [limit, setLimit] = useState(100);
    const generation = useRef(0);
    useEffect(() => {
        generation.current++;
        setResult(null); setLoading(false); setError(''); setSearch(''); setLimit(100); setShowUnchanged(false);
        return () => { generation.current++; };
    }, [projectId, version]);
    const load = async () => {
        const request = ++generation.current;
        setLoading(true); setError('');
        try {
            const next = await adminClient.getArtifactChanges(projectId, version);
            if (request === generation.current) setResult(next);
        } catch (failure) {
            if (request === generation.current) setError(extractApiErrorMessage(failure, 'Artifact comparison is unavailable.'));
        } finally {
            if (request === generation.current) setLoading(false);
        }
    };
    const visible = useMemo(() => (result?.files || []).filter(file =>
        (showUnchanged || file.change !== 'UNCHANGED') && file.path.toLowerCase().includes(search.toLowerCase())), [result, showUnchanged, search]);
    return <section className="rounded-2xl border border-slate-200 dark:border-slate-700 p-5 space-y-4" aria-label="Changes since approval">
        <div className="flex flex-wrap items-center justify-between gap-3">
            <h4 className="font-bold dark:text-white">Changes since approval</h4>
            <button type="button" onClick={load} disabled={loading} className="text-sm font-semibold text-indigo-600 dark:text-indigo-400 disabled:opacity-50">
                {loading ? 'Comparing artifact contents…' : result ? 'Refresh comparison' : 'Compare with approved version'}
            </button>
        </div>
        {error && <p role="alert" className="text-sm text-red-600 dark:text-red-400">{error}</p>}
        {result && !result.baselineVersion && <p className="text-sm text-slate-500">No previously approved version is available for comparison.</p>}
        {result?.baselineVersion && <>
            <p className="text-sm text-slate-500">Compared with approved version {result.baselineVersion}, using the contents of both stored artifacts.</p>
            <div className="flex flex-wrap gap-3 text-sm dark:text-slate-200">
                <span>{result.added} added</span><span>{result.modified} modified</span><span>{result.removed} removed</span><span>{result.unchanged} unchanged</span>
            </div>
            {(!result.contextComparable || result.contextChanged) && <p className="text-sm text-amber-700 dark:text-amber-300">
                {result.contextChanged ? 'Version metadata changed and needs review.' : 'Separate files or dependency metadata need additional comparison.'}
            </p>}
            <div className="flex flex-wrap items-center gap-4">
                <input aria-label="Filter changed files" value={search} onChange={event => { setSearch(event.target.value); setLimit(100); }} placeholder="Filter paths…" className="min-w-0 rounded-lg border border-slate-200 dark:border-slate-700 bg-transparent px-3 py-2 text-sm dark:text-white" />
                <label className="flex items-center gap-2 text-sm text-slate-500"><input type="checkbox" checked={showUnchanged} onChange={event => { setShowUnchanged(event.target.checked); setLimit(100); }} />Show unchanged files</label>
            </div>
            <div className="max-h-72 overflow-auto divide-y divide-slate-100 dark:divide-slate-800">
                {visible.slice(0, limit).map(file => <button key={file.path} type="button" onClick={() => onInspect(file.change === 'REMOVED' ? result.baselineVersion! : version, file.path)} className="w-full flex items-start gap-3 py-2 text-left hover:bg-slate-50 dark:hover:bg-slate-800">
                    <span className="w-20 shrink-0 text-xs text-slate-500">{file.change.toLowerCase()}</span>
                    <span className="min-w-0 break-all font-mono text-xs text-indigo-600 dark:text-indigo-300">{file.path}</span>
                </button>)}
                {visible.length === 0 && <p className="py-2 text-sm text-slate-500">No files match this view.</p>}
            </div>
            {visible.length > limit && <button type="button" onClick={() => setLimit(value => value + 100)} className="text-sm text-indigo-600 dark:text-indigo-400">Show more files ({visible.length - limit} remaining)</button>}
        </>}
    </section>;
}
