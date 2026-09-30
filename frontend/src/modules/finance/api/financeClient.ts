import { api } from '@/utils/api';
import type { AdStageReport } from '@/modules/finance/components/AdSettlementReview';

export const financeClient = {
    getAdSettlementStages: async () => (await api.get('/finance/admin/ad-settlements')).data,
    getAdSettlementStage: async (id: string) => (await api.get(`/finance/admin/ad-settlements/${encodeURIComponent(id)}`)).data,
    stageAdSettlement: async (report: AdStageReport) => (await api.post('/finance/admin/ad-settlements', report)).data,
    amendAdSettlement: async (id: string, amendment: { expectedRevision: number; operationId: string; report: AdStageReport; reason: string }) => (await api.post(`/finance/admin/ad-settlements/${encodeURIComponent(id)}/amendments`, amendment)).data,
    reviewAdSettlement: async (id: string, review: { expectedRevision: number; operationId: string; decision?: string; reason: string }) => (await api.post(`/finance/admin/ad-settlements/${encodeURIComponent(id)}/reviews`, review)).data,
    getCreatorOverview: async (range: string, ownerId?: string) => (
        await api.get('/finance/creator/overview', { params: { range, ownerId } })
    ).data,
    getFinanceContexts: async () => (await api.get('/finance/creator/contexts')).data,
    createStripeOnboardingLink: async (returnPath?: string, ownerId?: string, country?: string) => (
        await api.post('/finance/creator/stripe/onboarding-link', { returnPath, ownerId, country })
    ).data,
    refreshStripeStatus: async (ownerId?: string) => (await api.post('/finance/creator/stripe/refresh-status', { ownerId })).data,
    requestPayout: async (amountCents?: number, ownerId?: string, requestKey?: string) => (await api.post('/finance/creator/payouts/request', { amountCents, ownerId, requestKey })).data,
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

    getSupportSubscriptions: async () => (await api.get('/finance/support/subscriptions')).data,
    openSupportBillingPortal: async (subscriptionId: string) => (await api.post(`/finance/support/subscriptions/${encodeURIComponent(subscriptionId)}/billing-portal`)).data,

    getDonationConfig: async (projectId: string) => (await api.get(`/finance/projects/${projectId}/donation-config`)).data,
    createDonationCheckout: async (projectId: string, amountCents: number, recurring: boolean, guestCheckout = false) => (
        await api.post(`/finance/projects/${projectId}/donations/checkout-url`, { amountCents, recurring, guestCheckout })
    ).data,
    confirmDonationIntent: async (intentId: string) => (await api.post('/finance/donations/confirm', { intentId })).data,

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
