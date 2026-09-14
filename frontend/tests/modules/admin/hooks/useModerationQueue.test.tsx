import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { beforeEach, afterEach, expect, it, vi } from 'vitest';
import { useModerationQueue } from '@/modules/admin/hooks/useModerationQueue';
import { getModerationQueuePage, type QueuePage } from '@/modules/admin/api/moderationQueue';
vi.mock('@/modules/admin/api/moderationQueue', () => ({ getModerationQueuePage: vi.fn() }));
let root: Root; let container: HTMLDivElement; let state: ReturnType<typeof useModerationQueue>;
const page = (title: string, nextCursor: string | null = null): QueuePage => ({ items: [{ id: title, title, author: '', description: '', classification: 'PLUGIN', status: 'PENDING' }], nextCursor, unavailableItems: 0, order: 'PROJECT_VERSION' });
function Host({ enabled = true, subject = 'user' }: { enabled?: boolean; subject?: string }) { state = useModerationQueue(enabled, subject); return <span>{state.page.items.map(row => row.title).join(',')}</span>; }
const deferred = () => { let resolve!: (value: QueuePage) => void; let reject!: (reason: Error) => void; const promise = new Promise<QueuePage>((yes,no) => {resolve=yes;reject=no;}); return {promise,resolve,reject}; };
beforeEach(() => { vi.resetAllMocks();container=document.createElement('div');document.body.append(container);root=createRoot(container); });
afterEach(async () => {await act(async () => root.unmount());container.remove();});
it('replaces pages rather than accumulating an unbounded list', async () => {
    vi.mocked(getModerationQueuePage).mockResolvedValueOnce(page('first','cursor')).mockResolvedValueOnce(page('second'));
    await act(async () => root.render(<Host />));await act(async () => state.next());
    expect(container.textContent).toBe('second');expect(getModerationQueuePage).toHaveBeenLastCalledWith('cursor',expect.any(AbortSignal));
});
it('late next-page responses cannot replace a fresh restart', async () => {
    const old=deferred();vi.mocked(getModerationQueuePage).mockResolvedValueOnce(page('first','cursor')).mockReturnValueOnce(old.promise).mockResolvedValueOnce(page('fresh'));
    await act(async () => root.render(<Host />));await act(async () => state.next());await act(async () => { await state.restart(); });
    await act(async () => old.resolve(page('stale')));expect(container.textContent).toBe('fresh');
    expect(vi.mocked(getModerationQueuePage).mock.calls[1][1].aborted).toBe(true);
});
it('retains the page on failed continuation and retries the failed cursor', async () => {
    vi.mocked(getModerationQueuePage).mockResolvedValueOnce(page('first','cursor')).mockRejectedValueOnce(new Error('failed')).mockResolvedValueOnce(page('second'));
    await act(async () => root.render(<Host />));await act(async () => state.next());expect(container.textContent).toBe('first');expect(state.error).toBeTruthy();
    await act(async () => { await state.refresh(true); });expect(getModerationQueuePage).toHaveBeenCalledTimes(2);
    await act(async () => { await state.retry(); });expect(getModerationQueuePage).toHaveBeenLastCalledWith('cursor',expect.any(AbortSignal));expect(container.textContent).toBe('second');
});
it('permission loss clears entries and ignores an uncooperative in-flight response', async () => {
    const old=deferred();vi.mocked(getModerationQueuePage).mockReturnValueOnce(old.promise);
    await act(async () => root.render(<Host />));await act(async () => root.render(<Host enabled={false} />));
    await act(async () => old.resolve(page('private')));expect(container.textContent).toBe('');expect(state.loaded).toBe(false);
});
it('account change restarts from the beginning and rejects the old account response', async () => {
    const old=deferred();vi.mocked(getModerationQueuePage).mockReturnValueOnce(old.promise).mockResolvedValueOnce(page('new-user'));
    await act(async () => root.render(<Host subject="old" />));await act(async () => root.render(<Host subject="new" />));
    await act(async () => old.resolve(page('old-user')));expect(container.textContent).toBe('new-user');
});
it('continues empty pages and prevents duplicate next requests', async () => {
    const next=deferred();vi.mocked(getModerationQueuePage).mockResolvedValueOnce({items:[],nextCursor:'cursor',unavailableItems:25,order:'PROJECT_VERSION'}).mockReturnValueOnce(next.promise);
    await act(async () => root.render(<Host />));await act(async () => {state.next();state.next();});expect(getModerationQueuePage).toHaveBeenCalledTimes(2);
    await act(async () => next.resolve(page('found')));expect(container.textContent).toBe('found');
});
