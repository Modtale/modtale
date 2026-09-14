import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { beforeEach, afterEach, expect, it, vi } from 'vitest';
import { VerificationQueue } from '@/modules/admin/components/VerificationQueue';
import type { AdminVerificationQueueItem } from '@/types';
let root: Root; let container: HTMLDivElement;
beforeEach(() => { container = document.createElement('div'); document.body.append(container); root = createRoot(container); });
afterEach(async () => { await act(async () => root.unmount()); container.remove(); });
const item = (id: string, status: 'FAILED' | 'SUSPICIOUS', scanState?: string): AdminVerificationQueueItem => ({
    id, title: id, author: 'Creator', description: '', classification: 'PLUGIN', status: 'PENDING',
    pendingVersion: { id: 'v', versionNumber: '1', reviewStatus: 'PENDING', scan: {
        status, scanState, verdict: 'REVIEW', riskScore: 75, newIssueCount: 2, knownIssueCount: 1, escalatedIssueCount: 0,
    } },
});
it('separates service failures without hiding prior findings or inventing a malware risk label', async () => {
    const onReview = vi.fn();
    await act(async () => root.render(<VerificationQueue pendingProjects={[item('Threat', 'SUSPICIOUS'), item('Expired', 'FAILED', 'REMOTE_EXPIRED')]}
        loadingQueue={false} loadingReview={false} onReview={onReview} />));
    const content = container.querySelector('[aria-label="Content and security review"]')!;
    const operations = container.querySelector('[aria-label="Review service attention"]')!;
    expect(content.textContent).toContain('Threat'); expect(content.textContent).toContain('Risk 75');
    expect(content.textContent).not.toContain('Expired'); expect(operations.textContent).toContain('Expired');
    expect(operations.textContent).toContain('Review expired'); expect(operations.textContent).toContain('Security clearance withheld');
    expect(operations.textContent).not.toContain('Risk 75'); expect(operations.textContent).toContain('New 2 · Known 1');
    await act(async () => (operations.querySelector('button') as HTMLButtonElement).click());
    expect(onReview).toHaveBeenCalledWith('Expired');
});
it.each([['REMOTE_HELD', 'Review held'], ['REMOTE_CANCELLED', 'Review cancelled'], ['UNKNOWN', 'Review unavailable']])(
    'keeps %s failures visible when no content reviews remain', async (state, label) => {
        await act(async () => root.render(<VerificationQueue pendingProjects={[item('Failure', 'FAILED', state)]}
            loadingQueue={false} loadingReview={false} onReview={vi.fn()} />));
        expect(container.querySelector('[aria-label="Content and security review"]')).toBeNull();
        expect(container.textContent).toContain(label); expect(container.textContent).not.toContain('All Caught Up');
    });
