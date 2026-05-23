import { api } from '@/utils/api';

export const financeClient = {
    getCreatorOverview: async (range: string, ownerId?: string) => (
        await api.get('/finance/creator/overview', { params: { range, ownerId } })
    ).data,
    getFinanceContexts: async () => (await api.get('/finance/creator/contexts')).data,
    createStripeOnboardingLink: async (returnPath?: string, ownerId?: string) => (
        await api.post('/finance/creator/stripe/onboarding-link', { returnPath, ownerId })
    ).data,
    refreshStripeStatus: async (ownerId?: string) => (await api.post('/finance/creator/stripe/refresh-status', { ownerId })).data,
    requestPayout: async (amountCents?: number, ownerId?: string) => (await api.post('/finance/creator/payouts/request', { amountCents, ownerId })).data,
    getOrgPayoutPolicy: async (orgId: string) => (await api.get(`/finance/creator/orgs/${orgId}/payout-policy`)).data,
    updateOrgPayoutPolicy: async (orgId: string, data: { payoutMode: string; shares: Array<{ userId: string; percent: number }> }) => (
        await api.put(`/finance/creator/orgs/${orgId}/payout-policy`, data)
    ).data,

    updateProjectMonetization: async (projectId: string, data: {
        adsEnabled?: boolean;
        donationsEnabled?: boolean;
        suggestedDonationCents?: number;
        donationRecurringDefault?: boolean;
        donationPlatformCutBps?: number;
    }) => (await api.put(`/finance/projects/${projectId}/settings`, data)).data,

    getDonationConfig: async (projectId: string) => (await api.get(`/finance/projects/${projectId}/donation-config`)).data,
    createDonationCheckout: async (projectId: string, amountCents: number, recurring: boolean, guestCheckout = false) => (
        await api.get(`/finance/projects/${projectId}/donations/checkout-url`, { params: { amountCents, recurring, guestCheckout } })
    ).data,
    confirmDonationIntent: async (intentId: string) => (await api.get('/finance/donations/confirm', { params: { intentId } })).data,

    getAdSlot: async (projectId: string, placement?: string) => (await api.get(`/finance/ads/slot/${projectId}`, { params: { placement } })).data,
    trackAdImpression: async (campaignId: string, projectId: string) => (await api.post('/finance/ads/impression', { campaignId, projectId })).data,

    getAdminOverview: async (range: string) => (await api.get(`/finance/admin/overview?range=${range}`)).data,
    updateAdminSettings: async (data: {
        defaultAdRevenuePerClickCents?: number;
        minPayoutCents?: number;
    }) => (await api.put('/finance/admin/settings', data)).data,
    getAdCampaigns: async () => (await api.get('/finance/admin/ads/campaigns')).data,
    createAdCampaign: async (data: Record<string, any>) => (await api.post('/finance/admin/ads/campaigns', data)).data,
    updateAdCampaign: async (campaignId: string, data: Record<string, any>) => (await api.put(`/finance/admin/ads/campaigns/${campaignId}`, data)).data,
    startAdCampaign: async (campaignId: string) => (await api.post(`/finance/admin/ads/campaigns/${campaignId}/start`)).data,
    pauseAdCampaign: async (campaignId: string) => (await api.post(`/finance/admin/ads/campaigns/${campaignId}/pause`)).data,
    getTestAdSlot: async (projectId: string, placement?: string) => (await api.get(`/finance/admin/ads/test-slot/${projectId}`, { params: { placement } })).data,

    getPublicDailyRevenue: async (days: number) => (await api.get('/finance/public/daily-revenue', { params: { days } })).data,
    getPublicSettings: async () => (await api.get('/finance/public/settings')).data
};
