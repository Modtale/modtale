import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { beforeEach, afterEach, it, expect, vi } from 'vitest';
import { PriorFindingReasoning } from '@/modules/admin/views/PriorFindingReasoning';
import { loadPriorFindingReasoning } from '@/modules/admin/api/findingReviews';
vi.mock('@/modules/admin/api/findingReviews', () => ({ loadPriorFindingReasoning: vi.fn() }));
let container: HTMLDivElement; let root: Root;
const props = { projectId: 'project', versionId: 'target', token: 'snapshot', sources: [{ id: 'source', versionNumber: '1' }],
    issues: [{ filePath: 'Mod.class', type: 'Network', lineStart: 9 } as any] };
const result = { reviewToken: 'snapshot', sourceVersionId: 'source', sourceVersion: '1', assessedAt: Date.now(), omitted: 0,
    reviewReasons: ['Artifact contents changed; callers need current review.'], decisions: [{ id: 'decision', actorId: 'reviewer', createdAt: 1, expiresAt: 2,
        disposition: 'ACCEPT' as const, scope: 'WHOLE_ARTIFACT', rationale: '<script>untrusted rationale</script>', finding: { path: 'Mod.class', description: 'Connects', lineStart: 9 }, revokedDecisionId: null }] };
beforeEach(() => { vi.resetAllMocks(); container = document.createElement('div'); document.body.appendChild(container); root = createRoot(container); });
afterEach(async () => { await act(async () => root.unmount()); container.remove(); });
async function load() { await act(async () => container.querySelector<HTMLButtonElement>('button')!.click()); }
it('loads exact prior reasoning on demand without granting or recording a decision', async () => {
    vi.mocked(loadPriorFindingReasoning).mockResolvedValue(result);
    await act(async () => root.render(<PriorFindingReasoning {...props} />));
    expect(loadPriorFindingReasoning).not.toHaveBeenCalled();await load();
    expect(loadPriorFindingReasoning).toHaveBeenCalledWith('project', 'target', 'source', 0, 'snapshot');
    expect(container.textContent).toContain('callers need current review');expect(container.textContent).toContain('Expired.');
    expect(container.textContent).toContain('<script>untrusted rationale</script>');expect(container.querySelector('script')).toBeNull();
    expect(container.textContent).toContain('No current acceptance is granted');expect(container.querySelector('textarea')).toBeNull();
});
it('discards a response after the opened project token changes', async () => {
    let resolve!: (value: typeof result) => void;
    vi.mocked(loadPriorFindingReasoning).mockReturnValue(new Promise(done => { resolve = done; }));
    await act(async () => root.render(<PriorFindingReasoning {...props} />));await load();
    await act(async () => root.render(<PriorFindingReasoning {...props} token="new" />));
    await act(async () => resolve(result));expect(container.textContent).not.toContain('untrusted rationale');
});
it('removes prior reasoning when a refreshed source is no longer valid', async () => {
    vi.mocked(loadPriorFindingReasoning).mockResolvedValueOnce(result).mockRejectedValueOnce(new Error('Source approval changed'));
    await act(async () => root.render(<PriorFindingReasoning {...props} />));await load();await load();
    expect(container.querySelector('[role=alert]')).not.toBeNull();expect(container.textContent).not.toContain('untrusted rationale');
});
it('rejects a response from a different source or project snapshot', async () => {
    vi.mocked(loadPriorFindingReasoning).mockResolvedValue({ ...result, sourceVersionId: 'different' });
    await act(async () => root.render(<PriorFindingReasoning {...props} />));await load();
    expect(container.querySelector('[role=alert]')).not.toBeNull();expect(container.textContent).not.toContain('untrusted rationale');
});
it('explains unmatched evidence without claiming newness or safety', async () => {
    vi.mocked(loadPriorFindingReasoning).mockResolvedValue({ ...result, decisions: [] });
    await act(async () => root.render(<PriorFindingReasoning {...props} />));await load();
    expect(container.textContent).toContain('does not mean the finding is new or safe');
});
