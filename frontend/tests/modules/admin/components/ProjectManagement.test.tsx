import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { beforeEach, afterEach, it, expect, vi } from 'vitest';
import { ProjectManagement } from '@/modules/admin/components/ProjectManagement';
import { adminClient } from '@/modules/admin/api/adminClient';
vi.mock('@/modules/admin/api/adminClient', () => ({ adminClient: { getProjectById: vi.fn(), updateProjectRaw: vi.fn() } }));
vi.mock('@/modules/admin/utils/access', async importOriginal => ({ ...(await importOriginal<any>()), hasAdminPermission: () => true }));
vi.mock('@/components/ui/ModalPortal', () => ({ ModalPortal: ({ children }: any) => <div>{children}</div> }));
let container: HTMLDivElement, root: Root;
const status = vi.fn();
const project = { id: 'project', title: 'Original', author: 'Author', status: 'PENDING', versions: [], reviewToken: 'snapshot', authorId: 'owner', tags: ['tools'] };
beforeEach(() => { vi.clearAllMocks(); container = document.createElement('div'); document.body.append(container); root = createRoot(container); });
afterEach(async () => { await act(async () => root.unmount()); container.remove(); });
async function load(value = project) {
    vi.mocked(adminClient.getProjectById).mockResolvedValue(value);
    await act(async () => root.render(<ProjectManagement currentAdmin={{}} setStatus={status} />));
    const input = container.querySelector<HTMLInputElement>('input[placeholder="Lookup exact ID/Slug..."]')!;
    await act(async () => {
        Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!.call(input, 'project');
        input.dispatchEvent(new Event('input', { bubbles: true }));
    });
    await act(async () => input.closest('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })));
}
async function click(text: string) {
    const button = [...container.querySelectorAll('button')].find(b => b.textContent?.includes(text));
    expect(button).toBeTruthy(); await act(async () => button!.click());
}
it('edits metadata only and sends the snapshot opened with the editor', async () => {
    await load(); await click('Repair Metadata');
    const area = container.querySelector('textarea')!;
    expect(JSON.parse(area.value)).toEqual({ title: 'Original', tags: ['tools'] });
    await click('Save Changes');
    expect(adminClient.updateProjectRaw).toHaveBeenCalledWith('project', { title: 'Original', tags: ['tools'] }, 'snapshot');
    expect(container.querySelector('textarea')).toBeNull();
});
it('does not open an editable snapshot when the token is missing', async () => {
    await load({ ...project, reviewToken: undefined } as any); await click('Repair Metadata');
    expect(container.querySelector('textarea')).toBeNull(); expect(adminClient.updateProjectRaw).not.toHaveBeenCalled();
    expect(status).toHaveBeenCalledWith(expect.objectContaining({ title: 'Refresh required' }));
});
it('keeps the draft open after a conflicting save without retrying automatically', async () => {
    vi.mocked(adminClient.updateProjectRaw).mockRejectedValue(new Error('Conflict'));
    await load(); await click('Repair Metadata'); await click('Save Changes');
    expect(container.querySelector('textarea')).not.toBeNull(); expect(adminClient.updateProjectRaw).toHaveBeenCalledTimes(1);
    expect(status).toHaveBeenCalledWith(expect.objectContaining({ type: 'error' }));
});
