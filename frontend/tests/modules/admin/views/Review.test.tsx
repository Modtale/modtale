import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Review } from '@/modules/admin/views/Review';
vi.mock('@/modules/admin/views/FindingDecisions', () => ({ FindingDecisions: ({ onSaved }: any) => <button onClick={onSaved}>Save finding reasoning</button> }));
vi.mock('@/components/ui/ModalPortal', () => ({ ModalPortal: ({ children }: any) => children }));
vi.mock('@/modules/admin/api/adminClient', () => ({ adminClient: { publishProject: vi.fn().mockResolvedValue(null), getReviewDetails: vi.fn() } }));
import { adminClient } from '@/modules/admin/api/adminClient';

const clear = { status: 'CLEAN', verdict: 'AUTO_APPROVE', scanState: 'COMPLETED', issues: [],
    securityEvidence: { complete: true, clearanceGranted: true, policyVersion: 'warden-3.0.0:' + 'c'.repeat(64), artifactSha256: 'a'.repeat(64), contentSha256: 'b'.repeat(64), entryHashes: {} } };
describe('Review security clearance status', () => {
    let container: HTMLDivElement; let root: Root;
    beforeEach(() => { container = document.createElement('div'); document.body.appendChild(container); root = createRoot(container); });
    afterEach(async () => { await act(async () => root.unmount()); container.remove(); });
    async function render(scanResult: any, decision = false, token?: string, steps?: number) {
        const project = { mod: { id: 'project', slug: 'project', title: 'Example', status: decision ? 'PENDING' : 'PUBLISHED', reviewToken: token, classification: 'PLUGIN', tags: [],
            versions: [{ id: 'version', versionNumber: '1.0', reviewStatus: 'PENDING', reviewToken: 'old-version', scanResult }] } };
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
});
