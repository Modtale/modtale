import type { DonationConfig } from '@/modules/finance/api/financeTypes';

// Synthetic policy fixture: tips and ads intentionally use separate approved settings.
export const DEMO_MONETIZATION_POLICY = { donationPlatformCutBps: 1000, adCreatorSplitBps: 7500, minPayoutCents: 1000 };
export const createDemoDonationConfig = (): DonationConfig => ({
    projectId: 'demo-project', donationsEnabled: true, checkoutEnabled: true, recurringEnabled: true,
    testMode: true, availabilityMessage: 'Synthetic checkout only.', currency: 'usd', suggestedDonationCents: 500,
    donationRecurringDefault: false, donationPlatformCutBps: DEMO_MONETIZATION_POLICY.donationPlatformCutBps,
    donationPlatformCutPercent: DEMO_MONETIZATION_POLICY.donationPlatformCutBps / 100,
    minimumDonationCents: 100, maximumDonationCents: 100000,
});
