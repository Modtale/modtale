import React, { useEffect, useRef, useState } from 'react';
import { adminClient } from '../api/adminClient';
import { extractApiErrorMessage } from '@/utils/api';

type Key = { projectId: string; versionId: string };
export interface DependencyInspectionResult {
    reviewToken: string;
    artifactBytesVerified: boolean;
    inventory: {
        root: Key;
        identity: string | null;
        nodes: (Key & { versionNumber: string; artifactSha256: string })[];
        edges: { from: Key; declaration: number; dependency: { source: string; type: string; reference: { projectId: string; versionNumber: string } }; target: Key | null }[];
        gaps: { from: Key; reference: { projectId: string; versionNumber: string }; reason: string }[];
    };
}
const reasons: Record<string, string> = {
    MISSING: 'Pinned version not found', AMBIGUOUS: 'Version identity is ambiguous', UNAVAILABLE: 'Record unavailable',
    EXTERNAL: 'External dependency requires inspection', CYCLE: 'Dependency cycle', DEPTH_LIMIT: 'Dependency depth limit reached',
    NODE_LIMIT: 'Version limit reached', EDGE_LIMIT: 'Declaration limit reached', READ_LIMIT: 'Lookup limit reached',
    CHANGED: 'Record changed during inspection', INVALID_IDENTITY: 'Artifact identity mismatch',
    UNRESOLVED_CONTEXT: 'Runtime context is unresolved', SUPPLEMENTAL_CONTENT: 'Separate content requires inspection',
};
export function DependencyInspection({ projectId, versionId, reviewToken }: { projectId: string; versionId: string; reviewToken: string }) {
    const [result, setResult] = useState<DependencyInspectionResult | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const generation = useRef(0);
    useEffect(() => {
        generation.current++; setResult(null); setLoading(false); setError('');
        return () => { generation.current++; };
    }, [projectId, versionId, reviewToken]);
    const load = async () => {
        const request = ++generation.current; setResult(null); setError(''); setLoading(true);
        try {
            const next = await adminClient.getDependencyInspection(projectId, versionId, reviewToken);
            if (next.reviewToken !== reviewToken || next.inventory.root.projectId !== projectId || next.inventory.root.versionId !== versionId)
                throw new Error('Dependency inspection no longer matches this review. Refresh its evidence.');
            if (request === generation.current) setResult(next);
        } catch (failure) {
            if (request === generation.current) setError(extractApiErrorMessage(failure, 'Dependency inspection is unavailable.'));
        } finally { if (request === generation.current) setLoading(false); }
    };
    return <section aria-label="Dependency inventory" className="rounded-2xl border border-slate-200 dark:border-slate-700 p-5 space-y-4">
        <div className="flex items-center justify-between gap-3">
            <h4 className="font-bold dark:text-white">Dependency inventory</h4>
            <button type="button" onClick={load} disabled={loading} className="text-sm font-semibold text-indigo-600 dark:text-indigo-400 disabled:opacity-50">
                {loading ? 'Inspecting dependencies…' : result ? 'Refresh dependencies' : 'Inspect dependencies'}
            </button>
        </div>
        <p className="text-sm text-slate-500">Shows declared versions and recorded artifact hashes. Content verification and security approval are separate.</p>
        {error && <p role="alert" className="text-sm text-red-600 dark:text-red-400">{error}</p>}
        {result && <>
            <p role="status" className="text-sm dark:text-slate-200">{result.inventory.nodes.length} versions recorded · {result.inventory.edges.length} declarations · {result.inventory.gaps.length} unresolved items</p>
            {result.inventory.gaps.length > 0 && <ul aria-label="Unresolved dependency evidence" className="max-h-60 overflow-auto space-y-2 text-sm text-amber-700 dark:text-amber-300">
                {result.inventory.gaps.map((gap, index) => <li key={index} className="break-all">{gap.reference.projectId} / {gap.reference.versionNumber}: {reasons[gap.reason] || 'Unresolved dependency evidence'}</li>)}
            </ul>}
            <div className="max-h-72 overflow-auto">
                <table className="w-full text-left text-xs dark:text-slate-200">
                    <caption className="text-left py-2 font-semibold">Recorded versions, including the uploaded version</caption>
                    <thead><tr><th scope="col">Project</th><th scope="col">Version</th><th scope="col">Recorded SHA-256</th></tr></thead>
                    <tbody>{result.inventory.nodes.map(node => <tr key={JSON.stringify([node.projectId, node.versionId])}>
                        <td className="py-2 break-all">{node.projectId}</td><td className="px-2 break-all">{node.versionNumber}</td><td className="break-all font-mono">{node.artifactSha256}</td>
                    </tr>)}</tbody>
                </table>
            </div>
            <details><summary className="cursor-pointer text-sm dark:text-slate-200">Dependency declarations ({result.inventory.edges.length})</summary>
                <ul className="max-h-60 overflow-auto text-xs space-y-2 mt-3 dark:text-slate-200">
                    {result.inventory.edges.map((edge, index) => <li key={index} className="break-all">{edge.from.projectId} / {edge.from.versionId} → {edge.dependency.reference.projectId} / {edge.dependency.reference.versionNumber} ({edge.dependency.type.toLowerCase()}, {edge.dependency.source.toLowerCase()})</li>)}
                </ul>
            </details>
        </>}
    </section>;
}
