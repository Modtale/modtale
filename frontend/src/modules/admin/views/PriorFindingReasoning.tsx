import { useEffect, useRef, useState } from 'react';
import type { ScanIssue } from '@/types';
import { loadPriorFindingReasoning, type PriorFindingReasoning as Result } from '../api/findingReviews';
import { extractApiErrorMessage } from '@/utils/api';

type Props = { projectId: string; versionId: string; token: string; issues: ScanIssue[]; sources: { id: string; versionNumber: string }[]; issueIndex?: number; sourceVersionId?: string; autoLoad?: boolean };
export function PriorFindingReasoning({ projectId, versionId, token, issues, sources, issueIndex, sourceVersionId, autoLoad = false }: Props) {
    const [source, setSource] = useState(sourceVersionId || sources[0]?.id || '');
    const [selectedIssue, setIssue] = useState(0);
    const issue = issueIndex ?? selectedIssue;
    const [result, setResult] = useState<Result | null>(null);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const generation = useRef(0);
    useEffect(() => {
        generation.current++; setResult(null); setBusy(false); setError('');
        return () => { generation.current++; };
    }, [projectId, versionId, token, source, issue]);
    async function load() {
        const request = ++generation.current;
        setResult(null); setError(''); setBusy(true);
        try {
            const next = await loadPriorFindingReasoning(projectId, versionId, source, issue, token);
            if (next.reviewToken !== token || next.sourceVersionId !== source) throw new Error('The review changed. Refresh its evidence.');
            if (request === generation.current) setResult(next);
        } catch (failure) {
            if (request === generation.current) setError(extractApiErrorMessage(failure, 'Verified prior reasoning is unavailable.'));
        } finally { if (request === generation.current) setBusy(false); }
    }
    useEffect(() => {
        if (autoLoad && token && sources.some(v => v.id === source) && issue >= 0 && issue < issues.length) void load();
    }, [autoLoad, projectId, versionId, token, source, issue]);
    if (!sources.length || !issues.length) return null;
    return <section aria-label="Earlier finding reasoning" className="mt-4 rounded-lg border border-slate-300 dark:border-slate-700 p-4 space-y-3">
        <h4 className="font-bold text-sm">Earlier finding reasoning</h4>
        <p className="text-sm text-slate-500">Consult the explanation for an exact repeated finding in an approved version. Changed callers, resources and context still need current review.</p>
        <label className="block text-sm">Approved version<select aria-label="Earlier approved version" value={source} onChange={e => setSource(e.target.value)} className="block w-full p-2 text-slate-900">
            {sources.map(v => <option key={v.id} value={v.id}>{v.versionNumber}</option>)}
        </select></label>
        {issueIndex === undefined && <label className="block text-sm">Finding<select aria-label="Earlier reasoning finding" value={issue} onChange={e => setIssue(Number(e.target.value))} className="block w-full p-2 text-slate-900">
            {issues.map((finding, index) => <option key={index} value={index}>{index + 1}. {finding.type} — {finding.filePath}:{finding.lineStart}</option>)}
        </select></label>}
        <button type="button" disabled={busy || !token || !source} onClick={() => void load()} className="text-sm font-bold">{busy ? 'Loading earlier reasoning…' : 'Find earlier reasoning'}</button>
        {error && <p role="alert" className="text-sm text-red-600">{error}</p>}
        {result && <>
            {result.reviewReasons.map(reason => <p key={reason} className="text-sm text-amber-700 dark:text-amber-300">{reason}</p>)}
            {!result.decisions.length && <p className="text-sm">No verified acceptance matches this exact finding in the selected approval. This does not mean the finding is new or safe.</p>}
            {result.decisions.map(decision => <article key={decision.id} className="border-t pt-2 text-sm space-y-1">
                <p>Earlier acceptance · version {result.sourceVersion} · {decision.actorId} · {new Date(decision.createdAt).toLocaleString()}</p>
                <p className="break-all">{decision.finding.path}:{decision.finding.lineStart}</p>
                <p className="whitespace-pre-wrap">{decision.rationale}</p>
                <p className="text-xs">Scope: {decision.scope}. {decision.expiresAt <= Date.now() ? 'Expired.' : `Expires ${new Date(decision.expiresAt).toLocaleString()}.`} No current acceptance is granted by this display.</p>
            </article>)}
            {result.omitted > 0 && <p className="text-sm">{result.omitted} additional matches omitted. Inspect the source version’s decision history for the full record.</p>}
        </>}
    </section>;
}
