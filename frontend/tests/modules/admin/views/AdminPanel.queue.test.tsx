import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { beforeEach, afterEach, expect, it, vi } from 'vitest';
import { AdminPanel } from '@/modules/admin/views/AdminPanel';
import { api } from '@/utils/api';
vi.mock('@/utils/api', () => ({ api: { get: vi.fn() }, extractApiErrorMessage: (_: unknown, fallback: string) => fallback }));
vi.mock('@/modules/admin/views/Review', () => ({ Review: ({ reviewingProject }: any) => <div>Selected version {reviewingProject.selectedVersionId}</div> }));
vi.mock('@/components/ui/StatusModal', () => ({ StatusModal: ({ message }: any) => <div>{message}</div> }));
let root: Root;let container: HTMLDivElement;
const user={id:'reviewer',username:'Reviewer',adminPermissions:['PROJECT_REVIEW_READ']};
beforeEach(() => {vi.resetAllMocks();container=document.createElement('div');document.body.append(container);root=createRoot(container);});
afterEach(async () => {await act(async () => root.unmount());container.remove();});
it('uses the bounded API, continues repair-only pages and opens the chosen version', async () => {
    vi.mocked(api.get).mockResolvedValueOnce({data:{items:[],nextCursor:'1.s.0.YQ',unavailableItems:25,order:'PROJECT_VERSION'}})
        .mockResolvedValueOnce({data:{items:[{id:'p',title:'Found project',status:'PUBLISHED',classification:'PLUGIN',pendingVersion:{id:'v',versionNumber:'1',reviewStatus:'PENDING'}}],nextCursor:null,unavailableItems:0,order:'PROJECT_VERSION'}})
        .mockResolvedValueOnce({data:{mod:{id:'p',versions:[{id:'v',reviewStatus:'PENDING'}]}}});
    await act(async () => root.render(<AdminPanel currentUser={user} />));
    expect(container.textContent).toContain('25 entries on this page cannot be opened safely');
    expect(container.textContent).toContain('Continue to the next page');
    const next=[...container.querySelectorAll('button')].find(b=>b.textContent==='Next page')!;
    await act(async () => next.click());expect(container.textContent).toContain('Found project');expect(next.disabled).toBe(true);
    expect(document.activeElement?.textContent).toBe('Verification Queue');
    expect(vi.mocked(api.get).mock.calls[1][1]).toMatchObject({params:{cursor:'1.s.0.YQ',limit:25}});
    await act(async () => [...container.querySelectorAll('button')].find(b=>b.textContent?.includes('Verify Update'))!.click());
    expect(container.textContent).toContain('Selected version v');
    expect(vi.mocked(api.get).mock.calls.some(call=>call[0]==='/admin/verification/queue')).toBe(false);
});
it('does not load a queue without review-read permission', async () => {
    await act(async () => root.render(<AdminPanel currentUser={{id:'user',adminPermissions:[]}} />));
    expect(container.textContent).toContain('Access Denied');expect(api.get).not.toHaveBeenCalled();
});

it('focuses the error notice after explicit navigation fails', async () => {
    vi.mocked(api.get).mockResolvedValueOnce({data:{items:[],nextCursor:'1.s.0.YQ',unavailableItems:0,order:'PROJECT_VERSION'}})
        .mockRejectedValueOnce(new Error('failed'));
    await act(async () => root.render(<AdminPanel currentUser={user} />));
    const next=[...container.querySelectorAll('button')].find(b=>b.textContent==='Next page')!;
    await act(async () => next.click());
    expect(document.activeElement?.getAttribute('role')).toBe('alert');
    expect(document.activeElement?.textContent).toContain('Retry');
});
