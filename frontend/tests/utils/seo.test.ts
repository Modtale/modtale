import { describe, expect, it } from 'vitest';
import { isBotUserAgent } from '@/utils/seo';

describe('isBotUserAgent', () => {
    it('returns false for missing or ordinary browser agents', () => {
        expect(isBotUserAgent(null)).toBe(false);
        expect(isBotUserAgent('Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/537.36 Chrome/126.0.0.0 Safari/537.36')).toBe(false);
    });

    it('detects generic crawler markers and specific preview bots', () => {
        expect(isBotUserAgent('ExampleCrawler/1.0')).toBe(true);
        expect(isBotUserAgent('Slackbot-LinkExpanding 1.0 (+https://api.slack.com/robots)')).toBe(true);
        expect(isBotUserAgent('WhatsApp/2.24 Preview')).toBe(true);
    });
});
