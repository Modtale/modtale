import React, { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, expect, it, vi } from 'vitest';
import { DependencySelector } from '@/modules/project/components/DependencySelector';

const client = vi.hoisted(() => ({ searchProjects: vi.fn(), getProjectVersions: vi.fn(), getProject: vi.fn() }));
vi.mock('@/modules/project/api/projectClient', () => ({ projectClient: client }));
vi.mock('@/components/ui/Toast', () => ({ useToast: () => ({ showToast: vi.fn() }) }));

afterEach(() => { vi.useRealTimers(); document.body.innerHTML = ''; });

it.each([undefined, []])('loads selectable releases when a search summary has versions=%s', async versions => {
    vi.useFakeTimers();
    client.searchProjects.mockResolvedValue([{ id: 'weapons', title: 'Implement Weapons', author: 'Charlock', classification: 'DATA', versions }]);
    client.getProjectVersions.mockResolvedValue([{ id: 'release', versionNumber: '0.2.2', channel: 'RELEASE', gameVersions: ['game'], dependencies: [] }]);
    const host = document.createElement('div'); document.body.append(host);
    const root = createRoot(host); const onChange = vi.fn();
    try {
        await act(async () => root.render(<DependencySelector selectedDeps={[]} onChange={onChange} targetGameVersion="game" isModpack />));
        const input = host.querySelector('input')!;
        await act(async () => {
            Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!.call(input, 'Implement');
            input.dispatchEvent(new Event('input', { bubbles: true }));
        });
        await act(async () => { await vi.advanceTimersByTimeAsync(350); });
        const project = [...host.querySelectorAll('button')].find(button => button.textContent?.includes('Implement Weapons'))!;
        expect(project).toBeTruthy();
        await act(async () => project.click());
        expect(client.getProjectVersions).toHaveBeenCalledWith('weapons');
        const release = [...document.querySelectorAll('button')].find(button => button.textContent?.includes('0.2.2'))!;
        expect(release).toBeTruthy();
        await act(async () => release.click());
        expect(onChange).toHaveBeenCalledWith([expect.objectContaining({ projectId: 'weapons', versionNumber: '0.2.2' })]);
    } finally { await act(async () => root.unmount()); host.remove(); }
});


it('adds only checked related projects and shows their icons and release compatibility', async () => {
    vi.useFakeTimers();
    const deps = ['first', 'second'].map(id => ({ projectId: id, projectTitle: id, versionNumber: '1.0', source: 'MODTALE' }));
    client.searchProjects.mockResolvedValue([{ id: 'parent', title: 'Parent', classification: 'DATA' }]);
    client.getProjectVersions.mockImplementation(async id => [{ versionNumber: '1.0', channel: 'RELEASE', gameVersions: ['0.6.5'], dependencies: id === 'parent' ? deps : [] }]);
    client.getProject.mockImplementation(async id => ({ id, title: id, imageUrl: `https://example.com/${id}.png` }));
    const host = document.createElement('div'); document.body.append(host);
    const root = createRoot(host); const onChange = vi.fn();
    try {
        await act(async () => root.render(<DependencySelector selectedDeps={[]} onChange={onChange} isModpack />));
        await act(async () => {
            const input = host.querySelector('input')!;
            Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!.call(input, 'Par');
            input.dispatchEvent(new Event('input', { bubbles: true }));
            await vi.advanceTimersByTimeAsync(350);
        });
        await act(async () => { await vi.advanceTimersByTimeAsync(350); });
        await act(async () => [...host.querySelectorAll('button')].find(b => b.textContent?.includes('Parent'))!.click());
        await act(async () => [...document.querySelectorAll('button')].find(b => b.textContent?.includes('1.0'))!.click());
        const dialog = document.querySelector('[aria-label="Add dependencies for Parent"]')!;
        expect(dialog.querySelectorAll('img')).toHaveLength(2);
        expect(dialog.textContent).toContain('0.6.5');
        expect(dialog.textContent).not.toContain('Parent');
        await act(async () => (dialog.querySelectorAll('input')[1] as HTMLInputElement).click());
        await act(async () => [...dialog.querySelectorAll('button')].find(b => b.textContent?.includes('Add Selected'))!.click());
        expect(onChange.mock.lastCall![0].map((dep: { projectId: string }) => dep.projectId)).toEqual(['parent', 'first']);
    } finally { await act(async () => root.unmount()); }
});
