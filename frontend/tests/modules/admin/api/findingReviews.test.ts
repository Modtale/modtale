import { expect, it, vi } from 'vitest';
import { api } from '@/utils/api';
import { findingReviews, loadPriorFindingReasoning } from '@/modules/admin/api/findingReviews';
vi.mock('@/utils/api', () => ({ api: { get: vi.fn().mockResolvedValue({ data: {} }), post: vi.fn().mockResolvedValue({ data: {} }) } }));
it('encodes identifiers and binds every read/write to the reviewed snapshot', async () => {
    await findingReviews.history('p/x', 'v/x', 'token', 50);
    expect(api.get).toHaveBeenCalledWith('/admin/projects/p%2Fx/versions/v%2Fx/finding-decisions', { headers: { 'If-Match': 'token' }, params: { offset: 50 } });
    await findingReviews.record('p', 'v', 'token', 3, 'ACCEPT', 'Reasoning here');
    expect(api.post).toHaveBeenCalledWith('/admin/projects/p/versions/v/finding-decisions', { issueIndex: 3, disposition: 'ACCEPT', rationale: 'Reasoning here' }, { headers: { 'If-Match': 'token' } });
    await findingReviews.revoke('p', 'v', 'token', 'd/x', 'Correction here');
    expect(api.post).toHaveBeenCalledWith('/admin/projects/p/versions/v/finding-decisions/d%2Fx/revoke', { rationale: 'Correction here' }, { headers: { 'If-Match': 'token' } });
});

it('binds prior reasoning to both selected versions, occurrence and project snapshot', async () => {
    await loadPriorFindingReasoning('p/x', 'target/x', 'source/x', 7, 'project-token');
    expect(api.get).toHaveBeenLastCalledWith('/admin/projects/p%2Fx/versions/target%2Fx/prior-finding-reasoning',
        { headers: { 'If-Match': 'project-token' }, params: { sourceVersionId: 'source/x', issueIndex: 7 } });
});
