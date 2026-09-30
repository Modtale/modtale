import React, { act } from 'react';
import { createRoot } from 'react-dom/client';
import { describe, expect, it, vi } from 'vitest';
import { VerificationQueue } from '@/modules/admin/components/VerificationQueue';

describe('Verification queue rendering', () => {
    it('renders the first page and lets reviewers reach later projects', async () => {
        const container = document.createElement('div');
        document.body.append(container);
        const root = createRoot(container);
        const onReview = vi.fn();
        const pendingProjects = Array.from({ length: 61 }, (_, index) => ({
            id: `project-${index}`,
            title: `Project ${index}`,
            author: 'Creator',
            description: 'Pending review',
            imageUrl: '/assets/favicon.svg',
            classification: 'PLUGIN' as const,
            status: 'PENDING' as const,
            updatedAt: '2026-09-27'
        }));

        await act(async () => root.render(
            <VerificationQueue pendingProjects={pendingProjects} loadingQueue={false}
                loadingReview={false} onReview={onReview} />
        ));
        expect(container.querySelectorAll('img')).toHaveLength(20);
        expect(container.textContent).toContain('41 remaining');
        const showMore = [...container.querySelectorAll('button')].find(button => button.textContent?.includes('Show more'));
        await act(async () => showMore?.click());
        expect(container.querySelectorAll('img')).toHaveLength(40);
        expect(container.textContent).toContain('21 remaining');
        const verifyButtons = [...container.querySelectorAll('button')]
            .filter(button => button.textContent?.includes('Verify Project'));
        await act(async () => verifyButtons[39].click());
        expect(onReview).toHaveBeenCalledWith('project-39');
        await act(async () => root.unmount());
        container.remove();
    });
});
