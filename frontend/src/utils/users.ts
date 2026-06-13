import type { User } from '@/types';

type UserPayload = Partial<User> & {
    likedModIds?: string[];
};

export const normalizeUser = (user: UserPayload): User => ({
    ...user,
    likedProjectIds: user.likedProjectIds || user.likedModIds || []
} as User);

export const normalizeUsers = (users: UserPayload[]): User[] => users.map(normalizeUser);
