import React, { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, expect, it, vi } from 'vitest';
import { ModConfigFields } from '@/modules/project/components/ModConfigFields';

const client = vi.hoisted(() => ({ getProjectFull: vi.fn() }));
vi.mock('@/modules/project/api/projectClient', () => ({ projectClient: client }));
afterEach(() => { document.body.innerHTML = ''; vi.clearAllMocks(); });

it.each(['author:Plugin', 'bad/path:Plugin', undefined])('detects a safe release destination and keeps the override optional (%s)', async manifestId => {
    client.getProjectFull.mockResolvedValue({ versions: [{ versionNumber: '1.0', manifestId }] });
    const host = document.createElement('div'); document.body.append(host);
    const root = createRoot(host);
    const change = vi.fn();
    const button = (label: string) => [...document.querySelectorAll('button')].find(el => el.textContent === label)!;
    try {
        await act(async () => root.render(<ModConfigFields projectId="plugin" title="Plugin" versionNumber="1.0" configs={[]} onChange={change} />));
        await act(async () => button('Config').click());
        expect(document.querySelector('input[placeholder="Group_PluginName"]')).toBeNull();
        if (manifestId === 'author:Plugin') {
            expect(document.body.textContent).toContain('author_Plugin · Detected automatically');
        } else {
            expect(document.body.textContent).toContain('Import config folder');
            expect(document.body.textContent).not.toContain('Detected automatically');
        }
        await act(async () => button('Change').click());
        expect(document.querySelector('input[placeholder="Group_PluginName"]')).toBeTruthy();
    } finally { await act(async () => root.unmount()); }
});

it('edits attached settings without a raw preview and saves only when the attachment dialog is saved', async () => {
    client.getProjectFull.mockResolvedValue({ versions: [{ versionNumber: '1.0', manifestId: 'author:Plugin' }] });
    const file = new File(['{"enabled":true}'], 'config.json');
    Object.defineProperty(file, 'text', { value: async () => '{"enabled":true}' });
    const config = { id: 'config', projectId: 'plugin', destination: 'Universe/mods/author_Plugin', file };
    const host = document.createElement('div'); document.body.append(host);
    const root = createRoot(host); const change = vi.fn();
    const button = (label: string) => [...document.querySelectorAll('button')].find(el => el.textContent?.trim() === label)!;
    try {
        await act(async () => root.render(<ModConfigFields projectId="plugin" title="Plugin" versionNumber="1.0" configs={[config]} onChange={change} />));
        await act(async () => button('Configs (1)').click());
        expect(document.body.textContent).not.toContain('File details');
        await act(async () => button('config.json').click());
        expect(document.querySelector('pre')).toBeNull();
        const toggle = document.querySelector('[role="switch"]') as HTMLButtonElement;
        await act(async () => toggle.click());
        expect(toggle.getAttribute('aria-checked')).toBe('false');
        await act(async () => button('Reset changes').click());
        expect(toggle.getAttribute('aria-checked')).toBe('true');
        await act(async () => toggle.click());
        await act(async () => button('Save changes').click());
        expect(change).not.toHaveBeenCalled();
        await act(async () => button('Save configs').click());
        expect(change).toHaveBeenCalledOnce();
        const saved = change.mock.calls[0][0][0].file;
        const text = await new Promise<string>(resolve => { const reader = new FileReader(); reader.onload = () => resolve(String(reader.result)); reader.readAsText(saved); });
        expect(text).toBe('{"enabled":false}');
        expect(config.file).toBe(file);
    } finally { await act(async () => root.unmount()); }
});
