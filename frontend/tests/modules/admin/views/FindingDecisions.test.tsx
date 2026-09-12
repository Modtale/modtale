import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { beforeEach, afterEach, it, expect, vi } from 'vitest';
import { FindingDecisions } from '@/modules/admin/views/FindingDecisions';
import { findingReviews } from '@/modules/admin/api/findingReviews';
vi.mock('@/modules/admin/api/findingReviews', () => ({ findingReviews: { history: vi.fn(), record: vi.fn(), revoke: vi.fn() } }));
let container: HTMLDivElement; let root: Root;
const onSaved = vi.fn();
const event = { id: 'decision', actorId: 'reviewer', createdAt: Date.now(), expiresAt: Date.now() + 86400000,
    disposition: 'ACCEPT' as const, rationale: 'Reviewed the service integration', scope: 'WHOLE_ARTIFACT',
    finding: { path: 'Mod.class', description: 'connect', lineStart: 9 }, revokedDecisionId: null };
beforeEach(() => {
    vi.resetAllMocks(); container = document.createElement('div'); document.body.appendChild(container); root = createRoot(container);
    vi.mocked(findingReviews.history).mockResolvedValue({ events: [], nextOffset: null });
    vi.mocked(findingReviews.record).mockResolvedValue(event);
});
afterEach(async () => { await act(async () => root.unmount()); container.remove(); });
async function render(canDecide = true, token: string | undefined = 'snapshot') {
    await act(async () => root.render(<FindingDecisions projectId="project" versionId="version" token={token}
        issues={[{ filePath: 'Mod.class', type: 'Network', severity: 'LOW', description: 'connect', lineStart: 9, lineEnd: 9 } as any]}
        canDecide={canDecide} onSaved={onSaved} />));
}
async function click(text: string) {
    const button = [...container.querySelectorAll('button')].find(b => b.textContent === text)!;
    expect(button).toBeTruthy(); await act(async () => button.click());
}
async function reasoning() {
    const input = container.querySelector('textarea')!;
    await act(async () => {
        Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value')!.set!.call(input, 'Verified the documented integration');
        input.dispatchEvent(new Event('input', { bubbles: true }));
    });
}
it('records exact finding and snapshot, then requires a fresh review', async () => {
    await render(); await click('Finding decisions and history'); await reasoning(); await click('Record decision');
    expect(findingReviews.record).toHaveBeenCalledWith('project', 'version', 'snapshot', 0, 'REQUIRE_REVIEW', 'Verified the documented integration');
    expect(onSaved).toHaveBeenCalledOnce(); expect(container.textContent).toContain('Refresh the review evidence');
    expect([...container.querySelectorAll('button')].find(b => b.textContent === 'Record decision')?.disabled).toBe(true);
});
it('keeps reasoning after a conflicting write and does not report success', async () => {
    vi.mocked(findingReviews.record).mockRejectedValue(new Error('conflict'));
    await render(); await click('Finding decisions and history'); await reasoning(); await click('Record decision');
    expect(container.querySelector('textarea')?.value).toBe('Verified the documented integration');
    expect(container.querySelector('[role=alert]')).toBeTruthy(); expect(onSaved).not.toHaveBeenCalled();
});
it('requires current evidence and prevents read-only reviewers from deciding', async () => {
    await render(false); await click('Finding decisions and history');
    expect(container.querySelector('textarea')).toBeNull(); expect(container.textContent).not.toContain('Record decision');
    await act(async () => root.unmount()); root = createRoot(container);
    await render(true, ''); await click('Finding decisions and history');
    expect(findingReviews.history).toHaveBeenCalledTimes(1); expect(container.textContent).toContain('Reopen this review');
});
it('revokes with a reason while retaining the original record', async () => {
    vi.mocked(findingReviews.history).mockResolvedValue({ events: [event], nextOffset: null });
    vi.mocked(findingReviews.revoke).mockResolvedValue({ ...event, id: 'revocation', disposition: 'REVOKE', revokedDecisionId: event.id });
    await render(); await click('Finding decisions and history'); await reasoning(); await click('Revoke decision');
    expect(findingReviews.revoke).toHaveBeenCalledWith('project', 'version', 'snapshot', 'decision', 'Verified the documented integration');
    expect(container.querySelectorAll('li')).toHaveLength(2); expect(container.textContent).toContain('Revoked');
});

it('focuses changed decisions while keeping verified retained reasoning accessible', async () => {
    const changed = { ...event, id: 'changed', rationale: 'Earlier reasoning for a changed artifact' };
    vi.mocked(findingReviews.history).mockResolvedValue({ events: [event, changed], nextOffset: null, assessedAt: Date.now(),
        assessments: { decision: { state: 'APPLICABLE', explanation: 'The complete contents and context match.' },
            changed: { state: 'ARTIFACT_CHANGED', explanation: 'The artifact changed; inspect callers and resources.' } } });
    await render(); await click('Finding decisions and history');
    expect(container.querySelectorAll('li')).toHaveLength(1);
    expect(container.textContent).toContain('inspect callers and resources');
    expect(container.textContent).not.toContain('Reviewed the service integration');
    await click('Show retained reasoning (1)');
    expect(container.querySelectorAll('li')).toHaveLength(2);
    expect(container.textContent).toContain('The complete contents and context match.');
});
it('does not keep an acceptance collapsed after its assessed expiry', async () => {
    vi.mocked(findingReviews.history).mockResolvedValue({ events: [{ ...event, expiresAt: Date.now() - 1000 }], nextOffset: null,
        assessments: { decision: { state: 'APPLICABLE', explanation: 'Previously matched' } } });
    await render(); await click('Finding decisions and history');
    expect(container.querySelectorAll('li')).toHaveLength(1);
    expect(container.textContent).toContain('expired since the assessment');
    expect(container.textContent).not.toContain('Show retained reasoning');
});

it('updates earlier assessments when policy changes between history pages', async () => {
    vi.mocked(findingReviews.history)
        .mockResolvedValueOnce({ events: [event], nextOffset: 50, assessedAt: 1000,
            assessments: { decision: { state: 'APPLICABLE', explanation: 'Matched earlier policy' } } })
        .mockResolvedValueOnce({ events: [{ ...event, id: 'older' }], nextOffset: null, assessedAt: 2000,
            assessments: { decision: { state: 'POLICY_CHANGED', explanation: 'Policy changed since the first page' },
                older: { state: 'EXPIRED', explanation: 'Older decision expired' } } });
    await render(); await click('Finding decisions and history');
    expect(container.querySelectorAll('li')).toHaveLength(0);
    await click('Older decisions');
    expect(container.querySelectorAll('li')).toHaveLength(2);
    expect(container.textContent).toContain('Policy changed since the first page');
    expect(container.textContent).not.toContain('Show retained reasoning');
});
