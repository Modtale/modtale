import { expect, it, vi } from 'vitest';
import { getModerationQueuePage, validateQueuePage } from '@/modules/admin/api/moderationQueue';
import { api } from '@/utils/api';
vi.mock('@/utils/api', () => ({ api: { get: vi.fn() } }));
const empty = { items: [], nextCursor: null, unavailableItems: 0, order: 'PROJECT_VERSION' };
it('accepts an empty repair page with continuation', () => {
    expect(validateQueuePage({...empty,nextCursor:'1.s.0.YQ',unavailableItems:25}).nextCursor).toBe('1.s.0.YQ');
});
it.each([null, [], {...empty,order:'RISK'}, {...empty,nextCursor:3}, {...empty,unavailableItems:26}, {...empty,unavailableItems:0.5}, {...empty,items:Array(26).fill({id:'a'})}, {...empty,items:[{id:'a',title:{html:'bad'}}]}, {...empty,items:[{id:'a'},{id:'a'}]}])('rejects malformed envelopes before rendering', value => {
    expect(() => validateQueuePage(value)).toThrow();
});
it('sends bounded pagination and cancellation and rejects a nonadvancing page', async () => {
    const signal=new AbortController().signal;vi.mocked(api.get).mockResolvedValueOnce({data:empty});
    await getModerationQueuePage(null,signal);
    expect(api.get).toHaveBeenLastCalledWith('/admin/verification/queue/page',{params:{cursor:undefined,limit:25,filter:'ALL'},signal});
    vi.mocked(api.get).mockResolvedValueOnce({data:{...empty,nextCursor:'1.s.0.YQ'}});
    await expect(getModerationQueuePage('1.s.0.YQ',signal)).rejects.toThrow('did not advance');
});

it('binds the returned page and continuation to the selected filter', () => {
    expect(validateQueuePage({...empty,filter:'SECURITY',nextCursor:'2.SECURITY.s.0.YQ'},'SECURITY').filter).toBe('SECURITY');
    expect(() => validateQueuePage({...empty,filter:'OPERATIONS'},'SECURITY')).toThrow();
    expect(() => validateQueuePage({...empty,filter:'SECURITY',nextCursor:'1.s.0.YQ'},'SECURITY')).toThrow();
});
it('accepts bounded service diagnostics and rejects malformed service markers', () => {
    const row = {id:'project',pendingVersion:{id:'version',reviewStatus:'PENDING',scan:{status:'SUSPICIOUS',verdict:'REVIEW',scanState:'COMPLETED',
        reviewState:'RATE_LIMITED',serviceAttention:true,riskScore:0,knownIssueCount:7,newIssueCount:0,escalatedIssueCount:0}}};
    expect(validateQueuePage({...empty,items:[row]}).items[0].pendingVersion?.scan?.serviceAttention).toBe(true);
    expect(() => validateQueuePage({...empty,items:[{...row,pendingVersion:{...row.pendingVersion,scan:{...row.pendingVersion.scan,serviceAttention:'true'}}}]})).toThrow();
    expect(() => validateQueuePage({...empty,items:[{...row,pendingVersion:{...row.pendingVersion,scan:{...row.pendingVersion.scan,reviewState:'x'.repeat(65)}}}]})).toThrow();
});
