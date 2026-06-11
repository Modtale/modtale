import React, { act } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createRoot, type Root } from 'react-dom/client';
import { NotificationProvider, useNotifications } from '@/context/NotificationsContext';
import { api } from '@/utils/api';

vi.mock('@/utils/api', () => ({
    api: {
        get: vi.fn(),
        post: vi.fn(),
        delete: vi.fn()
    }
}));

const mockedApi = vi.mocked(api);

const settle = async (times = 8) => {
    for (let i = 0; i < times; i += 1) {
        await act(async () => {
            await Promise.resolve();
        });
    }
};

type ContextSnapshot = ReturnType<typeof useNotifications>;

const Probe = ({ onRender }: { onRender: (snapshot: ContextSnapshot) => void }) => {
    const snapshot = useNotifications();
    onRender(snapshot);

    return (
        <div
            id="probe"
            data-count={String(snapshot.notifications.length)}
            data-unread={String(snapshot.unreadCount)}
            data-loading={String(snapshot.loading)}
            data-idle={String(snapshot.isIdle)}
        />
    );
};

describe('NotificationsContext', () => {
    let container: HTMLDivElement;
    let root: Root;
    let latestSnapshot: ContextSnapshot;
    let consoleErrorSpy: ReturnType<typeof vi.spyOn>;

    beforeEach(() => {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        latestSnapshot = undefined as unknown as ContextSnapshot;
        consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
        vi.clearAllMocks();
    });

    afterEach(async () => {
        await act(async () => {
            root.unmount();
        });
        container.remove();
        consoleErrorSpy.mockRestore();
    });

    it('fetches notifications on mount and derives the unread count from the payload', async () => {
        mockedApi.get.mockResolvedValue({
            data: [
                { id: 'n1', title: 'One', message: 'hello', link: '/a', createdAt: '2026-06-08', read: false, type: 'FOLLOW' },
                { id: 'n2', title: 'Two', message: 'world', link: '/b', createdAt: '2026-06-08', read: true, type: 'COMMENT' }
            ]
        } as any);

        await act(async () => {
            root.render(
                <NotificationProvider userId="user-1">
                    <Probe onRender={snapshot => { latestSnapshot = snapshot; }} />
                </NotificationProvider>
            );
        });
        await settle();

        const probe = container.querySelector('#probe') as HTMLDivElement;
        expect(probe.dataset.count).toBe('2');
        expect(probe.dataset.unread).toBe('1');
        expect(probe.dataset.loading).toBe('false');
        expect(mockedApi.get).toHaveBeenCalledWith('/notifications');
        expect(latestSnapshot.notifications.map(notification => notification.id)).toEqual(['n1', 'n2']);
    });

    it('optimistically marks notifications as read and refreshes from the server when the write fails', async () => {
        mockedApi.get
            .mockResolvedValueOnce({
                data: [
                    { id: 'n1', title: 'One', message: 'hello', link: '/a', createdAt: '2026-06-08', read: false, type: 'FOLLOW' }
                ]
            } as any)
            .mockResolvedValueOnce({
                data: [
                    { id: 'n1', title: 'One', message: 'hello', link: '/a', createdAt: '2026-06-08', read: false, type: 'FOLLOW' }
                ]
            } as any);
        mockedApi.post.mockRejectedValueOnce(new Error('write failed'));

        await act(async () => {
            root.render(
                <NotificationProvider userId="user-1">
                    <Probe onRender={snapshot => { latestSnapshot = snapshot; }} />
                </NotificationProvider>
            );
        });
        await settle();

        expect(latestSnapshot.unreadCount).toBe(1);

        await act(async () => {
            await latestSnapshot.markAsRead('n1', false);
        });
        await settle();

        expect(mockedApi.post).toHaveBeenCalledWith('/notifications/n1/read');
        expect(mockedApi.get).toHaveBeenCalledTimes(2);
        expect(latestSnapshot.unreadCount).toBe(1);
        expect(latestSnapshot.notifications[0].read).toBe(false);
    });

    it('removes dismissed notifications and decrements the unread count when needed', async () => {
        mockedApi.get.mockResolvedValue({
            data: [
                { id: 'n1', title: 'One', message: 'hello', link: '/a', createdAt: '2026-06-08', read: false, type: 'FOLLOW' },
                { id: 'n2', title: 'Two', message: 'world', link: '/b', createdAt: '2026-06-08', read: true, type: 'COMMENT' }
            ]
        } as any);
        mockedApi.delete.mockResolvedValue({ data: null } as any);

        await act(async () => {
            root.render(
                <NotificationProvider userId="user-1">
                    <Probe onRender={snapshot => { latestSnapshot = snapshot; }} />
                </NotificationProvider>
            );
        });
        await settle();

        await act(async () => {
            await latestSnapshot.dismiss('n1');
        });
        await settle();

        expect(mockedApi.delete).toHaveBeenCalledWith('/notifications/n1');
        expect(latestSnapshot.notifications.map(notification => notification.id)).toEqual(['n2']);
        expect(latestSnapshot.unreadCount).toBe(0);
    });

    it('clears all notifications and resets unread state in one pass', async () => {
        mockedApi.get.mockResolvedValue({
            data: [
                { id: 'n1', title: 'One', message: 'hello', link: '/a', createdAt: '2026-06-08', read: false, type: 'FOLLOW' },
                { id: 'n2', title: 'Two', message: 'world', link: '/b', createdAt: '2026-06-08', read: false, type: 'COMMENT' }
            ]
        } as any);
        mockedApi.delete.mockResolvedValue({ data: null } as any);

        await act(async () => {
            root.render(
                <NotificationProvider userId="user-1">
                    <Probe onRender={snapshot => { latestSnapshot = snapshot; }} />
                </NotificationProvider>
            );
        });
        await settle();

        await act(async () => {
            await latestSnapshot.clearAll();
        });
        await settle();

        expect(mockedApi.delete).toHaveBeenCalledWith('/notifications/clear-all');
        expect(latestSnapshot.notifications).toEqual([]);
        expect(latestSnapshot.unreadCount).toBe(0);
    });
});
