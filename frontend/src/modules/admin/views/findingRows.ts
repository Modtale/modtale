import type { ScanIssue } from '@/types';

export type IndexedFinding = { issue: ScanIssue; originalIndex: number };
export type FindingRow = { kind: 'finding'; finding: IndexedFinding }
    | { kind: 'archive'; archive: string; findings: IndexedFinding[] }
    | { kind: 'repeated-prior'; findings: IndexedFinding[] };

export function findingRows(findings: IndexedFinding[], minimumGroupSize = 10, repeatedPriorEligible?: (issue: ScanIssue) => boolean): FindingRow[] {
    const byArchive = new Map<string, IndexedFinding[]>();
    for (const finding of findings) {
        const marker = finding.issue.filePath.indexOf('!/');
        if (marker < 0 || !finding.issue.filePath.slice(0, marker).toLowerCase().endsWith('.jar')) continue;
        const archive = finding.issue.filePath.slice(0, marker);
        const group = byArchive.get(archive) || [];
        group.push(finding);
        byArchive.set(archive, group);
    }
    const repeatedPrior = repeatedPriorEligible ? findings.filter(({ issue }) => repeatedPriorEligible(issue) && issue.knownIssue
        && issue.historicalFileEvidenceIdentical === true && !issue.escalated
        && (issue.reviewCadence || '').toUpperCase() !== 'ALWAYS'
        && issue.severity !== 'HIGH' && issue.severity !== 'CRITICAL'
        && !(issue.filePath.includes('!/')
            && (byArchive.get(issue.filePath.slice(0, issue.filePath.indexOf('!/')))?.length || 0) >= minimumGroupSize)) : [];
    const groupedPrior = repeatedPrior.length >= minimumGroupSize ? new Set(repeatedPrior.map(finding => finding.originalIndex)) : new Set<number>();
    const emitted = new Set<string>();
    let priorEmitted = false;
    const rows: FindingRow[] = [];
    for (const finding of findings) {
        if (groupedPrior.has(finding.originalIndex)) {
            if (!priorEmitted) rows.push({ kind: 'repeated-prior', findings: repeatedPrior });
            priorEmitted = true;
            continue;
        }
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
