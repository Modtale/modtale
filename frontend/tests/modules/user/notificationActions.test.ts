import { describe, expect, it } from 'vitest';
import type { Notification } from '@/context/NotificationsContext';
import { resolveNotificationAction } from '@/modules/user/utils/notificationActions';

const notification = (type: string, metadata?: Record<string, string>): Notification => ({
    id: 'notification-1',
    title: 'Action required',
    message: 'Please respond',
    link: '/dashboard/projects',
    createdAt: '2026-08-25T12:00:00',
    read: false,
    type,
    metadata
});

describe('resolveNotificationAction', () => {
    it('uses canonical projectId metadata for contributor invitations', () => {
        const invite = notification('CONTRIBUTOR_INVITE', { projectId: 'project-1' });

        expect(resolveNotificationAction(invite, true)).toEqual({
            endpoint: '/projects/project-1/invite/accept'
        });
        expect(resolveNotificationAction(invite, false)).toEqual({
            endpoint: '/projects/project-1/invite/decline'
        });
    });

    it('uses canonical projectId metadata for project transfers', () => {
        const transfer = notification('TRANSFER_REQUEST', { projectId: 'project-1' });

        expect(resolveNotificationAction(transfer, true)).toEqual({
            endpoint: '/projects/project-1/transfer/resolve',
            body: { accept: true }
        });
    });

    it('supports legacy modId metadata without consuming malformed actions', () => {
        expect(resolveNotificationAction(
            notification('CONTRIBUTOR_INVITE', { modId: 'legacy-project' }),
            true
        )).toEqual({ endpoint: '/projects/legacy-project/invite/accept' });

        expect(resolveNotificationAction(notification('CONTRIBUTOR_INVITE'), true)).toBeNull();
    });
});
