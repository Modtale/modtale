import React, { useState } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { api } from '../../utils/api';
import { ArrowRight, Loader2, Lock } from 'lucide-react';

export const ResetPassword = () => {
    const [searchParams] = useSearchParams();
    const token = searchParams.get('token');
    const navigate = useNavigate();

    const [password, setPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [success, setSuccess] = useState(false);

    if (!token) {
        return (
            <div className="flex-1 flex items-center justify-center p-4">
                <div className="bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/10 rounded-3xl p-8 max-w-md w-full shadow-xl text-center">
                    <h1 className="text-xl font-bold text-red-500 mb-2">Invalid Request</h1>
                    <p className="text-slate-500 dark:text-slate-400">Missing reset token.</p>
                </div>
            </div>
        );
    }

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        if (password !== confirmPassword) {
            setError("Passwords do not match.");
            return;
        }

        setLoading(true);
        setError(null);

        try {
            await api.post('/auth/reset-password', { token, password });
            setSuccess(true);
        } catch (err: any) {
            setError(err.response?.data?.error || "Failed to reset password.");
        } finally {
            setLoading(false);
        }
    };

    if (success) {
        return (
            <div className="flex-1 flex items-center justify-center p-4">
                <div className="bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/10 rounded-3xl p-8 max-w-md w-full shadow-xl text-center">
                    <div className="w-16 h-16 bg-green-100 dark:bg-green-900/20 text-green-600 dark:text-green-400 rounded-full flex items-center justify-center mx-auto mb-6">
                        <Lock className="w-8 h-8" />
                    </div>
                    <h1 className="text-2xl font-black text-slate-900 dark:text-white mb-2">Password Reset</h1>
                    <p className="text-slate-500 dark:text-slate-400 mb-8">
                        Your password has been successfully updated. You can now sign in with your new credentials.
                    </p>
                    <button
                        onClick={() => { window.location.href = '/'; }}
                        className="w-full bg-modtale-accent text-white py-3 px-4 rounded-xl font-bold hover:bg-modtale-accentHover transition-colors"
                    >
                        Go to Sign In
                    </button>
                </div>
            </div>
        );
    }

    return (
        <div className="flex-1 flex items-center justify-center p-4">
            <div className="bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/10 rounded-3xl p-8 max-w-md w-full shadow-xl">
                <div className="text-center mb-8">
                    <h1 className="text-2xl font-black text-slate-900 dark:text-white mb-2">Reset Password</h1>
                    <p className="text-slate-500 dark:text-slate-400 text-sm">Enter a new password for your account.</p>
                </div>

                <form onSubmit={handleSubmit} className="space-y-4">
                    {error && (
                        <div className="p-3 text-sm text-red-500 bg-red-50 dark:bg-red-900/20 rounded-lg text-center">
                            {error}
                        </div>
                    )}

                    <div className="space-y-1">
                        <label className="text-xs font-bold text-slate-700 dark:text-slate-300 uppercase">New Password</label>
                        <input
                            type="password"
                            required
                            minLength={6}
                            value={password}
                            onChange={e => setPassword(e.target.value)}
                            className="w-full px-3 py-2.5 rounded-lg bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 focus:ring-2 focus:ring-modtale-accent focus:border-transparent outline-none transition-all text-sm"
                            placeholder="••••••••"
                        />
                    </div>

                    <div className="space-y-1">
                        <label className="text-xs font-bold text-slate-700 dark:text-slate-300 uppercase">Confirm Password</label>
                        <input
                            type="password"
                            required
                            minLength={6}
                            value={confirmPassword}
                            onChange={e => setConfirmPassword(e.target.value)}
                            className="w-full px-3 py-2.5 rounded-lg bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 focus:ring-2 focus:ring-modtale-accent focus:border-transparent outline-none transition-all text-sm"
                            placeholder="••••••••"
                        />
                    </div>

                    <button
                        type="submit"
                        disabled={loading}
                        className="w-full bg-modtale-accent text-white py-3 px-4 rounded-xl font-bold flex items-center justify-center gap-2 hover:bg-modtale-accent/90 transition-colors active:scale-95 duration-200 shadow-lg shadow-modtale-accent/20"
                    >
                        {loading ? <Loader2 className="w-4 h-4 animate-spin" /> : (
                            <>
                                Reset Password <ArrowRight className="w-4 h-4" />
                            </>
                        )}
                    </button>
                </form>
            </div>
        </div>
    );
};