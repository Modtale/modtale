export interface DonationConfig {
    projectId: string;
    donationsEnabled: boolean;
    checkoutEnabled: boolean;
    recurringEnabled: boolean;
    testMode: boolean;
    availabilityMessage: string;
    currency: string;
    suggestedDonationCents: number;
    donationRecurringDefault: boolean;
    donationPlatformCutPercent: number;
    minimumDonationCents: number;
    maximumDonationCents: number;
}

export interface SponsoredPlacement {
    enabled: boolean;
    campaignId?: string;
    sponsorName?: string;
    headline?: string;
    body?: string;
    callToAction?: string;
    imageUrl?: string;
    creativeAltText?: string;
    clickUrl?: string;
    privacyLabel?: string;
    testCampaign?: boolean;
}

/** Parse decimal user input without floating-point rounding or silent amount changes. */
export function parseSupportAmount(value: string, minimum = 100, maximum = 100000): number | null {
    const match = /^(\d+)(?:\.(\d{1,2}))?$/.exec(value.trim());
    if (!match) return null;
    const cents = Number(match[1]) * 100 + Number((match[2] || '').padEnd(2, '0'));
    return Number.isSafeInteger(cents) && cents >= minimum && cents <= maximum ? cents : null;
}
