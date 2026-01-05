import React, { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api, BACKEND_URL } from '../../utils/api';
import { Save, Upload, Github, Twitter, Check, Eye, EyeOff, Trash2, Plus, Link, AlertTriangle, Edit3, XCircle, Image as ImageIcon } from 'lucide-react';
import type {User as UserType} from '../../types';
import { Spinner } from '../ui/Spinner';
import { ErrorBanner } from '../ui/error/ErrorBanner.tsx';
import { ImageCropperModal } from '../ui/ImageCropperModal';
import { StatusModal } from '../ui/StatusModal';

const DiscordIcon = ({ className }: { className?: string }) => (
    <svg className={className} fill="currentColor" viewBox="0 0 127.14 96.36"><path d="M107.7,8.07A105.15,105.15,0,0,0,81.47,0a72.06,72.06,0,0,0-3.36,6.83A97.68,97.68,0,0,0,49,6.83A72.37,72.37,0,0,0,45.64,0A105.89,105.89,0,0,0,19.39,8.09C2.79,32.65-1.71,56.6.54,80.21h0A105.73,105.73,0,0,0,32.71,96.36A77.11,77.11,0,0,0,39.6,85.25a68.42,68.42,0,0,1-10.85-5.18c.91-.66,1.8-1.34,2.66-2a75.57,75.57,0,0,0,64.32,0c.87.71,1.76,1.39,2.66,2a68.68,68.68,0,0,1-10.87,5.19a77,77,0,0,0,6.89,11.1A105.89,105.89,0,0,0,126.6,80.22c2.36-24.44-4.2-48.62-18.9-72.15ZM42.45,65.69C36.18,65.69,31,60,31,53s5-12.74,11.43-12.74S54,46,53.89,53,48.84,65.69,42.45,65.69Zm42.24,0C78.41,65.69,73.25,60,73.25,53s5-12.74,11.44-12.74S96.23,46,96.12,53,91.08,65.69,84.69,65.69Z" /></svg>
);

const GitLabIcon = ({ className }: { className?: string }) => (
    <svg className={className} viewBox="0 0 24 24" fill="currentColor" xmlns="http://www.w3.org/2000/svg">
        <path d="M22.65 14.39L12 22.13L1.35 14.39L4.74 3.99C4.82 3.73 5.12 3.63 5.33 3.82L8.99 7.5L12 10.5L15.01 7.5L18.67 3.82C18.88 3.63 19.18 3.73 19.26 3.99L22.65 14.39Z" />
    </svg>
);

const BlueskyIcon = ({ className }: { className?: string }) => (
    <svg className={className} viewBox="0 0 568 501" fill="currentColor" xmlns="http://www.w3.org/2000/svg">
        <path d="M123.121 33.664C188.241 83.564 263.357 167.332 284 200.793C304.643 167.332 379.759 83.564 444.879 33.664C497.868 -6.932 568 -22.108 568 46.54V218.456C568 243.66 550.05 266.304 525.669 271.936L429.574 294.116C408.665 298.944 397.697 323.76 411.39 340.948C447.869 386.724 513.799 432.892 531.867 447.668C564.128 474.056 544.721 526 502.981 526H463.317C433.09 526 404.931 513.292 386.324 490.308C363.393 461.98 322.99 401.7 284 345.244C245.01 401.7 204.607 461.98 181.676 490.308C163.069 513.292 134.91 526 104.683 526H65.019C23.279 526 3.872 474.056 36.133 447.668C54.201 432.892 120.131 386.724 156.61 340.948C170.303 323.76 159.335 298.944 138.426 294.116L42.331 271.936C17.95 266.304 0 243.66 0 218.456V46.54C0 -22.108 70.132 -6.932 123.121 33.664Z" transform="scale(0.85) translate(10, -20)"/>
    </svg>
);

const GoogleIcon = ({ className }: { className?: string }) => (
    <svg className={className} viewBox="0 0 24 24" fill="currentColor" xmlns="http://www.w3.org/2000/svg">
        <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" fill="#4285F4"/>
        <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/>
        <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" fill="#FBBC05"/>
        <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335"/>
    </svg>
);

interface ManageProfileProps {
    user: UserType;
    onUpdate: () => void;
}

export const ManageProfile: React.FC<ManageProfileProps> = ({ user, onUpdate }) => {
    const [searchParams, setSearchParams] = useSearchParams();

    const [bio, setBio] = useState(user.bio || '');
    const [username, setUsername] = useState(user.username);

    const [saving, setSaving] = useState(false);
    const [saved, setSaved] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [uploadingBanner, setUploadingBanner] = useState(false);
    const [uploadingAvatar, setUploadingAvatar] = useState(false);
    const [bannerToCrop, setBannerToCrop] = useState<string | null>(null);
    const [avatarToCrop, setAvatarToCrop] = useState<string | null>(null);
    const [isEditingUsername, setIsEditingUsername] = useState(false);

    const [showDeleteModal, setShowDeleteModal] = useState(false);
    const [showUnlinkModal, setShowUnlinkModal] = useState<{provider: string, label: string} | null>(null);

    const accounts = user.connectedAccounts || [];

    const getAvatarSource = () => {
        const url = user.avatarUrl || '';
        if (url.includes('githubusercontent')) return 'GitHub';
        if (url.includes('discordapp')) return 'Discord';
        if (url.includes('gravatar') || url.includes('gitlab')) return 'GitLab';
        if (url.includes('twimg')) return 'Twitter';
        if (url.includes('bsky')) return 'Bluesky';
        if (url.includes('googleusercontent')) return 'Google';
        if (url.includes('modtale-binaries') || url.includes('/api/files/proxy/')) return 'ModTale';
        return 'External';
    };

    useEffect(() => {
        const oauthError = searchParams.get('oauth_error');
        if (oauthError) {
            setError(decodeURIComponent(oauthError).replace(/\+/g, ' '));
            setSearchParams({});
        }
    }, [searchParams, setSearchParams]);

    const handleSave = async () => {
        setSaving(true);
        setError(null);
        try {
            await api.put('/user/profile', {
                bio,
                username: username !== user.username ? username : undefined
            });
            setSaved(true);
            setIsEditingUsername(false);
            setTimeout(() => setSaved(false), 2000);
            onUpdate();
        } catch (e: any) {
            setError(e.response?.data || "Failed to save profile.");
        } finally {
            setSaving(false);
        }
    };

    const onBannerSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
        if (e.target.files && e.target.files.length > 0) setBannerToCrop(URL.createObjectURL(e.target.files[0]));
    };

    const onAvatarSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
        if (e.target.files && e.target.files.length > 0) setAvatarToCrop(URL.createObjectURL(e.target.files[0]));
    };

    const handleBannerCropComplete = async (croppedFile: File) => {
        setBannerToCrop(null);
        setUploadingBanner(true);
        const formData = new FormData();
        formData.append('file', croppedFile);

        const uploadConfig = { headers: { 'Content-Type': 'multipart/form-data' } };

        try {
            await api.post('/user/profile/banner', formData, uploadConfig);
            onUpdate();
        } catch (e) {
            setError("Failed to upload banner.");
        } finally {
            setUploadingBanner(false);
        }
    };

    const handleAvatarCropComplete = async (croppedFile: File) => {
        setAvatarToCrop(null);
        setUploadingAvatar(true);
        const formData = new FormData();
        formData.append('file', croppedFile);

        const uploadConfig = { headers: { 'Content-Type': 'multipart/form-data' } };

        try {
            await api.post('/user/profile/avatar', formData, uploadConfig);
            onUpdate();
        } catch (e) {
            setError("Failed to upload avatar.");
        } finally {
            setUploadingAvatar(false);
        }
    };

    const handleDeleteAccount = async () => {
        try {
            await api.delete('/user/me');
            window.location.href = '/';
        } catch (e) {
            setError("Failed to delete account. Please try again later.");
            setShowDeleteModal(false);
        }
    };

    const handleUnlink = async () => {
        if (!showUnlinkModal) return;
        try {
            await api.delete(`/user/connections/${showUnlinkModal.provider}`);
            onUpdate();
            setShowUnlinkModal(null);
        } catch (e: any) {
            setError(e.response?.data || "Failed to unlink account.");
            setShowUnlinkModal(null);
        }
    };

    const AccountRow = ({ provider, icon: Icon, label }: { provider: string, icon: any, label: string }) => {
        const account = accounts.find(a => a.provider === provider);
        const isLinked = !!account;
        const canBeVisible = provider !== 'google';

        return (
            <div className={`flex items-center justify-between p-3 rounded-xl border transition-all h-full ${isLinked ? 'bg-white dark:bg-white/5 border-modtale-accent/30' : 'bg-slate-50 dark:bg-white/[0.02] border-slate-200 dark:border-white/5'}`}>
                <div className="flex items-center gap-3">
                    <div className={`p-2 rounded-lg ${isLinked ? 'text-modtale-accent bg-modtale-accent/10' : 'text-slate-400 bg-white dark:bg-white/5'}`}>
                        <Icon className="w-4 h-4" />
                    </div>
                    <div>
                        <h4 className="font-bold text-[10px] text-slate-900 dark:text-white uppercase tracking-wider">{label}</h4>
                        {isLinked ? (
                            <p className="text-[10px] text-green-600 dark:text-green-400 font-bold flex items-center gap-1 mt-0.5">
                                <Check className="w-3 h-3" /> {account.username}
                            </p>
                        ) : (
                            <p className="text-[10px] text-slate-400 mt-0.5">Not connected</p>
                        )}
                    </div>
                </div>
                {isLinked ? (
                    <div className="flex gap-1.5">
                        {canBeVisible ? (
                            <button
                                onClick={() => api.post(`/user/connections/${provider}/toggle-visibility`).then(onUpdate)}
                                className={`p-1.5 rounded-lg transition-colors ${account.visible ? 'text-modtale-accent bg-modtale-accent/10 hover:bg-modtale-accent/20' : 'text-slate-400 hover:bg-slate-100 dark:hover:bg-white/10'}`}
                                title={account.visible ? "Publicly Visible" : "Hidden from profile"}
                            >
                                {account.visible ? <Eye className="w-3.5 h-3.5" /> : <EyeOff className="w-3.5 h-3.5" />}
                            </button>
                        ) : (
                            <div className="p-1.5 text-slate-300 dark:text-white/10 cursor-not-allowed" title="Private connection">
                                <EyeOff className="w-3.5 h-3.5" />
                            </div>
                        )}
                        <button
                            onClick={() => setShowUnlinkModal({ provider, label })}
                            className="p-1.5 text-red-500 hover:bg-red-50 dark:hover:bg-red-950/30 rounded-lg transition-colors"
                            title="Unlink"
                        >
                            <Trash2 className="w-3.5 h-3.5" />
                        </button>
                    </div>
                ) : (
                    <button
                        onClick={() => window.location.href = `${BACKEND_URL}/oauth2/authorization/${provider}`}
                        className="bg-white dark:bg-white/10 border border-slate-200 dark:border-white/10 text-slate-700 dark:text-slate-200 px-3 py-1.5 rounded-lg text-[10px] font-black uppercase tracking-widest hover:border-modtale-accent hover:text-modtale-accent transition-all flex items-center gap-1.5 shadow-sm"
                    >
                        <Plus className="w-3 h-3" /> Link
                    </button>
                )}
            </div>
        );
    };

    return (
        <div className="relative pb-16 space-y-6">
            {error && <ErrorBanner message={error} />}
            {bannerToCrop && <ImageCropperModal imageSrc={bannerToCrop} onCancel={() => setBannerToCrop(null)} onCropComplete={handleBannerCropComplete} aspect={3 / 1} />}
            {avatarToCrop && <ImageCropperModal imageSrc={avatarToCrop} onCancel={() => setAvatarToCrop(null)} onCropComplete={handleAvatarCropComplete} aspect={1 / 1} />}

            {showDeleteModal && (
                <StatusModal
                    type="error"
                    title="Delete Account?"
                    message="This action is permanent and cannot be undone. All your data, including preferences and API keys, will be removed."
                    actionLabel="Yes, Delete My Account"
                    onAction={handleDeleteAccount}
                    onClose={() => setShowDeleteModal(false)}
                    secondaryLabel="Cancel"
                />
            )}

            {showUnlinkModal && (
                <StatusModal
                    type="warning"
                    title={`Unlink ${showUnlinkModal.label}?`}
                    message={`Are you sure you want to unlink your ${showUnlinkModal.label} account? You will no longer be able to sign in with it.`}
                    actionLabel="Yes, Unlink"
                    onAction={handleUnlink}
                    onClose={() => setShowUnlinkModal(null)}
                    secondaryLabel="Cancel"
                />
            )}

            <div className="space-y-0">
                <div className="relative w-full aspect-[3/1] bg-slate-800 overflow-hidden group rounded-b-3xl md:rounded-3xl shadow-sm">
                    <div
                        className={`w-full h-full bg-cover bg-center transition-opacity duration-300 ${user.bannerUrl ? 'opacity-100' : 'opacity-0'}`}
                        style={{ backgroundImage: user.bannerUrl ? `url(${user.bannerUrl})` : undefined }}
                    ></div>

                    {!user.bannerUrl && <div className="absolute inset-0 bg-gradient-to-br from-modtale-accent/20 via-slate-900 to-black" />}

                    <div className="absolute inset-0 bg-gradient-to-t from-slate-50/90 dark:from-slate-950/90 to-transparent" />

                    <label className={`cursor-pointer transition-all duration-300 ${
                        user.bannerUrl
                            ? "absolute top-6 right-6 z-30 bg-black/60 hover:bg-black/80 text-white px-4 py-2 rounded-xl text-xs font-bold border border-white/20 backdrop-blur-sm shadow-lg hover:scale-105"
                            : "absolute inset-0 z-30 flex flex-col items-center justify-center m-6 rounded-2xl border-2 border-dashed border-white/10 hover:border-white/30 bg-white/5 hover:bg-white/10 group/banner"
                    }`}>
                        <input type="file" className="hidden" accept="image/*" onChange={onBannerSelect} disabled={uploadingBanner} />
                        {user.bannerUrl ? (
                            <div className="flex flex-col items-end">
                                <div className="flex items-center gap-2">
                                    {uploadingBanner ? <Spinner className="w-4 h-4 text-white" /> : <ImageIcon className="w-4 h-4" />}
                                    {uploadingBanner ? 'Uploading...' : 'Change Banner'}
                                </div>
                                <span className="text-[10px] font-medium text-white/50">Rec: 1920x640</span>
                            </div>
                        ) : (
                            <>
                                <div className="w-16 h-16 rounded-full bg-white/5 flex items-center justify-center mb-4 group-hover/banner:scale-110 transition-transform">
                                    <Plus className="w-8 h-8 text-white/50 group-hover/banner:text-white transition-colors" />
                                </div>
                                <span className="text-lg font-bold text-white/80 group-hover/banner:text-white">Upload Profile Banner</span>
                                <span className="text-xs font-medium text-white/40 mt-1 group-hover/banner:text-white/60">Recommended: 1920x640 (3:1)</span>
                            </>
                        )}
                    </label>
                </div>

                <div className="max-w-6xl mx-auto px-4 relative z-50 -mt-20">
                    <div className="bg-white/95 dark:bg-slate-900/95 backdrop-blur-xl border border-slate-200 dark:border-white/10 rounded-3xl p-6 shadow-xl flex flex-col md:flex-row gap-6 items-start">

                        <div className="flex-shrink-0 mx-auto md:mx-0 -mt-16 md:-mt-16 relative group">
                            <label className="block w-24 h-24 md:w-40 md:h-40 rounded-[1.5rem] border-[4px] border-white dark:border-slate-800 shadow-xl overflow-hidden bg-slate-100 dark:bg-slate-800 relative cursor-pointer">
                                <input type="file" className="hidden" accept="image/*" onChange={onAvatarSelect} disabled={uploadingAvatar} />
                                <img src={user.avatarUrl} alt="" className="w-full h-full object-cover transition-transform group-hover:scale-105" />

                                <div className="absolute inset-0 bg-black/50 flex flex-col items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity duration-200 backdrop-blur-[2px]">
                                    {uploadingAvatar ? (
                                        <Spinner className="w-6 h-6 text-white" />
                                    ) : (
                                        <Upload className="w-6 h-6 text-white mb-1" />
                                    )}
                                    <span className="text-white text-[9px] font-bold uppercase text-center px-2">Change Avatar</span>
                                    <span className="text-white/70 text-[8px] font-bold uppercase text-center px-2 mt-0.5">Rec: 512x512</span>
                                </div>
                            </label>
                            <div className="text-center mt-2">
                                <span className="inline-block bg-slate-100 dark:bg-white/10 text-slate-500 dark:text-slate-400 text-[9px] font-bold px-2 py-0.5 rounded-full uppercase tracking-wide">
                                    Source: {getAvatarSource()}
                                </span>
                            </div>
                        </div>

                        <div className="flex-1 w-full space-y-4">
                            <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-2">
                                <div className="space-y-1 w-full max-w-md">
                                    <div>
                                        <label className="text-[10px] font-black text-slate-400 uppercase tracking-widest block mb-1">Username</label>
                                        <div className="flex items-center gap-2">
                                            {isEditingUsername ? (
                                                <div className="relative flex-1">
                                                    <input
                                                        type="text"
                                                        value={username}
                                                        onChange={(e) => setUsername(e.target.value)}
                                                        className="text-2xl md:text-3xl font-black text-slate-900 dark:text-white tracking-tighter bg-transparent border-b border-slate-200 dark:border-white/10 focus:border-modtale-accent focus:outline-none w-full placeholder-slate-300 dark:placeholder-white/20"
                                                        placeholder="Username"
                                                        autoFocus
                                                    />
                                                    <button onClick={() => { setUsername(user.username); setIsEditingUsername(false); }} className="absolute right-2 top-2 text-slate-400 hover:text-red-500">
                                                        <XCircle className="w-5 h-5" />
                                                    </button>
                                                </div>
                                            ) : (
                                                <div className="flex items-center gap-2 group cursor-pointer" onClick={() => setIsEditingUsername(true)}>
                                                    <h1 className="text-2xl md:text-3xl font-black text-slate-900 dark:text-white tracking-tighter">
                                                        {user.username}
                                                    </h1>
                                                    <Edit3 className="w-5 h-5 text-slate-300 opacity-0 group-hover:opacity-100 transition-opacity" />
                                                </div>
                                            )}
                                        </div>
                                    </div>

                                    {isEditingUsername && (
                                        <p className="text-[10px] text-orange-500 font-bold mt-1">
                                            Warning: Changing your username will break existing links to your profile.
                                        </p>
                                    )}
                                </div>

                                <button onClick={handleSave} disabled={saving} className="bg-modtale-accent text-white px-6 py-2 rounded-xl font-black flex items-center gap-2 transition-all shadow-lg active:scale-95 hover:bg-modtale-accentHover disabled:opacity-70 text-xs flex-shrink-0 mt-4 md:mt-0">
                                    {saving ? <Spinner className="w-4 h-4 !p-0" fullScreen={false} /> : (saved ? <Check className="w-4 h-4" /> : <Save className="w-4 h-4" />)}
                                    {saved ? 'Saved' : 'Save Changes'}
                                </button>
                            </div>

                            <div>
                                <div className="flex justify-between items-center mb-1.5 px-1">
                                    <label className="text-[10px] font-black text-slate-400 uppercase tracking-widest">Biography</label>
                                    <span className={`text-[10px] font-bold ${bio.length > 280 ? 'text-red-500' : 'text-slate-400'}`}>{bio.length}/300</span>
                                </div>
                                <textarea
                                    value={bio}
                                    onChange={(e) => setBio(e.target.value)}
                                    rows={3}
                                    className="w-full bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-xl px-4 py-3 text-sm focus:ring-2 focus:ring-modtale-accent outline-none transition-all dark:text-white placeholder:text-slate-400 resize-none"
                                    placeholder="Write something about yourself..."
                                    maxLength={300}
                                />
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            <div className="max-w-6xl mx-auto px-4">
                <div className="bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/10 rounded-3xl p-6 shadow-sm">
                    <div className="flex items-center gap-3 mb-4 border-b border-slate-100 dark:border-white/5 pb-4">
                        <Link className="w-4 h-4 text-modtale-accent" />
                        <h3 className="text-sm font-black text-slate-900 dark:text-white uppercase tracking-wide">Connected Accounts</h3>
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
                        <AccountRow provider="github" icon={Github} label="GitHub" />
                        <AccountRow provider="gitlab" icon={GitLabIcon} label="GitLab" />
                        <AccountRow provider="discord" icon={DiscordIcon} label="Discord" />
                        <AccountRow provider="twitter" icon={Twitter} label="X / Twitter" />
                        <AccountRow provider="bluesky" icon={BlueskyIcon} label="Bluesky" />
                        <AccountRow provider="google" icon={GoogleIcon} label="Google" />
                    </div>
                </div>
            </div>

            <div className="max-w-6xl mx-auto px-4">
                <div className="bg-red-50 dark:bg-red-900/10 border border-red-200 dark:border-red-900/20 rounded-3xl p-6 shadow-sm">
                    <div className="flex items-center justify-between">
                        <div>
                            <div className="flex items-center gap-3 mb-1">
                                <AlertTriangle className="w-5 h-5 text-red-600 dark:text-red-400" />
                                <h3 className="text-sm font-black text-red-900 dark:text-red-200 uppercase tracking-wide">Danger Zone</h3>
                            </div>
                            <p className="text-xs text-red-700 dark:text-red-300/70 max-w-md">
                                Permanently delete your account and all associated data. This action cannot be undone.
                            </p>
                        </div>
                        <button
                            onClick={() => setShowDeleteModal(true)}
                            className="bg-red-600 hover:bg-red-700 text-white px-5 py-2.5 rounded-xl font-bold text-xs transition-colors shadow-lg shadow-red-600/20 flex items-center gap-2"
                        >
                            <Trash2 className="w-4 h-4" /> Delete Account
                        </button>
                    </div>
                </div>
            </div>
        </div>
    );
};