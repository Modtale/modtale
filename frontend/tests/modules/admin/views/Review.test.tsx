import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Review } from '@/modules/admin/views/Review';
vi.mock('@/components/ui/ModalPortal', () => ({ ModalPortal: ({ children }: any) => children }));
vi.mock('@/modules/admin/api/adminClient', () => ({ adminClient: { publishProject: vi.fn().mockResolvedValue(null) } }));
import { adminClient } from '@/modules/admin/api/adminClient';

const clear = { status: 'CLEAN', verdict: 'AUTO_APPROVE', scanState: 'COMPLETED', issues: [],
    securityEvidence: { complete: true, clearanceGranted: true, policyVersion: 'warden-3.0.0:' + 'c'.repeat(64), artifactSha256: 'a'.repeat(64), contentSha256: 'b'.repeat(64), entryHashes: {} } };
describe('Review security clearance status', () => {
    let container: HTMLDivElement; let root: Root;
    beforeEach(() => { container = document.createElement('div'); document.body.appendChild(container); root = createRoot(container); });
    afterEach(async () => { await act(async () => root.unmount()); container.remove(); });
    async function render(scanResult: any, decision = false, token?: string) {
        const project = { mod: { id: 'project', slug: 'project', title: 'Example', status: decision ? 'PENDING' : 'PUBLISHED', reviewToken: token, classification: 'PLUGIN', tags: [],
            versions: [{ id: 'version', versionNumber: '1.0', reviewStatus: 'PENDING', scanResult }] } };
        await act(async () => root.render(<Review reviewingProject={project} onClose={vi.fn()} onApprove={vi.fn()} onReject={vi.fn()} setStatus={vi.fn()} canDecide={decision} />));
        for (let step = 0; step < (decision ? 4 : 2); step++) {
            await act(async () => container.querySelectorAll<HTMLInputElement>('input[type=checkbox]').forEach(input => input.click()));
            const next = [...container.querySelectorAll('button')].find(button => button.textContent?.includes('Next Step'));
            expect(next?.disabled).toBe(false);
            await act(async () => next!.click());
        }
    }
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
    it('shows completion for explicit completed clearance', async () => {
        await render(clear); expect(container.textContent).toContain('Artifact Review Completed');
    });
});
