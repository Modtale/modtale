import { useState } from 'react';
import type { ScanIssue } from '@/types';
import { findingReviews, type FindingDecision, type DecisionAssessment } from '../api/findingReviews';
import { extractApiErrorMessage } from '@/utils/api';

type Props = { projectId: string; versionId: string; token?: string; issues: ScanIssue[]; canDecide: boolean; onSaved: () => void };
export function FindingDecisions({ projectId, versionId, token, issues, canDecide, onSaved }: Props) {
    const [open, setOpen] = useState(false);
    const [events, setEvents] = useState<FindingDecision[]>([]);
    const [assessments, setAssessments] = useState<Record<string, DecisionAssessment>>({});
    const [assessedAt, setAssessedAt] = useState<number>();
    const [showApplicable, setShowApplicable] = useState(false);
    const [next, setNext] = useState<number | null>(null);
    const [issueIndex, setIssueIndex] = useState(0);
    const [disposition, setDisposition] = useState<'ACCEPT' | 'REQUIRE_REVIEW'>('REQUIRE_REVIEW');
    const [rationale, setRationale] = useState('');
    const [busy, setBusy] = useState(false);
    const [saved, setSaved] = useState(false);
    const [error, setError] = useState('');
    async function load(offset = 0) {
        if (!token) { setError('Reopen this review to load its current evidence.'); return; }
        setBusy(true); setError('');
        try {
            const result = await findingReviews.history(projectId, versionId, token, offset);
            setEvents(old => offset ? [...old, ...result.events] : result.events);
            setAssessments(result.assessments ?? {});
            setAssessedAt(result.assessedAt); setNext(result.nextOffset); setOpen(true);
        } catch (e) { setError(extractApiErrorMessage(e, 'Could not load decision history.')); }
        finally { setBusy(false); }
    }
    async function save(revokedId?: string) {
        if (!token || !canDecide || saved || rationale.trim().length < 10) return;
        setBusy(true); setError('');
        try {
            const event = revokedId
                ? await findingReviews.revoke(projectId, versionId, token, revokedId, rationale)
                : await findingReviews.record(projectId, versionId, token, issueIndex, disposition, rationale);
            setEvents(old => [event, ...old]); setSaved(true); onSaved();
        } catch (e) { setError(extractApiErrorMessage(e, 'Could not save the decision. Your explanation has been kept.')); }
        finally { setBusy(false); }
    }
    const superseded = new Set(events.map(event => event.supersedesDecisionId).filter(Boolean));
    const revoked = new Set(events.map(event => event.revokedDecisionId).filter(Boolean));
    const retained = (event: FindingDecision) => !saved && !revoked.has(event.id) && !superseded.has(event.id) && assessments[event.id]?.state === 'APPLICABLE' && event.expiresAt > Date.now();
    const retainedCount = events.filter(retained).length;
    const visible = events.filter(event => showApplicable || !retained(event));
    return <section className="mt-4 rounded-lg border border-slate-300 dark:border-slate-700 p-4 space-y-3">
        <button type="button" disabled={busy || saved} onClick={() => open ? setOpen(false) : void load()} aria-expanded={open} className="font-bold text-sm">{open ? 'Hide finding decisions' : 'Finding decisions and history'}</button>
        {error && <p role="alert" className="text-sm text-red-600">{error}</p>}
        {saved && <p role="status" className="text-sm">Decision saved. Close and reopen this review before another decision or publication.</p>}
        {open && <>
            <p className="text-sm text-slate-500">These decisions retain your reasoning for the inspected artifact and context. Recording or revoking a decision pauses automatic publication and requires a current moderator review. It does not grant security clearance.</p>
            {canDecide && <div className="space-y-2">
                {issues.length > 0 && <>
                    <label className="block text-sm">Finding<select aria-label="Finding" className="block w-full p-2 text-slate-900" value={issueIndex} onChange={e => setIssueIndex(Number(e.target.value))} disabled={busy || saved}>
                        {issues.map((issue, index) => <option key={index} value={index}>{index + 1}. {issue.type} — {issue.filePath}:{issue.lineStart}</option>)}
                    </select></label>
                    <label className="block text-sm">Conclusion<select aria-label="Conclusion" className="block w-full p-2 text-slate-900" value={disposition} onChange={e => setDisposition(e.target.value as typeof disposition)} disabled={busy || saved}>
                        <option value="REQUIRE_REVIEW">Further review required</option><option value="ACCEPT">Accept for this artifact and context</option>
                    </select></label>
                </>}
                <label className="block text-sm">Reasoning<textarea aria-label="Decision reasoning" className="block w-full p-2 text-slate-900" maxLength={4000} value={rationale} onChange={e => setRationale(e.target.value)} disabled={busy || saved} /></label>
                <p className="text-xs text-slate-500">Explain the evidence in 10–4000 characters. New acceptances expire after 30 days. Use this explanation for recording or revoking a decision.</p>
                {issues.length > 0 && <button type="button" disabled={busy || saved || !token || rationale.trim().length < 10} onClick={() => void save()} className="text-sm font-bold">Record decision</button>}
            </div>}
            {!events.length && <p className="text-sm">No recorded finding decisions.</p>}
            {assessedAt && <p className="text-xs text-slate-500">Assessed against this version at {new Date(assessedAt).toLocaleString()}. This comparison does not authorize publication.</p>}
            {retainedCount > 0 && <button type="button" aria-expanded={showApplicable} onClick={() => setShowApplicable(value => !value)}>{showApplicable ? 'Hide' : 'Show'} retained reasoning ({retainedCount})</button>}
            <ol className="space-y-3">{visible.map(event => <li key={event.id} className="border-t pt-2 text-sm">
                <p>{event.disposition === 'ACCEPT' ? 'Accepted for inspected artifact' : event.disposition === 'REVOKE' ? 'Decision revoked' : 'Further review required'} · {event.actorId} · {new Date(event.createdAt).toLocaleString()}</p>
                <p className="text-xs font-medium">{saved ? 'Reopen the review to reassess the updated history.' : assessments[event.id]?.state === 'APPLICABLE' && event.expiresAt <= Date.now() ? 'This acceptance has expired since the assessment.' : assessments[event.id]?.explanation ?? 'Current applicability has not been verified.'}</p>
                <p className="break-all">{event.finding.path}:{event.finding.lineStart}</p><p>{event.rationale}</p>
                {event.disposition !== 'REVOKE' && <p className="text-xs">{revoked.has(event.id) ? 'Revoked' : superseded.has(event.id) ? 'Superseded' : event.expiresAt === 0 ? 'No automatic expiry' : event.expiresAt <= Date.now() ? 'Expired' : `Expires ${new Date(event.expiresAt).toLocaleString()}`}</p>}
                {canDecide && event.disposition !== 'REVOKE' && !revoked.has(event.id) && !superseded.has(event.id) && <button type="button" disabled={busy || saved || rationale.trim().length < 10} onClick={() => void save(event.id)} className="font-bold">Revoke decision</button>}
            </li>)}</ol>
            {next !== null && <button type="button" disabled={busy || saved} onClick={() => void load(next)}>Older decisions</button>}
        </>}
    </section>;
}
