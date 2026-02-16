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
    <svg className={className} viewBox="0 0 568 501" fill="currentColor" xmlns="http://www.w3.org/2000/svg"><path d="M123.121 33.664C188.241 83.564 263.357 167.332 284 200.793C304.643 167.332 379.759 83.564 444.879 33.664C497.868 -6.932 568 -22.108 568 46.54V218.456C568 243.66 550.05 266.304 525.669 271.936L429.574 294.116C408.665 298.944 397.697 323.76 411.39 340.948C447.869 386.724 513.799 432.892 531.867 447.668C564.128 474.056 544.721 526 502.981 526H463.317C433.09 526 404.931 513.292 386.324 490.308C363.393 461.98 322.99 401.7 284 345.244C245.01 401.7 204.607 461.98 181.676 490.308C163.069 513.292 134.91 526 104.683 526H65.019C23.279 526 3.872 474.056 36.133 447.668C54.201 432.892 120.131 386.724 156.61 340.948C170.303 323.76 159.335 298.944 138.426 294.116L42.331 271.936C17.95 266.304 0 243.66 0 218.456V46.54C0 -22.108 70.132 -6.932 123.121 33.664Z" transform="scale(0.85) translate(10, -20)"/></svg>
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

        return (
            <div className={`flex items-center justify-between p-3 rounded-xl border transition-all h-full ${isLinked ? 'bg-white dark:bg-white/5 border-modtale-accent/30' : 'bg-slate-50 dark:bg-white/[0.02] border-slate-200 dark:border-white/5'}`}>
                <div className="flex items-center gap-3">
                    <div className={`p-2 rounded-lg ${isLinked ? 'text-modtale-accent bg-modtale-accent/10' : 'text-slate-400 bg-white dark:bg-white/5'}`}><Icon className="w-4 h-4" /></div>
                    <div>
                        <h4 className="font-bold text-[10px] text-slate-900 dark:text-white uppercase tracking-wider">{label}</h4>
                        {isLinked ? <p className="text-[10px] text-green-600 dark:text-green-400 font-bold flex items-center gap-1 mt-0.5"><Check className="w-3 h-3" /> {account.username}</p> : <p className="text-[10px] text-slate-400 mt-0.5">Not connected</p>}
                    </div>
                </div>
                {isLinked ? (
                    <div className="flex gap-1.5">
                        {canBeVisible ? (
                            <button onClick={() => api.post(`/user/connections/${provider}/toggle-visibility`).then(onUpdate)} className={`p-1.5 rounded-lg transition-colors ${account.visible ? 'text-modtale-accent bg-modtale-accent/10 hover:bg-modtale-accent/20' : 'text-slate-400 hover:bg-slate-100 dark:hover:bg-white/10'}`} title={account.visible ? "Publicly Visible" : "Hidden from profile"}>{account.visible ? <Eye className="w-3.5 h-3.5" /> : <EyeOff className="w-3.5 h-3.5" />}</button>
                        ) : (<div className="p-1.5 text-slate-300 dark:text-white/10 cursor-not-allowed" title="Private connection"><EyeOff className="w-3.5 h-3.5" /></div>)}
                        <button onClick={() => setShowUnlinkModal({ provider, label })} className="p-1.5 text-red-500 hover:bg-red-50 dark:hover:bg-red-950/30 rounded-lg transition-colors" title="Unlink"><Trash2 className="w-3.5 h-3.5" /></button>
                    </div>
                ) : (
                    <button onClick={() => window.location.href = `${BACKEND_URL}/oauth2/authorization/${provider}`} className="bg-white dark:bg-white/10 border border-slate-200 dark:border-white/10 text-slate-700 dark:text-slate-200 px-3 py-1.5 rounded-lg text-[10px] font-black uppercase tracking-widest hover:border-modtale-accent hover:text-modtale-accent transition-all flex items-center gap-1.5 shadow-sm whitespace-nowrap"><Plus className="w-3 h-3" /> Link</button>
                )}
            </div>
        );
    };

    const headerInput = (
        <div className="space-y-1 w-full min-w-0">
            <div>
                <label className="text-[10px] font-black text-slate-400 uppercase tracking-widest block mb-1">Username</label>
                <div className="flex items-center gap-2">
                    {isEditingUsername ? (
                        <div className="relative flex-1 max-w-full">
                            <input type="text" value={username} onChange={(e) => setUsername(e.target.value)} className="text-2xl md:text-3xl font-black text-slate-900 dark:text-white tracking-tighter bg-transparent border-b border-slate-200 dark:border-white/10 focus:border-modtale-accent focus:outline-none w-full placeholder-slate-300 dark:placeholder-white/20" placeholder="Username" autoFocus />
                            <button onClick={() => { setUsername(user.username); setIsEditingUsername(false); }} className="absolute right-2 top-2 text-slate-400 hover:text-red-500"><XCircle className="w-5 h-5" /></button>
                        </div>
                    ) : (
                        <div className="flex items-center gap-2 group cursor-pointer" onClick={() => setIsEditingUsername(true)}>
                            <h1 className="text-2xl md:text-3xl font-black text-slate-900 dark:text-white tracking-tighter truncate">{user.username}</h1>
                            <Edit3 className="w-5 h-5 text-slate-300 opacity-0 group-hover:opacity-100 transition-opacity" />
                        </div>
                    )}
                </div>
            </div>
            {isEditingUsername && <p className="text-[10px] text-orange-500 font-bold mt-1">Warning: Changing your username will break existing links to your profile.</p>}
        </div>
    );

    const actionContent = (
        <div className="flex flex-col md:flex-row items-center gap-2 w-full md:w-auto">
            {isDirty && <div className="text-[10px] font-bold text-amber-500 animate-pulse uppercase tracking-widest bg-amber-500/10 px-2 py-1 rounded whitespace-nowrap order-first md:order-none">Unsaved Changes</div>}
            <button onClick={handleSave} disabled={saving} className="bg-modtale-accent text-white px-6 py-2 rounded-xl font-black flex items-center gap-2 transition-all shadow-lg active:scale-95 hover:bg-modtale-accentHover disabled:opacity-70 text-xs flex-shrink-0 h-10 w-full md:w-auto justify-center whitespace-nowrap">
                {saving ? <Spinner className="w-4 h-4 !p-0" fullScreen={false} /> : (saved ? <Check className="w-4 h-4" /> : <Save className="w-4 h-4" />)}
                {saved ? 'Saved' : 'Save Changes'}
            </button>
        </div>
    );

    const bioInput = (
        <div>
            <div className="flex justify-between items-center mb-1.5 px-1">
                <label className="text-[10px] font-black text-slate-400 uppercase tracking-widest">Biography</label>
                <span className={`text-[10px] font-bold ${bio.length > 280 ? 'text-red-500' : 'text-slate-400'}`}>{bio.length}/300</span>
            </div>
            <textarea value={bio} onChange={(e) => setBio(e.target.value)} rows={3} className="w-full bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-xl px-4 py-3 text-sm focus:ring-2 focus:ring-modtale-accent outline-none transition-all dark:text-white placeholder:text-slate-400 resize-none" placeholder="Write something about yourself..." maxLength={300} />
        </div>
    );

    const containerClasses = "w-full mx-auto px-4 sm:px-8 md:px-12 lg:px-16";

    return (
        <div className="relative pb-16 space-y-6">
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
            />

            <div className={containerClasses}>
                <div className="bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/10 rounded-3xl p-6 shadow-sm">
                    <div className="flex items-center gap-3 mb-6 border-b border-slate-100 dark:border-white/5 pb-4">
                        <Lock className="w-4 h-4 text-modtale-accent" />
                        <h3 className="text-sm font-black text-slate-900 dark:text-white uppercase tracking-wide">Security</h3>
                    </div>

                    <div className="space-y-8">
                        <div>
                            <h4 className="font-bold text-slate-900 dark:text-white text-sm mb-2 flex items-center gap-2"><Mail className="w-4 h-4 text-slate-400" /> Email Address</h4>
                            <div className="p-4 rounded-2xl bg-slate-50 dark:bg-white/5 border border-slate-200 dark:border-white/10">
                                <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
                                    <div className="flex-1">
                                        <div className="flex items-center gap-2 mb-1">
                                            <span className="text-sm font-medium text-slate-900 dark:text-white">{user.email || 'No email associated'}</span>
                                            {user.email && (
                                                user.emailVerified
                                                    ? <span className="bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-400 text-[10px] font-bold px-2 py-0.5 rounded-full uppercase tracking-wider flex items-center gap-1"><ShieldCheck className="w-3 h-3"/> Verified</span>
                                                    : <span className="bg-yellow-100 dark:bg-yellow-900/30 text-yellow-700 dark:text-yellow-400 text-[10px] font-bold px-2 py-0.5 rounded-full uppercase tracking-wider flex items-center gap-1"><ShieldAlert className="w-3 h-3"/> Unverified</span>
                                            )}
                                        </div>
                                        <p className="text-xs text-slate-500">Used for login, notifications, and recovery.</p>
                                    </div>
                                </div>

                                {!user.emailVerified && user.email && (
                                    <div className="mt-4 pt-4 border-t border-slate-200 dark:border-white/10 flex flex-col md:flex-row items-start md:items-center justify-between gap-3">
                                        <div className="flex items-center gap-2 text-yellow-700 dark:text-yellow-400">
                                            <AlertTriangle className="w-4 h-4" />
                                            <p className="text-xs font-medium">Your email is unverified. Please check your inbox.</p>
                                        </div>
                                        {emailSent ? (
                                            <span className="text-xs font-bold text-green-600 dark:text-green-400 flex items-center gap-1.5">
                                                <Check className="w-3.5 h-3.5" /> Sent!
                                            </span>
                                        ) : (
                                            <button onClick={handleResendVerification} disabled={resendingEmail} className="text-xs font-bold text-slate-900 dark:text-white hover:underline disabled:opacity-50">
                                                {resendingEmail ? 'Sending...' : 'Resend Verification Email'}
                                            </button>
                                        )}
                                    </div>
                                )}
                            </div>
                        </div>

                        <div>
                            <h4 className="font-bold text-slate-900 dark:text-white text-sm mb-2 flex items-center gap-2"><ShieldCheck className="w-4 h-4 text-slate-400" /> Two-Factor Authentication</h4>
                            {(user as any).mfaEnabled ? (
                                <div className="flex items-center justify-between p-4 rounded-2xl bg-green-50 dark:bg-green-900/10 border border-green-200 dark:border-green-900/30">
                                    <div className="flex items-center gap-3 text-green-700 dark:text-green-400"><Check className="w-5 h-5" /><span className="font-bold text-sm">2FA is enabled. Your account is secure.</span></div>
                                    <button className="text-red-500 text-xs font-bold hover:underline opacity-50 cursor-not-allowed" title="Disabling 2FA is currently disabled for security.">Disable</button>
                                </div>
                            ) : (
                                <div>
                                    <p className="text-xs text-slate-500 dark:text-slate-400 mb-4 max-w-xl">Protect your account by requiring a code from an authenticator app (like Google Authenticator or Authy) when you log in.</p>
                                    {!showMfaSetup && <button onClick={handleStartMfaSetup} className="bg-slate-900 dark:bg-white text-white dark:text-slate-900 px-4 py-2 rounded-xl font-bold text-xs hover:bg-slate-800 transition-colors">Enable 2FA</button>}
                                </div>
                            )}

                            {showMfaSetup && (
                                <div className="mt-4 p-6 bg-slate-50 dark:bg-black/20 rounded-2xl border border-slate-200 dark:border-white/10 animate-in fade-in slide-in-from-top-2">
                                    <h4 className="font-bold text-slate-900 dark:text-white mb-4">Scan QR Code</h4>
                                    <div className="flex flex-col md:flex-row gap-6">
                                        <div className="bg-white p-2 rounded-xl w-fit h-fit shadow-sm"><img src={mfaQr} alt="2FA QR Code" className="w-40 h-40" /></div>
                                        <div className="space-y-4 flex-1">
                                            <p className="text-xs text-slate-500 dark:text-slate-400 leading-relaxed">1. Open Google Authenticator or Authy on your phone.<br/>2. Scan the QR code to the left.<br/>3. Enter the 6-digit code below to verify.</p>
                                            <div className="relative max-w-xs">
                                                <input type="text" placeholder="000 000" value={mfaCode} onChange={e => setMfaCode(e.target.value.replace(/\D/g, '').slice(0, 6))} className="w-full px-4 py-2 pl-10 rounded-xl bg-white dark:bg-black/40 border border-slate-200 dark:border-white/10 font-mono tracking-widest text-lg focus:ring-2 focus:ring-modtale-accent outline-none transition-all" maxLength={6} />
                                                <Smartphone className="absolute left-3 top-2.5 w-5 h-5 text-slate-400" />
                                            </div>
                                            <div className="flex gap-2 max-w-xs">
                                                <button onClick={handleVerifyMfa} disabled={mfaLoading || mfaCode.length !== 6} className="flex-1 bg-modtale-accent text-white py-2 rounded-xl font-bold text-xs hover:bg-modtale-accentHover transition-colors disabled:opacity-50 flex items-center justify-center gap-2">{mfaLoading ? <Spinner className="w-3 h-3" /> : 'Verify & Enable'}</button>
                                                <button onClick={() => { setShowMfaSetup(false); setMfaCode(''); }} className="px-4 py-2 text-xs font-bold text-slate-500 hover:text-slate-700 dark:hover:text-slate-300">Cancel</button>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            )}
                        </div>

                        <div className="border-t border-slate-100 dark:border-white/5 pt-6">
                            <h4 className="font-bold text-slate-900 dark:text-white text-sm mb-2 flex items-center gap-2">
                                <Key className="w-4 h-4 text-slate-400" />
                                {(user as any).hasPassword ? 'Change Password' : 'Set Password'}
                            </h4>

                            {(user as any).hasPassword ? (
                                <div>
                                    <p className="text-xs text-slate-500 dark:text-slate-400 mb-4 max-w-xl">Change your current login password.</p>
                                    <form onSubmit={handleChangePassword} className="max-w-md space-y-3">
                                        <div>
                                            <label className="text-[10px] font-black text-slate-400 uppercase tracking-widest block mb-1">Current Password</label>
                                            <input type="password" required value={currentPassword} onChange={e => setCurrentPassword(e.target.value)} className="w-full px-3 py-2.5 rounded-lg bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 focus:ring-2 focus:ring-modtale-accent focus:border-transparent outline-none transition-all text-sm" placeholder="••••••••" />
                                        </div>
                                        <div>
                                            <label className="text-[10px] font-black text-slate-400 uppercase tracking-widest block mb-1">New Password</label>
                                            <input type="password" required minLength={6} value={credPassword} onChange={e => setCredPassword(e.target.value)} className="w-full px-3 py-2.5 rounded-lg bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 focus:ring-2 focus:ring-modtale-accent focus:border-transparent outline-none transition-all text-sm" placeholder="••••••••" />
                                        </div>
                                        <div>
                                            <label className="text-[10px] font-black text-slate-400 uppercase tracking-widest block mb-1">Confirm New Password</label>
                                            <input type="password" required minLength={6} value={confirmPassword} onChange={e => setConfirmPassword(e.target.value)} className="w-full px-3 py-2.5 rounded-lg bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 focus:ring-2 focus:ring-modtale-accent focus:border-transparent outline-none transition-all text-sm" placeholder="••••••••" />
                                        </div>
                                        <button type="submit" disabled={savingCreds} className="bg-slate-900 dark:bg-white text-white dark:text-slate-900 px-6 py-2 rounded-xl font-bold text-xs hover:bg-slate-800 dark:hover:bg-slate-200 transition-colors flex items-center gap-2 h-10 mt-2">
                                            {savingCreds ? <Spinner className="w-4 h-4" /> : (credsSaved ? <Check className="w-4 h-4" /> : <Save className="w-4 h-4" />)} {credsSaved ? 'Saved' : 'Update Password'}
                                        </button>
                                    </form>
                                </div>
                            ) : (
                                <div>
                                    <p className="text-xs text-slate-500 dark:text-slate-400 mb-4 max-w-xl">Set a password to log in with your email address instead of a social provider.</p>
                                    <form onSubmit={handleSaveCredentials} className="max-w-md space-y-3">
                                        {!user.email && (
                                            <div>
                                                <label className="text-[10px] font-black text-slate-400 uppercase tracking-widest block mb-1">Email Address</label>
                                                <input type="email" required value={credEmail} onChange={e => setCredEmail(e.target.value)} className="w-full px-3 py-2.5 rounded-lg bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 focus:ring-2 focus:ring-modtale-accent focus:border-transparent outline-none transition-all text-sm" placeholder="your@email.com" />
                                            </div>
                                        )}
                                        <div>
                                            <label className="text-[10px] font-black text-slate-400 uppercase tracking-widest block mb-1">New Password</label>
                                            <input type="password" required minLength={6} value={credPassword} onChange={e => setCredPassword(e.target.value)} className="w-full px-3 py-2.5 rounded-lg bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 focus:ring-2 focus:ring-modtale-accent focus:border-transparent outline-none transition-all text-sm" placeholder="••••••••" />
                                        </div>
                                        <div>
                                            <label className="text-[10px] font-black text-slate-400 uppercase tracking-widest block mb-1">Confirm Password</label>
                                            <input type="password" required minLength={6} value={confirmPassword} onChange={e => setConfirmPassword(e.target.value)} className="w-full px-3 py-2.5 rounded-lg bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 focus:ring-2 focus:ring-modtale-accent focus:border-transparent outline-none transition-all text-sm" placeholder="••••••••" />
                                        </div>
                                        <button type="submit" disabled={savingCreds} className="bg-slate-900 dark:bg-white text-white dark:text-slate-900 px-6 py-2 rounded-xl font-bold text-xs hover:bg-slate-800 dark:hover:bg-slate-200 transition-colors flex items-center gap-2 h-10 mt-2">
                                            {savingCreds ? <Spinner className="w-4 h-4" /> : (credsSaved ? <Check className="w-4 h-4" /> : <Save className="w-4 h-4" />)} {credsSaved ? 'Saved' : 'Set Password'}
                                        </button>
                                    </form>
                                </div>
                            )}
                        </div>
                    </div>
                </div>
            </div>

            <div className={containerClasses}>
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

            <div className={containerClasses}>
                <div className="bg-red-50 dark:bg-red-900/10 border border-red-200 dark:border-red-900/20 rounded-3xl p-6 shadow-sm">
                    <div className="flex items-center justify-between">
                        <div>
                            <div className="flex items-center gap-3 mb-1"><AlertTriangle className="w-5 h-5 text-red-600 dark:text-red-400" /><h3 className="text-sm font-black text-red-900 dark:text-red-200 uppercase tracking-wide">Danger Zone</h3></div>
                            <p className="text-xs text-red-700 dark:text-red-300/70 max-w-md">Permanently delete your account and all associated data. This action cannot be undone.</p>
                        </div>
                        <button onClick={() => setShowDeleteModal(true)} className="bg-red-600 hover:bg-red-700 text-white px-5 py-2.5 rounded-xl font-bold text-xs transition-colors shadow-lg shadow-red-600/20 flex items-center gap-2"><Trash2 className="w-4 h-4" /> Delete Account</button>
                    </div>
                </div>
            </div>
        </div>
    );
};