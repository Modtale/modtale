import { useState } from 'react';

export default function FinanceShowcaseFrame() {
    const [mobile, setMobile] = useState(false);
    return <main style={{ minHeight: '100vh', background: '#e2e8f0', padding: 20, fontFamily: 'Inter, sans-serif' }}>
        <header style={{ maxWidth: 1100, margin: '0 auto 16px', display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 12 }}>
            <strong>Synthetic finance showcase · No payments</strong>
            <button type="button" aria-pressed={!mobile} onClick={() => setMobile(false)} style={{ padding: '10px 16px', borderRadius: 10, border: '1px solid #94a3b8', background: mobile ? '#fff' : '#2563eb', color: mobile ? '#0f172a' : '#fff' }}>Desktop viewport</button>
            <button type="button" aria-pressed={mobile} onClick={() => setMobile(true)} style={{ padding: '10px 16px', borderRadius: 10, border: '1px solid #94a3b8', background: mobile ? '#2563eb' : '#fff', color: mobile ? '#fff' : '#0f172a' }}>Mobile viewport</button>
            <span>{mobile ? '390 × 844 CSS pixels' : '1100 × 900 CSS pixels'} · Actual iframe viewport</span>
        </header>
        <iframe title="Synthetic finance components" src="/finance-preview?frame=1" style={{ display: 'block', width: mobile ? 390 : 1100, maxWidth: '100%', height: mobile ? 844 : 900, margin: '0 auto', border: '1px solid #94a3b8', borderRadius: mobile ? 24 : 12, background: '#fff' }} />
    </main>;
}
