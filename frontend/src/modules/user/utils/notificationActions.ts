import type { Notification } from '@/context/NotificationsContext';

export interface NotificationActionRequest {
    endpoint: string;
    body?: Record<string, boolean>;
}

export const resolveNotificationAction = (
    notification: Notification,
    accept: boolean
): NotificationActionRequest | null => {
    const projectId = notification.metadata?.projectId || notification.metadata?.modId;

    if (notification.type === 'TRANSFER_REQUEST') {
        return projectId
            ? { endpoint: `/projects/${projectId}/transfer/resolve`, body: { accept } }
            : null;
    }

    if (notification.type === 'ORG_INVITE') {
        const orgId = notification.metadata?.orgId;
        return orgId
            ? { endpoint: `/orgs/${orgId}/invite/${accept ? 'accept' : 'decline'}` }
            : null;
    }

    if (notification.type === 'CONTRIBUTOR_INVITE') {
        return projectId
            ? { endpoint: `/projects/${projectId}/invite/${accept ? 'accept' : 'decline'}` }
            : null;
    }

    return null;
};
