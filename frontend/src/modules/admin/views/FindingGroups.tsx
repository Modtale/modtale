import { useLayoutEffect, useMemo, useRef, useState } from 'react';
import type { ScanIssue } from '@/types';

type Props = { issues: ScanIssue[]; selected: string | null; onSelect: (type: string | null) => void };
const ranks = new Map([['CRITICAL', 4], ['HIGH', 3], ['MEDIUM', 2], ['LOW', 1]]);
const severityRank = (value: string) => ranks.get(value) ?? 5;

export function FindingGroups({ issues, selected, onSelect }: Props) {
    const [page, setPage] = useState(0);
    const summary = useRef<HTMLElement>(null);
    const pageSummary = useRef<HTMLParagraphElement>(null);
    const focusPage = useRef(false);
    const groups = useMemo(() => {
        const byType = new Map<string, { type: string; total: number; seen: number; always: number; severity: string; files: Set<string> }>();
        for (const issue of issues) {
            let group = byType.get(issue.type);
            if (!group) {
                group = { type: issue.type, total: 0, seen: 0, always: 0, severity: issue.severity, files: new Set() };
                byType.set(issue.type, group);
            }
            group.total++;
            group.seen += Number(!!issue.knownIssue);
            group.always += Number(issue.reviewCadence?.toUpperCase() === 'ALWAYS');
            if (severityRank(issue.severity) > severityRank(group.severity)) group.severity = issue.severity;
            group.files.add(issue.filePath);
        }
        return [...byType.values()].sort((a, b) => severityRank(b.severity) - severityRank(a.severity)
            || b.always - a.always || b.total - a.total || a.type.localeCompare(b.type));
    }, [issues]);
    const pages = Math.max(1, Math.ceil(groups.length / 12));
    const visiblePage = Math.min(page, pages - 1);
    useLayoutEffect(() => {
        if (focusPage.current) { focusPage.current = false; pageSummary.current?.focus(); }
    }, [visiblePage]);
    const changePage = (next: number) => { focusPage.current = true; setPage(next); };
    return <section aria-label="Finding groups" className="rounded-lg border border-slate-300 dark:border-slate-700 p-3 text-sm dark:text-slate-200">
        <details>
            <summary ref={summary} className="cursor-pointer font-bold">Finding groups ({groups.length} types)</summary>
            <p className="my-2 text-xs text-slate-600 dark:text-slate-300">Counts cover the whole scan. Previously seen and always-review counts can overlap. Grouping does not accept findings or verify a library's identity.</p>
            <p ref={pageSummary} tabIndex={-1} role="status" className="mb-2 text-xs">Finding groups {groups.length ? visiblePage * 12 + 1 : 0}–{Math.min((visiblePage + 1) * 12, groups.length)} of {groups.length}.</p>
            <div className="overflow-x-auto">
                <table className="w-full text-left text-xs" aria-label="Findings by type">
                    <thead><tr><th scope="col" className="p-2">Type</th><th scope="col" className="p-2">Highest severity</th><th scope="col" className="p-2">Findings</th><th scope="col" className="p-2">Files</th><th scope="col" className="p-2">Previously seen</th><th scope="col" className="p-2">Always review</th><th scope="col" className="p-2">View</th></tr></thead>
                    <tbody>{groups.slice(visiblePage * 12, (visiblePage + 1) * 12).map(group => <tr key={group.type} className="border-t border-slate-200 dark:border-slate-700">
                        <th scope="row" className="p-2 break-all">{group.type}</th><td className="p-2">{group.severity}</td><td className="p-2">{group.total}</td><td className="p-2">{group.files.size}</td><td className="p-2">{group.seen}</td><td className="p-2">{group.always}</td>
                        <td className="p-2"><button type="button" aria-label={`Show ${group.type} findings`} aria-pressed={selected === group.type} onClick={() => onSelect(group.type)} className="font-bold text-indigo-600 dark:text-indigo-300">Show</button></td>
                    </tr>)}</tbody>
                </table>
            </div>
            {pages > 1 && <nav aria-label="Finding group pages" className="mt-2 flex gap-3">
                <button type="button" disabled={visiblePage === 0} onClick={() => changePage(visiblePage - 1)}>Previous groups</button>
                <span>Group page {visiblePage + 1} of {pages}</span>
                <button type="button" disabled={visiblePage + 1 >= pages} onClick={() => changePage(visiblePage + 1)}>Next groups</button>
            </nav>}
        </details>
        {selected !== null && <p className="mt-2 break-all">Selected type: {selected}. <button type="button" onClick={() => { onSelect(null); summary.current?.focus(); }} className="font-bold text-indigo-600 dark:text-indigo-300">Show all types</button></p>}
    </section>;
}
