import React, { useState, useEffect } from 'react';
import { createPortal } from 'react-dom';
import { X, Github, ArrowRight, Loader2, ArrowLeft } from 'lucide-react';
import { DiscordBrandIcon, GitLabBrandIcon, GoogleBrandIcon } from '@/components/ui/icons/BrandIcons';
import { useNavigate } from 'react-router-dom';
import { BACKEND_URL } from '@/utils/api';
import { useToast } from '@/components/ui/Toast';
import { authClient } from '../api/authClient';

interface SignInModalProps {
    isOpen: boolean;
    onClose: () => void;
}

export function SignInModal({ isOpen, onClose }: SignInModalProps) {
    const { showToast } = useToast();
    const navigate = useNavigate();
    const [mounted, setMounted] = useState(false);
    const [mode, setMode] = useState<'signin' | 'register' | 'forgot-password'>('signin');
    const [email, setEmail] = useState('');
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        setMounted(true);
        if (isOpen) document.body.style.overflow = 'hidden';
        return () => { document.body.style.overflow = ''; };
    }, [isOpen]);

    if (!isOpen || !mounted) return null;

    const handleOAuthLogin = (provider: string) => {
        window.location.href = `${BACKEND_URL}/oauth2/authorization/${provider}`;
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setLoading(true);

        try {
            if (mode === 'register') {
                await authClient.register({ username, email, password });
                setMode('signin');
                showToast("Account created! Please sign in.", 'success');
                setLoading(false);
                return;
            }

            if (mode === 'forgot-password') {
                await authClient.forgotPassword({ email });
                showToast("If an account exists, a reset link has been sent.", 'success');
                setLoading(false);
                return;
            }

            const res = await authClient.signIn({
                username: username || email,
                password
            });

            if (res.data.mfa_required) {
                onClose();
                navigate(`/mfa?token=${res.data.pre_auth_token}`);
                return;
            }

            window.location.href = '/dashboard/profile';
        } catch (err: any) {
            console.error(err);
            if (err.response?.status === 401) {
                showToast("Invalid credentials.", 'error');
            } else if (err.response?.data?.error) {
                showToast(err.response.data.error, 'error');
            } else {
                showToast("An error occurred. Please try again.", 'error');
            }
        } finally {
            setLoading(false);
        }
    };

    return createPortal(
        <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/80 backdrop-blur-sm p-4 animate-in fade-in duration-200" onClick={onClose}>
            <div className="bg-white/90 dark:bg-slate-900/90 backdrop-blur-2xl border border-slate-200 dark:border-white/10 rounded-3xl max-w-sm w-full shadow-2xl relative scale-100 animate-in zoom-in-95 duration-200 overflow-hidden" onClick={e => e.stopPropagation()}>
                <div className="p-6">
                    <button onClick={onClose} className="absolute top-4 right-4 text-slate-400 hover:text-slate-600 dark:text-slate-500 dark:hover:text-slate-300 transition-colors">
                        <X className="w-5 h-5" />
                    </button>

                    <div className="text-center mb-6">
                        <h2 className="text-2xl font-black text-slate-900 dark:text-white tracking-tight mb-2">
                            {mode === 'signin' ? 'Welcome Back' : (mode === 'register' ? 'Create Account' : 'Reset Password')}
                        </h2>
                        <p className="text-slate-600 dark:text-slate-400 text-sm">
                            {mode === 'signin' ? 'Tell the tale of your mods.' : (mode === 'register' ? 'Join the community today.' : 'Enter your email to receive a reset link.')}
                        </p>
                    </div>

                    {mode !== 'forgot-password' && (
                        <>
                            <div className="space-y-3 mb-6">
                                <button
                                    onClick={() => handleOAuthLogin('github')}
                                    className="w-full bg-[#24292e] text-white py-3.5 px-4 rounded-xl font-bold flex items-center justify-center gap-3 hover:bg-[#2f363d] transition-colors active:scale-95 duration-200 shadow-lg shadow-black/10"
                                >
                                    <Github className="w-5 h-5" />
                                    <span className="text-sm">GitHub</span>
                                </button>

                                <div className="grid grid-cols-3 gap-3">
                                    <button
                                        onClick={() => handleOAuthLogin('gitlab')}
                                        className="w-full bg-[#FC6D26] text-white py-3 px-4 rounded-xl font-bold flex items-center justify-center hover:bg-[#e24329] transition-colors active:scale-95 duration-200 shadow-lg shadow-orange-500/20"
                                        title="Sign in with GitLab"
                                    >
                                        <GitLabBrandIcon className="w-5 h-5" />
                                    </button>
                                    <button
                                        onClick={() => handleOAuthLogin('discord')}
                                        className="w-full bg-[#5865F2] text-white py-3 px-4 rounded-xl font-bold flex items-center justify-center hover:bg-[#4752c4] transition-colors active:scale-95 duration-200 shadow-lg shadow-indigo-500/20"
                                        title="Sign in with Discord"
                                    >
                                        <DiscordBrandIcon className="w-5 h-5" />
                                    </button>
                                    <button
                                        onClick={() => handleOAuthLogin('google')}
                                        className="w-full bg-white text-slate-700 border border-slate-200 py-3 px-4 rounded-xl font-bold flex items-center justify-center hover:bg-slate-50 transition-colors active:scale-95 duration-200 shadow-lg shadow-black/5"
                                        title="Sign in with Google"
                                    >
                                        <GoogleBrandIcon className="w-5 h-5" />
                                    </button>
                                </div>
                            </div>

                            <div className="relative mb-6 flex items-center gap-3">
                                <div className="flex-1 border-t border-slate-200 dark:border-white/10"></div>
                                <span className="text-sm text-slate-500 dark:text-slate-400 font-medium">or use email</span>
                                <div className="flex-1 border-t border-slate-200 dark:border-white/10"></div>
                            </div>
                        </>
                    )}

                    <form onSubmit={handleSubmit} className="space-y-4">
                        {mode === 'register' && (
                            <div className="space-y-1">
                                <label className="text-xs font-bold text-slate-700 dark:text-slate-300 uppercase pl-1">Username</label>
                                <input
                                    type="text"
                                    required
                                    value={username}
                                    onChange={e => setUsername(e.target.value)}
                                    className="w-full px-4 py-3 rounded-xl bg-white/50 dark:bg-black/20 border border-slate-200 dark:border-white/10 focus:ring-2 focus:ring-modtale-accent focus:border-transparent outline-none transition-all text-sm text-slate-900 dark:text-white shadow-inner backdrop-blur-md"
                                    placeholder="Display name"
                                />
                            </div>
                        )}

                        <div className="space-y-1">
                            <label className="text-xs font-bold text-slate-700 dark:text-slate-300 uppercase pl-1">
                                {mode === 'signin' ? 'Email or Username' : 'Email'}
                            </label>
                            <input
                                type={mode === 'signin' ? "text" : "email"}
                                required
                                value={mode === 'signin' ? (username || email) : email}
                                onChange={e => mode === 'signin' ? setUsername(e.target.value) : setEmail(e.target.value)}
                                className="w-full px-4 py-3 rounded-xl bg-white/50 dark:bg-black/20 border border-slate-200 dark:border-white/10 focus:ring-2 focus:ring-modtale-accent focus:border-transparent outline-none transition-all text-sm text-slate-900 dark:text-white shadow-inner backdrop-blur-md"
                                placeholder="user@example.com"
                            />
                        </div>

                        {mode !== 'forgot-password' && (
                            <div className="space-y-1">
                                <div className="flex justify-between items-center pl-1">
                                    <label className="text-xs font-bold text-slate-700 dark:text-slate-300 uppercase">Password</label>
                                    {mode === 'signin' && (
                                        <button
                                            type="button"
                                            onClick={() => setMode('forgot-password')}
                                            className="text-xs text-modtale-accent hover:text-modtale-accentHover font-bold"
                                        >
                                            Forgot?
                                        </button>
                                    )}
                                </div>
                                <input
                                    type="password"
                                    required
                                    minLength={6}
                                    value={password}
                                    onChange={e => setPassword(e.target.value)}
                                    className="w-full px-4 py-3 rounded-xl bg-white/50 dark:bg-black/20 border border-slate-200 dark:border-white/10 focus:ring-2 focus:ring-modtale-accent focus:border-transparent outline-none transition-all text-sm text-slate-900 dark:text-white shadow-inner backdrop-blur-md"
                                    placeholder="••••••••"
                                />
                            </div>
                        )}

                        <button
                            type="submit"
                            disabled={loading}
                            className="w-full bg-modtale-accent text-white py-3 px-4 rounded-xl font-bold flex items-center justify-center gap-2 hover:bg-modtale-accentHover transition-colors active:scale-95 duration-200 shadow-lg shadow-modtale-accent/20 mt-2"
                        >
                            {loading ? <Loader2 className="w-5 h-5 animate-spin" /> : (
                                <>
                                    {mode === 'signin' ? 'Sign In' : (mode === 'register' ? 'Create Account' : 'Send Reset Link')}
                                    <ArrowRight className="w-5 h-5" />
                                </>
                            )}
                        </button>
                    </form>

                    <div className="mt-6 text-center">
                        <button
                            onClick={() => {
                                if (mode === 'forgot-password') {
                                    setMode('signin');
                                } else {
                                    setMode(mode === 'signin' ? 'register' : 'signin');
                                }
                            }}
                            className="text-sm font-bold text-slate-500 hover:text-modtale-accent dark:text-slate-400 dark:hover:text-white transition-colors flex items-center justify-center gap-2 mx-auto"
                        >
                            {mode === 'forgot-password' ? (
                                <>
                                    <ArrowLeft className="w-4 h-4" /> Back to Sign In
                                </>
                            ) : (
                                mode === 'signin' ? "Don't have an account? Sign up" : "Already have an account? Sign in"
                            )}
                        </button>
                    </div>
                </div>
            </div>
        </div>,
        document.body
    );
}
