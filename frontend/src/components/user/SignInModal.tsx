import React, { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { X, Github } from 'lucide-react';
import { BACKEND_URL } from '../../utils/api';

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

    useEffect(() => {
        setMounted(true);
        if (isOpen) document.body.style.overflow = 'hidden';
        return () => { document.body.style.overflow = 'unset'; };
    }, [isOpen]);

    if (!isOpen || !mounted) return null;

    const handleLogin = (provider: string) => {
        window.location.href = `${BACKEND_URL}/oauth2/authorization/${provider}`;
    };

    return createPortal(
        <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/80 backdrop-blur-sm p-4 animate-in fade-in duration-200" onClick={onClose}>
            <div className="bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/10 rounded-2xl max-w-sm w-full p-6 shadow-2xl relative scale-100 animate-in zoom-in-95 duration-200" onClick={e => e.stopPropagation()}>
                <button onClick={onClose} className="absolute top-4 right-4 text-slate-400 hover:text-slate-900 dark:hover:text-white transition-colors">
                    <X className="w-5 h-5" />
                </button>

                <div className="text-center mb-8">
                    <h2 className="text-2xl font-black text-slate-900 dark:text-white tracking-tight mb-2">Welcome Back</h2>
                    <p className="text-slate-500 dark:text-slate-400 text-sm">Sign in to manage your projects.</p>
                </div>

                <div className="space-y-3">
                    <button
                        onClick={() => handleLogin('github')}
                        className="w-full bg-[#24292e] text-white py-3.5 px-4 rounded-xl font-bold flex items-center justify-center gap-3 hover:bg-[#2f363d] transition-colors active:scale-95 duration-200 shadow-lg shadow-black/10"
                    >
                        <Github className="w-5 h-5" />
                        Sign in with GitHub
                    </button>

                    <button
                        onClick={() => handleLogin('gitlab')}
                        className="w-full bg-[#FC6D26] text-white py-3.5 px-4 rounded-xl font-bold flex items-center justify-center gap-3 hover:bg-[#e24329] transition-colors active:scale-95 duration-200 shadow-lg shadow-orange-500/20"
                    >
                        <GitLabIcon className="w-5 h-5" />
                        Sign in with GitLab
                    </button>

                    <button
                        onClick={() => handleLogin('discord')}
                        className="w-full bg-[#5865F2] text-white py-3.5 px-4 rounded-xl font-bold flex items-center justify-center gap-3 hover:bg-[#4752c4] transition-colors active:scale-95 duration-200 shadow-lg shadow-indigo-500/20"
                    >
                        <DiscordIcon className="w-5 h-5" />
                        Sign in with Discord
                    </button>

                    <button
                        onClick={() => handleLogin('google')}
                        className="w-full bg-white text-slate-700 border border-slate-200 py-3.5 px-4 rounded-xl font-bold flex items-center justify-center gap-3 hover:bg-slate-50 transition-colors active:scale-95 duration-200 shadow-lg shadow-black/5"
                    >
                        <GoogleIcon className="w-5 h-5" />
                        Sign in with Google
                    </button>
                </div>

                <div className="mt-6 text-center">
                    <p className="text-[10px] text-slate-400 dark:text-slate-500">
                        By signing in, you agree to our <a href="/terms" className="underline hover:text-slate-900 dark:hover:text-white">Terms</a> and <a href="/privacy" className="underline hover:text-slate-900 dark:hover:text-white">Privacy Policy</a>.
                    </p>
                </div>
            </div>
        </div>,
        document.body
    );
};