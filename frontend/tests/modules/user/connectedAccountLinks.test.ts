import { describe, expect, it } from 'vitest';
import { getConnectedAccountProfileUrl } from '@/modules/user/utils/connectedAccountLinks';

describe('connected account profile links', () => {
    it('normalizes legacy Twitter links to current X profile URLs', () => {
        expect(getConnectedAccountProfileUrl({
            provider: 'twitter',
            providerId: 'tw-1',
            username: '@modtale_dev',
            profileUrl: 'https://twitter.com/modtale_dev'
        })).toBe('https://x.com/modtale_dev');
    });

    it('builds Bluesky profile links from handles', () => {
        expect(getConnectedAccountProfileUrl({
            provider: 'bluesky',
            providerId: 'did:plc:abc',
            username: 'willow.bsky.social',
            profileUrl: ''
        })).toBe('https://bsky.app/profile/willow.bsky.social');
    });

    it('uses stable Discord account ids and otherwise preserves provider URLs', () => {
        expect(getConnectedAccountProfileUrl({
            provider: 'discord',
            providerId: '123 456',
            username: 'Willow',
            profileUrl: ''
        })).toBe('https://discord.com/users/123%20456');

        expect(getConnectedAccountProfileUrl({
            provider: 'github',
            providerId: 'gh-1',
            username: 'willow',
            profileUrl: 'https://github.com/willow'
        })).toBe('https://github.com/willow');
    });
});
