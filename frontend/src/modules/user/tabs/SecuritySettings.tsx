import React, { useState } from 'react';
import { createPortal } from 'react-dom';
import { Lock, Mail, ShieldCheck, ShieldAlert, AlertTriangle, Check, Smartphone, Save, Trash2 } from 'lucide-react';
import { userClient } from '../api/userClient';
import { Spinner } from '@/components/ui/Spinner';
import { StatusModal } from '@/components/ui/StatusModal';
import { ErrorBanner } from '@/components/ui/error/ErrorBanner';
import type { User } from '@/types';

interface SecuritySettingsProps {
    user: User;
    onUpdate: () => void;
}

export function SecuritySettings({ user, onUpdate }: SecuritySettingsProps) {
    const [credEmail, setCredEmail] = useState(user.email || '');
    const [credPassword, setCredPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [currentPassword, setCurrentPassword] = useState('');
    const [savingCreds, setSavingCreds] = useState(false);
    const [credsSaved, setCredsSaved] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const [showMfaSetup, setShowMfaSetup] = useState(false);
    const [mfaSecret, setMfaSecret] = useState('');
    const [mfaQr, setMfaQr] = useState('');
    const [mfaCode, setMfaCode] = useState('');
    const [mfaLoading, setMfaLoading] = useState(false);

    const [resendingEmail, setResendingEmail] = useState(false);
    const [emailSent, setEmailSent] = useState(false);

    const [showDeleteModal, setShowDeleteModal] = useState(false);

    const handleSaveCredentials = async (e: React.FormEvent) => {
        e.preventDefault();
        setError(null);
        if (credPassword !== confirmPassword) { setError("Passwords do not match."); return; }

        setSavingCreds(true);
        try {
            await userClient.updateCredentials({ email: credEmail, password: credPassword });
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
        if (credPassword !== confirmPassword) { setError("New passwords do not match."); return; }

        setSavingCreds(true);
        try {
            await userClient.changePassword({ currentPassword, newPassword: credPassword });
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

    const handleResendVerification = async () => {
        setResendingEmail(true);
        try {
            await userClient.resendVerification();
            setEmailSent(true);
        } catch (e: any) {
            setError(e.response?.data?.error || "Failed to send email.");
        } finally {
            setResendingEmail(false);
        }
    };

    const handleStartMfaSetup = async () => {
        try {
            const data = await userClient.startMfaSetup();
            setMfaSecret(data.secret);
            setMfaQr(data.qrCode);
            setShowMfaSetup(true);
            setError(null);
        } catch (e: any) {
            setError(e.response?.data?.error || "Failed to start 2FA setup.");
        }
    };

    const handleVerifyMfa = async () => {
        setMfaLoading(true);
        try {
            await userClient.verifyMfa(mfaCode);
            setShowMfaSetup(false);
            setMfaCode('');
            onUpdate();
        } catch (e: any) {
            setError(e.response?.data?.error || "Invalid verification code.");
        } finally {
            setMfaLoading(false);
        }
    };

    const handleDeleteAccount = async () => {
        try {
            await userClient.deleteAccount();
            window.location.href = '/';
        } catch (e) {
            setError("Failed to delete account. Please try again later.");
            setShowDeleteModal(false);
        }
    };

    return (
        <>
            {error && <ErrorBanner message={error} className="mb-6" />}
            {showDeleteModal && createPortal(
                <StatusModal type="error" title="Delete Account?" message="This action is permanent and cannot be undone. All your data, including preferences and API keys, will be removed." actionLabel="Yes, Delete My Account" onAction={handleDeleteAccount} onClose={() => setShowDeleteModal(false)} secondaryLabel="Cancel" />,
                document.body
            )}

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
                                    <span className="flex items-center justify-center w-8 h-8 rounded-full bg-modtale-accent/10 text-modtale-accent text-sm">1</span> Scan QR Code
                                </h4>
                                <div className="flex flex-col lg:flex-row gap-8 items-start">
                                    <div className="bg-white p-4 rounded-3xl w-fit shadow-md border border-slate-200 shrink-0 mx-auto lg:mx-0">
                                        <img src={mfaQr} alt="2FA QR Code" className="w-48 h-48" />
                                    </div>
                                    <div className="space-y-6 flex-1 w-full">
                                        <ul className="text-sm text-slate-600 dark:text-slate-400 space-y-3 font-medium bg-slate-50 dark:bg-white/5 p-5 rounded-2xl border border-slate-100 dark:border-white/5">
                                            <li className="flex gap-3"><span className="font-black text-slate-400">A.</span> Open your preferred authenticator app.</li>
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
                            <Lock className="w-4 h-4 text-slate-400" />
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

            <div className="border border-red-200 dark:border-red-900/30 bg-red-50/60 dark:bg-red-900/10 p-6 md:p-8 rounded-[2rem] backdrop-blur-xl shadow-sm relative overflow-hidden group mt-8">
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
        </>
    );
}