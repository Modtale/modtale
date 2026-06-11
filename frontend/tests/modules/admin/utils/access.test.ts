import { describe, expect, it } from 'vitest';
import { LEGACY_SUPER_ADMIN_ID, isAdminUser, isSuperAdminUser } from '@/modules/admin/utils/access';

describe('admin access helpers', () => {
    it('recognizes super admins by legacy id or role', () => {
        expect(isSuperAdminUser(null)).toBe(false);
        expect(isSuperAdminUser({ id: LEGACY_SUPER_ADMIN_ID, roles: ['USER'] })).toBe(true);
        expect(isSuperAdminUser({ id: 'u1', roles: ['SUPER_ADMIN'] })).toBe(true);
    });

    it('recognizes admins through admin or super admin privileges', () => {
        expect(isAdminUser({ id: 'u1', roles: ['USER'] })).toBe(false);
        expect(isAdminUser({ id: 'u1', roles: ['ADMIN'] })).toBe(true);
        expect(isAdminUser({ id: LEGACY_SUPER_ADMIN_ID, roles: ['USER'] })).toBe(true);
    });
});
