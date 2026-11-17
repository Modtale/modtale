import React, { act, useState } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { JamOrganizersPanel } from '@/modules/jam/components/JamOrganizersPanel';
import { api } from '@/utils/api';
import type { Modjam } from '@/types';

const initial = { id: 'jam-1', organizerRoles: [], organizerMembers: [], pendingOrganizerInvites: [] } as unknown as Modjam;
const setInput = (element: HTMLInputElement, value: string) => { Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!.call(element, value); element.dispatchEvent(new Event('input', { bubbles: true })); };
const Harness = () => { const [jam, setJam] = useState(initial); return <JamOrganizersPanel jam={jam} onUpdate={setJam} />; };

describe('organizer role controls', () => {
    let root: Root, container: HTMLDivElement;
    beforeEach(() => { container = document.createElement('div'); document.body.appendChild(container); root = createRoot(container); });
    afterEach(async () => { await act(async () => root.unmount()); container.remove(); });

    it('requires a role name and permissions and serializes repeated role saves', async () => {
        let resolve!: (value: any) => void;
        const post = vi.spyOn(api, 'post').mockImplementation(() => new Promise(done => { resolve = done; }));
        await act(async () => root.render(<Harness />));
        expect((container.querySelector('button[type="submit"]') as HTMLButtonElement).disabled).toBe(true);
        await act(async () => setInput(container.querySelector('[aria-label="Organizer role name"]')!, 'Rules editor'));
        await act(async () => (container.querySelector('input[type="checkbox"]') as HTMLInputElement).click());
        const form = container.querySelector('form')!;
        await act(async () => { form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); });
        expect(post).toHaveBeenCalledOnce();
        expect(post).toHaveBeenCalledWith('/modjams/jam-1/organizer-roles', expect.objectContaining({ name: 'Rules editor', permissions: ['EDIT_DETAILS'] }));
        await act(async () => resolve({ data: { ...initial, organizerRoles: [{ id: 'role-1', name: 'Rules editor', color: '#3b82f6', permissions: ['EDIT_DETAILS'] }] } }));
        expect(container.querySelector('[role="status"]')?.textContent).toBe('Role saved.');
        expect(container.querySelector('[aria-label="Edit Rules editor role"]')).not.toBeNull();
    });

    it('does not apply an in-flight role response after the panel is dismissed', async () => {
        let resolve!: (value: any) => void;
        vi.spyOn(api, 'post').mockImplementation(() => new Promise(done => { resolve = done; }));
        const updated = vi.fn();
        await act(async () => root.render(<JamOrganizersPanel jam={initial} onUpdate={updated} />));
        await act(async () => setInput(container.querySelector('[aria-label="Organizer role name"]')!, 'Co-host'));
        await act(async () => (container.querySelector('input[type="checkbox"]') as HTMLInputElement).click());
        await act(async () => container.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })));
        await act(async () => root.render(null));
        await act(async () => resolve({ data: initial }));
        expect(updated).not.toHaveBeenCalled();
    });

    it('prevents deleting a role that still has members or invitations', async () => {
        const jam = { ...initial, organizerRoles: [{ id: 'role-1', name: 'Co-host', color: '#3b82f6', permissions: ['EDIT_DETAILS'] }], pendingOrganizerInvites: [{ userId: 'helper', username: 'Helper', roleId: 'role-1' }] } as Modjam;
        await act(async () => root.render(<JamOrganizersPanel jam={jam} onUpdate={vi.fn()} />));
        expect((container.querySelector('[aria-label="Delete Co-host role"]') as HTMLButtonElement).disabled).toBe(true);
        expect(container.textContent).toContain('Helper');
        expect(container.textContent).toContain('Cancel invite');
    });
});
