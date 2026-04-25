export const isBotUserAgent = (userAgent: string | null): boolean => {
    if (!userAgent) return false;
    const lower = userAgent.toLowerCase();

    if (/(bot|spider|crawler|preview|snippet|slurp|facebookexternalhit|whatsapp|telegram|discord|skype|vkshare)/i.test(lower)) {
        return true;
    }

    const specificBots = [
        'googlebot', 'bingbot', 'yandexbot', 'duckduckbot', 'baiduspider',
        'sogou', 'exabot', 'facebot', 'twitterbot', 'linkedinbot',
        'embedly', 'quora link preview', 'pinterest', 'slackbot',
        'redditbot', 'applebot', 'ahrefsbot', 'semrushbot', 'mj12bot',
        'dotbot', 'petalbot', 'archive.org_bot', 'googleother',
        'google-extended', 'bingpreview', 'yahoo'
    ];

    return specificBots.some(bot => lower.includes(bot));
};