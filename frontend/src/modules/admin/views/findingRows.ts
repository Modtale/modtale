import type { ScanIssue } from '@/types';

export type IndexedFinding = { issue: ScanIssue; originalIndex: number };
export type FindingRow = { kind: 'finding'; finding: IndexedFinding }
    | { kind: 'archive'; archive: string; findings: IndexedFinding[] };

export function findingRows(findings: IndexedFinding[], minimumGroupSize = 10): FindingRow[] {
    const byArchive = new Map<string, IndexedFinding[]>();
    for (const finding of findings) {
        const marker = finding.issue.filePath.indexOf('!/');
        if (marker < 0 || !finding.issue.filePath.slice(0, marker).toLowerCase().endsWith('.jar')) continue;
        const archive = finding.issue.filePath.slice(0, marker);
        const group = byArchive.get(archive) || [];
        group.push(finding);
        byArchive.set(archive, group);
    }
    const emitted = new Set<string>();
    const rows: FindingRow[] = [];
    for (const finding of findings) {
        const marker = finding.issue.filePath.indexOf('!/');
        const archive = marker < 0 ? '' : finding.issue.filePath.slice(0, marker);
        const group = byArchive.get(archive);
        if (group && group.length >= minimumGroupSize) {
            if (!emitted.has(archive)) rows.push({ kind: 'archive', archive, findings: group });
            emitted.add(archive);
        } else rows.push({ kind: 'finding', finding });
    }
    return rows;
}
