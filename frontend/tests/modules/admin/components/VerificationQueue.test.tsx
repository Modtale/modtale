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
    expect(onReview).toHaveBeenCalledWith('Expired', 'v');
});
it.each([['REMOTE_BINDING_MISSING', 'Review state needs repair'], ['REMOTE_BINDING_MISMATCH', 'Review state needs repair'], ['REMOTE_UNSUPPORTED_CONTEXT', 'Review context unsupported'], ['REMOTE_HELD', 'Review held'], ['REMOTE_CANCELLED', 'Review cancelled'], ['UNKNOWN', 'Review unavailable']])(
    'keeps %s failures visible when no content reviews remain', async (state, label) => {
        await act(async () => root.render(<VerificationQueue pendingProjects={[item('Failure', 'FAILED', state)]}
            loadingQueue={false} loadingReview={false} onReview={vi.fn()} />));
        expect(container.querySelector('[aria-label="Content and security review"]')).toBeNull();
        expect(container.textContent).toContain(label); expect(container.textContent).not.toContain('All Caught Up');
    });

it('opens the exact version when multiple queue rows belong to one project', async () => {
    const first=item('Project', 'SUSPICIOUS');const second=item('Project', 'FAILED', 'REMOTE_HELD');
    second.pendingVersion!.id='second';second.pendingVersion!.versionNumber='2';const onReview=vi.fn();
    await act(async () => root.render(<VerificationQueue pendingProjects={[first,second]} loadingQueue={false} loadingReview={false} onReview={onReview} />));
    const buttons=container.querySelectorAll('button');expect(buttons).toHaveLength(2);
    await act(async () => (buttons[1] as HTMLButtonElement).click());expect(onReview).toHaveBeenLastCalledWith('Project','second');
    await act(async () => (buttons[0] as HTMLButtonElement).click());expect(onReview).toHaveBeenLastCalledWith('Project','v');
});

it('does not claim completion when an empty page still has continuation or repair entries', async () => {
    await act(async () => root.render(<VerificationQueue pendingProjects={[]} loadingQueue={false} loadingReview={false} onReview={vi.fn()} hasMore unavailableItems={25} />));
    expect(container.textContent).toContain('Continue to the next page');expect(container.textContent).not.toContain('All Caught Up');
});

it('keeps the focused review button mounted during refresh of loaded rows', async () => {
    const props={pendingProjects:[item('Project','SUSPICIOUS')],loadingReview:false,onReview:vi.fn()};
    await act(async () => root.render(<VerificationQueue {...props} loadingQueue={false} />));
    const button=container.querySelector('button')!;button.focus();
    await act(async () => root.render(<VerificationQueue {...props} loadingQueue />));
    expect(document.activeElement).toBe(button);expect(button.closest('[inert]')).toBeNull();
    await act(async () => root.render(<VerificationQueue {...props} loadingQueue={false} />));
    expect(document.activeElement).toBe(button);
});
