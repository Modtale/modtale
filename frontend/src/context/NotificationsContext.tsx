import React, { createContext, useContext, useState, useEffect, useCallback, useRef } from 'react';
import { api } from '../utils/api';

export interface Notification {
    id: string;
    title: string;
    message: string;
    link: string;
    iconUrl?: string;
    createdAt: string;
    read: boolean;
    type: string;
    metadata?: Record<string, string>;
}

interface NotificationContextType {
    notifications: Notification[];
    unreadCount: number;
    loading: boolean;
    isIdle: boolean;
    refresh: () => Promise<void>;
    markAsRead: (id: string, readState: boolean) => Promise<void>;
    markAllAsRead: () => Promise<void>;
    dismiss: (id: string) => Promise<void>;
    clearAll: () => Promise<void>;
}

const NotificationContext = createContext<NotificationContextType | undefined>(undefined);

export const NotificationProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    const [notifications, setNotifications] = useState<Notification[]>([]);
    const [unreadCount, setUnreadCount] = useState(0);
    const [loading, setLoading] = useState(true);

    const [isIdle, setIsIdle] = useState(false);
    const idleTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    const IDLE_TIMEOUT = 5 * 60 * 1000;
    const POLL_INTERVAL = 5 * 60 * 1000;

    const fetchNotifications = useCallback(async () => {
        try {
            const res = await api.get('/notifications');
            setNotifications(res.data);
            setUnreadCount(res.data.filter((n: Notification) => !n.read).length);
        } catch (e) {
            console.error("Failed to fetch notifications");
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        const resetIdleTimer = () => {
            if (isIdle) {
                setIsIdle(false);
                fetchNotifications();
            }
            if (idleTimerRef.current) clearTimeout(idleTimerRef.current);
            idleTimerRef.current = setTimeout(() => setIsIdle(true), IDLE_TIMEOUT);
        };

        const events = ['mousedown', 'mousemove', 'keydown', 'scroll', 'touchstart'];
        events.forEach(event => document.addEventListener(event, resetIdleTimer, { passive: true }));
        resetIdleTimer();

        return () => {
            if (idleTimerRef.current) clearTimeout(idleTimerRef.current);
            events.forEach(event => document.removeEventListener(event, resetIdleTimer));
        };
    }, [isIdle, fetchNotifications]);

    useEffect(() => {
        fetchNotifications();

        const interval = setInterval(() => {
            if (!isIdle) {
                fetchNotifications();
            }
        }, POLL_INTERVAL);

        return () => clearInterval(interval);
    }, [isIdle, fetchNotifications]);

    const refresh = async () => fetchNotifications();

    const markAsRead = async (id: string, readState: boolean) => {
        const newStatus = !readState;
        setNotifications(prev => prev.map(n => n.id === id ? { ...n, read: newStatus } : n));
        setUnreadCount(prev => !newStatus ? prev + 1 : Math.max(0, prev - 1));

        try {
            await api.post(`/notifications/${id}/${newStatus ? 'read' : 'unread'}`);
        } catch (e) { console.error(e); fetchNotifications(); }
    };

    const markAllAsRead = async () => {
        setNotifications(prev => prev.map(n => ({ ...n, read: true })));
        setUnreadCount(0);
        try { await api.post('/notifications/read-all'); } catch(e) { console.error(e); }
    };

    const dismiss = async (id: string) => {
        setNotifications(prev => {
            const isUnread = prev.find(n => n.id === id && !n.read);
            if (isUnread) setUnreadCount(c => Math.max(0, c - 1));
            return prev.filter(n => n.id !== id);
        });
        try { await api.delete(`/notifications/${id}`); } catch(e) { console.error(e); }
    };

    const clearAll = async () => {
        setNotifications([]);
        setUnreadCount(0);
        try { await api.delete('/notifications/clear-all'); } catch(e) { console.error(e); }
    };

    return (
        <NotificationContext.Provider value={{
            notifications, unreadCount, loading, isIdle,
            refresh, markAsRead, markAllAsRead, dismiss, clearAll
        }}>
            {children}
        </NotificationContext.Provider>
    );
};

export const useNotifications = () => {
    const context = useContext(NotificationContext);
    if (!context) throw new Error("useNotifications must be used within a NotificationProvider");
    return context;
};