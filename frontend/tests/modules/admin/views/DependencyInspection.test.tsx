import React, { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { DependencyInspection, type DependencyInspectionResult, type DependencyByteResult } from '@/modules/admin/views/DependencyInspection';
import { adminClient } from '@/modules/admin/api/adminClient';
vi.mock('@/modules/admin/api/adminClient', () => ({ adminClient: { getDependencyInspection: vi.fn(), verifyDependencyBytes: vi.fn() } }));
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

const complete = { ...result, inventory: { ...result.inventory, identity: 'a'.repeat(64), gaps: [] } };
const byteResult: DependencyByteResult = { reviewToken: 'token', inventoryIdentity: 'a'.repeat(64), verification: {
    inventoryIdentity: 'a'.repeat(64), state: 'MATCHED', bytes: 3, artifacts: [{ fileReference: 'file.jar', expectedSha256: 'a'.repeat(64), actualSha256: 'a'.repeat(64), bytes: 3, state: 'MATCHED' }],
} };
const verify = async () => act(async () => [...container.querySelectorAll('button')].find(button => button.textContent === 'Verify stored files')!.click());
it('verifies only resolved inventory on explicit request and keeps approval separate', async () => {
    vi.mocked(adminClient.getDependencyInspection).mockResolvedValue(complete);
    vi.mocked(adminClient.verifyDependencyBytes).mockResolvedValue(byteResult);
    await render(); await load(); expect(adminClient.verifyDependencyBytes).not.toHaveBeenCalled(); await verify();
    expect(adminClient.verifyDependencyBytes).toHaveBeenCalledWith('p', 'v', 'token', 'a'.repeat(64));
    expect(container.textContent).toContain('Security approval is still separate'); expect(container.textContent).toContain('file.jar');
    await render('changed'); expect(container.textContent).not.toContain('file.jar');
});
it('discards late byte results and mismatched inventory proofs', async () => {
    vi.mocked(adminClient.getDependencyInspection).mockResolvedValue(complete);
    let resolve!: (value: DependencyByteResult) => void;
    vi.mocked(adminClient.verifyDependencyBytes).mockReturnValueOnce(new Promise(done => { resolve = done; }));
    await render(); await load(); await verify(); await render('changed'); await act(async () => resolve(byteResult));
    expect(container.textContent).not.toContain('file.jar');
    await render(); await load();
    vi.mocked(adminClient.verifyDependencyBytes).mockResolvedValueOnce({ ...byteResult, inventoryIdentity: 'b'.repeat(64) });
    await verify(); expect(container.querySelector('[role="alert"]')).not.toBeNull(); expect(container.querySelector('table')).toBeNull();
});
it('keeps incomplete byte results explicit and hides verification for unresolved graphs', async () => {
    vi.mocked(adminClient.getDependencyInspection).mockResolvedValueOnce(result);
    await render(); await load(); expect(container.textContent).not.toContain('Verify stored files');
    vi.mocked(adminClient.getDependencyInspection).mockResolvedValueOnce(complete); await load();
    vi.mocked(adminClient.verifyDependencyBytes).mockResolvedValue({ ...byteResult, verification: { ...byteResult.verification, state: 'TIME_LIMIT', artifacts: [] } });
    await verify(); expect(container.textContent).toContain('File verification is incomplete'); expect(container.textContent).not.toContain('Stored files matched');
});
