import React, { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api, BACKEND_URL } from '../../utils/api';
import { Save, Github, Twitter, Check, Eye, EyeOff, Trash2, Plus, Link, AlertTriangle, Edit3, XCircle, Mail, ShieldCheck, ShieldAlert, Key, Smartphone, Lock } from 'lucide-react';
import type { User as UserType } from '../../types';
import { Spinner } from '../ui/Spinner';
import { ErrorBanner } from '../ui/error/ErrorBanner.tsx';
import { StatusModal } from '../ui/StatusModal';
import { ProfileLayout } from '../user/ProfileLayout';

const DiscordIcon = ({ className }: { className?: string }) => (
    <svg className={className} fill="currentColor" viewBox="0 0 127.14 96.36"><path d="M107.7,8.07A105.15,105.15,0,0,0,81.47,0a72.06,72.06,0,0,0-3.36,6.83A97.68,97.68,0,0,0,49,6.83,72.37,72.37,0,0,0,45.64,0A105.89,105.89,0,0,0,19.39,8.09C2.79,32.65-1.71,56.6.54,80.21h0A105.73,105.73,0,0,0,32.71,96.36A77.11,77.11,0,0,0,39.6,85.25a68.42,68.42,0,0,1-10.85-5.18c.91-.66,1.8-1.34,2.66-2a75.57,75.57,0,0,0,64.32,0c.87.71,1.76,1.39,2.66,2a68.68,68.68,0,0,1-10.87,5.19a77,77,0,0,0,6.89,11.1A105.89,105.89,0,0,0,126.6,80.22c2.36-24.44-4.2-48.62-18.9-72.15ZM42.45,65.69C36.18,65.69,31,60,31,53s5-12.74,11.43-12.74S54,46,53.89,53,48.84,65.69,42.45,65.69Zm42.24,0C78.41,65.69,73.25,60,73.25,53s5-12.74,11.44-12.74S96.23,46,96.12,53,91.08,65.69,84.69,65.69Z" /></svg>
);

const GitLabIcon = ({ className }: { className?: string }) => (
    <svg className={className} viewBox="0 0 24 24" fill="currentColor" xmlns="http://www.w3.org/2000/svg"><path d="M22.65 14.39L12 22.13L1.35 14.39L4.74 3.99C4.82 3.73 5.12 3.63 5.33 3.82L8.99 7.5L12 10.5L15.01 7.5L18.67 3.82C18.88 3.63 19.18 3.73 19.26 3.99L22.65 14.39Z" /></svg>
);

const BlueskyIcon = ({ className }: { className?: string }) => (
    <svg className={className} viewBox="0 -3.268 64 68.414" fill="currentColor"><path d="M13.873 3.805C21.21 9.332 29.103 20.537 32 26.55v15.882c0-.338-.13.044-.41.867-1.512 4.456-7.418 21.847-20.923 7.944-7.111-7.32-3.819-14.64 9.125-16.85-7.405 1.264-15.73-.825-18.014-9.015C1.12 23.022 0 8.51 0 6.55 0-3.268 8.579-.182 13.873 3.805zm36.254 0C42.79 9.332 34.897 20.537 32 26.55v15.882c0-.338.13.044.41.867 1.512 4.456 7.418 21.847 20.923 7.944 7.111-7.32 3.819-14.64-9.125-16.85 7.405 1.264 15.73-.825 18.014-9.015C62.88 23.022 64 8.51 64 6.55c0-9.818-8.578-6.732-13.873-2.745z"/></svg>
);

const GoogleIcon = ({ className }: { className?: string }) => (
    <svg className={className} viewBox="0 0 24 24" fill="currentColor" xmlns="http://www.w3.org/2000/svg"><path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" fill="#4285F4"/><path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/><path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" fill="#FBBC05"/><path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335"/></svg>
);

interface ManageProfileProps {
    user: UserType;
    onUpdate: () => void;
}

export const ManageProfile: React.FC<ManageProfileProps> = ({ user, onUpdate }) => {
    const [searchParams, setSearchParams] = useSearchParams();

    const [bio, setBio] = useState(user.bio || '');
    const [username, setUsername] = useState(user.username);
    const [isDirty, setIsDirty] = useState(false);

    const [saving, setSaving] = useState(false);
    const [saved, setSaved] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [isEditingUsername, setIsEditingUsername] = useState(false);
    const [showDeleteModal, setShowDeleteModal] = useState(false);
    const [showUnlinkModal, setShowUnlinkModal] = useState<{provider: string, label: string} | null>(null);
    const [resendingEmail, setResendingEmail] = useState(false);
    const [emailSent, setEmailSent] = useState(false);

    const [credEmail, setCredEmail] = useState(user.email || '');
    const [credPassword, setCredPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [currentPassword, setCurrentPassword] = useState('');
    const [savingCreds, setSavingCreds] = useState(false);
    const [credsSaved, setCredsSaved] = useState(false);

    const [showMfaSetup, setShowMfaSetup] = useState(false);
    const [mfaSecret, setMfaSecret] = useState('');
    const [mfaQr, setMfaQr] = useState('');
    const [mfaCode, setMfaCode] = useState('');
    const [mfaLoading, setMfaLoading] = useState(false);

    const accounts = user.connectedAccounts || [];

    useEffect(() => {
        const oauthError = searchParams.get('oauth_error');
        if (oauthError) {
            setError(decodeURIComponent(oauthError).replace(/\+/g, ' '));
            setSearchParams({});
        }
    }, [searchParams, setSearchParams]);

    useEffect(() => {
        const isBioChanged = (bio || '') !== (user.bio || '');
        const isUsernameChanged = username !== user.username;
        setIsDirty(isBioChanged || isUsernameChanged);
    }, [bio, username, user]);

    const handleSave = async () => {
        setSaving(true);
        setError(null);
        try {
            await api.put('/user/profile', { bio, username: username !== user.username ? username : undefined });
            setSaved(true);
            setIsDirty(false);
            setIsEditingUsername(false);
            setTimeout(() => setSaved(false), 2000);
            onUpdate();
        } catch (e: any) {
            setError(e.response?.data || "Failed to save profile.");
        } finally {
            setSaving(false);
        }
    };

    const handleSaveCredentials = async (e: React.FormEvent) => {
        e.preventDefault();
        setError(null);

        if (credPassword !== confirmPassword) {
            setError("Passwords do not match.");
            return;
        }

        setSavingCreds(true);
        try {
            await api.put('/auth/credentials', { email: credEmail, password: credPassword });
            setCredsSaved(true);
            setCredPassword('');
            setConfirmPassword('');
            setTimeout(() => setCredsSaved(false), 3000);
            onUpdate();
        } catch (e: any) {
            setError(e.response?.data || "Failed to update credentials.");
        } finally {
            setSavingCreds(false);
        }
    };

    const handleChangePassword = async (e: React.FormEvent) => {
        e.preventDefault();
        setError(null);

        if (credPassword !== confirmPassword) {
            setError("New passwords do not match.");
            return;
        }

        setSavingCreds(true);
        try {
            await api.post('/auth/change-password', { currentPassword, newPassword: credPassword });
            setCredsSaved(true);
            setCurrentPassword('');
            setCredPassword('');
            setConfirmPassword('');
            setTimeout(() => setCredsSaved(false), 3000);
        } catch (e: any) {
            setError(e.response?.data || "Failed to change password.");
        } finally {
            setSavingCreds(false);
        }
    };

    const handleBannerUpload = async (file: File) => {
        const formData = new FormData();
        formData.append('file', file);
        try {
            await api.post('/user/profile/banner', formData, { headers: { 'Content-Type': 'multipart/form-data' } });
            onUpdate();
        } catch (e) {
            setError("Failed to upload banner.");
            throw e;
        }
    };

    const handleAvatarUpload = async (file: File) => {
        const formData = new FormData();
        formData.append('file', file);
        try {
            await api.post('/user/profile/avatar', formData, { headers: { 'Content-Type': 'multipart/form-data' } });
            onUpdate();
        } catch (e) {
            setError("Failed to upload avatar.");
            throw e;
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

    const handleResendVerification = async () => {
        setResendingEmail(true);
        try {
            await api.post('/auth/resend-verification');
            setEmailSent(true);
        } catch (e: any) {
            setError(e.response?.data?.error || "Failed to send email.");
        } finally {
            setResendingEmail(false);
        }
    };

    const handleStartMfaSetup = async () => {
        try {
            const res = await api.get('/auth/mfa/setup');
            setMfaSecret(res.data.secret);
            setMfaQr(res.data.qrCode);
            setShowMfaSetup(true);
            setError(null);
        } catch (e: any) {
            setError(e.response?.data?.error || "Failed to start 2FA setup.");
        }
    };

    const handleVerifyMfa = async () => {
        setMfaLoading(true);
        try {
            await api.post('/auth/mfa/verify', { code: mfaCode });
            setShowMfaSetup(false);
            setMfaCode('');
            onUpdate();
        } catch (e: any) {
            setError(e.response?.data?.error || "Invalid verification code.");
        } finally {
            setMfaLoading(false);
        }
    };

    const AccountRow = ({ provider, icon: Icon, label }: { provider: string, icon: any, label: string }) => {
        const account = accounts.find(a => a.provider === provider);
        const isLinked = !!account;
        const canBeVisible = provider !== 'google';
        const isLastAuthMethod = isLinked && accounts.length <= 1 && !(user as any).hasPassword;

        return (
            <div className={`flex flex-col justify-between p-4 rounded-2xl border transition-all h-full ${isLinked ? 'bg-white/80 dark:bg-slate-900/40 border-modtale-accent/40 shadow-md' : 'bg-slate-50/80 dark:bg-white/[0.02] border-slate-200 dark:border-white/5 hover:border-slate-300 dark:hover:border-white/20'}`}>
                <div className="flex items-center gap-3 mb-4">
                    <div className={`p-2.5 rounded-xl ${isLinked ? 'text-modtale-accent bg-modtale-accent/10' : 'text-slate-400 bg-white dark:bg-white/5 shadow-sm border border-slate-200 dark:border-white/5'}`}>
                        <Icon className="w-5 h-5" />
                    </div>
                    <div className="flex-1 min-w-0">
                        <h4 className="font-bold text-xs text-slate-900 dark:text-white uppercase tracking-wider">{label}</h4>
                        {isLinked ? (
                            <p className="text-xs text-green-600 dark:text-green-400 font-bold flex items-center gap-1.5 mt-0.5 truncate">
                                <Check className="w-3.5 h-3.5 shrink-0" /> {account.username}
                            </p>
                        ) : (
                            <p className="text-xs text-slate-400 mt-0.5">Not connected</p>
                        )}
                    </div>
                </div>

                <div className="flex items-center justify-end pt-3 border-t border-slate-100 dark:border-white/5 mt-auto">
                    {isLinked ? (
                        <div className="flex gap-2 w-full">
                            {canBeVisible ? (
                                <button
                                    onClick={() => api.post(`/user/connections/${provider}/toggle-visibility`).then(onUpdate)}
                                    className={`flex-1 flex items-center justify-center gap-2 py-2 rounded-lg text-xs font-bold transition-colors ${account.visible ? 'text-modtale-accent bg-modtale-accent/10 hover:bg-modtale-accent/20' : 'text-slate-500 bg-slate-100 dark:bg-white/5 hover:bg-slate-200 dark:hover:bg-white/10'}`}
                                    title={account.visible ? "Publicly Visible" : "Hidden from profile"}
                                >
                                    {account.visible ? <><Eye className="w-3.5 h-3.5" /> Visible</> : <><EyeOff className="w-3.5 h-3.5" /> Hidden</>}
                                </button>
                            ) : (
                                <div className="flex-1 flex items-center justify-center gap-2 py-2 rounded-lg text-xs font-bold text-slate-400 bg-slate-50 dark:bg-white/[0.02] border border-slate-200 dark:border-white/5 cursor-not-allowed" title="Private connection">
                                    <EyeOff className="w-3.5 h-3.5" /> Private
                                </div>
                            )}
                            <button
                                onClick={() => setShowUnlinkModal({ provider, label })}
                                disabled={isLastAuthMethod}
                                className={`px-3 py-2 rounded-lg transition-colors ${isLastAuthMethod ? 'text-slate-300 dark:text-slate-600 bg-slate-50 dark:bg-white/[0.02] cursor-not-allowed' : 'text-red-500 hover:text-red-600 bg-red-50 hover:bg-red-100 dark:bg-red-950/30 dark:hover:bg-red-900/50'}`}
                                title={isLastAuthMethod ? "Cannot remove your only sign-in method. Set a password first." : "Unlink Account"}
                            >
                                <Trash2 className="w-4 h-4" />
                            </button>
                        </div>
                    ) : (
                        <button
                            onClick={() => window.location.href = `${BACKEND_URL}/oauth2/authorization/${provider}`}
                            className="w-full bg-white dark:bg-slate-800 border border-slate-200 dark:border-white/10 text-slate-700 dark:text-slate-200 py-2 rounded-lg text-xs font-bold hover:border-modtale-accent hover:text-modtale-accent transition-all flex items-center justify-center gap-2 shadow-sm"
                        >
                            <Plus className="w-4 h-4" /> Link Account
                        </button>
                    )}
                </div>
            </div>
        );
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
                            <h1 className="text-3xl md:text-4xl font-black text-slate-900 dark:text-white tracking-tighter truncate">{user.username}</h1>
                            <Edit3 className="w-5 h-5 text-slate-400 opacity-0 group-hover:opacity-100 transition-opacity" />
                        </div>
                    )}
                </div>
            </div>
            {isEditingUsername && <p className="text-xs text-orange-600 dark:text-orange-400 font-bold mt-2 ml-1">Warning: Changing your username will break existing links to your profile.</p>}
        </div>
    );

    const actionContent = (
        <div className="flex flex-col md:flex-row items-center gap-3 w-full md:w-auto mt-4 md:mt-0">
            {isDirty && <div className="text-[10px] font-bold text-amber-600 dark:text-amber-400 animate-pulse uppercase tracking-widest bg-amber-100 dark:bg-amber-500/10 border border-amber-200 dark:border-amber-500/20 px-3 py-1.5 rounded-lg whitespace-nowrap order-first md:order-none">Unsaved Changes</div>}
            <button onClick={handleSave} disabled={saving} className="bg-modtale-accent text-white px-6 py-2.5 rounded-xl font-bold flex items-center justify-center gap-2 transition-all shadow-lg shadow-modtale-accent/20 active:scale-95 hover:bg-modtale-accentHover disabled:opacity-70 text-sm flex-shrink-0 w-full md:w-auto whitespace-nowrap">
                {saving ? <Spinner className="w-4 h-4 !p-0" fullScreen={false} /> : (saved ? <Check className="w-4 h-4" /> : <Save className="w-4 h-4" />)}
                {saved ? 'Saved' : 'Save Changes'}
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
            {error && <ErrorBanner message={error} />}
            {showDeleteModal && <StatusModal type="error" title="Delete Account?" message="This action is permanent and cannot be undone. All your data, including preferences and API keys, will be removed." actionLabel="Yes, Delete My Account" onAction={handleDeleteAccount} onClose={() => setShowDeleteModal(false)} secondaryLabel="Cancel" />}
            {showUnlinkModal && <StatusModal type="warning" title={`Unlink ${showUnlinkModal.label}?`} message={`Are you sure you want to unlink your ${showUnlinkModal.label} account? You will no longer be able to sign in with it.`} actionLabel="Yes, Unlink" onAction={handleUnlink} onClose={() => setShowUnlinkModal(null)} secondaryLabel="Cancel" />}

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
                    <div className="bg-white/60 dark:bg-slate-900/40 border border-slate-200 dark:border-white/10 rounded-[2rem] p-6 md:p-8 shadow-sm backdrop-blur-xl">
                        <div className="flex items-center gap-4 mb-8 border-b border-slate-200 dark:border-white/10 pb-6">
                            <div className="p-3 bg-slate-100 dark:bg-white/5 rounded-2xl text-slate-500"><Lock className="w-5 h-5" /></div>
                            <div>
                                <h3 className="text-xl font-black text-slate-900 dark:text-white tracking-tight">Security Settings</h3>
                                <p className="text-xs text-slate-500 font-medium mt-1">Manage your credentials and account protection.</p>
                            </div>
                        </div>

                        <div className="space-y-10">
                            <div>
                                <h4 className="font-bold text-slate-900 dark:text-white text-sm mb-3 flex items-center gap-2"><Mail className="w-4 h-4 text-slate-400" /> Email Address</h4>
                                <div className="p-6 rounded-2xl bg-white dark:bg-white/[0.02] border border-slate-200 dark:border-white/10 shadow-sm">
                                    <div className="flex flex-col md:flex-row md:items-center justify-between gap-6">
                                        <div className="flex-1">
                                            <div className="flex items-center gap-3 mb-2">
                                                <span className="text-base font-bold text-slate-900 dark:text-white">{user.email || 'No email associated'}</span>
                                                {user.email && (
                                                    user.emailVerified
                                                        ? <span className="bg-green-50 dark:bg-green-500/10 border border-green-200 dark:border-green-500/30 text-green-700 dark:text-green-400 text-[10px] font-bold px-2.5 py-1 rounded-lg uppercase tracking-wider flex items-center gap-1.5"><ShieldCheck className="w-3.5 h-3.5"/> Verified</span>
                                                        : <span className="bg-yellow-50 dark:bg-yellow-500/10 border border-yellow-200 dark:border-yellow-500/30 text-yellow-700 dark:text-yellow-500 text-[10px] font-bold px-2.5 py-1 rounded-lg uppercase tracking-wider flex items-center gap-1.5"><ShieldAlert className="w-3.5 h-3.5"/> Unverified</span>
                                                )}
                                            </div>
                                            <p className="text-xs text-slate-500 dark:text-slate-400 font-medium">Used for login, notifications, and account recovery.</p>
                                        </div>
                                    </div>

                                    {!user.emailVerified && user.email && (
                                        <div className="mt-6 pt-5 border-t border-slate-100 dark:border-white/5 flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
                                            <div className="flex items-center gap-3 text-yellow-700 dark:text-yellow-400 bg-yellow-50 dark:bg-yellow-900/10 px-4 py-3 rounded-xl border border-yellow-200 dark:border-yellow-900/30 w-full md:w-auto">
                                                <AlertTriangle className="w-5 h-5 shrink-0" />
                                                <p className="text-xs font-bold">Your email is unverified. Please check your inbox.</p>
                                            </div>
                                            {emailSent ? (
                                                <span className="text-xs font-bold text-green-600 dark:text-green-400 flex items-center gap-2 px-4 py-3 bg-green-50 dark:bg-green-900/20 border border-green-200 dark:border-green-900/30 rounded-xl">
                                                    <Check className="w-4 h-4" /> Sent!
                                                </span>
                                            ) : (
                                                <button onClick={handleResendVerification} disabled={resendingEmail} className="text-xs font-bold text-slate-700 dark:text-slate-200 bg-slate-100 dark:bg-white/10 hover:bg-slate-200 dark:hover:bg-white/20 border border-slate-200 dark:border-white/5 px-4 py-3 rounded-xl transition-all shadow-sm disabled:opacity-50 flex items-center gap-2 w-full md:w-auto justify-center">
                                                    <Mail className="w-4 h-4" />
                                                    {resendingEmail ? 'Sending...' : 'Resend Verification Email'}
                                                </button>
                                            )}
                                        </div>
                                    )}
                                </div>
                            </div>

                            <div className="border-t border-slate-200 dark:border-white/10 pt-8">
                                <h4 className="font-bold text-slate-900 dark:text-white text-sm mb-3 flex items-center gap-2"><ShieldCheck className="w-4 h-4 text-slate-400" /> Two-Factor Authentication</h4>
                                {(user as any).mfaEnabled ? (
                                    <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 p-6 rounded-2xl bg-green-50/50 dark:bg-green-900/10 border border-green-200 dark:border-green-900/30 shadow-sm">
                                        <div className="flex items-center gap-4 text-green-700 dark:text-green-400">
                                            <div className="bg-green-200 dark:bg-green-800/50 p-2.5 rounded-xl shadow-sm border border-green-300 dark:border-green-700/50">
                                                <Check className="w-5 h-5" />
                                            </div>
                                            <div>
                                                <span className="font-black text-sm block mb-0.5">2FA is actively protecting your account.</span>
                                                <span className="text-xs font-medium opacity-80">Authenticator app configured.</span>
                                            </div>
                                        </div>
                                        <button className="text-red-500 text-xs font-bold hover:bg-red-50 dark:hover:bg-red-900/20 px-4 py-2 rounded-lg transition-colors border border-transparent hover:border-red-200 dark:hover:border-red-900/30 opacity-50 cursor-not-allowed" title="Disabling 2FA is currently disabled for security.">Disable 2FA</button>
                                    </div>
                                ) : (
                                    <div className="p-6 rounded-2xl bg-white dark:bg-white/[0.02] border border-slate-200 dark:border-white/10 shadow-sm">
                                        <p className="text-sm text-slate-600 dark:text-slate-400 font-medium mb-5 max-w-2xl leading-relaxed">Protect your account from unauthorized access by requiring a time-based code from an authenticator app (like Google Authenticator or Authy) when you log in.</p>
                                        {!showMfaSetup && <button onClick={handleStartMfaSetup} className="bg-slate-900 dark:bg-white text-white dark:text-slate-900 px-6 py-3 rounded-xl font-bold text-sm hover:opacity-90 transition-all shadow-lg active:scale-95 flex items-center gap-2"><ShieldCheck className="w-4 h-4"/> Enable 2FA</button>}
                                    </div>
                                )}

                                {showMfaSetup && (
                                    <div className="mt-6 p-6 sm:p-8 bg-white dark:bg-slate-900/80 rounded-2xl border border-slate-200 dark:border-white/10 shadow-lg animate-in fade-in slide-in-from-top-4">
                                        <h4 className="font-black text-slate-900 dark:text-white mb-6 text-xl flex items-center gap-3">
                                            <span className="flex items-center justify-center w-8 h-8 rounded-full bg-modtale-accent/10 text-modtale-accent text-sm">1</span>
                                            Scan QR Code
                                        </h4>
                                        <div className="flex flex-col lg:flex-row gap-8 items-start">
                                            <div className="bg-white p-4 rounded-3xl w-fit shadow-md border border-slate-200 shrink-0 mx-auto lg:mx-0">
                                                <img src={mfaQr} alt="2FA QR Code" className="w-48 h-48" />
                                            </div>
                                            <div className="space-y-6 flex-1 w-full">
                                                <ul className="text-sm text-slate-600 dark:text-slate-400 space-y-3 font-medium bg-slate-50 dark:bg-white/5 p-5 rounded-2xl border border-slate-100 dark:border-white/5">
                                                    <li className="flex gap-3"><span className="font-black text-slate-400">A.</span> Open your preferred authenticator app (e.g. Google Authenticator).</li>
                                                    <li className="flex gap-3"><span className="font-black text-slate-400">B.</span> Scan the QR code, or enter the setup key manually if scanning fails.</li>
                                                    <li className="flex gap-3"><span className="font-black text-slate-400">C.</span> Enter the generated 6-digit verification code below.</li>
                                                </ul>

                                                <div>
                                                    <label className="text-[10px] font-black text-slate-400 uppercase tracking-widest block mb-2 ml-1">Verification Code</label>
                                                    <div className="relative max-w-sm">
                                                        <input type="text" placeholder="000 000" value={mfaCode} onChange={e => setMfaCode(e.target.value.replace(/\D/g, '').slice(0, 6))} className="w-full px-5 py-4 pl-12 rounded-xl bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 font-mono tracking-[0.5em] text-2xl focus:ring-2 focus:ring-modtale-accent outline-none transition-all shadow-inner text-slate-900 dark:text-white" maxLength={6} />
                                                        <Smartphone className="absolute left-4 top-1/2 -translate-y-1/2 w-6 h-6 text-slate-400" />
                                                    </div>
                                                </div>

                                                <div className="flex flex-col sm:flex-row gap-3 max-w-sm pt-2">
                                                    <button onClick={handleVerifyMfa} disabled={mfaLoading || mfaCode.length !== 6} className="flex-1 bg-modtale-accent text-white py-3.5 rounded-xl font-bold text-sm hover:bg-modtale-accentHover transition-colors disabled:opacity-50 flex items-center justify-center gap-2 shadow-lg shadow-modtale-accent/20 active:scale-95">
                                                        {mfaLoading ? <Spinner className="w-5 h-5" /> : <><Check className="w-4 h-4"/> Verify & Enable</>}
                                                    </button>
                                                    <button onClick={() => { setShowMfaSetup(false); setMfaCode(''); }} className="px-6 py-3.5 text-sm font-bold text-slate-600 dark:text-slate-400 bg-slate-100 dark:bg-white/10 hover:bg-slate-200 dark:hover:bg-white/20 rounded-xl transition-colors">Cancel</button>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                )}
                            </div>

                            <div className="border-t border-slate-200 dark:border-white/10 pt-8">
                                <h4 className="font-bold text-slate-900 dark:text-white text-sm mb-3 flex items-center gap-2">
                                    <Key className="w-4 h-4 text-slate-400" />
                                    {(user as any).hasPassword ? 'Change Password' : 'Set Password'}
                                </h4>

                                <div className="p-6 rounded-2xl bg-white dark:bg-white/[0.02] border border-slate-200 dark:border-white/10 shadow-sm">
                                    {(user as any).hasPassword ? (
                                        <div>
                                            <p className="text-sm text-slate-600 dark:text-slate-400 font-medium mb-6 max-w-xl">Update your account password to maintain security.</p>
                                            <form onSubmit={handleChangePassword} className="max-w-md space-y-5">
                                                <div>
                                                    <label className="text-[10px] font-black text-slate-400 uppercase tracking-widest block mb-2 ml-1">Current Password</label>
                                                    <input type="password" required value={currentPassword} onChange={e => setCurrentPassword(e.target.value)} className="w-full px-4 py-3 rounded-xl bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 focus:ring-2 focus:ring-modtale-accent focus:border-transparent outline-none transition-all text-sm shadow-inner dark:text-white" placeholder="••••••••" />
                                                </div>
                                                <div className="pt-2 border-t border-slate-100 dark:border-white/5">
                                                    <label className="text-[10px] font-black text-slate-400 uppercase tracking-widest block mb-2 ml-1 mt-2">New Password</label>
                                                    <input type="password" required minLength={6} value={credPassword} onChange={e => setCredPassword(e.target.value)} className="w-full px-4 py-3 rounded-xl bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 focus:ring-2 focus:ring-modtale-accent focus:border-transparent outline-none transition-all text-sm shadow-inner dark:text-white" placeholder="••••••••" />
                                                </div>
                                                <div>
                                                    <label className="text-[10px] font-black text-slate-400 uppercase tracking-widest block mb-2 ml-1">Confirm New Password</label>
                                                    <input type="password" required minLength={6} value={confirmPassword} onChange={e => setConfirmPassword(e.target.value)} className="w-full px-4 py-3 rounded-xl bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 focus:ring-2 focus:ring-modtale-accent focus:border-transparent outline-none transition-all text-sm shadow-inner dark:text-white" placeholder="••••••••" />
                                                </div>
                                                <button type="submit" disabled={savingCreds} className="bg-slate-900 dark:bg-white text-white dark:text-slate-900 px-8 py-3 rounded-xl font-bold text-sm hover:opacity-90 transition-all flex items-center justify-center gap-2 mt-4 shadow-lg active:scale-95 w-full sm:w-auto">
                                                    {savingCreds ? <Spinner className="w-4 h-4" /> : (credsSaved ? <Check className="w-4 h-4" /> : <Save className="w-4 h-4" />)} {credsSaved ? 'Saved' : 'Update Password'}
                                                </button>
                                            </form>
                                        </div>
                                    ) : (
                                        <div>
                                            <p className="text-sm text-slate-600 dark:text-slate-400 font-medium mb-6 max-w-xl">Set a password to log in with your email address instead of relying solely on a social provider.</p>
                                            <form onSubmit={handleSaveCredentials} className="max-w-md space-y-5">
                                                {!user.email && (
                                                    <div className="mb-4">
                                                        <label className="text-[10px] font-black text-slate-400 uppercase tracking-widest block mb-2 ml-1">Email Address</label>
                                                        <input type="email" required value={credEmail} onChange={e => setCredEmail(e.target.value)} className="w-full px-4 py-3 rounded-xl bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 focus:ring-2 focus:ring-modtale-accent focus:border-transparent outline-none transition-all text-sm shadow-inner dark:text-white" placeholder="your@email.com" />
                                                    </div>
                                                )}
                                                <div>
                                                    <label className="text-[10px] font-black text-slate-400 uppercase tracking-widest block mb-2 ml-1">New Password</label>
                                                    <input type="password" required minLength={6} value={credPassword} onChange={e => setCredPassword(e.target.value)} className="w-full px-4 py-3 rounded-xl bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 focus:ring-2 focus:ring-modtale-accent focus:border-transparent outline-none transition-all text-sm shadow-inner dark:text-white" placeholder="••••••••" />
                                                </div>
                                                <div>
                                                    <label className="text-[10px] font-black text-slate-400 uppercase tracking-widest block mb-2 ml-1">Confirm Password</label>
                                                    <input type="password" required minLength={6} value={confirmPassword} onChange={e => setConfirmPassword(e.target.value)} className="w-full px-4 py-3 rounded-xl bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 focus:ring-2 focus:ring-modtale-accent focus:border-transparent outline-none transition-all text-sm shadow-inner dark:text-white" placeholder="••••••••" />
                                                </div>
                                                <button type="submit" disabled={savingCreds} className="bg-slate-900 dark:bg-white text-white dark:text-slate-900 px-8 py-3 rounded-xl font-bold text-sm hover:opacity-90 transition-all flex items-center justify-center gap-2 mt-4 shadow-lg active:scale-95 w-full sm:w-auto">
                                                    {savingCreds ? <Spinner className="w-4 h-4" /> : (credsSaved ? <Check className="w-4 h-4" /> : <Save className="w-4 h-4" />)} {credsSaved ? 'Saved' : 'Set Password'}
                                                </button>
                                            </form>
                                        </div>
                                    )}
                                </div>
                            </div>
                        </div>
                    </div>

                    <div className="bg-white/60 dark:bg-slate-900/40 border border-slate-200 dark:border-white/10 rounded-[2rem] p-6 md:p-8 shadow-sm backdrop-blur-xl">
                        <div className="flex items-center gap-4 mb-8 border-b border-slate-200 dark:border-white/10 pb-6">
                            <div className="p-3 bg-slate-100 dark:bg-white/5 rounded-2xl text-slate-500"><Link className="w-5 h-5 text-modtale-accent" /></div>
                            <div>
                                <h3 className="text-xl font-black text-slate-900 dark:text-white tracking-tight">Connected Accounts</h3>
                                <p className="text-xs text-slate-500 font-medium mt-1">Link accounts to sign in easily and display them on your profile.</p>
                            </div>
                        </div>
                        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
                            <AccountRow provider="github" icon={Github} label="GitHub" />
                            <AccountRow provider="gitlab" icon={GitLabIcon} label="GitLab" />
                            <AccountRow provider="discord" icon={DiscordIcon} label="Discord" />
                            <AccountRow provider="twitter" icon={Twitter} label="X / Twitter" />
                            <AccountRow provider="bluesky" icon={BlueskyIcon} label="Bluesky" />
                            <AccountRow provider="google" icon={GoogleIcon} label="Google" />
                        </div>
                    </div>

                    <div className="border border-red-200 dark:border-red-900/30 bg-red-50/60 dark:bg-red-900/10 p-6 md:p-8 rounded-[2rem] backdrop-blur-xl shadow-sm relative overflow-hidden group">
                        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-6 pl-2">
                            <div>
                                <div className="flex items-center gap-3 mb-2">
                                    <div className="p-2.5 bg-red-100 dark:bg-red-900/50 rounded-xl text-red-600 dark:text-red-400 shadow-sm border border-red-200 dark:border-red-800/50"><AlertTriangle className="w-5 h-5" /></div>
                                    <h3 className="text-xl font-black text-red-900 dark:text-red-200 tracking-tight">Danger Zone</h3>
                                </div>
                                <p className="text-sm font-medium text-red-700/90 dark:text-red-300/80 max-w-lg mt-1">Permanently delete your account and all associated data. This action is immediate and cannot be undone.</p>
                            </div>
                            <button onClick={() => setShowDeleteModal(true)} className="bg-white dark:bg-red-900/40 text-red-600 dark:text-red-400 border border-red-200 dark:border-red-900/50 px-8 py-3.5 rounded-xl font-bold text-sm hover:bg-red-50 dark:hover:bg-red-900/60 hover:border-red-300 transition-all shadow-sm flex items-center justify-center gap-2 active:scale-95 shrink-0 w-full sm:w-auto"><Trash2 className="w-4 h-4" /> Delete Account</button>
                        </div>
                    </div>
                </div>
            </ProfileLayout>
        </div>
    );
};