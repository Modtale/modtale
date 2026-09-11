import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Review } from '@/modules/admin/views/Review';
vi.mock('@/components/ui/ModalPortal', () => ({ ModalPortal: ({ children }: any) => children }));
vi.mock('@/modules/admin/api/adminClient', () => ({ adminClient: {} }));

const clear = { status: 'CLEAN', verdict: 'AUTO_APPROVE', scanState: 'COMPLETED', issues: [],
    securityEvidence: { complete: true, clearanceGranted: true, policyVersion: 'warden-3.0.0', artifactSha256: 'a'.repeat(64), entryHashes: {} } };
describe('Review security clearance status', () => {
    let container: HTMLDivElement; let root: Root;
    beforeEach(() => { container = document.createElement('div'); document.body.appendChild(container); root = createRoot(container); });
    afterEach(async () => { await act(async () => root.unmount()); container.remove(); });
    async function render(scanResult: any) {
        const project = { mod: { id: 'project', slug: 'project', title: 'Example', status: 'PUBLISHED', classification: 'PLUGIN', tags: [],
            versions: [{ id: 'version', versionNumber: '1.0', reviewStatus: 'PENDING', scanResult }] } };
        await act(async () => root.render(<Review reviewingProject={project} onClose={vi.fn()} onApprove={vi.fn()} onReject={vi.fn()} setStatus={vi.fn()} />));
        for (let step = 0; step < 2; step++) {
            await act(async () => container.querySelectorAll<HTMLInputElement>('input[type=checkbox]').forEach(input => input.click()));
            const next = [...container.querySelectorAll('button')].find(button => button.textContent?.includes('Next Step'));
            expect(next?.disabled).toBe(false);
            await act(async () => next!.click());
        }
    }
    it.each([undefined, { status: 'CLEAN', issues: [] }, { ...clear, verdict: 'REVIEW' },
        { ...clear, scanState: 'INCOMPLETE' }, { ...clear, securityEvidence: { complete: true, clearanceGranted: false } }])
    ('does not show completed clearance for missing or insufficient evidence %#', async scan => {
        await render(scan);
        expect(container.textContent).not.toContain('Artifact Review Completed');
    });
    it('shows completion for explicit completed clearance', async () => {
        await render(clear); expect(container.textContent).toContain('Artifact Review Completed');
    });
});
