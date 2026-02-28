import React, { useState, useEffect, useRef } from 'react';
import { Bell, X, Trash2, Check, X as XIcon, RefreshCw, Circle } from 'lucide-react';
import { api, BACKEND_URL } from '../../utils/api';
import { useNotifications, type Notification } from '../../context/NotificationsContext.tsx';

export const NotificationMenu: React.FC = () => {
    const [isOpen, setIsOpen] = useState(false);
    const [actionLoading, setActionLoading] = useState<string | null>(null);
    const [isRefreshing, setIsRefreshing] = useState(false);
    const menuRef = useRef<HTMLDivElement>(null);

    const {
        notifications, unreadCount, isIdle, refresh,
        markAllAsRead, markAsRead, dismiss, clearAll
    } = useNotifications();

    const handleRefresh = async () => {
        setIsRefreshing(true);
        await refresh();
        setIsRefreshing(false);
    };

    useEffect(() => {
        if (isOpen && unreadCount > 0) {
            markAllAsRead();
        }
    }, [isOpen]);

    useEffect(() => {
        const handleClick = (e: MouseEvent) => {
            if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
                setIsOpen(false);
            }
        };
        const handleScroll = () => setIsOpen(false);
        document.addEventListener('mousedown', handleClick);
        window.addEventListener('scroll', handleScroll, { passive: true });
        return () => {
            document.removeEventListener('mousedown', handleClick);
            window.removeEventListener('scroll', handleScroll);
        };
    }, []);

    const handleAction = async (e: React.MouseEvent, n: Notification, accept: boolean) => {
        e.preventDefault();
        e.stopPropagation();
        setActionLoading(n.id);
        try {
            if (n.type === 'TRANSFER_REQUEST' && n.metadata?.modId) {
                await api.post(`/projects/${n.metadata.modId}/transfer/resolve`, { accept });
            } else if (n.type === 'ORG_INVITE' && n.metadata?.orgId) {
                const endpoint = accept ? `/orgs/${n.metadata.orgId}/invite/accept` : `/orgs/${n.metadata.orgId}/invite/decline`;
                await api.post(endpoint);
            } else if (n.type === 'CONTRIBUTOR_INVITE' && n.metadata?.modId) {
                const endpoint = accept ? `/projects/${n.metadata.modId}/invite/accept` : `/projects/${n.metadata.modId}/invite/decline`;
                await api.post(endpoint);
            }
            dismiss(n.id);
        } catch (err) {
            console.error("Action failed", err);
            alert("Action failed. The request may have expired.");
        } finally {
            setActionLoading(null);
        }
    };

    const toggleReadStatus = (e: React.MouseEvent, id: string, currentReadStatus: boolean) => {
        e.preventDefault();
        e.stopPropagation();
        markAsRead(id, currentReadStatus);
    };

    return (
        <div className="relative" ref={menuRef}>
            <button
                onClick={() => setIsOpen(!isOpen)}
                className={`relative p-2 transition-colors rounded-lg hover:bg-slate-100 dark:hover:bg-white/5 ${isIdle ? 'opacity-50' : 'text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-white'}`}
                title={isIdle ? "Notifications paused (Idle)" : "Notifications"}
            >
                <Bell className="w-5 h-5" />
                {unreadCount > 0 && <span className="absolute top-1 right-1 w-2.5 h-2.5 bg-red-500 rounded-full border-2 border-white dark:border-modtale-card animate-pulse"></span>}
            </button>

            {isOpen && (
                <div className="fixed left-4 right-4 top-16 md:absolute md:left-auto md:right-0 md:top-full md:mt-2 md:w-96 max-h-[80vh] bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/10 rounded-xl shadow-2xl z-[200] overflow-hidden animate-in fade-in zoom-in-95 duration-100">
                    <div className="p-3 border-b border-slate-100 dark:border-white/5 flex justify-between items-center bg-slate-50 dark:bg-black/20">
                        <div className="flex items-center gap-2">
                            <h3 className="font-bold text-sm text-slate-900 dark:text-white">Notifications</h3>
                            <button
                                onClick={handleRefresh}
                                disabled={isRefreshing}
                                className={`p-1 rounded-md text-slate-400 hover:text-slate-600 dark:hover:text-white hover:bg-slate-200 dark:hover:bg-white/10 transition-colors ${isRefreshing ? 'animate-spin' : ''}`}
                                title="Refresh"
                            >
                                <RefreshCw className="w-3 h-3" />
                            </button>
                        </div>
                        {notifications.length > 0 && (
                            <button onClick={clearAll} className="text-xs text-modtale-accent font-bold hover:underline flex items-center gap-1">
                                <Trash2 className="w-3 h-3" /> Clear All
                            </button>
                        )}
                    </div>

                    <div className="max-h-96 overflow-y-auto scrollbar-thin scrollbar-thumb-slate-300 dark:scrollbar-thumb-white/10 scrollbar-track-transparent custom-scrollbar">
                        {notifications.length === 0 ? (
                            <div className="p-8 text-center text-slate-500 dark:text-slate-400 text-xs"><Bell className="w-8 h-8 mx-auto mb-2 opacity-20" />No notifications</div>
                        ) : (
                            <div className="divide-y divide-slate-100 dark:divide-white/5">
                                {notifications.map(n => (
                                    <a
                                        href={n.link}
                                        key={n.id}
                                        className={`p-4 transition-colors group relative flex items-start gap-3 hover:bg-slate-100 dark:hover:bg-white/10 ${n.read ? 'bg-white dark:bg-modtale-card' : 'bg-slate-50 dark:bg-white/5'}`}
                                    >
                                        <img
                                            src={n.iconUrl ? (n.iconUrl.startsWith('/api') ? `${BACKEND_URL}${n.iconUrl}` : n.iconUrl) : "https://modtale.net/assets/favicon.svg"}
                                            alt=""
                                            className="w-10 h-10 rounded-md object-cover bg-slate-200 dark:bg-white/10 flex-shrink-0"
                                            onError={(e) => {
                                                const target = e.currentTarget;
                                                target.onerror = null;
                                                target.src = "https://modtale.net/assets/favicon.svg";
                                            }}
                                        />
                                        <div className="flex-1 min-w-0 pr-12">
                                            <div className={`font-bold text-sm text-slate-800 dark:text-slate-200 mb-0.5 truncate ${!n.read ? 'text-modtale-accent' : ''}`}>
                                                {n.title} {!n.read && <span className="inline-block w-1.5 h-1.5 bg-red-500 rounded-full ml-1 mb-0.5"></span>}
                                            </div>
                                            <div className="text-xs text-slate-500 dark:text-slate-400 whitespace-pre-wrap break-words">{n.message}</div>

                                            {(n.type === 'TRANSFER_REQUEST' || n.type === 'ORG_INVITE' || n.type === 'CONTRIBUTOR_INVITE') ? (
                                                <div className="flex gap-2 mt-3">
                                                    {actionLoading === n.id ? (
                                                        <span className="text-xs text-slate-400 italic">Processing...</span>
                                                    ) : (
                                                        <>
                                                            <button onClick={(e) => handleAction(e, n, true)} className="flex-1 bg-green-500 hover:bg-green-600 text-white text-xs font-bold py-1.5 rounded flex items-center justify-center gap-1 transition-colors"><Check className="w-3 h-3" /> Accept</button>
                                                            <button onClick={(e) => handleAction(e, n, false)} className="flex-1 bg-red-500 hover:bg-red-600 text-white text-xs font-bold py-1.5 rounded flex items-center justify-center gap-1 transition-colors"><XIcon className="w-3 h-3" /> Decline</button>
                                                        </>
                                                    )}
                                                </div>
                                            ) : (
                                                <div className="text-[10px] text-slate-400 mt-1">{new Date(n.createdAt).toLocaleDateString()}</div>
                                            )}
                                        </div>

                                        <div className="absolute top-3 right-3 flex flex-col gap-1 opacity-0 group-hover:opacity-100 focus-within:opacity-100 transition-opacity">
                                            <button
                                                onClick={(e) => { e.preventDefault(); e.stopPropagation(); dismiss(n.id); }}
                                                className="p-1 text-slate-300 hover:text-red-500 hover:bg-slate-100 dark:hover:bg-white/10 rounded-md transition-colors"
                                                title="Dismiss"
                                            >
                                                <XIcon className="w-3.5 h-3.5" />
                                            </button>
                                            <button
                                                onClick={(e) => toggleReadStatus(e, n.id, n.read)}
                                                className="p-1 text-slate-300 hover:text-modtale-accent hover:bg-slate-100 dark:hover:bg-white/10 rounded-md transition-colors"
                                                title={n.read ? "Mark as unread" : "Mark as read"}
                                            >
                                                <Circle className={`w-3.5 h-3.5 ${!n.read ? 'fill-modtale-accent text-modtale-accent' : ''}`} />
                                            </button>
                                        </div>
                                    </a>
                                ))}
                            </div>
                        )}
                    </div>
                </div>
            )}

            <style dangerouslySetInnerHTML={{ __html: `
                .custom-scrollbar::-webkit-scrollbar { width: 6px; }
                .custom-scrollbar::-webkit-scrollbar-track { background: transparent; }
                .custom-scrollbar::-webkit-scrollbar-thumb { background: rgba(156, 163, 175, 0.3); border-radius: 20px; }
                .dark .custom-scrollbar::-webkit-scrollbar-thumb { background: rgba(255, 255, 255, 0.1); }
                .custom-scrollbar::-webkit-scrollbar-thumb:hover { background: rgba(156, 163, 175, 0.5); }
                .dark .custom-scrollbar::-webkit-scrollbar-thumb:hover { background: rgba(255, 255, 255, 0.2); }
            `}} />
        </div>
    );
};