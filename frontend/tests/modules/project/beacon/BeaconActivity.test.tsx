import React, { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';
import { BeaconActivity } from '@/modules/project/beacon/BeaconActivity';
const stats = { slug: 'example', displayName: 'Example', activeServers: 0, activePlayers: 0, recordServers: 12, recordPlayers: 24, lastHeartbeatAt: null, activity: [{ bucketStart: '2026-09-01T00:00:00Z', servers: 0, players: 0 }] };
let container: HTMLDivElement;
let root: Root;
let fetchMock: ReturnType<typeof vi.fn>;
beforeEach(() => { container = document.createElement('div'); document.body.append(container); root = createRoot(container); fetchMock = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ stats }) }); vi.stubGlobal('fetch', fetchMock); });
afterEach(async () => { await act(async () => root.unmount()); container.remove(); vi.unstubAllGlobals(); });
const render = async (component: React.ReactNode) => { await act(async () => root.render(component)); };
const click = async (text: string) => { const button = [...container.querySelectorAll('button')].find(b => b.textContent?.includes(text)); expect(button).toBeTruthy(); await act(async () => button!.click()); };
describe('Beacon activity', () => {
    it('shows compact sidebar counts and links to full statistics', async () => {
        await render(<BeaconActivity projectId="project" />);
        expect(container.textContent).toContain('Active servers0');
        expect(container.textContent).toContain('Active players0');
        expect(container.querySelector('a')?.href).toBe('https://modstats.io/stats/example');
        expect(container.textContent).not.toContain('Record');
        expect(container.querySelector('svg[role="img"]')).toBeNull();
        await click('Activity');
        expect(container.querySelector('dl')).toBeNull();
        await click('Activity');
        expect(container.querySelector('dl')).not.toBeNull();
    });
    it('hides the public panel for unmatched projects', async () => {
        fetchMock.mockResolvedValue({ ok: true, json: async () => ({ stats: null }) });
        await render(<BeaconActivity projectId="project" />);
        expect(container.innerHTML).toBe('');
    });
    it('quietly omits activity when the provider is unavailable', async () => {
        fetchMock.mockResolvedValueOnce({ ok: false });
        await render(<BeaconActivity projectId="project" />);
        expect(container.innerHTML).toBe('');
    });
});
