import { expect, it, vi } from 'vitest';
import { getOriginPage, validOriginCursor, validateOriginPage } from '@/modules/admin/api/reviewOrigins';
import { api } from '@/utils/api';
vi.mock('@/utils/api', () => ({ api: { get: vi.fn() } }));
const empty = { items: [], nextCursor: null, examinedSlots: 0, scope: 'RETAINED_REVIEW_ORIGINS' };
const item = { position: { projectIdType: 'STRING', projectId: 'a', versionIndex: 0 }, versionId: null, ambiguousVersion: true, requestId: null, jobId: null, originState: 'MISSING' };
it('accepts empty continuation and distinguishes BSON root identities', () => {
    expect(validateOriginPage({ ...empty, examinedSlots: 25, nextCursor: 'o1.v.1.s.25.YQ' }).nextCursor).toBeTruthy();
    const id='abcdefabcdefabcdefabcdef';
    expect(validateOriginPage({ ...empty, examinedSlots: 2, items: [{...item,position:{...item.position,projectId:id}}, {...item,position:{...item.position,projectId:id,projectIdType:'OBJECT_ID'}}] }).items).toHaveLength(2);
});
it.each(['d1.v.1.s.0.YQ','1.s.0.YQ','o1.a.1.s.1.YQ','o1.v.1.s.0.YR','o1.v.1.s.0._w','o1.v.1.s.16777217.YQ','o1.v.1.o.0.YQ'])('rejects out-of-scope or malformed cursor %s', value => expect(validOriginCursor(value)).toBe(false));
it.each([null, {...empty,scope:'OTHER'}, {...empty,examinedSlots:26}, {...empty,items:[item]}, {...empty,examinedSlots:1,items:[{...item,originState:'toString'}]}, {...empty,examinedSlots:1,items:[{...item,ambiguousVersion:false}]}, {...empty,examinedSlots:1,items:[{...item,jobId:'payload'}]}, {...empty,examinedSlots:1,items:[{...item,requestId:['payload']}]}, {...empty,examinedSlots:2,items:[item,item]}])('rejects unsafe origin inventory data', value => expect(() => validateOriginPage(value)).toThrow());
it('bounds requests, forwards cancellation and rejects a repeated cursor', async () => {
    const signal=new AbortController().signal;vi.mocked(api.get).mockResolvedValueOnce({data:empty});await getOriginPage(null,signal);
    expect(api.get).toHaveBeenLastCalledWith('/admin/verification/origins/page',{params:{cursor:undefined,limit:25},signal});
    vi.mocked(api.get).mockResolvedValueOnce({data:{...empty,nextCursor:'o1.v.1.s.0.YQ'}});
    await expect(getOriginPage('o1.v.1.s.0.YQ',signal)).rejects.toThrow('did not advance');
});
