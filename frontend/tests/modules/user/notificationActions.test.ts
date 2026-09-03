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

    it('rejects contributor invitations without projectId metadata', () => {
        expect(resolveNotificationAction(notification('CONTRIBUTOR_INVITE', { modId: 'project-1' }), true)).toBeNull();
        expect(resolveNotificationAction(notification('CONTRIBUTOR_INVITE'), true)).toBeNull();
    });
});
