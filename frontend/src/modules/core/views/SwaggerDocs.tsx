import React, { useEffect, useState } from 'react';
import { ExternalLink, Sparkles, TimerReset, ArrowUpRight } from 'lucide-react';
import { BACKEND_URL } from '@/utils/api';

const SWAGGER_UI_URL = `${BACKEND_URL}/api/v1/docs/swagger`;
const REDIRECT_DELAY_MS = 900;

export const SwaggerDocs: React.FC = () => {
    const [countdown, setCountdown] = useState(Math.ceil(REDIRECT_DELAY_MS / 300));

    useEffect(() => {
        const head = document.head;
        const preconnect = document.createElement('link');
        preconnect.rel = 'preconnect';
        preconnect.href = BACKEND_URL;
        head.appendChild(preconnect);

        const countdownInterval = window.setInterval(() => {
            setCountdown((current) => (current > 1 ? current - 1 : current));
        }, 300);

        const redirectTimeout = window.setTimeout(() => {
            window.location.replace(SWAGGER_UI_URL);
        }, REDIRECT_DELAY_MS);

        return () => {
            window.clearTimeout(redirectTimeout);
            window.clearInterval(countdownInterval);
            head.removeChild(preconnect);
        };
    }, []);

    return (
        <div className="min-h-screen bg-[radial-gradient(circle_at_top,rgba(56,189,248,0.14),transparent_38%),linear-gradient(180deg,#f8fafc_0%,#e2e8f0_100%)] dark:bg-[radial-gradient(circle_at_top,rgba(56,189,248,0.18),transparent_35%),linear-gradient(180deg,#020617_0%,#0f172a_100%)] flex items-center justify-center px-4">
            <div className="max-w-2xl w-full border border-slate-200 dark:border-white/10 rounded-[2rem] p-8 md:p-10 shadow-2xl bg-white/85 dark:bg-slate-900/82 backdrop-blur-2xl">
                <div className="flex items-center gap-3 text-sky-600 dark:text-sky-400 mb-5">
                    <div className="p-3 rounded-2xl bg-sky-50 dark:bg-sky-500/10 border border-sky-100 dark:border-sky-500/20">
                        <Sparkles className="w-6 h-6" />
                    </div>
                    <div className="text-xs uppercase tracking-[0.3em] font-bold">Interactive Reference</div>
                </div>

                <h1 className="text-3xl md:text-4xl font-black text-slate-900 dark:text-white mb-3">
                    Opening Swagger UI
                </h1>
                <p className="text-base text-slate-600 dark:text-slate-300 leading-relaxed mb-6 max-w-xl">
                    You’re heading to the live interactive explorer backed by the same OpenAPI document as this custom reference.
                </p>

                <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-8">
                    <div className="rounded-2xl border border-slate-200 dark:border-white/10 bg-slate-50/80 dark:bg-white/5 p-4">
                        <div className="text-xs uppercase tracking-[0.24em] text-slate-500 dark:text-slate-400 mb-2">Redirecting</div>
                        <div className="text-2xl font-black text-slate-900 dark:text-white">{countdown}</div>
                    </div>
                    <div className="rounded-2xl border border-slate-200 dark:border-white/10 bg-slate-50/80 dark:bg-white/5 p-4">
                        <div className="text-xs uppercase tracking-[0.24em] text-slate-500 dark:text-slate-400 mb-2">Destination</div>
                        <div className="text-sm font-mono text-slate-700 dark:text-slate-200">/api/v1/docs/swagger</div>
                    </div>
                    <div className="rounded-2xl border border-slate-200 dark:border-white/10 bg-slate-50/80 dark:bg-white/5 p-4">
                        <div className="text-xs uppercase tracking-[0.24em] text-slate-500 dark:text-slate-400 mb-2">Source</div>
                        <div className="text-sm font-semibold text-slate-700 dark:text-slate-200">Live backend spec</div>
                    </div>
                </div>

                <div className="flex flex-col sm:flex-row gap-3">
                    <a
                        href={SWAGGER_UI_URL}
                        target="_blank"
                        rel="noreferrer"
                        className="inline-flex items-center justify-center gap-2 px-5 py-3 rounded-xl font-bold bg-slate-900 dark:bg-white text-white dark:text-slate-900"
                    >
                        Open Swagger UI <ExternalLink className="w-4 h-4" />
                    </a>
                    <button
                        type="button"
                        onClick={() => window.location.replace(SWAGGER_UI_URL)}
                        className="inline-flex items-center justify-center gap-2 px-5 py-3 rounded-xl font-bold border border-slate-200 dark:border-white/10 text-slate-700 dark:text-slate-200 bg-white/80 dark:bg-slate-950/40"
                    >
                        Continue now <ArrowUpRight className="w-4 h-4" />
                    </button>
                </div>

                <div className="mt-6 flex items-center gap-2 text-sm text-slate-500 dark:text-slate-400">
                    <TimerReset className="w-4 h-4" />
                    <span>If nothing happens automatically, your browser likely blocked the redirect.</span>
                </div>
            </div>
        </div>
    );
};
