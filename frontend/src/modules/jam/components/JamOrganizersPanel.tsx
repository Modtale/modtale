import React, { useEffect, useRef, useState } from 'react';
import { Pencil, Plus, Trash2, UserPlus } from 'lucide-react';
import { api, extractApiErrorMessage } from '@/utils/api';
import type { JamOrganizerRole, JamPermission, Modjam, User } from '@/types';
import { StatusModal } from '@/components/ui/StatusModal';

const permissions: { value: JamPermission; label: string }[] = [
    { value: 'EDIT_DETAILS', label: 'Edit overview and images' },
    { value: 'EDIT_RULES', label: 'Edit rules' },
    { value: 'MANAGE_SETTINGS', label: 'Manage settings and judging criteria' },
    { value: 'MANAGE_JUDGES', label: 'Manage judges' },
    { value: 'VIEW_RESULTS', label: 'View early results' },
    { value: 'ANNOUNCE_WINNERS', label: 'Announce winners' }
];
const emptyRole = (): Omit<JamOrganizerRole, 'id'> & { id?: string } => ({ name: '', color: '#3b82f6', permissions: [] });

/** Role/member mutations are independent of unsaved content in the builder. */
export const JamOrganizersPanel: React.FC<{ jam: Modjam; onUpdate: (jam: Modjam) => void }> = ({ jam, onUpdate }) => {
    const [role, setRole] = useState(emptyRole);
    const [username, setUsername] = useState('');
    const [roleId, setRoleId] = useState('');
    const [busy, setBusy] = useState(false);
    const [message, setMessage] = useState<string | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [deleteRole, setDeleteRole] = useState<JamOrganizerRole | null>(null);
    const [profiles, setProfiles] = useState<Record<string, User>>({});
    const pending = useRef(false);
    const mounted = useRef(true);
    const context = useRef(jam.id);
    context.current = jam.id;
    useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
    const memberIds = (jam.organizerMembers || []).map(member => member.userId).sort().join(',');
    useEffect(() => {
        if (!memberIds) { setProfiles({}); return; }
        const controller = new AbortController();
        api.post('/users/batch/ids', { ids: memberIds.split(',') }, { signal: controller.signal })
            .then(response => { if (!controller.signal.aborted) setProfiles(Object.fromEntries((response.data || []).map((user: User) => [user.id, user]))); })
            .catch(() => {});
        return () => controller.abort();
    }, [memberIds]);

    const mutate = async (action: () => Promise<{ data: Modjam }>, success: string, after?: () => void) => {
        if (pending.current) return;
        pending.current = true;
        setBusy(true);
        setError(null);
        setMessage(null);
        const target = jam.id;
        try {
            const response = await action();
            if (!mounted.current || context.current !== target) return;
            onUpdate(response.data);
            setMessage(success);
            after?.();
        } catch (failure) {
            if (mounted.current && context.current === target) setError(extractApiErrorMessage(failure, 'We could not update the organizers.'));
        } finally {
            pending.current = false;
            if (mounted.current && context.current === target) setBusy(false);
        }
    };
    const roles = jam.organizerRoles || [];
    const roleName = (id: string) => roles.find(item => item.id === id)?.name || 'Removed role';

    return <section className="space-y-5 rounded-2xl border border-slate-200 dark:border-white/10 bg-white dark:bg-slate-900 p-4 sm:p-6" aria-busy={busy}>
        <div><h3 className="text-lg font-black text-slate-900 dark:text-white">Organizers</h3><p className="mt-1 text-xs text-slate-500">Create roles with only the access each organizer needs. Only the host can manage roles and invitations.</p></div>
        {error && <p role="alert" className="rounded-xl bg-red-500/10 p-3 text-sm text-red-600 dark:text-red-400">{error}</p>}
        {message && <p role="status" className="text-sm text-green-700 dark:text-green-400">{message}</p>}
        <form onSubmit={event => { event.preventDefault(); mutate(() => api.post(`/modjams/${jam.id}/organizer-roles`, role), 'Role saved.', () => setRole(emptyRole())); }} className="space-y-3">
            <div className="flex flex-wrap items-end gap-3">
                <label className="min-w-0 flex-1 text-xs font-bold text-slate-500">Role name<input aria-label="Organizer role name" disabled={busy} value={role.name} maxLength={50} onChange={event => setRole({ ...role, name: event.target.value })} placeholder="Co-host" className="mt-1 w-full rounded-xl border border-slate-200 dark:border-white/10 bg-slate-50 dark:bg-black/20 px-3 py-2 text-sm text-slate-900 dark:text-white" /></label>
                <label className="text-xs font-bold text-slate-500">Color<input aria-label="Organizer role color" type="color" disabled={busy} value={role.color} onChange={event => setRole({ ...role, color: event.target.value })} className="mt-1 block h-10 w-12 rounded-lg bg-transparent" /></label>
            </div>
            <fieldset disabled={busy} className="grid grid-cols-1 sm:grid-cols-2 gap-2"><legend className="mb-2 text-xs font-bold text-slate-500">Role permissions</legend>{permissions.map(permission => <label key={permission.value} className="flex items-center gap-2 rounded-lg bg-slate-50 dark:bg-white/5 p-3 text-xs font-medium"><input type="checkbox" checked={role.permissions.includes(permission.value)} onChange={event => setRole({ ...role, permissions: event.target.checked ? [...role.permissions, permission.value] : role.permissions.filter(value => value !== permission.value) })} className="rounded text-modtale-accent" />{permission.label}</label>)}</fieldset>
            <div className="flex gap-2"><button type="submit" disabled={busy || !role.name.trim() || !role.permissions.length} className="flex items-center gap-2 rounded-xl bg-modtale-accent px-4 py-2 text-sm font-bold text-white disabled:opacity-50"><Plus className="size-4" />{role.id ? 'Save role' : 'Create role'}</button>{role.id && <button type="button" disabled={busy} onClick={() => setRole(emptyRole())} className="rounded-xl px-4 py-2 text-sm font-bold text-slate-500">Cancel editing</button>}</div>
        </form>
        {roles.length > 0 && <ul className="space-y-2">{roles.map(item => {
            const inUse = jam.organizerMembers?.some(member => member.roleId === item.id) || jam.pendingOrganizerInvites?.some(invite => invite.roleId === item.id);
            return <li key={item.id} className="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-slate-200 dark:border-white/10 px-3 py-2"><div className="min-w-0"><span className="inline-block size-2 rounded-full mr-2" style={{ backgroundColor: /^#[0-9a-f]{6}$/i.test(item.color) ? item.color : '#3b82f6' }} /><span className="font-bold text-sm">{item.name}</span><span className="ml-2 text-xs text-slate-500">{item.permissions.length} permissions</span></div><div className="flex gap-1"><button type="button" aria-label={`Edit ${item.name} role`} disabled={busy} onClick={() => setRole({ ...item })} className="p-2 text-slate-500 hover:text-modtale-accent"><Pencil className="size-4" /></button><button type="button" aria-label={`Delete ${item.name} role`} disabled={busy || inUse} title={inUse ? 'Remove members and invitations before deleting this role' : 'Delete role'} onClick={() => setDeleteRole(item)} className="p-2 text-slate-500 hover:text-red-500 disabled:opacity-30"><Trash2 className="size-4" /></button></div></li>;
        })}</ul>}
        <form onSubmit={event => { event.preventDefault(); mutate(() => api.post(`/modjams/${jam.id}/organizers/invite`, { username: username.trim(), roleId }), 'Organizer invited.', () => setUsername('')); }} className="flex flex-col sm:flex-row sm:items-end gap-3 border-t border-slate-200 dark:border-white/10 pt-4">
            <label className="flex-1 min-w-0 text-xs font-bold text-slate-500">Username<input aria-label="Organizer username" value={username} disabled={busy} maxLength={50} onChange={event => setUsername(event.target.value)} className="mt-1 w-full rounded-xl border border-slate-200 dark:border-white/10 bg-slate-50 dark:bg-black/20 px-3 py-2 text-sm text-slate-900 dark:text-white" /></label>
            <label className="flex-1 min-w-0 text-xs font-bold text-slate-500">Role<select aria-label="Organizer invitation role" value={roleId} disabled={busy} onChange={event => setRoleId(event.target.value)} className="mt-1 w-full rounded-xl border border-slate-200 dark:border-white/10 bg-slate-50 dark:bg-slate-900 px-3 py-2 text-sm"><option value="">Choose a role</option>{roles.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
            <button type="submit" disabled={busy || !username.trim() || !roles.some(item => item.id === roleId)} className="flex items-center justify-center gap-2 rounded-xl bg-modtale-accent px-4 py-2 text-sm font-bold text-white disabled:opacity-50"><UserPlus className="size-4" />Invite</button>
        </form>
        <ul className="space-y-2">{(jam.organizerMembers || []).map(member => <li key={member.userId} className="flex items-center justify-between gap-3 text-sm"><span className="min-w-0 truncate">{profiles[member.userId]?.username || member.userId}<span className="ml-2 text-xs text-slate-500">{roleName(member.roleId)}</span></span><button type="button" disabled={busy} onClick={() => mutate(() => api.delete(`/modjams/${jam.id}/organizers/${member.userId}`), 'Organizer removed.')} className="text-xs font-bold text-red-500">Remove</button></li>)}{(jam.pendingOrganizerInvites || []).map(invite => <li key={invite.userId} className="flex items-center justify-between gap-3 text-sm"><span className="min-w-0 truncate">{invite.username}<span className="ml-2 text-xs text-slate-500">Invited · {roleName(invite.roleId)}</span></span><button type="button" disabled={busy} onClick={() => mutate(() => api.delete(`/modjams/${jam.id}/organizers/${invite.userId}`), 'Invitation canceled.')} className="text-xs font-bold text-red-500">Cancel invite</button></li>)}</ul>
        {deleteRole && <StatusModal type="warning" title="Delete organizer role?" message={`Delete the ${deleteRole.name} role?`} actionLabel="Delete role" secondaryLabel="Cancel" onClose={() => { if (!pending.current) setDeleteRole(null); }} onAction={() => mutate(() => api.delete(`/modjams/${jam.id}/organizer-roles/${deleteRole.id}`), 'Role deleted.', () => setDeleteRole(null))} />}
    </section>;
};
