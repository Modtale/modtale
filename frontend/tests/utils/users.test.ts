import { describe, expect, it } from 'vitest';
import { normalizeUser, normalizeUsers } from '@/utils/users';

describe('user normalization', () => {
    it('maps backend likedModIds onto likedProjectIds', () => {
        const user = normalizeUser({
            id: 'user-1',
            username: 'Ada',
            avatarUrl: '',
            likedModIds: ['project-1']
        });

        expect(user.likedProjectIds).toEqual(['project-1']);
    });

    it('keeps likedProjectIds when both field names are present', () => {
        const user = normalizeUser({
            id: 'user-1',
            username: 'Ada',
            avatarUrl: '',
            likedProjectIds: ['project-2'],
            likedModIds: ['project-1']
        });

        expect(user.likedProjectIds).toEqual(['project-2']);
    });

    it('normalizes user arrays', () => {
        const users = normalizeUsers([
            {
                id: 'user-1',
                username: 'Ada',
                avatarUrl: '',
                likedModIds: ['project-1']
            }
        ]);

        expect(users[0].likedProjectIds).toEqual(['project-1']);
    });
});
