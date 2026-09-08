import React, { act } from 'react';
import { createRoot } from 'react-dom/client';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { HelmetProvider } from 'react-helmet-async';
import { expect, it, vi } from 'vitest';
import { worldListClient } from '@/modules/worldlist/api/worldListClient';
import WorldModListView from '@/modules/worldlist/views/WorldModListView';

vi.mock('@/modules/worldlist/api/worldListClient', () => ({
    worldListClient: { get: vi.fn().mockResolvedValue({
        id: 'list', title: 'Shared list', mods: [], modCount: 0, viewCount: 1,
        expiresAt: '2026-10-01', configs: [{ scope: 'WORLD', path: 'Example/config.json', content: '<script>unsafe()</script>' }],
    }) },
    worldListDownloadUrl: () => '/download',
}));
vi.mock('@/modules/project/components/ProjectCard', () => ({ ProjectCard: () => null }));

it('shows config scope, path, and escaped content on the shared list', async () => {
    const container = document.createElement('div');
    const root = createRoot(container);
    try {
        await act(async () => {
            root.render(<HelmetProvider><MemoryRouter initialEntries={['/lists/list']}><Routes>
                <Route path="/lists/:id" element={<WorldModListView />} />
            </Routes></MemoryRouter></HelmetProvider>);
        });
        expect(container.textContent).toContain('1 config file');
        expect(container.querySelector('summary')?.textContent).toContain('World mods / Example/config.json');
        expect(container.querySelector('pre')?.textContent).toBe('<script>unsafe()</script>');
        expect(container.querySelector('script')).toBeNull();
    } finally {
        await act(async () => root.unmount());
    }
});

it('offers only launcher installation for a list containing CurseForge mods', async () => {
    vi.mocked(worldListClient.get).mockResolvedValueOnce({ id: 'list', title: 'Shared list', mods: [{ projectId: 'curseforge:1', projectTitle: 'Example', source: 'CURSEFORGE', versionNumber: '1.0' }], modCount: 1, configs: [] } as any);
    const container = document.createElement('div');
    const root = createRoot(container);
    try {
        await act(async () => {
            root.render(<HelmetProvider><MemoryRouter initialEntries={['/lists/list']}><Routes>
                <Route path="/lists/:id" element={<WorldModListView />} />
            </Routes></MemoryRouter></HelmetProvider>);
        });
        expect(container.querySelector('a[href="/download"]')).toBeNull();
        expect(container.textContent).toContain('Install');
        expect(container.textContent).toContain('CurseForge');
    } finally { await act(async () => root.unmount()); }
});
