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
it('keeps new findings from a failed review in security triage with the service failure visible', async () => {
    const onReview = vi.fn();
    await act(async () => root.render(<VerificationQueue pendingProjects={[item('Threat', 'SUSPICIOUS'), item('Expired', 'FAILED', 'REMOTE_EXPIRED')]}
        loadingQueue={false} loadingReview={false} onReview={onReview} />));
    const content = container.querySelector('[aria-label="Content and security review"]')!;
    const operations = container.querySelector('[aria-label="Review service attention"]');
    expect(content.textContent).toContain('Threat'); expect(content.textContent).toContain('Risk 75');
    expect(content.textContent).toContain('Expired'); expect(content.textContent).toContain('Review expired');
    expect(content.textContent).toContain('Security clearance withheld');
    expect(content.textContent).toContain('New 2 · Known 1');
    expect(operations).toBeNull();
    await act(async () => ([...content.querySelectorAll('button')].at(-1) as HTMLButtonElement).click());
    expect(onReview).toHaveBeenCalledWith('Expired', 'v');
});
it('routes completed provider failures without new findings to diagnostics', async () => {
    const service = item('Provider', 'SUSPICIOUS', 'COMPLETED');
    service.pendingVersion!.scan!.serviceAttention = true;
    service.pendingVersion!.scan!.reviewState = 'RATE_LIMITED';
    service.pendingVersion!.scan!.newIssueCount = 0;
    service.pendingVersion!.scan!.knownIssueCount = 7;
    const onReview = vi.fn();
    await act(async () => root.render(<VerificationQueue pendingProjects={[service]} loadingQueue={false} loadingReview={false} onReview={onReview} />));
    expect(container.querySelector('[aria-label="Content and security review"]')).toBeNull();
    const operations = container.querySelector('[aria-label="Review service attention"]')!;
    expect(operations.textContent).toContain('Review service rate limit reached');
    expect(operations.textContent).toContain('Security clearance withheld');
    expect(operations.textContent).toContain('New 0 · Known 7');
    expect(operations.textContent).not.toContain('Risk 75');
    await act(async () => (operations.querySelector('button') as HTMLButtonElement).click());
    expect(onReview).toHaveBeenCalledWith('Provider', 'v');
});
it('keeps a provider failure with new findings in security triage', async () => {
    const mixed = item('Mixed', 'SUSPICIOUS', 'COMPLETED');
    mixed.pendingVersion!.scan!.serviceAttention = true;
    mixed.pendingVersion!.scan!.reviewState = 'RATE_LIMITED';
    await act(async () => root.render(<VerificationQueue pendingProjects={[mixed]} loadingQueue={false} loadingReview={false} onReview={vi.fn()} />));
    const content = container.querySelector('[aria-label="Content and security review"]')!;
    expect(content.textContent).toContain('Mixed');
    expect(content.textContent).toContain('Review service rate limit reached');
    expect(content.textContent).toContain('Risk 75');
    expect(container.querySelector('[aria-label="Review service attention"]')).toBeNull();
});
it.each([['REMOTE_ORIGIN_UNVERIFIED', 'Original review service unverified'], ['REMOTE_CONTEXT_CONFLICT', 'Review service context conflict'], ['REMOTE_ISOLATED', 'Local review isolated'], ['REMOTE_BINDING_MISSING', 'Review state needs repair'], ['REMOTE_BINDING_MISMATCH', 'Review state needs repair'], ['REMOTE_UNSUPPORTED_CONTEXT', 'Review context unsupported'], ['REMOTE_HELD', 'Review held'], ['REMOTE_CANCELLED', 'Review cancelled'], ['UNKNOWN', 'Review unavailable']])(
    'keeps %s failures visible when no content reviews remain', async (state, label) => {
        const failure = item('Failure', 'FAILED', state); failure.pendingVersion!.scan!.newIssueCount = 0;
        await act(async () => root.render(<VerificationQueue pendingProjects={[failure]}
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


it('reveals later rows on the current page without changing their exact review target', async () => {
    const pendingProjects = Array.from({ length: 25 }, (_, index) => ({
        ...item(`Project ${index}`, 'SUSPICIOUS'),
        id: `project-${index}`,
        imageUrl: '/assets/favicon.svg',
        pendingVersion: { ...item('', 'SUSPICIOUS').pendingVersion!, id: `version-${index}` },
    }));
    const onReview = vi.fn();
    await act(async () => root.render(<VerificationQueue pendingProjects={pendingProjects}
        loadingQueue={false} loadingReview={false} onReview={onReview} />));
    expect(container.querySelectorAll('img')).toHaveLength(20);
    expect(container.textContent).toContain('5 remaining on this page');
    const showMore = [...container.querySelectorAll('button')].find(button => button.textContent?.includes('Show more'))!;
    await act(async () => showMore.click());
    expect(container.querySelectorAll('img')).toHaveLength(25);
    expect(container.textContent).not.toContain('Show more');
    const reviewButtons = [...container.querySelectorAll('button')].filter(button => button.textContent?.includes('Verify Project'));
    await act(async () => reviewButtons[24].click());
    expect(onReview).toHaveBeenCalledWith('project-24', 'version-24');
    expect(container.querySelector('img')?.getAttribute('loading')).toBe('lazy');
    expect(container.querySelector('img')?.getAttribute('decoding')).toBe('async');
});

it('preserves service diagnostics when revealing the remaining page rows', async () => {
    const pendingProjects = Array.from({ length: 21 }, (_, index) => item(`Project ${index}`, 'SUSPICIOUS'));
    const service = item('Provider', 'FAILED', 'REMOTE_HELD');
    service.pendingVersion!.scan!.newIssueCount = 0;
    pendingProjects[20] = service;
    await act(async () => root.render(<VerificationQueue pendingProjects={pendingProjects}
        loadingQueue={false} loadingReview={false} onReview={vi.fn()} />));
    expect(container.querySelector('[aria-label="Review service attention"]')).toBeNull();
    const showMore = [...container.querySelectorAll('button')].find(button => button.textContent?.includes('Show more'))!;
    await act(async () => showMore.click());
    const operations = container.querySelector('[aria-label="Review service attention"]')!;
    expect(operations.textContent).toContain('Provider');
    expect(operations.textContent).toContain('Review held');
    expect(operations.textContent).toContain('Security clearance withheld');
    expect(operations.textContent).not.toContain('Risk 75');
});
