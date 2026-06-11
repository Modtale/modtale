import React, { useState, useEffect } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { ShieldCheck, ArrowRight, Loader2, Smartphone } from 'lucide-react';
import { SiteRoutes } from '@/utils/routes';
import { authClient } from '../api/authClient';
import { StatusModal } from '@/components/ui/StatusModal';
import { extractApiErrorMessage } from '@/utils/api';

export function MfaVerify() {
    const [searchParams] = useSearchParams();
    const token = searchParams.get('token');
    const redirectTo = SiteRoutes.internalRedirect(
        searchParams.get('redirect'),
        SiteRoutes.dashboardProfile()
    );
    const navigate = useNavigate();

    const [code, setCode] = useState('');
    const [loading, setLoading] = useState(false);
    const [statusModal, setStatusModal] = useState<{ title: string; msg: string } | null>(null);

    useEffect(() => {
        if (!token) {
            navigate(SiteRoutes.home());
        }
    }, [token, navigate]);

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setLoading(true);
        setStatusModal(null);

        try {
            await authClient.validateMfaLogin({
                pre_auth_token: token,
                code: code
            });
            window.location.href = redirectTo;
        } catch (err: unknown) {
            setStatusModal({
                title: 'Verification Failed',
                msg: extractApiErrorMessage(err, 'We could not verify that two-factor authentication code.')
            });
            setLoading(false);
        }
    };

    return (
        <div className="min-h-screen bg-slate-50 dark:bg-slate-950 flex flex-col pb-20">
            {statusModal && (
                <StatusModal
                    type="error"
                    title={statusModal.title}
                    message={statusModal.msg}
                    onClose={() => setStatusModal(null)}
                />
            )}
            <div className="flex-1 flex items-center justify-center p-4 relative">
                <div className="absolute inset-0 bg-gradient-to-br from-modtale-accent/5 to-transparent pointer-events-none" />
                <div className="bg-white/90 dark:bg-slate-900/90 backdrop-blur-2xl border border-slate-200 dark:border-white/10 rounded-3xl p-8 max-w-md w-full shadow-2xl relative z-10">
                    <div className="text-center mb-8">
                        <div className="w-16 h-16 bg-modtale-accent/10 text-modtale-accent rounded-2xl flex items-center justify-center mx-auto mb-4 border border-modtale-accent/20 shadow-inner">
                            <ShieldCheck className="w-8 h-8" />
                        </div>
                        <h1 className="text-2xl font-black text-slate-900 dark:text-white mb-2">Two-Factor Authentication</h1>
                        <p className="text-slate-500 dark:text-slate-400 text-sm">
                            Your account is protected. Please enter the code from your authenticator app to continue.
                        </p>
                    </div>

                    <form onSubmit={handleSubmit} className="space-y-6">
                        <div className="space-y-2">
                            <label className="text-xs font-bold text-slate-700 dark:text-slate-300 uppercase block text-center tracking-widest">Authentication Code</label>
                            <div className="relative">
                                <input
                                    type="text"
                                    required
                                    value={code}
                                    onChange={e => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                                    className="w-full px-4 py-4 pl-12 rounded-xl bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 focus:ring-2 focus:ring-modtale-accent focus:border-transparent outline-none transition-all text-2xl font-mono tracking-[0.5em] text-center shadow-inner dark:text-white"
                                    placeholder="000000"
                                    autoFocus
                                    maxLength={6}
                                />
                                <Smartphone className="absolute left-4 top-[1.1rem] w-6 h-6 text-slate-400" />
                            </div>
                        </div>

                        <button
                            type="submit"
                            disabled={loading || code.length !== 6}
                            className="w-full bg-modtale-accent text-white py-4 px-4 rounded-xl font-bold flex items-center justify-center gap-2 hover:bg-modtale-accentHover transition-all active:scale-95 duration-200 shadow-lg shadow-modtale-accent/20 disabled:opacity-50 disabled:active:scale-100 text-lg"
                        >
                            {loading ? <Loader2 className="w-6 h-6 animate-spin" /> : (
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
}
