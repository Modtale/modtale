import React, { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { DependencyInspection, type DependencyInspectionResult } from '@/modules/admin/views/DependencyInspection';
import { adminClient } from '@/modules/admin/api/adminClient';
vi.mock('@/modules/admin/api/adminClient', () => ({ adminClient: { getDependencyInspection: vi.fn() } }));
let container: HTMLDivElement, root: Root;
const result: DependencyInspectionResult = { reviewToken: 'token', artifactBytesVerified: false, inventory: {
    root: { projectId: 'p', versionId: 'v' }, identity: null,
    nodes: [{ projectId: 'p', versionId: 'v', versionNumber: '<script>untrusted</script>', artifactSha256: 'a'.repeat(64) }],
    edges: [], gaps: [{ from: { projectId: 'p', versionId: 'v' }, reference: { projectId: 'missing', versionNumber: '1' }, reason: 'MISSING' }],
} };
beforeEach(() => { vi.clearAllMocks(); container = document.createElement('div'); document.body.appendChild(container); root = createRoot(container); });
afterEach(async () => { await act(async () => root.unmount()); container.remove(); });
const render = async (token = 'token', versionId = 'v') => act(async () => root.render(<DependencyInspection projectId="p" versionId={versionId} reviewToken={token} />));
const load = async () => act(async () => container.querySelector('button')!.click());
it('loads on demand and distinguishes recorded declarations from approval', async () => {
    vi.mocked(adminClient.getDependencyInspection).mockResolvedValue(result);
    await render(); expect(adminClient.getDependencyInspection).not.toHaveBeenCalled(); await load();
    expect(adminClient.getDependencyInspection).toHaveBeenCalledWith('p', 'v', 'token');
    expect(container.textContent).toContain('Pinned version not found');
    expect(container.textContent).toContain('Content verification and security approval are separate');
    expect(container.textContent).toContain('<script>untrusted</script>'); expect(container.querySelector('script')).toBeNull();
});
it('discards delayed results after a review or version change', async () => {
    let resolve!: (value: DependencyInspectionResult) => void;
    vi.mocked(adminClient.getDependencyInspection).mockReturnValue(new Promise(done => { resolve = done; }));
    await render(); await load(); await render('new-token', 'new-version'); await act(async () => resolve(result));
    expect(container.textContent).not.toContain('Pinned version not found'); expect(container.textContent).toContain('Inspect dependencies');
});
it('rejects mismatched root identities and clears evidence on failed refresh', async () => {
    vi.mocked(adminClient.getDependencyInspection).mockResolvedValueOnce({ ...result, inventory: { ...result.inventory, root: { projectId: 'other', versionId: 'v' } } });
    await render(); await load(); expect(container.querySelector('[role="alert"]')).not.toBeNull();
    expect(container.querySelector('table')).toBeNull();
    vi.mocked(adminClient.getDependencyInspection).mockResolvedValueOnce(result); await load(); expect(container.querySelector('table')).not.toBeNull();
    vi.mocked(adminClient.getDependencyInspection).mockRejectedValueOnce(new Error('Changed')); await load();
    expect(container.querySelector('table')).toBeNull(); expect(container.querySelector('[role="alert"]')).not.toBeNull();
});
