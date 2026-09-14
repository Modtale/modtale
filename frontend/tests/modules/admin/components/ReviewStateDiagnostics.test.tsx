import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { beforeEach, afterEach, expect, it, vi } from 'vitest';
import { ReviewStateDiagnostics } from '@/modules/admin/components/ReviewStateDiagnostics';
import { getDiagnosticPage, type DiagnosticPage } from '@/modules/admin/api/reviewDiagnostics';
vi.mock('@/modules/admin/api/reviewDiagnostics', async original => ({ ...await original<object>(), getDiagnosticPage: vi.fn() }));
let root: Root;let container: HTMLDivElement;
const empty: DiagnosticPage={items:[],nextCursor:null,examinedSlots:25,scope:'PENDING_SCAN_STRUCTURE'};
const records: DiagnosticPage={...empty,items:[{position:{projectIdType:'STRING',projectId:'a',versionIndex:3},versionId:null,reasons:['DUPLICATE_VERSION_ID']}]};
beforeEach(async()=>{vi.mocked(getDiagnosticPage).mockReset();container=document.createElement('div');document.body.append(container);root=createRoot(container);await act(async()=>root.render(<ReviewStateDiagnostics subject="one"/>));});
afterEach(async()=>{await act(async()=>root.unmount());container.remove();});
async function click(text: string){const button=[...container.querySelectorAll('button')].find(b=>b.textContent===text)!;expect(button).toBeTruthy();await act(async()=>button.click());}
it('loads on demand, explains ambiguity, and offers no repair or clearance action',async()=>{
    expect(getDiagnosticPage).not.toHaveBeenCalled();vi.mocked(getDiagnosticPage).mockResolvedValueOnce(records);await click('Inspect scan diagnostics');
    expect(container.textContent).toContain('3 (zero-based)');expect(container.textContent).toContain('Resolve the ambiguity');expect(container.textContent).toContain('Identifier unavailable');
    expect(container.textContent).not.toContain('Approve');expect(container.querySelector('a')).toBeNull();
});
it('continues empty pages, focuses navigation, and retries the failed position',async()=>{
    const cursor='d1.v.1.s.25.YQ';vi.mocked(getDiagnosticPage).mockResolvedValueOnce({...empty,nextCursor:cursor});await click('Inspect scan diagnostics');
    expect(container.textContent).toContain('Continue to the next page');vi.mocked(getDiagnosticPage).mockRejectedValueOnce(new Error('private failure'));
    await click('Next diagnostic page');expect(document.activeElement).toBe(container.querySelector('[role="alert"]'));expect(container.textContent).not.toContain('private failure');
    vi.mocked(getDiagnosticPage).mockResolvedValueOnce(records);await click('Retry diagnostics');
    expect(getDiagnosticPage).toHaveBeenLastCalledWith(cursor,expect.any(AbortSignal));expect(document.activeElement).toBe(container.querySelector('h2'));
});
it.each(['close','account','unmount'])('rejects late responses after %s',async(mode)=>{
    let resolve!: (page: DiagnosticPage)=>void;vi.mocked(getDiagnosticPage).mockImplementationOnce(()=>new Promise(r=>{resolve=r;}));await click('Inspect scan diagnostics');
    const signal=vi.mocked(getDiagnosticPage).mock.calls[0][1];
    if(mode==='close')await click('Hide scan diagnostics');
    else await act(async()=>root.render(mode==='account'?<ReviewStateDiagnostics subject="two"/>:<div>Access removed</div>));
    expect(signal.aborted).toBe(true);await act(async()=>resolve(records));expect(container.textContent).not.toContain('Resolve the ambiguity');
    if(mode==='account')expect(container.textContent).toContain('Inspect scan diagnostics');
});
it('replaces pages instead of accumulating records',async()=>{
    vi.mocked(getDiagnosticPage).mockResolvedValueOnce({...records,nextCursor:'d1.v.1.s.25.YQ'});await click('Inspect scan diagnostics');
    vi.mocked(getDiagnosticPage).mockResolvedValueOnce(empty);await click('Next diagnostic page');expect(container.querySelector('[aria-label="Diagnostic records"]')).toBeNull();
    expect(container.textContent).toContain('Refresh from start');
});
