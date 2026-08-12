import { describe, expect, it } from 'vitest';
import {
    ALL_ADMIN_PERMISSIONS,
    AdminPermission,
    getEffectiveAdminPermissions,
    hasAdminPermission,
    hasAnyAdminPermission,
    isAdminUser
} from '@/modules/admin/utils/access';

describe('admin access helpers', () => {
    it('ignores legacy admin ids and roles', () => {
        const legacyIdUser = { id: '692620f7c2f3266e23ac0ded', roles: ['USER'] };
        const adminRoleUser = { id: 'u1', roles: ['ADMIN'] };
        const superAdminRoleUser = { id: 'u2', roles: ['SUPER_ADMIN'] };

        expect(isAdminUser(legacyIdUser)).toBe(false);
        expect(isAdminUser(adminRoleUser)).toBe(false);
        expect(isAdminUser(superAdminRoleUser)).toBe(false);
        expect(getEffectiveAdminPermissions(legacyIdUser).size).toBe(0);
    });

    it('recognizes explicit permissions', () => {
        const user = { id: 'u2', roles: ['USER'], adminPermissions: [AdminPermission.USER_READ] };

        expect(isAdminUser(user)).toBe(true);
        expect(hasAnyAdminPermission(user)).toBe(true);
        expect(hasAdminPermission(user, AdminPermission.USER_READ)).toBe(true);
        expect(hasAdminPermission(user, AdminPermission.USER_DELETE)).toBe(false);
    });

    it('recognizes users with every explicit permission as admins', () => {
        expect(isAdminUser({ id: 'u1', adminPermissions: ALL_ADMIN_PERMISSIONS })).toBe(true);
        expect(getEffectiveAdminPermissions({ id: 'u1', adminPermissions: ALL_ADMIN_PERMISSIONS }).size).toBe(ALL_ADMIN_PERMISSIONS.length);
    });

    it('rejects normal and missing users', () => {
        expect(isAdminUser({ id: 'u1', roles: ['USER'] })).toBe(false);
        expect(isAdminUser(null)).toBe(false);
        expect(hasAnyAdminPermission(null)).toBe(false);
    });
});
