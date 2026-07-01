import React, { useEffect, useState } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { Save, Check, ExternalLink, XCircle, Edit3 } from 'lucide-react';
import { userClient } from '../api/userClient';
import { ProfileLayout } from '@/modules/user/components/ProfileLayout.tsx';
import { Spinner } from '@/components/ui/Spinner';
import { StatusModal } from '@/components/ui/StatusModal';
import { SecuritySettings } from '../tabs/SecuritySettings';
import { ConnectionsSettings } from '../tabs/ConnectionsSettings';
import { SiteRoutes } from '@/utils/routes';
import { extractApiErrorMessage } from '@/utils/api';
import type { User } from '@/types';

interface ManageProfileProps {
    user: User;
    onUpdate: () => void;
}

export function ManageProfile({ user, onUpdate }: ManageProfileProps) {
    const [searchParams, setSearchParams] = useSearchParams();

    const [bio, setBio] = useState(user.bio || '');
    const [username, setUsername] = useState(user.username);
    const [isDirty, setIsDirty] = useState(false);

    const [saving, setSaving] = useState(false);
    const [saved, setSaved] = useState(false);
    const [statusModal, setStatusModal] = useState<{ title: string; msg: string } | null>(null);
    const [isEditingUsername, setIsEditingUsername] = useState(false);

    useEffect(() => {
        const oauthError = searchParams.get('oauth_error');
        if (oauthError) {
            setStatusModal({
                title: 'Account Connection Failed',
                msg: decodeURIComponent(oauthError).replace(/\+/g, ' ')
            });
            setSearchParams({});
        }
    }, [searchParams, setSearchParams]);

    useEffect(() => {
        const isBioChanged = (bio || '') !== (user.bio || '');
        const isUsernameChanged = username !== user.username;
        setIsDirty(isBioChanged || isUsernameChanged);
    }, [bio, username, user]);

    useEffect(() => {
        const handleBeforeUnload = (event: BeforeUnloadEvent) => {
            if (!isDirty) return;
            event.preventDefault();
            event.returnValue = '';
        };

        window.addEventListener('beforeunload', handleBeforeUnload);
        return () => window.removeEventListener('beforeunload', handleBeforeUnload);
    }, [isDirty]);

    const handleSave = async () => {
        setSaving(true);
        setStatusModal(null);
        try {
            await userClient.updateProfile({ bio, username: username !== user.username ? username : undefined });
            setSaved(true);
            setIsDirty(false);
            setIsEditingUsername(false);
            setTimeout(() => setSaved(false), 2000);
            onUpdate();
        } catch (e: unknown) {
            setStatusModal({
                title: 'Profile Save Failed',
                msg: extractApiErrorMessage(e, 'We could not save your profile changes.')
            });
        } finally {
            setSaving(false);
        }
    };

    const handleBannerUpload = async (file: File) => {
        try {
            await userClient.uploadBanner(file);
            onUpdate();
        } catch (e: unknown) {
            setStatusModal({
                title: 'Banner Upload Failed',
                msg: extractApiErrorMessage(e, 'We could not upload your banner.')
            });
            throw e;
        }
    };

    const handleAvatarUpload = async (file: File) => {
        try {
            await userClient.uploadAvatar(file);
            onUpdate();
        } catch (e: unknown) {
            setStatusModal({
                title: 'Avatar Upload Failed',
                msg: extractApiErrorMessage(e, 'We could not upload your avatar.')
            });
            throw e;
        }
    };

    const headerInput = (
        <div className="space-y-1 w-full min-w-0">
            <div>
                <label className="text-[10px] font-black text-slate-400 uppercase tracking-widest block mb-1 ml-1">Username</label>
                <div className="flex items-center gap-2">
                    {isEditingUsername ? (
                        <div className="relative flex-1 max-w-full">
                            <input type="text" value={username} onChange={(e) => setUsername(e.target.value)} className="text-2xl md:text-3xl font-black text-slate-900 dark:text-white bg-white/80 dark:bg-black/20 border border-slate-200 dark:border-white/10 focus:ring-2 focus:ring-modtale-accent focus:border-transparent outline-none w-full placeholder-slate-300 dark:placeholder-white/20 shadow-inner rounded-xl px-4 py-2 transition-all" placeholder="Username" autoFocus />
                            <button onClick={() => { setUsername(user.username); setIsEditingUsername(false); }} className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-red-500 transition-colors"><XCircle className="w-5 h-5" /></button>
                        </div>
                    ) : (
                        <div className="flex items-center gap-3 group cursor-pointer hover:bg-white/60 dark:hover:bg-white/5 px-4 py-2 -ml-4 rounded-2xl transition-colors" onClick={() => setIsEditingUsername(true)}>
                            <h1 className="text-3xl md:text-4xl font-black text-slate-900 dark:text-white tracking-normal truncate">{user.username}</h1>
                            <Edit3 className="w-5 h-5 text-slate-400 opacity-0 group-hover:opacity-100 transition-opacity" />
                        </div>
                    )}
                </div>
            </div>
            {isEditingUsername && <p className="text-xs text-orange-600 dark:text-orange-400 font-bold mt-2 ml-1">Changing your username updates the profile label in your URL, but the link stays ID-backed.</p>}
        </div>
    );

    const actionContent = (
        <div className="flex flex-col md:flex-row items-center gap-3 w-full md:w-auto mt-4 md:mt-0">
            <Link to={SiteRoutes.creator(user.id, user.username)} target="_blank" className="bg-slate-100 dark:bg-white/5 border border-slate-200 dark:border-white/10 text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-white/10 px-4 py-2.5 rounded-xl font-bold flex items-center justify-center gap-2 transition-colors text-sm w-full md:w-auto whitespace-nowrap shadow-sm">
                <ExternalLink className="w-4 h-4" />
            </Link>

            {isDirty && (
                <div className="flex items-center px-2 h-8 rounded border border-amber-300/60 dark:border-amber-400/30 bg-amber-50/80 dark:bg-amber-500/10 text-amber-700 dark:text-amber-300 animate-pulse">
                    <span className="text-[9px] font-semibold tracking-wide">Not Saved</span>
                </div>
            )}

            <button onClick={handleSave} disabled={saving} className="bg-modtale-accent text-white px-6 py-2.5 rounded-xl font-bold flex items-center justify-center gap-2 transition-all shadow-lg shadow-modtale-accent/20 active:scale-95 hover:bg-modtale-accentHover disabled:opacity-70 text-sm flex-shrink-0 w-full md:w-auto whitespace-nowrap">
                {saving ? <Spinner className="w-4 h-4 !p-0" fullScreen={false} /> : (saved ? <Check className="w-4 h-4" /> : <Save className="w-4 h-4" />)}
                {saved ? 'Saved' : ''}
            </button>
        </div>
    );

    const bioInput = (
        <div className="mt-2">
            <div className="flex justify-between items-center mb-2 px-1">
                <label className="text-[10px] font-black text-slate-400 uppercase tracking-widest">Biography</label>
                <span className={`text-[10px] font-bold ${bio.length > 280 ? 'text-red-500' : 'text-slate-400'}`}>{bio.length}/300</span>
            </div>
            <textarea
                value={bio}
                onChange={(e) => setBio(e.target.value)}
                rows={4}
                className="w-full bg-white/80 dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-2xl px-4 py-3 text-sm focus:ring-2 focus:ring-modtale-accent outline-none transition-all dark:text-white placeholder:text-slate-400 resize-none shadow-inner"
                placeholder="Write something about yourself..."
                maxLength={300}
            />
        </div>
    );

    return (
        <div className="relative">
            {statusModal && (
                <StatusModal
                    type="error"
                    title={statusModal.title}
                    message={statusModal.msg}
                    onClose={() => setStatusModal(null)}
                />
            )}
            <ProfileLayout
                user={user}
                isEditing={true}
                onBannerUpload={handleBannerUpload}
                onAvatarUpload={handleAvatarUpload}
                headerInput={headerInput}
                actionInput={actionContent}
                bioInput={bioInput}
            >
                <div className="space-y-8 pb-12">
                    <SecuritySettings user={user} onUpdate={onUpdate} />
                    <ConnectionsSettings user={user} onUpdate={onUpdate} />
                </div>
            </ProfileLayout>
        </div>
    );
}
