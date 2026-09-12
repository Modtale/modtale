import { api } from '@/utils/api';

export interface FindingDecision {
    id: string;
    actorId: string;
    createdAt: number;
    expiresAt: number;
    disposition: 'ACCEPT' | 'REQUIRE_REVIEW' | 'REVOKE';
    rationale: string;
    scope: string;
    finding: { path: string; description: string; lineStart: number };
    revokedDecisionId: string | null;
    supersedesDecisionId?: string | null;
}
export interface FindingHistory { events: FindingDecision[]; nextOffset: number | null }
const path = (project: string, version: string) => `/admin/projects/${encodeURIComponent(project)}/versions/${encodeURIComponent(version)}/finding-decisions`;
export const findingReviews = {
    history: async (project: string, version: string, token: string, offset = 0): Promise<FindingHistory> =>
        (await api.get(path(project, version), { headers: { 'If-Match': token }, params: { offset } })).data,
    record: async (project: string, version: string, token: string, issueIndex: number, disposition: 'ACCEPT' | 'REQUIRE_REVIEW', rationale: string): Promise<FindingDecision> =>
        (await api.post(path(project, version), { issueIndex, disposition, rationale }, { headers: { 'If-Match': token } })).data,
    revoke: async (project: string, version: string, token: string, decision: string, rationale: string): Promise<FindingDecision> =>
        (await api.post(`${path(project, version)}/${encodeURIComponent(decision)}/revoke`, { rationale }, { headers: { 'If-Match': token } })).data,
};
