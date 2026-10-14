import React, { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { DependencyInspection, type DependencyInspectionResult, type DependencyByteResult, type RootByteResult, type OverrideContentResult } from '@/modules/admin/views/DependencyInspection';
import { adminClient } from '@/modules/admin/api/adminClient';
vi.mock('@/modules/admin/api/adminClient', () => ({ adminClient: { getDependencyInspection: vi.fn(), verifyDependencyBytes: vi.fn(), verifyUploadedBytes: vi.fn(), inspectOverrideContents: vi.fn(), getOverrideConfigWindow: vi.fn() } }));
let container: HTMLDivElement, root: Root;
const result: DependencyInspectionResult = { reviewToken: 'token', artifactBytesVerified: false, modpackOverrideAvailable: false, inventory: {
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
const verifyRoot = async () => act(async () => [...container.querySelectorAll('button')].find(button => button.textContent === 'Verify uploaded artifact bytes')!.click());
const rootByteResult: RootByteResult = { reviewToken: 'token', artifactSha256: 'a'.repeat(64), verification: {
    state: 'MATCHED', bytes: 3, artifacts: [{ fileReference: 'modpack-overrides/upload.zip', expectedSha256: 'a'.repeat(64), actualSha256: 'a'.repeat(64), bytes: 3, state: 'MATCHED' }],
} };
const overrideResult: OverrideContentResult = { reviewToken: 'token', artifactSha256: 'a'.repeat(64), observation: {
    state: 'MATCHED', observedArchiveSha256: 'a'.repeat(64), archiveBytes: 50,
    files: [{ path: 'overrides/Universe/mods/Example/config.json', sha256: 'b'.repeat(64), bytes: 2, source: 'MODTALE', projectId: 'child' }], window: null,
} };
it('inspects saved override contents on demand while keeping review holds visible', async () => {
    vi.mocked(adminClient.getDependencyInspection).mockResolvedValue({ ...result, modpackOverrideAvailable: true });
    vi.mocked(adminClient.inspectOverrideContents).mockResolvedValue(overrideResult);
    await render(); await load();
    await act(async () => [...container.querySelectorAll('button')].find(button => button.textContent === 'Inspect override contents')!.click());
    expect(adminClient.inspectOverrideContents).toHaveBeenCalledWith('p', 'v', 'token', 'a'.repeat(64));
    expect(container.textContent).toContain('Their behavior still needs security review');
    expect(container.textContent).toContain('Pinned version not found');
    expect(container.textContent).toContain('Recorded config files (1)');
});
it('reads snapshot-bound config text without rendering markup or claiming clearance', async () => {
    const path = overrideResult.observation.files[0].path;
    const content = '{"command":"<script>"}';
    vi.mocked(adminClient.getDependencyInspection).mockResolvedValue({ ...result, modpackOverrideAvailable: true });
    vi.mocked(adminClient.inspectOverrideContents).mockResolvedValue(overrideResult);
    vi.mocked(adminClient.getOverrideConfigWindow).mockResolvedValue({ ...overrideResult, observation: { ...overrideResult.observation,
        files: [], window: { path, sha256: 'b'.repeat(64), start: 0, end: content.length, totalCharacters: content.length, content } } });
    await render(); await load();
    await act(async () => [...container.querySelectorAll('button')].find(button => button.textContent === 'Inspect override contents')!.click());
    await act(async () => [...container.querySelectorAll('button')].find(button => button.textContent === 'Read config')!.click());
    expect(adminClient.getOverrideConfigWindow).toHaveBeenCalledWith('p', 'v', 'token', 'a'.repeat(64), path, 0);
    expect(container.querySelector('[aria-label="Config text window"]')?.textContent).toBe(content);
    expect(container.querySelector('script')).toBeNull();
    expect(container.textContent).toContain('Security review remains separate');
    await render('changed'); expect(container.querySelector('[aria-label="Config text window"]')).toBeNull();
});
it('pages config text by verified character offsets and discards a mismatched hash', async () => {
    const path = overrideResult.observation.files[0].path;
    vi.mocked(adminClient.getDependencyInspection).mockResolvedValue({ ...result, modpackOverrideAvailable: true });
    vi.mocked(adminClient.inspectOverrideContents).mockResolvedValue(overrideResult);
    vi.mocked(adminClient.getOverrideConfigWindow).mockResolvedValueOnce({ ...overrideResult, observation: {
        ...overrideResult.observation, files: [], window: { path, sha256: 'b'.repeat(64), start: 0, end: 32768,
            totalCharacters: 32769, content: 'x'.repeat(32768) } } });
    vi.mocked(adminClient.getOverrideConfigWindow).mockResolvedValueOnce({ ...overrideResult, observation: {
        ...overrideResult.observation, files: [], window: { path, sha256: 'b'.repeat(64), start: 32768, end: 32769,
            totalCharacters: 32769, content: 'y' } } });
    await render(); await load();
    await act(async () => [...container.querySelectorAll('button')].find(button => button.textContent === 'Inspect override contents')!.click());
    await act(async () => [...container.querySelectorAll('button')].find(button => button.textContent === 'Read config')!.click());
    await act(async () => [...container.querySelectorAll('button')].find(button => button.textContent === 'Next config text')!.click());
    expect(adminClient.getOverrideConfigWindow).toHaveBeenLastCalledWith('p', 'v', 'token', 'a'.repeat(64), path, 32768);
    expect(container.querySelector('[aria-label="Config text window"]')?.textContent).toBe('y');
    vi.mocked(adminClient.getOverrideConfigWindow).mockResolvedValueOnce({ ...overrideResult, observation: {
        ...overrideResult.observation, files: [], window: { path, sha256: 'c'.repeat(64), start: 0, end: 1,
            totalCharacters: 1, content: 'z' } } });
    await act(async () => [...container.querySelectorAll('button')].find(button => button.textContent === 'Previous config text')!.click());
    expect(container.querySelector('[aria-label="Config text window"]')).toBeNull();
    expect(container.querySelector('table')).toBeNull();
    expect(container.querySelector('[role="alert"]')).not.toBeNull();
});
it('checks uploaded bytes on a held graph without presenting dependency clearance', async () => {
    vi.mocked(adminClient.getDependencyInspection).mockResolvedValue(result);
    vi.mocked(adminClient.verifyUploadedBytes).mockResolvedValue(rootByteResult);
    await render(); await load(); expect(container.textContent).not.toContain('Verify stored files');
    await verifyRoot();
    expect(adminClient.verifyUploadedBytes).toHaveBeenCalledWith('p', 'v', 'token', 'a'.repeat(64));
    expect(container.textContent).toContain('Dependencies and supplemental content still require separate review');
    expect(container.textContent).toContain('Pinned version not found');
    await render('changed'); expect(container.textContent).not.toContain('Uploaded artifact bytes matched');
});
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
