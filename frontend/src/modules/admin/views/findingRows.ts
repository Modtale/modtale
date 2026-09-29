import type { ScanIssue } from '@/types';

export type IndexedFinding = { issue: ScanIssue; originalIndex: number };
export type FindingRow = { kind: 'finding'; finding: IndexedFinding }
    | { kind: 'archive'; archive: string; findings: IndexedFinding[] }
    | { kind: 'repeated-prior'; findings: IndexedFinding[] }
    | { kind: 'vetted-high'; findings: IndexedFinding[] }
    | { kind: 'indirect-package'; packagePath: string; findings: IndexedFinding[] };

function indirectPackagePath(path: string): string | null {
    if (!path.endsWith('.class') || path.includes('!/')) return null;
    // Presentation scope only. A package name is not evidence that code is third-party or safe.
    const prefixes = ['org/h2/', 'com/google/', 'com/mysql/', 'org/bson/', 'com/twelvemonkeys/', 'javassist/util/'];
    const known = prefixes.find(prefix => path.startsWith(prefix));
    if (known) return known.slice(0, -1);
    const marker = path.indexOf('/libs/');
    if (marker < 0) return null;
    const next = path.indexOf('/', marker + '/libs/'.length);
    return next < 0 ? null : path.slice(0, next);
}

export function findingRows(findings: IndexedFinding[], minimumGroupSize = 10, repeatedPriorEligible?: (issue: ScanIssue) => boolean,
    approvedBaselineVersion?: string, groupIndirect = false): FindingRow[] {
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
    const vettedHigh = repeatedPriorEligible && approvedBaselineVersion ? findings.filter(({ issue }) => repeatedPriorEligible(issue)
        && issue.knownIssue && issue.historicalFileEvidenceIdentical === true && issue.resolved === true
        && issue.severity === 'HIGH' && !issue.escalated && issue.reviewCadence?.toUpperCase() === 'WHEN_CHANGED'
        && issue.baselineVersion === approvedBaselineVersion
        && !(issue.filePath.includes('!/')
            && (byArchive.get(issue.filePath.slice(0, issue.filePath.indexOf('!/')))?.length || 0) >= minimumGroupSize)) : [];
    const groupedHigh = vettedHigh.length >= minimumGroupSize ? new Set(vettedHigh.map(finding => finding.originalIndex)) : new Set<number>();
    const byIndirectPackage = new Map<string, IndexedFinding[]>();
    const indirectPackageByIndex = new Map<number, string>();
    if (groupIndirect) for (const finding of findings) {
        const issue = finding.issue;
        if (groupedHigh.has(finding.originalIndex) || groupedPrior.has(finding.originalIndex)
            || issue.type !== 'IndirectInvocation' || !['MEDIUM', 'LOW'].includes(issue.severity)
            || issue.escalated || issue.reviewCadence?.toUpperCase() === 'ALWAYS') continue;
        const packagePath = indirectPackagePath(issue.filePath);
        if (!packagePath) continue;
        const group = byIndirectPackage.get(packagePath) || [];
        group.push(finding);
        byIndirectPackage.set(packagePath, group);
        indirectPackageByIndex.set(finding.originalIndex, packagePath);
    }
    const emitted = new Set<string>();
    const emittedIndirect = new Set<string>();
    let priorEmitted = false, highEmitted = false;
    const rows: FindingRow[] = [];
    for (const finding of findings) {
        if (groupedHigh.has(finding.originalIndex)) {
            if (!highEmitted) rows.push({ kind: 'vetted-high', findings: vettedHigh });
            highEmitted = true;
            continue;
        }
        if (groupedPrior.has(finding.originalIndex)) {
            if (!priorEmitted) rows.push({ kind: 'repeated-prior', findings: repeatedPrior });
            priorEmitted = true;
            continue;
        }
        if (groupIndirect) {
            const packagePath = indirectPackageByIndex.get(finding.originalIndex);
            if (packagePath) {
                const group = byIndirectPackage.get(packagePath);
                if (group && group.length >= minimumGroupSize) {
                    if (!emittedIndirect.has(packagePath)) rows.push({ kind: 'indirect-package', packagePath, findings: group });
                    emittedIndirect.add(packagePath);
                    continue;
                }
            }
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
