import { ArrowLeft, ArrowRight, Eye, FileText, HeartHandshake, Key, Lock, Server, Shield, ShieldCheck, Smartphone } from 'lucide-react';
import { SkeletonSurface } from '@/components/ui/Skeleton';

const panel = 'bg-white/90 dark:bg-slate-900/90 backdrop-blur-2xl border border-slate-200 dark:border-white/10 rounded-3xl shadow-2xl';
const container = 'max-w-[112rem] px-6 sm:px-12 md:px-16 lg:px-20 xl:px-28 mx-auto';

export function AuthRouteSkeleton({ path, hasToken }: { path: string; hasToken: boolean }) {
    const mfa = path === '/mfa';
    const verify = path === '/verify';
    const invalidReset = path === '/reset-password' && !hasToken;
    const Icon = mfa ? ShieldCheck : Lock;
    const content = <div className={`${panel} p-8 max-w-md w-full relative z-10 ${verify || invalidReset ? 'text-center' : ''}`}>
        {invalidReset ? <><h1 data-skeleton-keep className="text-xl font-bold text-red-500 mb-2">Invalid Request</h1><p data-skeleton-keep className="text-slate-500 dark:text-slate-400">Missing reset token.</p></> : verify ? <>
            <div className="flex justify-center mb-6"><div data-skeleton-media className="size-16 rounded-full" /></div>
            <h1 className="text-2xl font-black mb-2">Email Verified</h1><p className="text-slate-500 dark:text-slate-400 mb-8 font-medium">Your email has been successfully verified!</p>
            <div className="flex flex-col gap-3"><span data-skeleton-keep className="w-full bg-modtale-accent text-white py-3 px-4 rounded-xl font-bold flex items-center justify-center gap-2 shadow-lg shadow-modtale-accent/20">Go to Dashboard<ArrowRight className="size-4" /></span><span data-skeleton-keep className="text-sm text-slate-500 font-bold">Back to Home</span></div>
        </> : <>
            <div data-skeleton-keep className="text-center mb-8"><div className="size-16 bg-modtale-accent/10 text-modtale-accent rounded-2xl flex items-center justify-center mx-auto mb-4 border border-modtale-accent/20 shadow-inner"><Icon className="size-8" /></div>
                <h1 className="text-2xl font-black mb-2">{mfa ? 'Two-Factor Authentication' : 'Reset Password'}</h1><p className="text-slate-500 dark:text-slate-400 text-sm">{mfa ? 'Your account is protected. Please enter the code from your authenticator app to continue.' : 'Enter a new password for your account.'}</p>
            </div>
            <div className={mfa ? 'space-y-6' : 'space-y-5'}>
                {(mfa ? ['Authentication Code'] : ['New Password', 'Confirm Password']).map(label => <div key={label} className={mfa ? 'space-y-2' : 'space-y-1'}><label data-skeleton-keep className={mfa ? 'text-xs font-bold uppercase block text-center tracking-widest' : 'text-[10px] font-black text-slate-400 uppercase tracking-widest block pl-1'}>{label}</label>
                    <div className="relative"><input readOnly tabIndex={-1} className={`w-full px-4 rounded-xl bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 shadow-inner ${mfa ? 'py-4 pl-12 text-2xl font-mono tracking-[0.5em] text-center' : 'py-3 text-sm'}`} />{mfa && <Smartphone className="absolute left-4 top-[1.1rem] size-6 text-slate-400" />}</div>
                </div>)}
                <span data-skeleton-keep className={`w-full bg-modtale-accent text-white px-4 rounded-xl flex items-center justify-center gap-2 shadow-lg shadow-modtale-accent/20 ${mfa ? 'py-4 font-bold text-lg opacity-50' : 'py-3.5 font-black mt-2'}`}>{mfa ? 'Verify & Login' : 'Update Password'}<ArrowRight className="size-5" /></span>
            </div>
        </>}
    </div>;
    return <SkeletonSurface label={verify ? 'Loading email verification' : mfa ? 'Loading two-factor authentication' : 'Loading password reset'}>
        {mfa ? <div className="min-h-screen bg-slate-50 dark:bg-slate-950 flex flex-col pb-20"><div className="flex-1 flex items-center justify-center p-4 relative"><div className="absolute inset-0 bg-gradient-to-br from-modtale-accent/5 to-transparent" />{content}</div></div> : <div className={`flex-1 flex items-center justify-center p-4 ${verify || invalidReset ? 'min-h-[60vh]' : 'min-h-[80vh]'}`}>{content}</div>}
    </SkeletonSurface>;
}

export function DocsRouteSkeleton({ path }: { path: string }) {
    const api = path === '/api-docs';
    const privacy = path === '/privacy';
    const Icon = privacy ? Shield : FileText;
    const SummaryIcon = privacy ? Eye : HeartHandshake;
    return <SkeletonSurface label={api ? 'Loading API documentation' : privacy ? 'Loading privacy policy' : 'Loading terms of service'}>
        <div className="min-h-screen bg-slate-50 dark:bg-slate-950 pb-20"><div className={`${container} ${api ? 'py-16 overflow-x-hidden' : 'py-12'}`}>
            {api ? <>
                <div className="text-center mb-12 w-full"><h1 data-skeleton-keep className="text-4xl md:text-5xl font-black mb-4 tracking-tight">Modtale <span className="text-modtale-accent">API v1</span></h1><p data-skeleton-keep className="text-lg text-slate-600 dark:text-slate-400 max-w-3xl mx-auto mb-6">This page is generated from live backend OpenAPI v1 metadata. Swagger and this custom reference now share the same source.</p>
                    <div className="inline-flex flex-wrap justify-center items-center gap-3 px-5 py-3 bg-slate-100 dark:bg-white/5 rounded-full text-sm font-mono border border-slate-200 dark:border-white/10 mb-6 max-w-full"><Server data-skeleton-keep className="size-4 text-modtale-accent" /><span data-skeleton-keep>Base URL:</span><span className="font-bold break-all">https://api.modtale.net</span></div>
                    <div data-skeleton-keep className="flex flex-col sm:flex-row justify-center items-center gap-4 w-full">{['Get API Key', 'Open Swagger UI', 'View OpenAPI YAML'].map((label, index) => <span key={label} className={`px-6 py-3 rounded-xl font-bold flex items-center justify-center gap-2 w-full sm:w-auto ${index === 0 ? 'bg-slate-900 dark:bg-white text-white dark:text-slate-900 shadow-lg' : 'bg-white dark:bg-slate-900/90 border border-slate-200 dark:border-white/10 shadow-sm'}`}>{index === 0 && <Shield className="size-4" />}{label}{index > 0 && <ArrowRight className="size-3 opacity-50" />}</span>)}</div>
                </div>
                <div className={`${panel} p-6 md:p-10 w-full overflow-hidden`}><div data-skeleton-keep className="flex items-center gap-3 mb-6"><div className="p-3 bg-green-50 dark:bg-green-500/10 rounded-lg text-green-600"><Key className="size-6" /></div><div><h2 className="text-xl font-black">Authentication & Security</h2><p className="text-sm text-slate-500">Secure your requests.</p></div></div>
                    <p className="text-sm mb-6 leading-relaxed">All authenticated API requests must be directed to https://api.modtale.net. Include your API key in the request header to identify your client and get higher limits.</p>
                    <div className="bg-slate-900 rounded-lg p-4 font-mono text-sm text-slate-300 border border-white/10 mb-8">X-MODTALE-KEY: md_12345abcdef...</div><h3 data-skeleton-keep className="text-sm font-bold uppercase mb-4 tracking-wider">Rate Limits (Token Bucket)</h3>
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">{['Public', 'Authenticated', 'Developer'].map(label => <div key={label} className="p-6 rounded-xl bg-slate-50 dark:bg-slate-800/50 border border-slate-100 dark:border-white/5"><h4 className="font-bold mb-3">{label}</h4><p className="text-3xl font-black">100</p><p className="text-sm mt-3">Requests per minute</p></div>)}</div>
                </div>
            </> : <>
                <div data-skeleton-keep className="mb-6 flex items-center text-slate-500 font-bold"><ArrowLeft className="size-4 mr-2" />Back</div>
                <div className={`${panel} p-8 md:p-12`}><div className="flex items-center gap-3 mb-8 pb-6 border-b border-slate-100 dark:border-white/5"><div data-skeleton-keep className={`p-3 rounded-lg ${privacy ? 'bg-green-100 dark:bg-green-900/20 text-green-600' : 'bg-blue-100 dark:bg-blue-900/20 text-blue-600'}`}><Icon className="size-8" /></div><div><h1 data-skeleton-keep className="text-3xl font-black">{privacy ? 'Privacy Policy' : 'Terms of Service'}</h1><p className="text-slate-500 font-medium">Last updated: June 16, 2026</p></div></div>
                    <div className="bg-slate-50 dark:bg-black/20 p-6 rounded-xl border border-slate-200 dark:border-white/5 mb-8"><h3 data-skeleton-keep className="text-lg font-bold mb-2 flex items-center gap-2"><SummaryIcon className="size-5 text-modtale-accent" />Plain-English Summary</h3><ul className="list-disc list-inside space-y-1 text-sm">{[0, 1, 2, 3, 4].map(index => <li key={index}>Information about your account, projects, and the services available on Modtale.</li>)}</ul></div>
                    {!privacy && <div className="p-4 bg-red-50 dark:bg-red-900/10 border border-red-200 dark:border-red-900/30 rounded-lg my-6"><h3 data-skeleton-keep className="font-bold mb-1">Unofficial Fan Project</h3><p className="text-sm">Modtale is an independent community platform for Hytale projects and creators.</p></div>}
                    <div className="prose dark:prose-invert prose-slate max-w-none">{[1, 2, 3].map(index => <section key={index}><h3>{index}. {privacy ? 'Your information' : 'Using Modtale'}</h3><p>This page describes how Modtale works and the information you need when using accounts, profiles, projects, downloads, organizations, comments, notifications, and related services.</p><p>Read the following details to learn more about the platform and your account.</p></section>)}</div>
                </div>
            </>}
        </div></div>
    </SkeletonSurface>;
}
