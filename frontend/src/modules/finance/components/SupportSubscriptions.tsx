import { useEffect, useRef, useState } from 'react';
import { ExternalLink, HeartHandshake } from 'lucide-react';
import { financeClient } from '@/modules/finance/api/financeClient';
import { extractApiErrorMessage } from '@/utils/api';
import { theme } from '@/styles/theme';

interface SupportSubscription {
    id: string;
    projectId: string;
    projectTitle: string;
    projectUrl: string;
    amountCents: number;
    currency: string;
    status: string;
    cancelAtPeriodEnd: boolean;
    testMode: boolean;
}

export function SupportSubscriptions() {
    const [subscriptions, setSubscriptions] = useState<SupportSubscription[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [opening, setOpening] = useState('');
    const busyRef = useRef(false);
    const [revision, setRevision] = useState(0);
    useEffect(() => {
        let active = true;
        setLoading(true); setError('');
        financeClient.getSupportSubscriptions().then(rows => { if (active) setSubscriptions(rows); })
            .catch(error => { if (active) setError(extractApiErrorMessage(error, 'Could not load your monthly support.')); })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
    }, [revision]);

    const manage = async (subscriptionId: string) => {
        if (busyRef.current) return;
        busyRef.current = true; setOpening(subscriptionId); setError('');
        const portal = window.open('about:blank', '_blank');
        if (portal) portal.opener = null;
        try {
            if (!portal) throw new Error('Allow a new tab to manage your monthly support.');
            const result = await financeClient.openSupportBillingPortal(subscriptionId);
            const url = new URL(result.url);
            if (url.protocol !== 'https:' || url.hostname !== 'billing.stripe.com') throw new Error('Invalid billing destination.');
            portal.location.replace(url.href);
        } catch (error) {
            portal?.close(); setError(extractApiErrorMessage(error, 'Could not open billing management. Please try again.'));
        } finally { busyRef.current = false; setOpening(''); }
    };

    return <section className={theme.components.panel + ' p-5'} aria-labelledby="monthly-support-title">
        <div className="flex items-start justify-between gap-3"><div>
            <h2 id="monthly-support-title" className="flex items-center gap-2 text-xl font-black text-slate-900 dark:text-white"><HeartHandshake className="h-5 w-5 text-modtale-accent" />Your monthly support</h2>
            <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">Manage the creators you support, update payment details, or cancel future renewals.</p>
        </div><button type="button" className={theme.components.buttonSecondary} disabled={loading || !!opening} onClick={() => setRevision(value => value + 1)}>Refresh</button></div>
        {error && <p role="alert" className="mt-4 text-sm text-red-600 dark:text-red-400">{error}</p>}
        {loading ? <p role="status" className="mt-4 text-sm text-slate-500">Loading monthly support…</p> : subscriptions.length === 0 ? <div className="mt-4 rounded-xl border border-dashed border-slate-200 p-6 text-center text-sm text-slate-500 dark:border-white/10 dark:text-slate-400">You don’t have any monthly support plans. One-time tips never renew.</div> : <div className="mt-4 space-y-3">{subscriptions.map(subscription => <div key={subscription.id} className="flex flex-col gap-3 rounded-xl border border-slate-200 p-4 sm:flex-row sm:items-center sm:justify-between dark:border-white/10">
            <div><a href={subscription.projectUrl || undefined} className="font-bold text-modtale-accent hover:underline">{subscription.projectTitle}</a><p className="mt-1 text-sm text-slate-700 dark:text-slate-200">{new Intl.NumberFormat(undefined, { style: 'currency', currency: subscription.currency.toUpperCase() }).format(subscription.amountCents / 100)} / month{subscription.testMode ? ' · Test support' : ''}</p><p className="mt-1 text-xs text-slate-500 dark:text-slate-400">{subscription.cancelAtPeriodEnd ? 'Cancels at the end of this billing period' : subscription.status.replaceAll('_', ' ')}</p></div>
            <button type="button" disabled={!!opening} onClick={() => manage(subscription.id)} className={theme.components.buttonSecondary}>{opening === subscription.id ? 'Opening…' : 'Manage or cancel'}<ExternalLink className="h-4 w-4" /><span className="sr-only"> in a new tab</span></button>
        </div>)}</div>}
    </section>;
}
