import React, { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { ArrowRight, Loader2, Lock } from 'lucide-react';
import { authClient } from '../api/authClient';
import { StatusModal } from '@/components/ui/StatusModal';
import { extractApiErrorMessage } from '@/utils/api';

export function ResetPassword() {
    const [searchParams] = useSearchParams();
    const token = searchParams.get('token');

    const [password, setPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [loading, setLoading] = useState(false);
    const [statusModal, setStatusModal] = useState<{ title: string; msg: string } | null>(null);
    const [success, setSuccess] = useState(false);

    if (!token) {
        return (
            <div className="flex-1 flex items-center justify-center p-4 min-h-[60vh]">
                <div className="bg-white/90 dark:bg-slate-900/90 backdrop-blur-2xl border border-slate-200 dark:border-white/10 rounded-3xl p-8 max-w-md w-full shadow-2xl text-center">
                    <h1 className="text-xl font-bold text-red-500 mb-2">Invalid Request</h1>
                    <p className="text-slate-500 dark:text-slate-400">Missing reset token.</p>
                </div>
            </div>
        );
    }

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        if (password !== confirmPassword) {
            setStatusModal({
                title: 'Passwords Do Not Match',
                msg: 'The new password and confirmation do not match yet. Please make sure both fields are identical before continuing.'
            });
            return;
        }

        setLoading(true);
        setStatusModal(null);

        try {
            await authClient.resetPassword({ token, password });
            setSuccess(true);
        } catch (err: unknown) {
            setStatusModal({
                title: 'Password Reset Failed',
                msg: extractApiErrorMessage(err, 'We could not reset your password.')
            });
        } finally {
            setLoading(false);
        }
    };

    if (success) {
        return (
            <div className="flex-1 flex items-center justify-center p-4 min-h-[60vh]">
                <div className="bg-white/90 dark:bg-slate-900/90 backdrop-blur-2xl border border-slate-200 dark:border-white/10 rounded-3xl p-8 max-w-md w-full shadow-2xl text-center">
                    <div className="w-16 h-16 bg-green-100 dark:bg-green-900/20 text-green-600 dark:text-green-400 rounded-full flex items-center justify-center mx-auto mb-6">
                        <Lock className="w-8 h-8" />
                    </div>
                    <h1 className="text-2xl font-black text-slate-900 dark:text-white mb-2">Password Reset</h1>
                    <p className="text-slate-500 dark:text-slate-400 mb-8">
                        Your password has been successfully updated. You can now sign in with your new credentials.
                    </p>
                    <button
                        onClick={() => { window.location.href = '/'; }}
                        className="w-full bg-modtale-accent text-white py-3 px-4 rounded-xl font-bold hover:bg-modtale-accentHover transition-colors shadow-lg shadow-modtale-accent/20"
                    >
                        Go to Sign In
                    </button>
                </div>
            </div>
        );
    }

    return (
        <div className="flex-1 flex items-center justify-center p-4 min-h-[80vh]">
            {statusModal && (
                <StatusModal
                    type="error"
                    title={statusModal.title}
                    message={statusModal.msg}
                    onClose={() => setStatusModal(null)}
                />
            )}
            <div className="bg-white/90 dark:bg-slate-900/90 backdrop-blur-2xl border border-slate-200 dark:border-white/10 rounded-3xl p-8 max-w-md w-full shadow-2xl">
                <div className="text-center mb-8">
                    <div className="w-16 h-16 bg-modtale-accent/10 text-modtale-accent rounded-2xl flex items-center justify-center mx-auto mb-4 border border-modtale-accent/20 shadow-inner">
                        <Lock className="w-8 h-8" />
                    </div>
                    <h1 className="text-2xl font-black text-slate-900 dark:text-white mb-2">Reset Password</h1>
                    <p className="text-slate-500 dark:text-slate-400 text-sm">Enter a new password for your account.</p>
                </div>

                <form onSubmit={handleSubmit} className="space-y-5">
                    <div className="space-y-1">
                        <label className="text-[10px] font-black text-slate-400 uppercase tracking-widest block pl-1">New Password</label>
                        <input
                            type="password"
                            required
                            minLength={6}
                            value={password}
                            onChange={e => setPassword(e.target.value)}
                            className="w-full px-4 py-3 rounded-xl bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 focus:ring-2 focus:ring-modtale-accent focus:border-transparent outline-none transition-all text-sm shadow-inner dark:text-white"
                            placeholder="••••••••"
                        />
                    </div>

                    <div className="space-y-1">
                        <label className="text-[10px] font-black text-slate-400 uppercase tracking-widest block pl-1">Confirm Password</label>
                        <input
                            type="password"
                            required
                            minLength={6}
                            value={confirmPassword}
                            onChange={e => setConfirmPassword(e.target.value)}
                            className="w-full px-4 py-3 rounded-xl bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 focus:ring-2 focus:ring-modtale-accent focus:border-transparent outline-none transition-all text-sm shadow-inner dark:text-white"
                            placeholder="••••••••"
                        />
                    </div>

                    <button
                        type="submit"
                        disabled={loading}
                        className="w-full bg-modtale-accent text-white py-3.5 px-4 rounded-xl font-black flex items-center justify-center gap-2 hover:bg-modtale-accentHover transition-all active:scale-95 duration-200 shadow-lg shadow-modtale-accent/20 mt-2"
                    >
                        {loading ? <Loader2 className="w-5 h-5 animate-spin" /> : (
                            <>
                                Update Password <ArrowRight className="w-5 h-5" />
                            </>
                        )}
                    </button>
                </form>
            </div>
        </div>
    );
}
