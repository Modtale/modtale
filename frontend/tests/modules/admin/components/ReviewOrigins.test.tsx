import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { beforeEach, afterEach, expect, it, vi } from 'vitest';
import { ReviewOrigins } from '@/modules/admin/components/ReviewOrigins';
import { getOriginPage, type OriginPage } from '@/modules/admin/api/reviewOrigins';
vi.mock('@/modules/admin/api/reviewOrigins', async original => ({ ...await original<object>(), getOriginPage: vi.fn() }));
let root: Root;let container: HTMLDivElement;
const empty: OriginPage={items:[],nextCursor:null,examinedSlots:25,scope:'RETAINED_REVIEW_ORIGINS'};
const records: OriginPage={...empty,items:[{position:{projectIdType:'STRING',projectId:'a',versionIndex:3},versionId:null,ambiguousVersion:true,requestId:null,jobId:null,originState:'MISSING'}]};
beforeEach(async()=>{vi.mocked(getOriginPage).mockReset();container=document.createElement('div');document.body.append(container);root=createRoot(container);await act(async()=>root.render(<ReviewOrigins subject="one"/>));});
afterEach(async()=>{await act(async()=>root.unmount());container.remove();});
async function click(text: string){const button=[...container.querySelectorAll('button')].find(b=>b.textContent===text)!;expect(button).toBeTruthy();await act(async()=>button.click());}
it('loads only on demand and exposes references without approval or cancellation actions',async()=>{
    expect(getOriginPage).not.toHaveBeenCalled();vi.mocked(getOriginPage).mockResolvedValueOnce(records);await click('Inspect review origins');
    expect(container.textContent).toContain('3 (zero-based)');expect(container.textContent).toContain('Version identity is ambiguous');expect(container.textContent).toContain('Original service not recorded');
    expect(container.textContent).toContain('outside this inventory');expect(container.querySelector('a')).toBeNull();
    expect([...container.querySelectorAll('button')].map(b=>b.textContent)).toEqual(['Hide review origins','Refresh origins from start','Next origin page']);
});
it('continues empty pages and retries the failed position with focus recovery',async()=>{
    const cursor='o1.v.1.s.25.YQ';vi.mocked(getOriginPage).mockResolvedValueOnce({...empty,nextCursor:cursor});await click('Inspect review origins');
    expect(container.textContent).toContain('Continue to the next page');vi.mocked(getOriginPage).mockRejectedValueOnce(new Error('private failure'));
    await click('Next origin page');expect(document.activeElement).toBe(container.querySelector('[role="alert"]'));expect(container.textContent).not.toContain('private failure');
    vi.mocked(getOriginPage).mockResolvedValueOnce(records);await click('Retry origin inventory');
    expect(getOriginPage).toHaveBeenLastCalledWith(cursor,expect.any(AbortSignal));expect(document.activeElement).toBe(container.querySelector('h2'));
});
it.each(['close','account','unmount'])('discards pending responses after %s',async(mode)=>{
    let resolve!: (page: OriginPage)=>void;vi.mocked(getOriginPage).mockImplementationOnce(()=>new Promise(r=>{resolve=r;}));await click('Inspect review origins');
    const signal=vi.mocked(getOriginPage).mock.calls[0][1];
    if(mode==='close')await click('Hide review origins');else await act(async()=>root.render(mode==='account'?<ReviewOrigins subject="two"/>:<div>Access removed</div>));
    expect(signal.aborted).toBe(true);await act(async()=>resolve(records));expect(container.textContent).not.toContain('Original service not recorded');
});
it('replaces pages and distinguishes recorded identity from security clearance',async()=>{
    vi.mocked(getOriginPage).mockResolvedValueOnce({...records,items:[{...records.items[0],originState:'RECORDED'}],nextCursor:'o1.v.1.s.25.YQ'});await click('Inspect review origins');
    expect(container.textContent).toContain('Service identity recorded');expect(container.textContent).toContain('does not establish that a mod is safe');
    vi.mocked(getOriginPage).mockResolvedValueOnce(empty);await click('Next origin page');expect(container.querySelector('[aria-label="Retained review references"]')).toBeNull();
});
