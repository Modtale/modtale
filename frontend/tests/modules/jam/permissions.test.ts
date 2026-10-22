import { describe, expect, it } from 'vitest';
import { hasJamPermission } from '@/modules/jam/utils/permissions';
import type { Modjam } from '@/types';

describe('jam organizer permissions', () => {
    const jam = { hostId: 'host', organizerRoles: [{ id: 'editor', name: 'Editor', color: '#123456', permissions: ['EDIT_DETAILS'] }], organizerMembers: [{ userId: 'member', roleId: 'editor' }] } as Modjam;
    it('gives the host complete access and scopes member access to the assigned role', () => {
        expect(hasJamPermission(jam, 'host', 'ANNOUNCE_WINNERS')).toBe(true);
        expect(hasJamPermission(jam, 'member', 'EDIT_DETAILS')).toBe(true);
        expect(hasJamPermission(jam, 'member', 'MANAGE_SETTINGS')).toBe(false);
        expect(hasJamPermission(jam, 'stranger', 'EDIT_DETAILS')).toBe(false);
        expect(hasJamPermission(jam, undefined, 'EDIT_DETAILS')).toBe(false);
    });
    it('fails closed when a referenced role is removed', () => {
        expect(hasJamPermission({ ...jam, organizerRoles: [] }, 'member', 'EDIT_DETAILS')).toBe(false);
    });
});
