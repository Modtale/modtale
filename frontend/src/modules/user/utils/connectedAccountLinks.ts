import type { ConnectedAccount } from '@/types';

type ConnectedAccountLink = Pick<ConnectedAccount, 'provider' | 'providerId' | 'profileUrl' | 'username'>;

export const getConnectedAccountProfileUrl = (account: ConnectedAccountLink): string => {
    const provider = account.provider?.trim().toLowerCase();
    const username = account.username?.trim().replace(/^@+/, '');

    if (provider === 'discord' && account.providerId) {
        return `https://discord.com/users/${encodeURIComponent(account.providerId)}`;
    }
    if (provider === 'twitter' && username) {
        return `https://x.com/${encodeURIComponent(username)}`;
    }
    if (provider === 'bluesky' && username) {
        return `https://bsky.app/profile/${encodeURIComponent(username)}`;
    }

    return account.profileUrl || '#';
};
