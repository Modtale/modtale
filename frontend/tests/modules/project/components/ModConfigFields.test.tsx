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
