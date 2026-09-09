import React, { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, expect, it, vi } from 'vitest';
import { DependencySelector } from '@/modules/project/components/DependencySelector';

const client = vi.hoisted(() => ({ searchProjects: vi.fn(), getProjectVersions: vi.fn() }));
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
