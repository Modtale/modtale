import React, { useState, useEffect } from 'react';
import { X, Github, ArrowRight, Loader2, ArrowLeft, CheckCircle2 } from 'lucide-react';
import { DiscordBrandIcon, GitLabBrandIcon, GoogleBrandIcon } from '@/components/ui/icons/BrandIcons';
import { useLocation, useNavigate } from 'react-router-dom';
import { BACKEND_URL, extractApiErrorMessage } from '@/utils/api';
import { StatusModal } from '@/components/ui/StatusModal';
import { ModalPortal } from '@/components/ui/ModalPortal';
import { useToast } from '@/components/ui/Toast';
import { SiteRoutes } from '@/utils/routes';
import {
    authClient,
    completeSignInMethod,
    getLastSignInMethod,
    normalizeSignInResponse,
    SIGN_IN_METHOD_LABELS,
    stageSignInMethod,
    type SignInMethod
} from '../api/authClient';

interface SignInModalProps {
    isOpen: boolean;
    onClose: () => void;
}

type OAuthSignInMethod = Exclude<SignInMethod, 'email'>;

export function SignInModal({ isOpen, onClose }: SignInModalProps) {
    const { showToast } = useToast();
    const navigate = useNavigate();
    const location = useLocation();
    const [mounted, setMounted] = useState(false);
    const [mode, setMode] = useState<'signin' | 'register' | 'forgot-password'>('signin');
    const [email, setEmail] = useState('');
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [loading, setLoading] = useState(false);
    const [statusModal, setStatusModal] = useState<{ title: string; msg: string } | null>(null);
    const [lastSignInMethod, setLastSignInMethod] = useState<SignInMethod | null>(null);

    useEffect(() => {
        setMounted(true);
        if (isOpen) setLastSignInMethod(getLastSignInMethod());
        if (isOpen) document.body.style.overflow = 'hidden';
        return () => { document.body.style.overflow = ''; };
    }, [isOpen]);

    if (!isOpen || !mounted) return null;

    const redirectTo = SiteRoutes.internalRedirect(
        new URLSearchParams(location.search).get('redirect'),
        SiteRoutes.dashboardProfile()
    );

    const isLastMethod = (method: SignInMethod) => mode === 'signin' && lastSignInMethod === method;
    const lastMethodHighlightClass = (method: SignInMethod) => isLastMethod(method)
        ? 'ring-2 ring-modtale-accent ring-offset-2 ring-offset-white dark:ring-offset-slate-900'
        : '';
    const providerTitle = (method: OAuthSignInMethod) => `${isLastMethod(method) ? 'Last used: ' : ''}Sign in with ${SIGN_IN_METHOD_LABELS[method]}`;

    const renderLastUsedBadge = (method: SignInMethod, compact = false) => {
        if (!isLastMethod(method)) return null;

        if (compact) {
            return (
                <span className="absolute -right-2 -top-2 inline-flex h-6 w-6 items-center justify-center rounded-full bg-modtale-accent text-white shadow-lg shadow-modtale-accent/25">
                    <CheckCircle2 className="h-3.5 w-3.5" />
                    <span className="sr-only">Last used</span>
                </span>
            );
        }

        return (
            <span className="absolute right-3 top-1/2 hidden -translate-y-1/2 items-center gap-1 rounded-full bg-white/15 px-2 py-1 text-[10px] font-black uppercase tracking-wide text-white sm:inline-flex">
                <CheckCircle2 className="h-3 w-3" />
                Last used
            </span>
        );
    };

    const handleOAuthLogin = (provider: OAuthSignInMethod) => {
        stageSignInMethod(provider);
        window.location.href = `${BACKEND_URL}/oauth2/authorization/${provider}`;
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setLoading(true);
        setStatusModal(null);

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
            stageSignInMethod('email');

            const signInResult = normalizeSignInResponse(res.data);

            if (signInResult.mfaRequired) {
                if (location.pathname !== '/login') {
                    onClose();
                }
                const searchParams = new URLSearchParams({ token: signInResult.preAuthToken || '' });
                if (redirectTo) searchParams.set('redirect', redirectTo);
                navigate(`${SiteRoutes.mfa()}?${searchParams.toString()}`);
                return;
            }

            completeSignInMethod('email');
            window.location.href = redirectTo;
        } catch (err: any) {
            console.error(err);
            const fallbackByMode = mode === 'register'
                ? 'We could not create that account.'
                : mode === 'forgot-password'
                    ? 'We could not send a password reset link.'
                    : 'We could not sign you in.';

            const titleByMode = mode === 'register'
                ? 'Account Creation Failed'
                : mode === 'forgot-password'
                    ? 'Reset Link Failed'
                    : 'Sign-In Failed';

            setStatusModal({
                title: titleByMode,
                msg: extractApiErrorMessage(err, fallbackByMode)
            });
        } finally {
            setLoading(false);
        }
    };

    return (
        <ModalPortal>
        <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/80 backdrop-blur-sm p-4 animate-in fade-in duration-200" onClick={onClose}>
            {statusModal && (
                <StatusModal
                    type="error"
                    title={statusModal.title}
                    message={statusModal.msg}
                    onClose={() => setStatusModal(null)}
                />
            )}
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
                        {mode === 'signin' && lastSignInMethod && (
                            <div className="mt-3 inline-flex items-center gap-2 rounded-full border border-modtale-accent/30 bg-modtale-accent/10 px-3 py-1.5 text-xs font-bold text-modtale-accent">
                                <CheckCircle2 className="h-3.5 w-3.5" />
                                <span>Last used: {SIGN_IN_METHOD_LABELS[lastSignInMethod]}</span>
                            </div>
                        )}
                    </div>

                    {mode !== 'forgot-password' && (
                        <>
                            <div className="space-y-3 mb-6">
                                <button
                                    onClick={() => handleOAuthLogin('github')}
                                    className={`relative w-full bg-[#24292e] text-white py-3.5 px-4 rounded-xl font-bold flex items-center justify-center gap-3 hover:bg-[#2f363d] transition-colors active:scale-95 duration-200 shadow-lg shadow-black/10 ${lastMethodHighlightClass('github')}`}
                                    title={providerTitle('github')}
                                    aria-label={providerTitle('github')}
                                >
                                    <Github className="w-5 h-5" />
                                    <span className="text-sm">GitHub</span>
                                    {renderLastUsedBadge('github')}
                                </button>

                                <div className="grid grid-cols-3 gap-3">
                                    <button
                                        onClick={() => handleOAuthLogin('gitlab')}
                                        className={`relative w-full bg-[#FC6D26] text-white py-3 px-4 rounded-xl font-bold flex items-center justify-center hover:bg-[#e24329] transition-colors active:scale-95 duration-200 shadow-lg shadow-orange-500/20 ${lastMethodHighlightClass('gitlab')}`}
                                        title={providerTitle('gitlab')}
                                        aria-label={providerTitle('gitlab')}
                                    >
                                        <GitLabBrandIcon className="w-5 h-5" />
                                        {renderLastUsedBadge('gitlab', true)}
                                    </button>
                                    <button
                                        onClick={() => handleOAuthLogin('discord')}
                                        className={`relative w-full bg-[#5865F2] text-white py-3 px-4 rounded-xl font-bold flex items-center justify-center hover:bg-[#4752c4] transition-colors active:scale-95 duration-200 shadow-lg shadow-indigo-500/20 ${lastMethodHighlightClass('discord')}`}
                                        title={providerTitle('discord')}
                                        aria-label={providerTitle('discord')}
                                    >
                                        <DiscordBrandIcon className="w-5 h-5" />
                                        {renderLastUsedBadge('discord', true)}
                                    </button>
                                    <button
                                        onClick={() => handleOAuthLogin('google')}
                                        className={`relative w-full bg-white text-slate-700 border border-slate-200 py-3 px-4 rounded-xl font-bold flex items-center justify-center hover:bg-slate-50 transition-colors active:scale-95 duration-200 shadow-lg shadow-black/5 ${lastMethodHighlightClass('google')}`}
                                        title={providerTitle('google')}
                                        aria-label={providerTitle('google')}
                                    >
                                        <GoogleBrandIcon className="w-5 h-5" />
                                        {renderLastUsedBadge('google', true)}
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
                            <div className="flex items-center justify-between gap-3 pl-1">
                                <label className="text-xs font-bold text-slate-700 dark:text-slate-300 uppercase">
                                    {mode === 'signin' ? 'Email or Username' : 'Email'}
                                </label>
                                {isLastMethod('email') && (
                                    <span className="inline-flex items-center gap-1 rounded-full bg-modtale-accent/10 px-2 py-0.5 text-[10px] font-black uppercase tracking-wide text-modtale-accent">
                                        <CheckCircle2 className="h-3 w-3" />
                                        Last used
                                    </span>
                                )}
                            </div>
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
        </div>
        </ModalPortal>
    );
}
