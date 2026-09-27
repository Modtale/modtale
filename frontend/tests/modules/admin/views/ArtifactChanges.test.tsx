import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ArtifactChanges, type ArtifactChangeSummary } from '@/modules/admin/views/ArtifactChanges';
import { adminClient } from '@/modules/admin/api/adminClient';
vi.mock('@/modules/admin/api/adminClient', () => ({ adminClient: { getArtifactChanges: vi.fn() } }));
const summary: ArtifactChangeSummary = { reviewToken: 'snapshot', baselineVersion: '1.0', contextComparable: true, contextChanged: false, contextChanges: [],
    added: 1, modified: 1, removed: 1, unchanged: 1, files: [
        { path: 'added.class', change: 'ADDED' }, { path: 'modified.class', change: 'MODIFIED' },
        { path: 'removed.class', change: 'REMOVED' }, { path: 'unchanged.class', change: 'UNCHANGED' }
    ] };
describe('artifact changes', () => {
    let container: HTMLDivElement;
    let root: Root;
    const button = (text: string) => [...container.querySelectorAll('button')].find(node => node.textContent?.includes(text))!;
    beforeEach(() => {
        container = document.createElement('div'); document.body.appendChild(container); root = createRoot(container);
        vi.clearAllMocks();
    });
    afterEach(async () => { await act(async () => root.unmount()); container.remove(); });
    it('focuses on changed files and opens removed files from the approved version', async () => {
        vi.mocked(adminClient.getArtifactChanges).mockResolvedValue(summary);
        const inspect = vi.fn();
        await act(async () => root.render(<ArtifactChanges reviewToken="snapshot" projectId="project" version="2.0" onInspect={inspect} />));
        expect(adminClient.getArtifactChanges).not.toHaveBeenCalled();
        await act(async () => button('Compare with approved version').click());
        expect(container.textContent).not.toContain('unchanged.class');
        await act(async () => button('removed.class').click());
        expect(inspect).toHaveBeenLastCalledWith('1.0', 'removed.class', 'snapshot');
        await act(async () => button('modified.class').click());
        expect(inspect).toHaveBeenLastCalledWith('2.0', 'modified.class', 'snapshot');
        await act(async () => container.querySelector<HTMLInputElement>('input[type="checkbox"]')!.click());
        expect(container.textContent).toContain('unchanged.class');
    });
    it('names the changed runtime context without implying approval reuse', async () => {
        vi.mocked(adminClient.getArtifactChanges).mockResolvedValue({ ...summary, contextChanged: true,
            contextChanges: ['GAME_VERSIONS', 'MANIFEST_VERSION'] });
        await act(async () => root.render(<ArtifactChanges autoLoad reviewToken="snapshot" projectId="project" version="2.0" onInspect={vi.fn()} />));
        expect(container.textContent).toContain('Review changed game versions, manifest version.');
        expect(container.textContent).not.toContain('approved for reuse');
    });
    it('does not present unverified approval context as a precise metadata comparison', async () => {
        vi.mocked(adminClient.getArtifactChanges).mockResolvedValue({ ...summary, contextComparable: false,
            contextChanged: false, contextChanges: [] });
        await act(async () => root.render(<ArtifactChanges autoLoad reviewToken="snapshot" projectId="project" version="2.0" onInspect={vi.fn()} />));
        expect(container.textContent).toContain('earlier approval context cannot be verified');
        expect(container.textContent).not.toContain('Review changed game versions');
    });
    it('loads a prior-version comparison when an update review opens without requiring a click', async () => {
        vi.mocked(adminClient.getArtifactChanges).mockResolvedValue(summary);
        await act(async () => root.render(<ArtifactChanges autoLoad reviewToken="snapshot" projectId="project" version="2.0" onInspect={vi.fn()} />));
        expect(adminClient.getArtifactChanges).toHaveBeenCalledTimes(1);
        expect(adminClient.getArtifactChanges).toHaveBeenCalledWith('project', '2.0', 'snapshot');
        expect(container.textContent).toContain('modified.class');
        expect(container.textContent).not.toContain('unchanged.class');
        await act(async () => root.render(<ArtifactChanges autoLoad reviewToken="snapshot" projectId="project" version="2.0" onInspect={vi.fn()} />));
        expect(adminClient.getArtifactChanges).toHaveBeenCalledTimes(1);
    });
    it('discards an automatic comparison after the review snapshot changes', async () => {
        let resolveOld!: (value: ArtifactChangeSummary) => void;
        vi.mocked(adminClient.getArtifactChanges)
            .mockReturnValueOnce(new Promise(done => { resolveOld = done; }))
            .mockResolvedValueOnce({ ...summary, reviewToken: 'new-review', files: [{ path: 'new.class', change: 'ADDED' }] });
        await act(async () => root.render(<ArtifactChanges autoLoad reviewToken="snapshot" projectId="project" version="2.0" onInspect={vi.fn()} />));
        await act(async () => root.render(<ArtifactChanges autoLoad reviewToken="new-review" projectId="project" version="2.0" onInspect={vi.fn()} />));
        await act(async () => resolveOld(summary));
        expect(adminClient.getArtifactChanges).toHaveBeenCalledTimes(2);
        expect(container.textContent).toContain('new.class');
        expect(container.textContent).not.toContain('modified.class');
    });
    it('does not show a late comparison from a different version', async () => {
        let resolve!: (value: ArtifactChangeSummary) => void;
        vi.mocked(adminClient.getArtifactChanges).mockReturnValue(new Promise(done => { resolve = done; }));
        await act(async () => root.render(<ArtifactChanges reviewToken="snapshot" projectId="project" version="2.0" onInspect={vi.fn()} />));
        await act(async () => button('Compare with approved version').click());
        await act(async () => root.render(<ArtifactChanges reviewToken="snapshot" projectId="project" version="3.0" onInspect={vi.fn()} />));
        await act(async () => resolve(summary));
        expect(container.textContent).not.toContain('removed.class');
        expect(button('Compare with approved version')).toBeTruthy();
    });
    it('removes the old comparison during refresh and after a conflict', async () => {
        vi.mocked(adminClient.getArtifactChanges).mockResolvedValueOnce(summary);
        await act(async () => root.render(<ArtifactChanges reviewToken="snapshot" projectId="project" version="2.0" onInspect={vi.fn()} />));
        await act(async () => button('Compare with approved version').click());
        expect(container.textContent).toContain('removed.class');
        let reject!: (error: Error) => void;
        vi.mocked(adminClient.getArtifactChanges).mockReturnValueOnce(new Promise((_, fail) => { reject = fail; }));
        await act(async () => button('Refresh comparison').click());
        expect(container.textContent).not.toContain('removed.class');
        await act(async () => reject(new Error('Project changed')));
        expect(container.querySelector('[role="alert"]')).not.toBeNull();
        expect(container.textContent).not.toContain('removed.class');
        expect(button('Compare with approved version')).toBeTruthy();
    });
    it('shows an unavailable baseline without implying that nothing changed', async () => {
        vi.mocked(adminClient.getArtifactChanges).mockResolvedValue({ ...summary, baselineVersion: null, files: [] });
        await act(async () => root.render(<ArtifactChanges reviewToken="snapshot" projectId="project" version="2.0" onInspect={vi.fn()} />));
        await act(async () => button('Compare with approved version').click());
        expect(container.textContent).toContain('No previously approved version is available for comparison.');
    });
    it('discards a comparison when the review token changes without a version change', async () => {
        let resolve!: (value: ArtifactChangeSummary) => void;
        vi.mocked(adminClient.getArtifactChanges).mockReturnValue(new Promise(done => { resolve = done; }));
        await act(async () => root.render(<ArtifactChanges reviewToken="snapshot" projectId="project" version="2.0" onInspect={vi.fn()} />));
        await act(async () => button('Compare with approved version').click());
        expect(adminClient.getArtifactChanges).toHaveBeenCalledWith('project', '2.0', 'snapshot');
        await act(async () => root.render(<ArtifactChanges reviewToken="new-review" projectId="project" version="2.0" onInspect={vi.fn()} />));
        await act(async () => resolve(summary));
        expect(container.textContent).not.toContain('removed.class');
    });
    it('rejects a response bound to another review', async () => {
        vi.mocked(adminClient.getArtifactChanges).mockResolvedValue({ ...summary, reviewToken: 'other' });
        await act(async () => root.render(<ArtifactChanges reviewToken="snapshot" projectId="project" version="2.0" onInspect={vi.fn()} />));
        await act(async () => button('Compare with approved version').click());
        expect(container.querySelector('[role="alert"]')).not.toBeNull();
        expect(container.textContent).not.toContain('removed.class');
    });
});
