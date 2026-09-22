import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { User } from '@/types';
import { CreateProject } from '@/modules/project/views/CreateProject';

const mocks = vi.hoisted(() => ({ post: vi.fn(), get: vi.fn(), navigate: vi.fn() }));
vi.mock('@/utils/api', () => ({ api: { get: mocks.get, post: mocks.post }, extractApiErrorMessage: (_: unknown, fallback: string) => fallback }));
vi.mock('react-router-dom', async importOriginal => ({ ...await importOriginal<typeof import('react-router-dom')>(), useNavigate: () => mocks.navigate }));

let container: HTMLDivElement;
let root: Root;
const button = (text: string) => [...container.querySelectorAll('button')].find(node => node.textContent?.includes(text))!;
describe('CreateProject', () => {
    beforeEach(async () => {
        vi.resetAllMocks();
        mocks.get.mockResolvedValue({ data: [] });
        mocks.post.mockResolvedValue({ data: { id: 'draft-1', slug: 'my-mod' } });
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        await act(async () => root.render(<MemoryRouter><CreateProject currentUser={{ id: 'user-1', username: 'Creator', emailVerified: true } as User} onNavigate={() => {}} onRefresh={() => {}} /></MemoryRouter>));
    });
    afterEach(async () => { await act(async () => root.unmount()); container.remove(); });

    it('creates a regular project draft from the upload page', async () => {
        expect(container.textContent).not.toContain('CurseForge');
        expect(container.querySelector('#curseforge-reference')).toBeNull();
        await act(async () => button('Plugins').click());
        expect(container.textContent).toContain("Let's give it a name.");
        const inputs = container.querySelectorAll('input');
        await act(async () => {
            for (const [input, value] of [[inputs[0], 'My Mod'], [inputs[1], 'A new Hytale mod.']] as const) {
                Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!.call(input, value);
                input.dispatchEvent(new Event('input', { bubbles: true }));
            }
        });
        expect(mocks.post).not.toHaveBeenCalled();
        await act(async () => button('Start Building').click());
        const payload = mocks.post.mock.calls[0][1] as FormData;
        expect(Object.fromEntries(payload.entries())).toEqual({
            title: 'My Mod', description: 'A new Hytale mod.', classification: 'PLUGIN', owner: 'user-1',
        });
        expect(mocks.navigate).toHaveBeenCalled();
    });
});
