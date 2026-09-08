import { useEffect, useState } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { CheckCircle2, Loader2, ShieldCheck, XCircle } from 'lucide-react';
import { SiteRoutes } from '@/utils/routes';
import type { User } from '@/types';
import { authClient } from '../api/authClient';
import { extractApiErrorMessage } from '@/utils/api';

interface LauncherAuthProps {
    user: User | null;
    loadingAuth: boolean;
}

const DEFAULT_APP_NAME = 'Modtale Launcher';
const MAX_APP_NAME_LENGTH = 80;

const normalizeAppName = (appName: string | null) => {
    const trimmed = appName?.trim();
    if (!trimmed) return DEFAULT_APP_NAME;
    return trimmed.slice(0, MAX_APP_NAME_LENGTH);
};

const isLoopbackCallback = (url: URL) => {
    const hostname = url.hostname.toLowerCase();
    return url.protocol === 'http:'
        && Boolean(url.port)
        && ['localhost', '127.0.0.1', '::1', '[::1]'].includes(hostname);
};

const appendCallbackParams = (redirectUri: string, code: string, state?: string | null) => {
    const target = new URL(redirectUri);
    target.searchParams.set('code', code);
    if (state) {
        target.searchParams.set('state', state);
    }
    return target.toString();
};

const appendErrorParams = (redirectUri: string, error: string, state?: string | null) => {
    const target = new URL(redirectUri);
    target.searchParams.set('error', error);
    if (state) {
        target.searchParams.set('state', state);
    }
    return target.toString();
};

export function LauncherAuth({ user, loadingAuth }: LauncherAuthProps) {
    const [searchParams] = useSearchParams();
    const location = useLocation();
    const navigate = useNavigate();
    const [error, setError] = useState<string | null>(null);
    const [redirectUri, setRedirectUri] = useState<string | null>(null);
    const [state, setState] = useState<string | null>(null);
    const [authorizing, setAuthorizing] = useState(false);
    const appName = normalizeAppName(searchParams.get('app_name') ?? searchParams.get('client_name'));

    useEffect(() => {
        if (loadingAuth) return;

        const nextRedirectUri = searchParams.get('redirect_uri');
        const nextState = searchParams.get('state');
        setAuthorizing(false);

        if (!nextRedirectUri) {
            setRedirectUri(null);
            setError('The launcher did not provide a callback URL.');
            return;
        }

        try {
            const parsedRedirectUri = new URL(nextRedirectUri);
            if (!isLoopbackCallback(parsedRedirectUri)) {
                setRedirectUri(null);
                setError('The launcher callback URL must point to this device.');
                return;
            }
        } catch {
            setRedirectUri(null);
            setError('The launcher callback URL is invalid.');
            return;
        }

        setError(null);
        setRedirectUri(nextRedirectUri);
        setState(nextState);

        if (!user) {
            navigate(SiteRoutes.login(`${location.pathname}${location.search}`), { replace: true });
        }
    }, [loadingAuth, location.pathname, location.search, navigate, searchParams, user]);

    const handleAuthorize = async () => {
        if (!redirectUri) {
            setError('The launcher did not provide a callback URL.');
            return;
        }

        setAuthorizing(true);
        setError(null);

        try {
            const response = await authClient.issueLauncherAuthCode({ redirectUri, state });
            window.location.href = appendCallbackParams(
                response.data.redirectUri,
                response.data.code,
                response.data.state
            );
        } catch (err) {
            setAuthorizing(false);
            setError(extractApiErrorMessage(err, 'We could not authorize the launcher.'));
        }
    };

    const handleCancel = () => {
        if (!redirectUri) {
            navigate(SiteRoutes.home(), { replace: true });
            return;
        }
        window.location.href = appendErrorParams(redirectUri, 'access_denied', state);
    };

    if (loadingAuth || (!user && !error)) {
        return (
            <div className="min-h-[70vh] flex items-center justify-center px-4">
                <div className="w-full max-w-md border border-slate-200 dark:border-white/10 bg-white/90 dark:bg-slate-900/90 rounded-2xl p-8 text-center shadow-xl">
                    <div className="mx-auto mb-5 flex h-16 w-16 items-center justify-center rounded-2xl bg-modtale-accent/10 text-modtale-accent border border-modtale-accent/20">
                        <Loader2 className="w-8 h-8 animate-spin" />
                    </div>
                    <h1 className="text-2xl font-black text-slate-900 dark:text-white mb-3">
                        Checking Your Session
                    </h1>
                    <p className="text-sm text-slate-600 dark:text-slate-400 leading-6">
                        Keep this tab open while Modtale prepares launcher authentication.
                    </p>
                </div>
            </div>
        );
    }

    const displayName = user?.displayName || user?.username || 'your Modtale account';
    const icon = error ? <XCircle className="w-8 h-8" /> : <ShieldCheck className="w-8 h-8" />;

    if (error) {
        return (
            <div className="min-h-[70vh] flex items-center justify-center px-4">
                <div className="w-full max-w-md border border-red-500/20 bg-white/90 dark:bg-slate-900/90 rounded-2xl p-8 text-center shadow-xl">
                    <div className="mx-auto mb-5 flex h-16 w-16 items-center justify-center rounded-2xl bg-red-500/10 text-red-500 border border-red-500/20">
                        {icon}
                    </div>
                    <h1 className="text-2xl font-black text-slate-900 dark:text-white mb-3">
                        Launcher Sign-In Failed
                    </h1>
                    <p className="text-sm text-slate-600 dark:text-slate-400 leading-6">
                        {error}
                    </p>
                </div>
            </div>
        );
    }

    return (
        <div className="min-h-[70vh] flex items-center justify-center px-4">
            <div className="w-full max-w-md border border-slate-200 dark:border-white/10 bg-white/90 dark:bg-slate-900/90 rounded-2xl p-8 text-center shadow-xl">
                <div className="mx-auto mb-5 flex h-16 w-16 items-center justify-center rounded-2xl bg-modtale-accent/10 text-modtale-accent border border-modtale-accent/20">
                    {icon}
                </div>
                <h1 className="text-2xl font-black text-slate-900 dark:text-white mb-3">
                    Do you want to authenticate with {appName}?
                </h1>
                <p className="text-sm text-slate-600 dark:text-slate-400 leading-6">
                    {appName} wants to connect to Modtale as <span className="font-bold text-slate-800 dark:text-slate-200">{displayName}</span>. Only continue if you started this sign-in from the launcher.
                </p>
                <div className="mt-7 grid gap-3 sm:grid-cols-2">
                    <button
                        type="button"
                        onClick={handleCancel}
                        disabled={authorizing}
                        className="flex items-center justify-center gap-2 rounded-xl border border-slate-200 dark:border-white/10 px-4 py-3 text-sm font-bold text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-white/10 disabled:cursor-not-allowed disabled:opacity-60 transition-colors"
                    >
                        <XCircle className="w-5 h-5" />
                        Cancel
                    </button>
                    <button
                        type="button"
                        onClick={handleAuthorize}
                        disabled={authorizing}
                        className="flex items-center justify-center gap-2 rounded-xl bg-modtale-accent px-4 py-3 text-sm font-bold text-white shadow-lg shadow-modtale-accent/20 hover:bg-modtale-accentHover disabled:cursor-not-allowed disabled:opacity-70 transition-colors"
                    >
                        {authorizing ? <Loader2 className="w-5 h-5 animate-spin" /> : <CheckCircle2 className="w-5 h-5" />}
                        Authenticate
                    </button>
                </div>
            </div>
        </div>
    );
}
