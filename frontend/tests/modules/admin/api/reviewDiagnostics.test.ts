import { expect, it, vi } from 'vitest';
import { getDiagnosticPage, validDiagnosticCursor, validateDiagnosticPage } from '@/modules/admin/api/reviewDiagnostics';
import { api } from '@/utils/api';
vi.mock('@/utils/api', () => ({ api: { get: vi.fn() } }));
const empty = { items: [], nextCursor: null, examinedSlots: 0, scope: 'PENDING_SCAN_STRUCTURE' };
const item = { position: { projectIdType: 'STRING', projectId: 'a', versionIndex: 0 }, versionId: null, reasons: ['INVALID_VERSION_ID'] };
it('accepts empty continuation and preserves BSON distinctions', () => {
    expect(validateDiagnosticPage({ ...empty, examinedSlots: 25, nextCursor: 'd1.v.1.s.25.YQ' }).nextCursor).toBeTruthy();
    const id='abcdefabcdefabcdefabcdef';
    expect(validateDiagnosticPage({ ...empty, examinedSlots: 2, items: [{...item,position:{...item.position,projectId:id}}, {...item,position:{...item.position,projectId:id,projectIdType:'OBJECT_ID'}}] }).items).toHaveLength(2);
});
it.each(['1.s.0.YQ','d1.a.1.s.1.YQ','d1.v.1.s.0.YR','d1.v.1.s.0._w','d1.v.1.s.16777217.YQ','d1.v.1.s.00.YQ','d1.v.1.s.0.YQ=','d1.v.1.o.0.YQ'])('rejects malformed cursor %s', value => expect(validDiagnosticCursor(value)).toBe(false));
it.each([null, {...empty,scope:'OTHER'}, {...empty,examinedSlots:26}, {...empty,items:[item]}, {...empty,examinedSlots:1,items:[{...item,reasons:['UNKNOWN']}]}, {...empty,examinedSlots:1,items:[{...item,reasons:['toString']}]}, {...empty,examinedSlots:1,items:[{...item,versionId:'bad\n'}]}, {...empty,examinedSlots:2,items:[item,item]}])('rejects unsafe diagnostic data', value => expect(() => validateDiagnosticPage(value)).toThrow());
it('bounds requests and rejects nonadvancing responses', async () => {
    const signal=new AbortController().signal;vi.mocked(api.get).mockResolvedValueOnce({data:empty});await getDiagnosticPage(null,signal);
    expect(api.get).toHaveBeenLastCalledWith('/admin/verification/diagnostics/page',{params:{cursor:undefined,limit:25},signal});
    vi.mocked(api.get).mockResolvedValueOnce({data:{...empty,nextCursor:'d1.v.1.s.0.YQ'}});
    await expect(getDiagnosticPage('d1.v.1.s.0.YQ',signal)).rejects.toThrow('did not advance');
});
