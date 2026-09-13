import { loadPriorFindingReasoning } from '@/modules/admin/api/findingReviews';
vi.mock('@/modules/admin/api/findingReviews', () => ({ loadPriorFindingReasoning: vi.fn() }));
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Review } from '@/modules/admin/views/Review';
vi.mock('@/modules/admin/views/FindingDecisions', () => ({ FindingDecisions: ({ onSaved }: any) => <button onClick={onSaved}>Save finding reasoning</button> }));
vi.mock('@/components/ui/ModalPortal', () => ({ ModalPortal: ({ children }: any) => children }));
vi.mock('@/modules/admin/api/adminClient', () => ({ adminClient: { publishProject: vi.fn().mockResolvedValue(null), getReviewDetails: vi.fn(), getStructure: vi.fn(), getArtifactChanges: vi.fn(), getFileWindow: vi.fn() } }));
import { adminClient } from '@/modules/admin/api/adminClient';

const clear = { status: 'CLEAN', verdict: 'AUTO_APPROVE', scanState: 'COMPLETED', issues: [],
    securityEvidence: { complete: true, clearanceGranted: true, policyVersion: 'warden-3.0.0:' + 'c'.repeat(64), artifactSha256: 'a'.repeat(64), contentSha256: 'b'.repeat(64), entryHashes: {} } };
describe('Review security clearance status', () => {
    let container: HTMLDivElement; let root: Root;
    beforeEach(() => { container = document.createElement('div'); document.body.appendChild(container); root = createRoot(container); });
    afterEach(async () => { await act(async () => root.unmount()); container.remove(); });
    async function render(scanResult: any, decision = false, token?: string, steps?: number, sources: any[] = []) {
        const project = { mod: { id: 'project', slug: 'project', title: 'Example', status: decision ? 'PENDING' : 'PUBLISHED', reviewToken: token, classification: 'PLUGIN', tags: [],
            versions: [{ id: 'version', versionNumber: '1.0', reviewStatus: 'PENDING', reviewToken: 'old-version', scanResult }, ...sources] } };
        await act(async () => root.render(<Review reviewingProject={project} onClose={vi.fn()} onApprove={vi.fn()} onReject={vi.fn()} setStatus={vi.fn()} canDecide={decision} />));
        for (let step = 0; step < (steps ?? (decision ? 4 : 2)); step++) {
            await act(async () => container.querySelectorAll<HTMLInputElement>('input[type=checkbox]').forEach(input => input.click()));
            const next = [...container.querySelectorAll('button')].find(button => button.textContent?.includes('Next Step'));
            expect(next?.disabled).toBe(false);
            await act(async () => next!.click());
        }
    }
    it('requires refreshed evidence before publishing after a finding decision changes the snapshot', async () => {
        vi.mocked(adminClient.publishProject).mockClear();
        await render(clear, true, 'project-snapshot', 2);
        const save = [...container.querySelectorAll('button')].find(b => b.textContent === 'Save finding reasoning');
        expect(save).toBeTruthy(); await act(async () => save!.click());
        for (let step = 0; step < 2; step++) {
            await act(async () => container.querySelectorAll<HTMLInputElement>('input[type=checkbox]').forEach(input => input.click()));
            const next = [...container.querySelectorAll('button')].find(b => b.textContent?.includes('Next Step'));
            await act(async () => next!.click());
        }
        const approve = [...container.querySelectorAll('button')].find(b => b.textContent?.includes('Approve & Publish'));
        expect(approve?.disabled).toBe(true);
        expect(adminClient.publishProject).not.toHaveBeenCalled();
    });
    function button(text: string) { return [...container.querySelectorAll('button')].find(b => b.textContent?.includes(text))!; }
    async function click(text: string) { await act(async () => button(text).click()); }
    const refreshed = () => ({ mod: { id: 'project', slug: 'project', title: 'Updated project', status: 'PENDING', reviewToken: 'fresh-project', classification: 'PLUGIN', tags: [],
        versions: [{ id: 'sibling', versionNumber: '2.0', reviewStatus: 'PENDING', reviewToken: 'sibling-token', scanResult: clear },
            { id: 'version', versionNumber: '1.0', reviewStatus: 'PENDING', reviewToken: 'fresh-version', scanResult: clear }] } });
    it('refreshes in place, resets checks and publishes the same inspected version with the new token', async () => {
        vi.mocked(adminClient.publishProject).mockClear();
        vi.mocked(adminClient.getReviewDetails).mockResolvedValue(refreshed());
        await render(clear, true, 'old-project', 2);
        await click('Save finding reasoning'); await click('Refresh evidence and restart checklist');
        expect(adminClient.getReviewDetails).toHaveBeenCalledWith('project');
        expect(button('Next Step').disabled).toBe(true);
        for (let step = 0; step < 4; step++) {
            await act(async () => container.querySelectorAll<HTMLInputElement>('input[type=checkbox]').forEach(input => input.click()));
            await click('Next Step');
        }
        await click('Approve & Publish');
        expect(adminClient.publishProject).toHaveBeenCalledWith('project', 'fresh-project', 'version');
    });
    it.each(['missing', 'approved', 'token', 'project', 'stale'])('keeps decisions disabled when refresh cannot validate the selected review: %s', async kind => {
        const data = refreshed();
        if (kind === 'missing') data.mod.versions.pop();
        if (kind === 'approved') data.mod.versions[1].reviewStatus = 'APPROVED';
        if (kind === 'token') data.mod.versions[1].reviewToken = '';
        if (kind === 'project') data.mod.id = 'another-project';
        if (kind === 'stale') data.mod.versions[1].reviewToken = 'old-version';
        vi.mocked(adminClient.getReviewDetails).mockResolvedValue(data);
        await render(clear, true, 'old-project', 2);
        await click('Save finding reasoning'); await click('Refresh evidence and restart checklist');
        expect(container.querySelector('[role=alert]')).toBeTruthy();
        expect(button('Refresh evidence and restart checklist').disabled).toBe(false);
        expect(container.textContent).toContain('Refresh the evidence');
    });
    it('keeps the review held on a failed refresh and permits retry', async () => {
        vi.mocked(adminClient.getReviewDetails).mockRejectedValueOnce(new Error('Unavailable')).mockResolvedValueOnce(refreshed());
        await render(clear, true, 'old-project', 2);
        await click('Save finding reasoning'); await click('Refresh evidence and restart checklist');
        expect(container.querySelector('[role=alert]')).toBeTruthy();
        await click('Refresh evidence and restart checklist');
        expect(container.querySelector('[role=alert]')).toBeNull();
        expect(button('Next Step').disabled).toBe(true);
    });
    it('ignores a late refresh response after a different review is opened', async () => {
        let finish!: (value: any) => void;
        vi.mocked(adminClient.getReviewDetails).mockReturnValue(new Promise(resolve => { finish = resolve; }));
        await render(clear, true, 'old-project', 2);
        await click('Save finding reasoning'); await click('Refresh evidence and restart checklist');
        const other = refreshed(); other.mod.id = 'other'; other.mod.title = 'Different review';
        await act(async () => root.render(<Review reviewingProject={other} onClose={vi.fn()} onApprove={vi.fn()} onReject={vi.fn()} setStatus={vi.fn()} canDecide />));
        await act(async () => finish(refreshed()));
        expect(container.textContent).not.toContain('Updated project');
        expect(container.textContent).toContain('Different review');
    });
    it('publishes exactly the inspected version with the project snapshot', async () => {
        vi.mocked(adminClient.publishProject).mockClear();
        await render(clear, true, 'project-snapshot');
        const approve = [...container.querySelectorAll('button')].find(button => button.textContent?.includes('Approve & Publish'));
        await act(async () => approve!.click());
        expect(adminClient.publishProject).toHaveBeenCalledWith('project', 'project-snapshot', 'version');
    });
    it('requires refreshing a project review that has no snapshot', async () => {
        vi.mocked(adminClient.publishProject).mockClear();
        await render(clear, true);
        const approve = [...container.querySelectorAll('button')].find(button => button.textContent?.includes('Approve & Publish'));
        await act(async () => approve!.click());
        expect(adminClient.publishProject).not.toHaveBeenCalled();
    });
    it.each([undefined, { status: 'CLEAN', issues: [] }, { ...clear, verdict: 'REVIEW' },
        { ...clear, scanState: 'INCOMPLETE' }, { ...clear, securityEvidence: { complete: true, clearanceGranted: false } }])
    ('does not show completed clearance for missing or insufficient evidence %#', async scan => {
        await render(scan);
        expect(container.textContent).not.toContain('Artifact Review Completed');
    });
    it('shows precisely reused approval but holds fresh adverse evidence', async () => {
        const reused = { ...clear, status: 'SUSPICIOUS', verdict: 'REVIEW', reusedReviewVersion: '0.9',
            securityEvidence: { ...clear.securityEvidence, clearanceGranted: false, reviewState: 'POLICY_REVIEW' } };
        await render(reused);
        expect(container.textContent).toContain('Previously approved contents and context match version 0.9.');
        await act(async () => root.unmount()); root = createRoot(container);
        await render({ ...reused, securityEvidence: { ...reused.securityEvidence, reviewState: 'NEW_SECURITY_EVIDENCE' } });
        expect(container.textContent).not.toContain('Artifact Review Completed');
    });
    it('holds results whose policy identity is obsolete', async () => {
        await render({ ...clear, securityEvidence: { ...clear.securityEvidence, policyVersion: 'warden-3.0.0' } });
        expect(container.textContent).not.toContain('Artifact Review Completed');
    });
    it.each([true, false, undefined])('distinguishes identical local evidence without claiming clearance: %s', async identical => {
        await render({ ...clear, status: 'SUSPICIOUS', verdict: 'REVIEW',
            issues: [{ type: 'Network', severity: 'LOW', description: 'connect', filePath: 'Mod.class',
                lineStart: 9, lineEnd: 11, knownIssue: true, baselineVersion: '0.9',
                historicalFileEvidenceIdentical: identical }] });
        const show = container.querySelector<HTMLButtonElement>('button[aria-label="Show findings"]');
        if (show) await act(async () => show.click());
        expect(container.textContent?.includes('Same finding and file as approved version 0.9.')).toBe(identical === true);
        if (identical) expect(container.textContent).toContain('Changes elsewhere still require review.');
        expect(container.textContent).not.toContain('Artifact Review Completed');
        expect([...container.querySelectorAll('button')].some(button => button.textContent?.includes('Inspect'))).toBe(true);
    });
    it('shows completion for explicit completed clearance' , async () => {
        await render(clear); expect(container.textContent).toContain('Artifact Review Completed');
    });
    it('carries the opened project token from a removed-file comparison into source inspection', async () => {
        vi.mocked(adminClient.getArtifactChanges).mockResolvedValue({ reviewToken: 'snapshot', baselineVersion: '0.9',
            contextComparable: true, contextChanged: false, added: 0, modified: 0, removed: 1, unchanged: 0,
            files: [{ path: 'removed.txt', change: 'REMOVED' }] });
        vi.mocked(adminClient.getStructure).mockResolvedValue(['removed.txt']);
        vi.mocked(adminClient.getFileWindow).mockResolvedValue({identity:'a'.repeat(64),content:'Previously approved file contents',format:'TEXT_RESOURCE',start:0,end:33,totalCharacters:33,firstLine:1,lineMatched:true,representationComplete:true,gaps:[]});
        await render(clear, true, 'snapshot', 2);
        await click('Compare with approved version');
        await click('removed.txt');
        expect(adminClient.getArtifactChanges).toHaveBeenLastCalledWith('project', '1.0', 'snapshot');
        expect(adminClient.getStructure).toHaveBeenLastCalledWith('project', '0.9', 'snapshot');
        expect(adminClient.getFileWindow).toHaveBeenLastCalledWith('project', '0.9', 'removed.txt', 'snapshot', 0, undefined, 0);
        expect(container.textContent).toContain('Previously approved file contents');
    });
    it('keeps the latest selected comparison file when structure responses arrive out of order', async () => {
        vi.mocked(adminClient.getArtifactChanges).mockResolvedValue({ reviewToken: 'snapshot', baselineVersion: '0.9',
            contextComparable: true, contextChanged: false, added: 1, modified: 0, removed: 1, unchanged: 0,
            files: [{ path: 'old.txt', change: 'REMOVED' }, { path: 'new.txt', change: 'ADDED' }] });
        let stale!: (value: string[]) => void;
        vi.mocked(adminClient.getStructure).mockReturnValueOnce(new Promise(done => { stale = done; }))
            .mockResolvedValueOnce(['new.txt']);
        vi.mocked(adminClient.getFileWindow).mockResolvedValue({identity:'a'.repeat(64),content:'Current selection contents',format:'TEXT_RESOURCE',start:0,end:26,totalCharacters:26,firstLine:1,lineMatched:true,representationComplete:true,gaps:[]});
        await render(clear, true, 'snapshot', 2);
        await click('Compare with approved version'); await click('old.txt'); await click('new.txt');
        await act(async () => stale(['old.txt']));
        expect(adminClient.getFileWindow).toHaveBeenLastCalledWith('project', '1.0', 'new.txt', 'snapshot', 0, undefined, 0);
        expect(container.querySelector('code')?.textContent).toBe('Current selection contents');
    });
    const earlierSources = [{ id: 'older', versionNumber: '0.8', reviewStatus: 'APPROVED' }, { id: 'baseline', versionNumber: '0.9', reviewStatus: 'APPROVED' }];
    const twoFindings = { ...clear, status: 'SUSPICIOUS', verdict: 'REVIEW', issues: [
        { type: 'Network', severity: 'LOW', description: 'first original occurrence', filePath: 'Same.class', lineStart: 9, lineEnd: 9, baselineVersion: '0.9' },
        { type: 'Network', severity: 'HIGH', description: 'second original occurrence', filePath: 'Same.class', lineStart: 9, lineEnd: 9, baselineVersion: '0.9' }
    ] };
    const earlierResponse = (rationale: string) => ({ reviewToken: 'snapshot', sourceVersionId: 'baseline', sourceVersion: '0.9', assessedAt: Date.now(), reviewReasons: ['Current review required'], omitted: 0,
        decisions: [{ id: 'reasoning', actorId: 'reviewer', createdAt: 1, expiresAt: 2, disposition: 'ACCEPT' as const, scope: 'WHOLE_ARTIFACT', rationale,
            finding: { path: 'Same.class', description: 'prior', lineStart: 9 }, revokedDecisionId: null }] });
    it('opens prior reasoning in one click using the original occurrence index after sorting', async () => {
        vi.mocked(loadPriorFindingReasoning).mockReset().mockResolvedValue(earlierResponse('Exact second occurrence reasoning'));
        await render(twoFindings, true, 'snapshot', 2, earlierSources);
        const show = container.querySelector<HTMLButtonElement>('button[aria-label="Show findings"]');
        if (show) await act(async () => show.click());
        const buttons = container.querySelectorAll<HTMLButtonElement>('button[aria-label^="Earlier reasoning for finding"]');
        expect(buttons[0].getAttribute('aria-label')).toBe('Earlier reasoning for finding 2');
        expect(loadPriorFindingReasoning).not.toHaveBeenCalled();
        await act(async () => buttons[0].click());
        expect(loadPriorFindingReasoning).toHaveBeenCalledExactlyOnceWith('project', 'version', 'baseline', 1, 'snapshot');
        expect(container.querySelector('select[aria-label="Earlier reasoning finding"]')).toBeNull();
        expect(container.textContent).toContain('Exact second occurrence reasoning');
        expect(container.textContent).toContain('No current acceptance is granted');
    });
    it('does not attach a late rationale to a different selected occurrence', async () => {
        let resolve!: (value: ReturnType<typeof earlierResponse>) => void;
        vi.mocked(loadPriorFindingReasoning).mockReset().mockReturnValueOnce(new Promise(done => { resolve = done; }))
            .mockResolvedValueOnce(earlierResponse('Current occurrence reasoning'));
        await render(twoFindings, true, 'snapshot', 2, earlierSources);
        const show = container.querySelector<HTMLButtonElement>('button[aria-label="Show findings"]');
        if (show) await act(async () => show.click());
        await act(async () => container.querySelector<HTMLButtonElement>('button[aria-label="Earlier reasoning for finding 2"]')!.click());
        await act(async () => container.querySelector<HTMLButtonElement>('button[aria-label="Earlier reasoning for finding 1"]')!.click());
        await act(async () => resolve(earlierResponse('Stale occurrence reasoning')));
        expect(loadPriorFindingReasoning).toHaveBeenLastCalledWith('project', 'version', 'baseline', 0, 'snapshot');
        expect(container.querySelectorAll('section[aria-label="Earlier finding reasoning"]')).toHaveLength(1);
        expect(container.textContent).toContain('Current occurrence reasoning');expect(container.textContent).not.toContain('Stale occurrence reasoning');
    });
});
