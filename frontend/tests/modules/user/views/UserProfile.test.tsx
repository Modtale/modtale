import React, { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { UserProfile } from '@/modules/user/views/UserProfile';
import { api } from '@/utils/api';

vi.mock('@/utils/api', () => ({ api: { get: vi.fn() } }));
vi.mock('@/modules/user/components/ProfileLayout', () => ({
    ProfileLayout: ({ user, children }: any) => <><h1>{user.username}</h1>{children}</>
}));
vi.mock('@/modules/project/components/ProjectCard', () => ({
    ProjectCard: () => null, ProjectCardSkeleton: () => null
}));
vi.mock('@/modules/project/components/dialogs/ReportModal', () => ({ ReportModal: () => null }));

const org = { id: 'org-id', username: 'ExampleOrg', accountType: 'ORGANIZATION' };
const member = { id: 'member-id', username: 'liamsystems_1', accountType: 'USER' };

function CurrentPath() {
    const location = useLocation();
    return <output>{location.pathname}{location.search}{location.hash}</output>;
}

describe('UserProfile navigation', () => {
    let container: HTMLDivElement;
    let root: Root;

    beforeEach(() => {
        vi.useFakeTimers();
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        vi.mocked(api.get).mockImplementation(async (url) => {
            if (url === '/user/profile/ExampleOrg') return { data: org };
            if (url === '/user/profile/liamsystems_1' || url === '/user/profile/member-id') return { data: member };
            if (url === '/orgs/org-id/members') return { data: [member] };
            if (url === '/users/member-id/organizations') return { data: [org] };
            if (url.startsWith('/creators/')) return { data: { content: [], totalPages: 0, totalElements: 0 } };
            throw new Error(`Unexpected request: ${url}`);
        });
    });

    afterEach(async () => {
        await act(async () => root.unmount());
        container.remove();
        vi.useRealTimers();
        vi.mocked(api.get).mockReset();
    });

    async function render(path: string) {
        await act(async () => root.render(
            <MemoryRouter initialEntries={[path]}>
                <CurrentPath />
                <Routes>
                    {['/creator/:id', '/user/:id'].map(route => (
                        <Route key={route} path={route} element={<UserProfile onBack={() => {}} likedModIds={[]} onToggleFavorite={() => {}} currentUser={null} />} />
                    ))}
                </Routes>
            </MemoryRouter>
        ));
        await act(async () => { await vi.advanceTimersByTimeAsync(350); });
    }

    it('stays on a member profile after following its organization link, and can return', async () => {
        await render('/creator/ExampleOrg');
        let resolveMember!: (response: any) => void;
        vi.mocked(api.get).mockImplementationOnce(() => new Promise(resolve => { resolveMember = resolve; }));
        await act(async () => {
            container.querySelector<HTMLAnchorElement>('a[href="/creator/liamsystems_1"]')!.click();
        });
        expect(container.querySelector('output')?.textContent).toBe('/creator/liamsystems_1');
        await act(async () => { resolveMember({ data: member }); });
        await act(async () => { await vi.advanceTimersByTimeAsync(1000); });
        expect(container.querySelector('output')?.textContent).toBe('/creator/liamsystems_1');
        expect(container.querySelector('h1')?.textContent).toBe('liamsystems_1');

        await act(async () => {
            container.querySelector<HTMLAnchorElement>('a[href="/creator/ExampleOrg"]')!.click();
        });
        await act(async () => { await vi.advanceTimersByTimeAsync(1000); });
        expect(container.querySelector('output')?.textContent).toBe('/creator/ExampleOrg');
        expect(container.querySelector('h1')?.textContent).toBe('ExampleOrg');
    });

    it('still canonicalizes a legacy ID URL and preserves its query and hash', async () => {
        await render('/user/member-id?tab=projects#published');
        expect(container.querySelector('output')?.textContent).toBe('/creator/liamsystems_1?tab=projects#published');
    });
});
