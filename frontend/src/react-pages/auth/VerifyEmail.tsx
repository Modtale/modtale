import React, { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api } from '../../utils/api.ts';
import { Check, X, Loader2, ArrowRight } from 'lucide-react';
import type { User } from '../../types.ts';

interface VerifyEmailProps {
    user: User | null;
    isDarkMode: boolean;
    toggleDarkMode: () => void;
    onLogout: () => void;
    onNavigate: (page: string) => void;
    currentPage: string;
    onAuthorClick: (author: string) => void;
}

export const VerifyEmail: React.FC<VerifyEmailProps> = ({ }) => {
    const [searchParams] = useSearchParams();
    const token = searchParams.get('token');

    const [status, setStatus] = useState<'loading' | 'success' | 'error'>('loading');
    const [message, setMessage] = useState('Verifying your email...');

    useEffect(() => {
        if (!token) {
            setStatus('error');
            setMessage('Invalid verification link.');
            return;
        }

        api.post(`/auth/verify?token=${token}`)
            .then(() => {
                setStatus('success');
                setMessage('Your email has been successfully verified!');
            })
            .catch((err) => {
                setStatus('error');
                setMessage(err.response?.data?.error || 'Failed to verify email. The link may have expired.');
            });
    }, [token]);

    return (
        <div className="flex-1 flex items-center justify-center p-4 min-h-[60vh]">
            <div className="bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/10 rounded-3xl p-8 max-w-md w-full shadow-xl text-center">
                <div className="flex justify-center mb-6">
                    {status === 'loading' && <Loader2 className="w-16 h-16 text-modtale-accent animate-spin" />}
                    {status === 'success' && (
                        <div className="w-16 h-16 bg-green-100 dark:bg-green-900/20 text-green-600 dark:text-green-400 rounded-full flex items-center justify-center">
                            <Check className="w-8 h-8" />
                        </div>
                    )}
                    {status === 'error' && (
                        <div className="w-16 h-16 bg-red-100 dark:bg-red-900/20 text-red-600 dark:text-red-400 rounded-full flex items-center justify-center">
                            <X className="w-8 h-8" />
                        </div>
                    )}
                </div>

                <h1 className="text-2xl font-black text-slate-900 dark:text-white mb-2">
                    {status === 'loading' ? 'Verifying...' : (status === 'success' ? 'Email Verified' : 'Verification Failed')}
                </h1>

                <p className="text-slate-500 dark:text-slate-400 mb-8">
                    {message}
                </p>

                <div className="flex flex-col gap-3">
                    <a
                        href="/dashboard/profile"
                        className="w-full bg-modtale-accent text-white py-3 px-4 rounded-xl font-bold flex items-center justify-center gap-2 hover:bg-modtale-accentHover transition-colors"
                    >
                        Go to Dashboard <ArrowRight className="w-4 h-4" />
                    </a>
                    <a href="/public" className="text-sm text-slate-500 hover:text-slate-900 dark:hover:text-white font-medium">
                        Back to Home
                    </a>
                </div>
            </div>
        </div>
    );
};