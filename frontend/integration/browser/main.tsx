import { useEffect, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { api } from '@/utils/api';
import { financeClient } from '@/modules/finance/api/financeClient';
import type { DonationConfig } from '@/modules/finance/api/financeTypes';
import { openSupportCheckout, verifySupportReturn } from '@/modules/finance/api/supportCheckout';
import { DonationPromptModal } from '@/modules/project/components/dialogs/DonationPromptModal';
import { SupportSubscriptions } from '@/modules/finance/components/SupportSubscriptions';

// Browser-side production client, actual cookies and popup APIs. Provider requests are intercepted by Playwright.
api.defaults.adapter = 'xhr';
api.defaults.timeout = 5000;
Object.assign(window, { financeFixture: { api, financeClient, verifySupportReturn } });

function Fixture() {
    const [configuration, setConfiguration] = useState<DonationConfig>();
    const [show, setShow] = useState(false);
    const [subscriptions, setSubscriptions] = useState(false);
    const [processing, setProcessing] = useState(false);
    const [outcome, setOutcome] = useState('');
    const [user, setUser] = useState('guest');
    const generation = useRef(0);
    useEffect(() => { financeClient.getDonationConfig('project').then(setConfiguration); }, []);
    const signin = async (username: string) => {
        await api.post('/auth/logout');
        await api.post('/auth/signin', { username, password: 'isolated-fixture-password' });
        setConfiguration(await financeClient.getDonationConfig('project')); setUser(username);
    };
    const dismiss = () => { generation.current++; setShow(false); };
    return <main>
        <h1>Isolated finance integration</h1><p data-testid="identity">{user}</p>
        <button onClick={() => signin('owner')}>Sign in owner</button>
        <button onClick={() => signin('other')}>Sign in other</button>
        <button onClick={async () => { await api.post('/auth/logout'); setUser('guest'); setSubscriptions(false); }}>Sign out</button>
        <button disabled={!configuration} onClick={() => { generation.current++; setOutcome(''); setShow(true); }}>Open support</button>
        <button disabled={!configuration} onClick={() => { generation.current++; setConfiguration(value => value && ({ ...value, donationPlatformCutBps: 1000 })); setOutcome(''); setShow(true); }}>Open stale quote</button>
        <button onClick={() => setSubscriptions(value => !value)}>Toggle monthly support</button>
        <p role="status" data-testid="outcome">{outcome}</p>
        {subscriptions && <SupportSubscriptions />}
        {configuration && <DonationPromptModal show={show} suggestedAmountCents={configuration.suggestedDonationCents}
            recurringDefault={false} currency={configuration.currency} platformCutBps={configuration.donationPlatformCutBps}
            allowRecurring={configuration.recurringEnabled} testMode={configuration.testMode} isProcessing={processing}
            errorMessage={outcome === 'TERMS_CHANGED' ? 'Support terms changed. Review the new share before retrying.' : undefined}
            onClose={dismiss} onSkip={dismiss} onDonate={async (amountCents, recurring, guestCheckout) => {
                const current = generation.current; setProcessing(true);
                const result = await openSupportCheckout({ projectId: 'project', amountCents, recurring, guestCheckout,
                    expectedPlatformCutBps: configuration.donationPlatformCutBps, isCurrent: () => current === generation.current });
                if (current !== generation.current) return;
                setProcessing(false); setOutcome(result.status);
                if (result.status === 'TERMS_CHANGED') setConfiguration(result.configuration);
                if (result.status === 'OPENED') dismiss();
            }} />}
    </main>;
}
createRoot(document.getElementById('fixture')!).render(<Fixture />);
