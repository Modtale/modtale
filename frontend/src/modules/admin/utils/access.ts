export const LEGACY_SUPER_ADMIN_ID = '692620f7c2f3266e23ac0ded';

type MaybeUser = {
    id?: string | null;
    roles?: string[] | null;
} | null | undefined;

export const isSuperAdminUser = (user: MaybeUser): boolean => {
    if (!user) return false;
    return user.id === LEGACY_SUPER_ADMIN_ID || Boolean(user.roles?.includes('SUPER_ADMIN'));
};

export const isAdminUser = (user: MaybeUser): boolean => {
    if (!user) return false;
    return Boolean(user.roles?.includes('ADMIN')) || isSuperAdminUser(user);
};
