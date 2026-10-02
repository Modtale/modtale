import type { JamPermission, Modjam } from '@/types';

export const hasJamPermission = (jam: Modjam, userId: string | undefined, permission: JamPermission): boolean => {
    if (!userId) return false;
    if (jam.hostId === userId) return true;
    const roleId = jam.organizerMembers?.find(member => member.userId === userId)?.roleId;
    return !!roleId && !!jam.organizerRoles?.some(role => role.id === roleId && role.permissions.includes(permission));
};
