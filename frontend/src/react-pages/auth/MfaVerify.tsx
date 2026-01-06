import React, { useState, useEffect } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { api } from '../../utils/api.ts';
import { ShieldCheck, ArrowRight, Loader2, Smartphone } from 'lucide-react';
import { Navbar } from '../../components/Navbar.tsx';
import { Footer } from '../../components/Footer.tsx';
import type { User } from '../../types.ts';

interface MfaVerifyProps {
    user: User | null;
    isDarkMode: boolean;
    toggleDarkMode: () => void;
    onLogout: () => void;
    onNavigate: (page: string) => void;
    currentPage: string;
    onAuthorClick: (author: string) => void;
}

export const MfaVerify: React.FC<MfaVerifyProps> = ({ }) => {
    const [searchParams] = useSearchParams();
    const token = searchParams.get('token');
    const navigate = useNavigate();

    const [code, setCode] = useState('');
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        if (!token) {
            navigate('/');
        }
    }, [token, navigate]);

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setLoading(true);
        setError(null);

        try {
            await api.post('/auth/mfa/validate-login', {
                pre_auth_token: token,
                code: code
            });
            window.location.href = '/dashboard/profile';
        } catch (err: any) {
            setError(err.response?.data?.error || "Invalid code. Please try again.");
            setLoading(false);
        }
    };

    return (
        <div className="min-h-screen bg-slate-50 dark:bg-black flex flex-col">
            <div className="flex-1 flex items-center justify-center p-4">
                <div className="bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/10 rounded-3xl p-8 max-w-md w-full shadow-xl">
                    <div className="text-center mb-8">
                        <div className="w-16 h-16 bg-modtale-accent/10 text-modtale-accent rounded-full flex items-center justify-center mx-auto mb-4">
                            <ShieldCheck className="w-8 h-8" />
                        </div>
                        <h1 className="text-2xl font-black text-slate-900 dark:text-white mb-2">Two-Factor Authentication</h1>
                        <p className="text-slate-500 dark:text-slate-400 text-sm">
                            Your account is protected. Please enter the code from your authenticator app to continue.
                        </p>
                    </div>

                    <form onSubmit={handleSubmit} className="space-y-6">
                        {error && (
                            <div className="p-3 text-sm text-red-500 bg-red-50 dark:bg-red-900/20 rounded-lg text-center">
                                {error}
                            </div>
                        )}

                        <div className="space-y-2">
                            <label className="text-xs font-bold text-slate-700 dark:text-slate-300 uppercase block text-center">Authentication Code</label>
                            <div className="relative">
                                <input
                                    type="text"
                                    required
                                    value={code}
                                    onChange={e => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                                    className="w-full px-4 py-3 pl-12 rounded-xl bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 focus:ring-2 focus:ring-modtale-accent focus:border-transparent outline-none transition-all text-xl font-mono tracking-[0.5em] text-center"
                                    placeholder="000000"
                                    autoFocus
                                    maxLength={6}
                                />
                                <Smartphone className="absolute left-4 top-3.5 w-6 h-6 text-slate-400" />
                            </div>
                        </div>

                        <button
                            type="submit"
                            disabled={loading || code.length !== 6}
                            className="w-full bg-modtale-accent text-white py-3.5 px-4 rounded-xl font-bold flex items-center justify-center gap-2 hover:bg-modtale-accent/90 transition-colors active:scale-95 duration-200 shadow-lg shadow-modtale-accent/20 disabled:opacity-50 disabled:active:scale-100"
                        >
                            {loading ? <Loader2 className="w-5 h-5 animate-spin" /> : (
                                <>
                                    Verify & Login <ArrowRight className="w-5 h-5" />
                                </>
                            )}
                        </button>
                    </form>
                </div>
            </div>
        </div>
    );
};