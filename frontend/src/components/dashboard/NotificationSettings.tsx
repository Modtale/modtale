import React, { useState } from 'react';
import { api, BACKEND_URL } from '../../utils/api';
import { Bell, Save, Check, Trash2, X } from 'lucide-react';
import type { User as UserType } from '../../types';
import { Spinner } from '../ui/Spinner';
import { Link } from 'react-router-dom';
import { useNotifications } from '../../context/NotificationsContext.tsx';

interface NotificationSettingsProps {
    user: UserType;
}

type PrefLevel = 'OFF' | 'ON';

export const NotificationSettings: React.FC<NotificationSettingsProps> = ({ user }) => {
    const { notifications, loading: loadingNotifications, dismiss, clearAll, isIdle } = useNotifications();

    const [projectUpdates, setProjectUpdates] = useState<PrefLevel>(user.notificationPreferences?.projectUpdates === 'OFF' ? 'OFF' : 'ON');
    const [creatorUploads, setCreatorUploads] = useState<PrefLevel>(user.notificationPreferences?.creatorUploads === 'OFF' ? 'OFF' : 'ON');
    const [newComments, setNewComments] = useState<PrefLevel>(user.notificationPreferences?.newComments === 'OFF' ? 'OFF' : 'ON');
    const [newFollowers, setNewFollowers] = useState<PrefLevel>(user.notificationPreferences?.newFollowers === 'OFF' ? 'OFF' : 'ON');
    const [dependencyUpdates, setDependencyUpdates] = useState<PrefLevel>(user.notificationPreferences?.dependencyUpdates === 'OFF' ? 'OFF' : 'ON');

    const [saving, setSaving] = useState(false);
    const [saved, setSaved] = useState(false);

    const handleSave = async () => {
        setSaving(true);
        try {
            await api.put('/user/settings/notifications', {
                projectUpdates, creatorUploads, newComments, newFollowers, dependencyUpdates
            });
            setSaved(true);
            setTimeout(() => setSaved(false), 2000);
        } catch (e) {
            console.error("Failed to save settings", e);
        } finally {
            setSaving(false);
        }
    };

    const Toggle = ({ label, desc, value, onChange }: { label: string, desc: string, value: PrefLevel, onChange: (v: PrefLevel) => void }) => {
        const isOn = value === 'ON';
        return (
            <div className="flex flex-col sm:flex-row sm:items-center justify-between p-6 border-b border-slate-200 dark:border-white/10 last:border-0 gap-4 hover:bg-white/40 dark:hover:bg-white/[0.02] transition-colors group">
                <div>
                    <h3 className="font-bold text-slate-900 dark:text-white">{label}</h3>
                    <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">{desc}</p>
                </div>
                <div className="relative flex bg-white/60 dark:bg-black/20 border border-slate-200 dark:border-white/10 p-1 rounded-xl shadow-inner shrink-0 w-fit">
                    <div
                        className={`absolute top-1 bottom-1 w-16 rounded-lg transition-all duration-300 ease-out shadow-sm ${
                            isOn
                                ? 'bg-modtale-accent shadow-modtale-accent/30 border-transparent'
                                : 'bg-white dark:bg-slate-700 border border-slate-200 dark:border-white/10'
                        }`}
                        style={{ transform: `translateX(${isOn ? 100 : 0}%)` }}
                    />

                    <button
                        onClick={() => onChange('OFF')}
                        className={`relative z-10 w-16 py-2 text-xs font-bold transition-colors duration-300 ${
                            !isOn ? 'text-slate-900 dark:text-white' : 'text-slate-500 dark:text-slate-400 hover:text-slate-700 dark:hover:text-slate-300'
                        }`}
                    >
                        Off
                    </button>
                    <button
                        onClick={() => onChange('ON')}
                        className={`relative z-10 w-16 py-2 text-xs font-bold transition-colors duration-300 ${
                            isOn ? 'text-white' : 'text-slate-500 dark:text-slate-400 hover:text-slate-700 dark:hover:text-slate-300'
                        }`}
                    >
                        On
                    </button>
                </div>
            </div>
        );
    };

    return (
        <div className="space-y-8">
            <div className="space-y-6">
                <div className="flex justify-between items-center mb-6">
                    <div>
                        <h2 className="text-2xl font-black text-slate-900 dark:text-white">Notification Preferences</h2>
                        <p className="text-slate-500 text-sm">Control what alerts you receive.</p>
                    </div>
                </div>

                <div className="bg-white/40 dark:bg-white/5 border border-slate-200 dark:border-white/10 rounded-2xl shadow-sm overflow-hidden backdrop-blur-md">
                    <Toggle label="Favorite Project Updates" desc="Notify me when projects I've favorited release new versions." value={projectUpdates} onChange={setProjectUpdates} />
                    <Toggle label="New Creator Uploads" desc="Notify me when creators I follow upload new projects." value={creatorUploads} onChange={setCreatorUploads} />
                    <Toggle label="New Comments" desc="Get notified when someone comments on your project." value={newComments} onChange={setNewComments} />
                    <Toggle label="New Followers" desc="Notify me when someone starts following me." value={newFollowers} onChange={setNewFollowers} />
                    <Toggle label="Dependency Updates" desc="Alert me when a project I depend on releases a new version." value={dependencyUpdates} onChange={setDependencyUpdates} />
                </div>

                <div className="flex justify-end">
                    <button
                        onClick={handleSave}
                        disabled={saving}
                        className="bg-modtale-accent text-white px-8 py-3 rounded-xl font-bold hover:bg-modtale-accentHover transition-all shadow-lg shadow-modtale-accent/20 flex items-center gap-2 disabled:opacity-70 active:scale-95"
                    >
                        {saving ? <Spinner className="w-4 h-4 !p-0" fullScreen={false} /> : (saved ? <Check className="w-4 h-4" /> : <Save className="w-4 h-4" />)}
                        {saved ? 'Saved!' : 'Save Changes'}
                    </button>
                </div>
            </div>

            <div>
                <div className="flex justify-between items-center mb-6">
                    <div>
                        <h2 className="text-xl font-black text-slate-900 dark:text-white flex items-center gap-2">
                            <Bell className="w-6 h-6 text-modtale-accent" /> Recent Notifications
                            {isIdle && <span className="text-xs font-bold text-slate-500 bg-slate-200/50 dark:bg-white/10 px-2 py-0.5 rounded-md">Paused</span>}
                        </h2>
                    </div>
                    {notifications.length > 0 && (
                        <button onClick={clearAll} className="text-xs font-bold text-red-500 hover:bg-red-50 dark:hover:bg-red-900/10 px-3 py-1.5 rounded-lg transition-colors flex items-center gap-2">
                            <Trash2 className="w-3 h-3" /> Clear All
                        </button>
                    )}
                </div>

                {loadingNotifications ? (
                    <div className="py-12"><Spinner fullScreen={false} /></div>
                ) : notifications.length === 0 ? (
                    <div className="bg-white/40 dark:bg-white/5 border border-slate-200 dark:border-white/10 rounded-2xl p-8 text-center text-slate-500 backdrop-blur-md shadow-sm">
                        <Bell className="w-8 h-8 mx-auto mb-2 opacity-20" />
                        <p className="text-sm font-bold">No notifications yet.</p>
                    </div>
                ) : (
                    <div className="bg-white/40 dark:bg-white/5 border border-slate-200 dark:border-white/10 rounded-2xl overflow-hidden shadow-sm divide-y divide-slate-200 dark:divide-white/10 backdrop-blur-md">
                        {notifications.map(n => (
                            <div key={n.id} className="p-5 flex items-start gap-4 hover:bg-white/60 dark:hover:bg-white/[0.02] transition-colors group relative">
                                {n.iconUrl ? (
                                    <img
                                        src={n.iconUrl.startsWith('/api') ? `${BACKEND_URL}${n.iconUrl}` : n.iconUrl}
                                        alt=""
                                        className="w-12 h-12 rounded-xl object-cover bg-slate-200 dark:bg-white/10 flex-shrink-0 shadow-sm border border-slate-200 dark:border-white/5"
                                        onError={(e) => e.currentTarget.style.display = 'none'}
                                    />
                                ) : (
                                    <div className="w-12 h-12 rounded-xl bg-slate-200/50 dark:bg-white/5 border border-slate-200 dark:border-white/10 flex items-center justify-center flex-shrink-0 text-slate-400">
                                        <Bell className="w-6 h-6" />
                                    </div>
                                )}

                                <div className="flex-1 min-w-0 pr-8">
                                    <h4 className="font-bold text-slate-900 dark:text-white mb-1 truncate">{n.title}</h4>
                                    <p className="text-sm text-slate-600 dark:text-slate-400 mb-2 leading-relaxed">{n.message}</p>
                                    <div className="flex items-center gap-3">
                                        <span className="text-xs font-medium text-slate-500">{new Date(n.createdAt).toLocaleDateString()} at {new Date(n.createdAt).toLocaleTimeString()}</span>
                                        <Link to={n.link} className="text-xs font-bold text-modtale-accent hover:underline">View Details</Link>
                                    </div>
                                </div>

                                <button
                                    onClick={() => dismiss(n.id)}
                                    className="absolute top-5 right-5 p-2 text-slate-300 hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-900/10 rounded-xl transition-colors opacity-0 group-hover:opacity-100 focus:opacity-100"
                                >
                                    <X className="w-4 h-4" />
                                </button>
                            </div>
                        ))}
                    </div>
                )}
            </div>
        </div>
    );
};