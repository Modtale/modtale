import React, { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { X, Github, Mail, ArrowRight, Loader2, ArrowLeft, Smartphone } from 'lucide-react';
import { BACKEND_URL, api } from '../../utils/api';

const GitLabIcon = ({ className }: { className?: string }) => (
    <svg className={className} viewBox="0 0 24 24" fill="currentColor" xmlns="http://www.w3.org/2000/svg">
        <path d="M22.65 14.39L12 22.13L1.35 14.39L4.74 3.99C4.82 3.73 5.12 3.63 5.33 3.82L8.99 7.5L12 10.5L15.01 7.5L18.67 3.82C18.88 3.63 19.18 3.73 19.26 3.99L22.65 14.39Z" />
    </svg>
);

const DiscordIcon = ({ className }: { className?: string }) => (
    <svg className={className} fill="currentColor" viewBox="0 0 127.14 96.36">
        <path d="M107.7,8.07A105.15,105.15,0,0,0,81.47,0a72.06,72.06,0,0,0-3.36,6.83A97.68,97.68,0,0,0,49,6.83,72.37,72.37,0,0,0,45.64,0,105.89,105.89,0,0,0,19.39,8.09C2.79,32.65-1.71,56.6.54,80.21h0A105.73,105.73,0,0,0,32.71,96.36,77.11,77.11,0,0,0,39.6,85.25a68.42,68.42,0,0,1-10.85-5.18c.91-.66,1.8-1.34,2.66-2a75.57,75.57,0,0,0,64.32,0c.87.71,1.76,1.39,2.66,2a68.68,68.68,0,0,1-10.87,5.19,77,77,0,0,0,6.89,11.1A105.89,105.89,0,0,0,126.6,80.22c2.36-24.44-4.2-48.62-18.9-72.15ZM42.45,65.69C36.18,65.69,31,60,31,53s5-12.74,11.43-12.74S54,46,53.89,53,48.84,65.69,42.45,65.69Zm42.24,0C78.41,65.69,73.25,60,73.25,53s5-12.74,11.44-12.74S96.23,46,96.12,53,91.08,65.69,84.69,65.69Z" />
    </svg>
);

const GoogleIcon = ({ className }: { className?: string }) => (
    <svg className={className} viewBox="0 0 24 24" fill="currentColor" xmlns="http://www.w3.org/2000/svg">
        <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" fill="#4285F4"/>
        <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.04-3.71 1.04-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/>
        <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" fill="#FBBC05"/>
        <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335"/>
    </svg>
);

interface SignInModalProps {
    isOpen: boolean;
    onClose: () => void;
}

export const SignInModal: React.FC<SignInModalProps> = ({ isOpen, onClose }) => {
    const [mounted, setMounted] = useState(false);
    const [mode, setMode] = useState<'signin' | 'register' | 'forgot-password' | 'mfa'>('signin');
    const [email, setEmail] = useState('');
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [mfaCode, setMfaCode] = useState('');
    const [preAuthToken, setPreAuthToken] = useState<string | null>(null);

    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [successMessage, setSuccessMessage] = useState<string | null>(null);

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
        setError(null);
        setSuccessMessage(null);

        try {
            if (mode === 'register') {
                await api.post('/auth/register', { username, email, password });
                setMode('signin');
                setSuccessMessage("Account created! Please sign in.");
                setLoading(false);
                return;
            }

            if (mode === 'forgot-password') {
                await api.post('/auth/forgot-password', { email });
                setSuccessMessage("If an account exists, a reset link has been sent.");
                setLoading(false);
                return;
            }

            if (mode === 'mfa') {
                await api.post('/auth/mfa/validate-login', {
                    pre_auth_token: preAuthToken,
                    code: mfaCode
                });
                window.location.href = '/dashboard/profile';
                return;
            }

            const res = await api.post('/auth/signin', {
                username: username || email,
                password
            });

            if (res.data.mfa_required) {
                setPreAuthToken(res.data.pre_auth_token);
                setMode('mfa');
                setLoading(false);
                return;
            }

            window.location.href = '/dashboard/profile';
        } catch (err: any) {
            console.error(err);
            if (err.response?.status === 401) {
                setError(mode === 'mfa' ? "Invalid code." : "Invalid credentials.");
            } else if (err.response?.data?.error) {
                setError(err.response.data.error);
            } else {
                setError("An error occurred. Please try again.");
            }
        } finally {
            setLoading(false);
        }
    };

    return createPortal(
        <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/80 backdrop-blur-sm p-4 animate-in fade-in duration-200" onClick={onClose}>
            <div className="bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/10 rounded-2xl max-w-sm w-full p-6 shadow-2xl relative scale-100 animate-in zoom-in-95 duration-200" onClick={e => e.stopPropagation()}>
                <button onClick={onClose} className="absolute top-4 right-4 text-slate-400 hover:text-slate-900 dark:hover:text-white transition-colors">
                    <X className="w-5 h-5" />
                </button>

                <div className="text-center mb-6">
                    <h2 className="text-2xl font-black text-slate-900 dark:text-white tracking-tight mb-2">
                        {mode === 'signin' ? 'Welcome Back' : (mode === 'register' ? 'Create Account' : (mode === 'mfa' ? 'Security Check' : 'Reset Password'))}
                    </h2>
                    <p className="text-slate-500 dark:text-slate-400 text-sm">
                        {mode === 'signin' ? 'Sign in to manage your projects.' : (mode === 'register' ? 'Join the community today.' : (mode === 'mfa' ? 'Enter the code from your authenticator app.' : 'Enter your email to receive a reset link.'))}
                    </p>
                </div>

                {mode !== 'forgot-password' && mode !== 'mfa' && (
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
                                    <GitLabIcon className="w-5 h-5" />
                                </button>
                                <button
                                    onClick={() => handleOAuthLogin('discord')}
                                    className="w-full bg-[#5865F2] text-white py-3 px-4 rounded-xl font-bold flex items-center justify-center hover:bg-[#4752c4] transition-colors active:scale-95 duration-200 shadow-lg shadow-indigo-500/20"
                                    title="Sign in with Discord"
                                >
                                    <DiscordIcon className="w-5 h-5" />
                                </button>
                                <button
                                    onClick={() => handleOAuthLogin('google')}
                                    className="w-full bg-white text-slate-700 border border-slate-200 py-3 px-4 rounded-xl font-bold flex items-center justify-center hover:bg-slate-50 transition-colors active:scale-95 duration-200 shadow-lg shadow-black/5"
                                    title="Sign in with Google"
                                >
                                    <GoogleIcon className="w-5 h-5" />
                                </button>
                            </div>
                        </div>

                        <div className="relative mb-6">
                            <div className="absolute inset-0 flex items-center">
                                <div className="w-full border-t border-slate-200 dark:border-white/10"></div>
                            </div>
                            <div className="relative flex justify-center text-xs uppercase">
                                <span className="bg-white dark:bg-modtale-card px-2 text-slate-500">Or continue with email</span>
                            </div>
                        </div>
                    </>
                )}

                <form onSubmit={handleSubmit} className="space-y-4">
                    {error && (
                        <div className="p-3 text-sm text-red-500 bg-red-50 dark:bg-red-900/20 rounded-lg text-center">
                            {error}
                        </div>
                    )}
                    {successMessage && (
                        <div className="p-3 text-sm text-green-500 bg-green-50 dark:bg-green-900/20 rounded-lg text-center">
                            {successMessage}
                        </div>
                    )}

                    {mode === 'register' && (
                        <div className="space-y-1">
                            <label className="text-xs font-bold text-slate-700 dark:text-slate-300 uppercase">Username</label>
                            <input
                                type="text"
                                required
                                value={username}
                                onChange={e => setUsername(e.target.value)}
                                className="w-full px-3 py-2.5 rounded-lg bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 focus:ring-2 focus:ring-modtale-accent focus:border-transparent outline-none transition-all text-sm"
                                placeholder="Display name"
                            />
                        </div>
                    )}

                    {(mode === 'signin' || mode === 'register' || mode === 'forgot-password') && (
                        <div className="space-y-1">
                            <label className="text-xs font-bold text-slate-700 dark:text-slate-300 uppercase">
                                {mode === 'signin' ? 'Email or Username' : 'Email'}
                            </label>
                            <input
                                type={mode === 'signin' ? "text" : "email"}
                                required
                                value={mode === 'signin' ? (username || email) : email}
                                onChange={e => mode === 'signin' ? setUsername(e.target.value) : setEmail(e.target.value)}
                                className="w-full px-3 py-2.5 rounded-lg bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 focus:ring-2 focus:ring-modtale-accent focus:border-transparent outline-none transition-all text-sm"
                                placeholder={mode === 'signin' ? "user@example.com" : "user@example.com"}
                            />
                        </div>
                    )}

                    {(mode === 'signin' || mode === 'register') && (
                        <div className="space-y-1">
                            <div className="flex justify-between items-center">
                                <label className="text-xs font-bold text-slate-700 dark:text-slate-300 uppercase">Password</label>
                                {mode === 'signin' && (
                                    <button
                                        type="button"
                                        onClick={() => { setMode('forgot-password'); setError(null); setSuccessMessage(null); }}
                                        className="text-xs text-modtale-accent hover:text-modtale-accentHover font-medium"
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
                                className="w-full px-3 py-2.5 rounded-lg bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 focus:ring-2 focus:ring-modtale-accent focus:border-transparent outline-none transition-all text-sm"
                                placeholder="••••••••"
                            />
                        </div>
                    )}

                    {mode === 'mfa' && (
                        <div className="space-y-1">
                            <label className="text-xs font-bold text-slate-700 dark:text-slate-300 uppercase">Authentication Code</label>
                            <div className="relative">
                                <input
                                    type="text"
                                    required
                                    value={mfaCode}
                                    onChange={e => setMfaCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                                    className="w-full px-3 py-2.5 pl-10 rounded-lg bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 focus:ring-2 focus:ring-modtale-accent focus:border-transparent outline-none transition-all text-sm font-mono tracking-widest text-center"
                                    placeholder="000 000"
                                    autoFocus
                                />
                                <Smartphone className="absolute left-3 top-2.5 w-5 h-5 text-slate-400" />
                            </div>
                        </div>
                    )}

                    <button
                        type="submit"
                        disabled={loading}
                        className="w-full bg-modtale-accent text-white py-3 px-4 rounded-xl font-bold flex items-center justify-center gap-2 hover:bg-modtale-accent/90 transition-colors active:scale-95 duration-200 shadow-lg shadow-modtale-accent/20"
                    >
                        {loading ? <Loader2 className="w-4 h-4 animate-spin" /> : (
                            <>
                                {mode === 'signin' ? 'Sign In' : (mode === 'register' ? 'Create Account' : (mode === 'mfa' ? 'Verify Code' : 'Send Reset Link'))}
                                <ArrowRight className="w-4 h-4" />
                            </>
                        )}
                    </button>
                </form>

                <div className="mt-6 text-center">
                    <button
                        onClick={() => {
                            if (mode === 'forgot-password' || mode === 'mfa') {
                                setMode('signin');
                            } else {
                                setMode(mode === 'signin' ? 'register' : 'signin');
                            }
                            setError(null);
                            setSuccessMessage(null);
                            setMfaCode('');
                        }}
                        className="text-sm text-slate-500 hover:text-modtale-accent dark:text-slate-400 dark:hover:text-white transition-colors flex items-center justify-center gap-2 mx-auto"
                    >
                        {(mode === 'forgot-password' || mode === 'mfa') ? (
                            <>
                                <ArrowLeft className="w-3 h-3" /> Back to Sign In
                            </>
                        ) : (
                            mode === 'signin' ? "Don't have an account? Sign up" : "Already have an account? Sign in"
                        )}
                    </button>
                </div>
            </div>
        </div>,
        document.body
    );
};