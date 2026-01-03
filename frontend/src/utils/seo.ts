export const isBotUserAgent = (userAgent: string | null): boolean => {
    if (!userAgent) return false;
    const lower = userAgent.toLowerCase();

    return [
        'googlebot',
        'bingbot',
        'slurp', // Yahoo
        'duckduckbot',
        'baiduspider',
        'yandexbot',
        'sogou',
        'exabot',
        'facebot',
        'facebookexternalhit',
        'twitterbot',
        'whatsapp',
        'telegrambot',
        'discordbot',
        'linkedinbot',
        'embedly',
        'quora link preview',
        'pinterest',
        'slackbot',
        'redditbot',
        'applebot'
    ].some(bot => lower.includes(bot));
};